# PGlite-JDBC

pglite-jdbc is a library that enables calling pglite (https://github.com/electric-sql/pglite) through a JDBC interface. The JDBC call interface is compatible with pgjdbc (https://github.com/pgjdbc/pgjdbc).

## Dependencies

- wasmer C API (`libwasmer.so`) + JNA bridge.
- pgjdbc public API: `org.postgresql:postgresql:42.7.3` (for `org.postgresql.*` compatibility types).

WASM runtime properties:

- `pglite.wasmer.lib.path`: absolute path override for `libwasmer.so` (or compatible `libwasmer*.so`).
- `pglite.wasmer.trace`: enables Wasmer native loader diagnostics.
- `pglite.wasmer.perf.tests=true`: enables perf-only test suite (`@Tag("wasmer-perf")`).

The runtime is Wasmer-only. Startup runs Wasmer import/export preflight before initialization.
Wasmer module lookup prefers `native/linux-x86_64/pglite.wasmu` (loaded via `wasm_module_deserialize`) and falls back to `pglite.wasm`.

## Migration from pgjdbc

You can migrate existing pgjdbc-based code with minimal application changes:

1. Change JDBC URL to `jdbc:pglite:...`.
2. Use `io.github.hidekatsu_izuno.pglite_jdbc.Driver` as the JDBC driver class.
3. Existing `org.postgresql.PGConnection/PGStatement/PGResultSetMetaData` casts are supported.

Supported `org.postgresql.PGConnection` extensions:

- `getCopyAPI()` (`COPY IN/OUT`, text/csv)
- `getLargeObjectAPI()` (LO SQL-function based path)
- `getFastpathAPI()` (LO-internal calls only; generic fastpath calls are rejected)
- `getPreferQueryMode()/getAutosave()/setAutosave(...)`

Property aliases supported for easier migration:

- `defaultRowFetchSize` (alias of `defaultFetchSize`)
- `queryTimeout`
- `autosave`
- `preferQueryMode`
- `currentSchema`
- `ApplicationName`

Not supported in this compatibility scope:

- remote PostgreSQL connection path
- replication, SSL/GSS/socket-based features
- `org.postgresql.ds.*` class-name compatibility (use `io.github.hidekatsu_izuno.pglite_jdbc.ds.PGSimpleDataSource`)

## License

PGlite-JDBC is dual-licensed under the terms of the [Apache License 2.0](https://github.com/electric-sql/pglite/blob/main/LICENSE) and the [PostgreSQL License](https://github.com/electric-sql/pglite/blob/main/POSTGRES-LICENSE), you can choose which you prefer.

Changes to the [Postgres source](https://github.com/electric-sql/postgres-wasm) are licensed under the PostgreSQL License.
