#!/usr/bin/env python3
"""Refresh bundled Wasmer C API libraries using the version pinned in pom.xml."""
import argparse
import hashlib
import json
import pathlib
import tarfile
import urllib.request
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent.parent
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
VERSION = ET.parse(ROOT / 'pom.xml').findtext('m:properties/m:wasmer.version', namespaces=NS)
DEST = ROOT / 'src/main/resources/io/github/hidekatsu_izuno/pglite_jdbc/wasmer/native'
PLATFORMS = {'linux-amd64': 'libwasmer-headless.so', 'linux-aarch64': 'libwasmer-headless.so',
             'darwin-arm64': 'libwasmer-headless.dylib', 'windows-amd64': 'wasmer-headless.dll'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--platform', action='append', choices=PLATFORMS)
    parser.add_argument('--cache-dir', type=pathlib.Path, default=ROOT / 'tmp/wasmer-downloads')
    args = parser.parse_args()
    args.cache_dir.mkdir(parents=True, exist_ok=True)
    manifest_path = DEST / 'manifest.json'
    manifest = json.loads(manifest_path.read_text()) if manifest_path.exists() else {}
    if manifest.get('version') != VERSION:
        manifest = {'version': VERSION, 'libraries': {}}
    for platform in args.platform or PLATFORMS:
        filename = PLATFORMS[platform]
        release_platform = 'windows-gnu64' if platform == 'windows-amd64' else platform
        url = f'https://github.com/wasmerio/wasmer/releases/download/v{VERSION}/wasmer-{release_platform}.tar.gz'
        archive = args.cache_dir / f'wasmer-{VERSION}-{release_platform}.tar.gz'
        if not archive.exists():
            partial = archive.with_suffix('.partial')
            urllib.request.urlretrieve(url, partial)
            partial.replace(archive)
        with tarfile.open(archive) as bundle:
            matches = [m for m in bundle if pathlib.PurePosixPath(m.name).name == filename and m.isfile()]
            if len(matches) != 1:
                raise RuntimeError(f'Expected exactly one {filename} in {url}')
            payload = bundle.extractfile(matches[0]).read()
        target = DEST / platform / filename
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(payload)
        manifest['libraries'][platform] = {'file': filename, 'source': url,
                                            'sha256': hashlib.sha256(payload).hexdigest()}
        print(f'{platform}: {len(payload)} bytes', flush=True)
    (DEST / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')


if __name__ == '__main__':
    main()
