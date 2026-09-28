# PGlite-JDBC

pglite-jdbc is a library that enables calling pglite (https://github.com/electric-sql/pglite) through a JDBC interface. The JDBC call interface is compatible with pgjdbc (https://github.com/pgjdbc/pgjdbc).

## Requirements

- Java 21 or newer
- Maven

## Dependencies

- Unmodified WASM and data from official `@electric-sql/pglite@0.5.3`, bundled in the application classpath.
- Endive runtime/WASM/WASI modules (legacy WASI inputs only).
- Wasmer 7.4.2 headless C-API shared libraries, accessed through JNA's JNI bridge.
- Precompiled Wasmer modules bundled alongside the original PGlite WASM resources.
- pgjdbc public API: `org.postgresql:postgresql` (for `org.postgresql.*` compatibility types).
- Jackson databind for JSON handling.

## Usage

The driver is registered through `META-INF/services/java.sql.Driver`, so it can
be used directly with `DriverManager` when the jar is on the classpath:

```java
try (var connection = DriverManager.getConnection("jdbc:pglite:")) {
    try (var statement = connection.prepareStatement("SELECT ?::int4 AS value")) {
        statement.setInt(1, 7);
        try (var resultSet = statement.executeQuery()) {
            resultSet.next();
            System.out.println(resultSet.getInt(1));
        }
    }
}
```

`PGSimpleDataSource` is also available:

```java
var dataSource = new io.github.hidekatsu_izuno.pglite_jdbc.ds.PGSimpleDataSource();
dataSource.setUrl("jdbc:pglite:");

try (var connection = dataSource.getConnection()) {
    // Use the connection through the standard JDBC API.
}
```

The JDBC URL format is:

```text
jdbc:pglite:[dataDir][?key=value&...]
```

When `dataDir` is omitted, PGlite uses an in-memory filesystem. Use
`memory://...` for an explicit in-memory database, or a filesystem path such as
`jdbc:pglite:/tmp/pglite-db` or `jdbc:pglite:file:///tmp/pglite-db` for a
persistent database.

Common connection properties:

- `user` (default: `postgres`)
- `database` (default: `template1`)
- `dataDir`
- `debug`
- `relaxedDurability`
- `defaultRowFetchSize` (alias of `defaultFetchSize`)
- `queryTimeout`
- `autosave`
- `preferQueryMode`
- `currentSchema`
- `ApplicationName`

Runtime diagnostic system properties:

- `pglite.trace_init`
- `pglite.trace_protocol`
- `pglite.trace_host_calls`
- `pglite.trace_wasi_calls`
- `pglite.trace_env_calls`
- `pglite.trace_exec`
- `pglite.native_call_timeout_ms`
- `pglite.force_wasmer` (require Wasmer; do not fall back to Endive)
- `pglite.force_endive` (legacy WASI inputs only; incompatible with the bundled official WASM)
- `pglite.wasmer.library` (absolute path to an external Wasmer C-API library; overrides the bundled library)

Wasmer headless runs the official Emscripten binaries on Linux x86-64/AArch64,
macOS ARM64, and Windows x86-64. Native libraries and precompiled modules are
loaded from the classpath. JNA uses JNI to call Wasmer's C API; no Wasmer CLI,
Node.js, or JavaScript engine is needed at runtime.

Headless libraries deserialize compiled modules keyed by the original WASM
SHA-256, Wasmer version and OS/CPU. All targets use Cranelift. The Java host
implements Emscripten's filesystem, dynamic linking, function-table calls and
longjmp ABI. Initialization executes the separate official `initdb.wasm` and
isolated PostgreSQL child instances sharing the database files.

Small generated WASM trampolines adapt Wasmer's C callbacks to function-table
entries. They are separate from the unchanged upstream binaries. The official
extension files retain their `.so` filenames; their contents are WebAssembly.

The official Emscripten ABI requires Wasmer. A missing native library or compiled
artifact produces an explicit error; Endive supports the previous WASI ABI only.
An external compiler-enabled Wasmer library can compile the WASM at runtime.

## Updating PGlite

The upstream version is pinned in `scripts/update-pglite.py`. The updater verifies
npm's SHA-512 package integrity, installs the original main/initdb/extension WASM
and matching data files, and records their SHA-256 hashes in
`pglite/release/upstream-manifest.json`. It also generates the host trampolines:

```sh
python3 scripts/update-pglite.py
RAYON_NUM_THREADS=1 python3 scripts/compile-wasmer.py --wasmer /path/to/wasmer
```

Updating PGlite may require corresponding Emscripten ABI changes in the Java host.
The runtime loads all resources from the classpath and makes no package downloads.

## Updating Wasmer

The version is pinned by `wasmer.version` in `pom.xml`. Refresh the official
headless libraries, then compile the bundled main module and extensions using
that exact version of the Wasmer CLI with Cranelift enabled:

```sh
python3 scripts/update-wasmer.py
RAYON_NUM_THREADS=1 python3 scripts/compile-wasmer.py --wasmer /path/to/wasmer
```

Both scripts accept repeatable `--platform` arguments. Downloads are cached in
`tmp/wasmer-downloads`. Each output directory contains a manifest recording its
source/target and SHA-256 hashes. Compiled modules must be regenerated when the
WASM resources or Wasmer version change. Old compiled modules are removed after
each successful platform update.

## Build

```sh
mise x -- mvn compile
```

For tests on Linux/WSL, use the low-resource runner. It sets low CPU priority,
uses one CPU and one test JVM at a time, limits Java heap sizes, and disables
JUnit/Wasmer parallel execution. It uses an 8 MiB thread stack for the Endive
interpreter. It isolates Maven output in `tmp/test-build`
to avoid IDE compiler interference and stops the test process group above 3 GiB
of resident memory (`PGLITE_TEST_RSS_LIMIT_MIB` overrides the limit). The default
heap is 384 MiB. The complete suite's artifact inspection tests parse the main
module with Endive and require a larger heap:

```sh
PGLITE_TEST_HEAP=1536m bash scripts/test-low-resource.sh -Dpglite.force_wasmer=true
```

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

Not supported in this compatibility scope:

- remote PostgreSQL connection path
- replication, SSL/GSS/socket-based features
- `org.postgresql.ds.*` class-name compatibility (use `io.github.hidekatsu_izuno.pglite_jdbc.ds.PGSimpleDataSource`)

## License

PGlite-JDBC follows PGlite's licensing and is dual-licensed under the terms of
the [Apache License 2.0](LICENSE) and the [PostgreSQL License](POSTGRES-LICENSE);
you may choose either license.

Changes to the [Postgres source](https://github.com/electric-sql/postgres-wasm)
are licensed under the PostgreSQL License.

<!--
- mvn verify
- git tag vX.XX.X && git push origin --tags
- cancel: git tag -d vX.XX.X && git push origin :refs/tags/vX.XX.X
- mvn -Prelease clean deploy
-->
