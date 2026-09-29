#!/usr/bin/env python3
"""Install unmodified official PGlite WASM and data from a pinned npm release."""
import base64
from decimal import Decimal
import hashlib
import io
import json
import pathlib
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
VERSION = '0.5.8'
DEST = ROOT / 'src/main/resources/io/github/hidekatsu_izuno/pglite_jdbc/pglite/release'


def unpack(archive, target):
    with tarfile.open(fileobj=io.BytesIO(archive), mode='r:gz') as tar:
        tar.extractall(target, filter='data')


def main():
    url = f'https://registry.npmjs.org/@electric-sql/pglite/{VERSION}'
    with urllib.request.urlopen(url) as response:
        metadata = json.load(response)
    with urllib.request.urlopen(metadata['dist']['tarball']) as response:
        archive = response.read()
    integrity = 'sha512-' + base64.b64encode(hashlib.sha512(archive).digest()).decode()
    if integrity != metadata['dist']['integrity']:
        raise ValueError('npm package integrity mismatch')
    with tempfile.TemporaryDirectory() as temporary:
        temp = pathlib.Path(temporary)
        unpack(archive, temp)
        source = temp / 'package/dist'
        output = temp / 'release'
        output.mkdir()
        for name in ('pglite.wasm', 'initdb.wasm'):
            shutil.copyfile(source / name, output / name)
        for bundle in sorted(source.glob('*.tar.gz')):
            unpack(bundle.read_bytes(), output / bundle.name)
        # Emscripten embeds byte offsets for the unmodified pglite.data package.
        glue = (source / 'pglite.js').read_text()
        entries = re.findall(r'\{filename:"([^"]+)",start:([^,}]+),end:([^,}]+)\}', glue)
        if not entries:
            raise ValueError('No Emscripten data manifest found')
        data = (source / 'pglite.data').read_bytes()
        for name, start, end in entries:
            if not name.startswith('/pglite/'):
                continue
            path = output / name.removeprefix('/pglite/')
            if not path.resolve().is_relative_to(output.resolve()):
                raise ValueError(f'Unsafe data path: {name}')
            path.parent.mkdir(parents=True, exist_ok=True)
            start, end = Decimal(start), Decimal(end)
            if start != int(start) or end != int(end) or not 0 <= start <= end <= len(data):
                raise ValueError(f'Invalid data offsets: {name}: {start}, {end}')
            path.write_bytes(data[int(start):int(end)])
        manifest = {'package': metadata['name'], 'version': VERSION,
                    'tarball': metadata['dist']['tarball'], 'integrity': integrity,
                    'files': {str(p.relative_to(output)): hashlib.sha256(p.read_bytes()).hexdigest()
                              for p in sorted(output.rglob('*')) if p.is_file()}}
        (output / 'upstream-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
        if DEST.exists():
            shutil.rmtree(DEST)
        shutil.copytree(output, DEST)
    subprocess.run([sys.executable, str(ROOT / 'scripts/emscripten-trampolines.py')], check=True)
    print(f'Installed official @electric-sql/pglite@{VERSION}; regenerate Wasmer artifacts next.')


if __name__ == '__main__':
    main()
