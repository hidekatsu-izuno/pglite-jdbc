Bundled Wasmer native runtime for Linux x86_64.

Required files:
- libwasmer-headless.so
- libwasmer-headless.so.sha256
- pglite.wasmu
- LICENSE

Runtime behavior:
- Default loader extracts and validates `libwasmer-headless.so` using the `.sha256` file.
- `-Dpglite.wasmer.lib.path=...` overrides bundled loading.
