#!/usr/bin/env python3
"""Compile classpath WASM modules for the bundled Wasmer headless runtimes."""
import argparse
import gzip
import hashlib
import json
import os
import pathlib
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent.parent
RESOURCES = ROOT / 'src/main/resources/io/github/hidekatsu_izuno/pglite_jdbc'
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
VERSION = ET.parse(ROOT / 'pom.xml').findtext('m:properties/m:wasmer.version', namespaces=NS)
TARGETS = {'linux-amd64': ('x86_64-unknown-linux-gnu', 'cranelift'),
           'linux-aarch64': ('aarch64-unknown-linux-gnu', 'cranelift'),
           'darwin-arm64': ('aarch64-apple-darwin', 'cranelift'),
           # Official PGlite uses Emscripten longjmp, not WASM EH.
           'windows-amd64': ('x86_64-pc-windows-gnu', 'cranelift')}


def main():
    os.environ.setdefault("RAYON_NUM_THREADS", "1")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--wasmer', default='wasmer', help='Wasmer CLI with Cranelift and LLVM')
    parser.add_argument('--platform', action='append', choices=TARGETS)
    parser.add_argument('--force', action='store_true', help='Regenerate existing artifacts')
    args = parser.parse_args()
    version = subprocess.check_output([args.wasmer, '--version'], text=True)
    if version.split()[1] != VERSION:
        parser.error(f'Expected Wasmer {VERSION}, got {version.strip()}')
    for platform in args.platform or TARGETS:
        target, compiler = TARGETS[platform]
        dest = RESOURCES / 'wasmer/compiled' / VERSION / platform
        dest.mkdir(parents=True, exist_ok=True)
        previous_path = dest / 'manifest.json'
        if previous_path.exists():
            previous = json.loads(previous_path.read_text())
            if previous.get('target') != target or previous.get('compiler') != compiler:
                for entry in previous.get('modules', {}).values():
                    (dest / entry['artifact']).unlink(missing_ok=True)
        manifest = {'version': VERSION, 'target': target, 'compiler': compiler, 'modules': {}}
        for source in sorted((RESOURCES / 'pglite/release').rglob('*')):
            if not source.is_file() or source.suffix not in ('.wasm', '.so'):
                continue
            if source.read_bytes()[:4] != b'\0asm':
                continue
            digest = hashlib.sha256(source.read_bytes()).hexdigest()
            output = dest / (digest + '.wasmu.gz')
            if args.force or not output.exists():
                with tempfile.TemporaryDirectory(prefix='pglite-aot-') as temp:
                    artifact = pathlib.Path(temp) / 'module.wasmu'
                    subprocess.run([args.wasmer, 'compile', '--' + compiler, '--target', target,
                                    '--enable-exceptions', str(source), '-o', str(artifact)], check=True)
                    temporary_output = output.with_suffix('.partial')
                    temporary_output.write_bytes(gzip.compress(artifact.read_bytes(), mtime=0))
                    temporary_output.replace(output)
            manifest['modules'][str(source.relative_to(RESOURCES))] = {
                'wasm_sha256': digest, 'artifact': output.name,
                'artifact_sha256': hashlib.sha256(output.read_bytes()).hexdigest()}
            print(f'{platform}: {source.name}', flush=True)
        for stale in dest.glob('*.wasmu.gz'):
            if stale.name not in {entry['artifact'] for entry in manifest['modules'].values()}:
                stale.unlink()
        (dest / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')


if __name__ == '__main__':
    main()
