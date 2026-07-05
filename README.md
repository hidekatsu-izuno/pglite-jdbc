# PGlite-JDBC

pglite-jdbc is a library that enables calling pglite (https://github.com/electric-sql/pglite) through a JDBC interface. The JDBC call interface is compatible with pgjdbc (https://github.com/pgjdbc/pgjdbc).

## Requirements

- Java 21
- Maven

## Dependencies

- PGlite WASM artifacts bundled in the application classpath.
- Endive runtime/WASM/WASI modules for executing the bundled PostgreSQL WASM.
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

## Build

```sh
mise x -- mvn compile
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
