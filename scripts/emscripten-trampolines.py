#!/usr/bin/env python3
"""Build typed WASM trampolines for Wasmer's dynamically typed C callbacks.

The upstream binaries are never rewritten. Imports with an address in the guest
function table must be WASM functions, because Wasmer's dynamic C callbacks do
not provide funcref trampolines. These wrappers simply forward their arguments.
"""
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
DEST = ROOT / 'src/main/resources/io/github/hidekatsu_izuno/pglite_jdbc/pglite/release'


def leb(n):
    result = bytearray()
    while True:
        byte = n & 127
        n >>= 7
        result.append(byte | (128 if n else 0))
        if not n:
            return bytes(result)


def string(s):
    data = s.encode()
    return leb(len(data)) + data


class Reader:
    def __init__(self, data):
        self.data, self.pos = data, 0

    def byte(self):
        b = self.data[self.pos]
        self.pos += 1
        return b

    def number(self):
        result = shift = 0
        while True:
            b = self.byte()
            result |= (b & 127) << shift
            if b < 128:
                return result
            shift += 7

    def take(self, count):
        start = self.pos
        self.pos += count
        return self.data[start:self.pos]

    def string(self):
        return self.take(self.number()).decode()


def sections(data):
    reader = Reader(data[8:])
    while reader.pos < len(reader.data):
        kind = reader.byte()
        yield kind, reader.take(reader.number())


def imports(data):
    types = []
    for kind, contents in sections(data):
        r = Reader(contents)
        if kind == 1:
            for _ in range(r.number()):
                assert r.byte() == 96
                params = r.take(r.number())
                results = r.take(r.number())
                types.append((params, results))
        if kind == 2:
            for _ in range(r.number()):
                module, name, kind = r.string(), r.string(), r.byte()
                if kind == 0:
                    yield module, name, types[r.number()]
                elif kind == 3:
                    r.take(2)
                elif kind in (1, 2):
                    if kind == 1:
                        r.byte()
                    flags = r.number()
                    r.number()
                    if flags & 1:
                        r.number()
                elif kind == 4:
                    r.byte(); r.number()
                else:
                    raise ValueError(kind)


def module(entries):
    entries = list(entries)
    types = []
    for _, _, signature in entries:
        if signature not in types:
            types.append(signature)
    def section(kind, items):
        payload = leb(len(items)) + b''.join(items)
        return bytes([kind]) + leb(len(payload)) + payload
    result = b'\0asm\1\0\0\0'
    result += section(1, [b'\x60' + leb(len(p)) + p + leb(len(r)) + r for p, r in types])
    result += section(2, [string(m) + string(n) + b'\0' + leb(types.index(t)) for m, n, t in entries])
    result += section(3, [leb(types.index(t)) for m, n, t in entries])
    result += section(7, [string(m + '.' + n) + b'\0' + leb(len(entries) + i) for i, (m, n, t) in enumerate(entries)])
    bodies = []
    for index, (_, _, (params, results)) in enumerate(entries):
        body = b'\0' + b''.join(b'\x20' + leb(i) for i in range(len(params))) + b'\x10' + leb(index) + b'\x0b'
        bodies.append(leb(len(body)) + body)
    return result + section(10, bodies)


def main():
    target = DEST / 'host'
    target.mkdir(exist_ok=True)
    entries = {}
    for path in sorted(DEST.rglob('*')):
        if not path.is_file() or target in path.parents or path.suffix not in ('.wasm', '.so'):
            continue
        data = path.read_bytes()
        if data[:4] != b'\0asm':
            continue
        for m, n, signature in imports(data):
            if (m, n) in entries and entries[m, n] != signature:
                raise ValueError(f'Conflicting import signature: {m}.{n}')
            entries[m, n] = signature
    # Include all imports, including extension-only imports. Most extension
    # symbols resolve to the main module and do not use these host exports.
    (target / 'emscripten.wasm').write_bytes(module((m, n, t) for (m, n), t in sorted(entries.items())))
    for count in (1, 2):
        (target / f'callback-{count}.wasm').write_bytes(module([('java', 'callback', (b'\x7f' * count, b'\x7f'))]))


if __name__ == '__main__':
    main()
