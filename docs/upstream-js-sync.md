# PGlite JavaScript compatibility audit

The Java APIs were checked against PGlite **0.5.8**, commit
[`ae182ff8bd5ba4acb887d6c925d607a1498aa0b5`](https://github.com/electric-sql/pglite/tree/ae182ff8bd5ba4acb887d6c925d607a1498aa0b5),
on 2026-09-29. Upstream `main` pointed to the same commit at the time of the audit.
The scope is JavaScript correctness fixes affecting the existing Java APIs.
The bundled official WASM, initdb, data and extensions are pinned to **0.5.8**,
with matching Wasmer artifacts for all four supported platforms. Browser/worker
APIs remain outside the Java migration scope.

| Upstream change | Java handling |
| --- | --- |
| [Reset protocol state after malformed packets](https://github.com/electric-sql/pglite/commit/a290741) | Reset the retained buffer and offsets when packet decoding throws. Test a fragmented malformed DataRow followed by ReadyForQuery on the same parser. |
| [Reject closed transaction handles](https://github.com/electric-sql/pglite/commit/7e784a4) | Mark the handle closed before rolling back a failed callback. Keep guards on query, exec, sql, rollback and listen. Transaction sql now calls the internal query method, matching upstream, instead of deadlocking on the transaction semaphore it already holds. |
| [Positional formatQuery parameters](https://github.com/electric-sql/pglite/commit/354f4ae) | Replace the Java-only literal substitution with upstream Parse/Describe/Sync and PostgreSQL format(), using `%n$L` and inferred parameter types. Test out-of-order/repeated parameters, quoted text, arrays, bytea and use inside a transaction. |
| [Command and rowCount results](https://github.com/electric-sql/pglite/commit/219af1e) | Add `Results.command()` and `Results.rowCount()`. Keep the existing record constructor, `commandTag()`, `commandRowCount()` and cumulative `affectedRows()` for JDBC compatibility. A tag without a count returns null from the new rowCount accessor. |
| [NULL array elements](https://github.com/electric-sql/pglite/commit/2aa4d1a) | Port upstream's quote-aware array parsing. Unquoted NULL becomes null; quoted "NULL", empty strings, nested arrays and escapes remain intact. Parser state is local to each invocation for Java concurrency. |
| [Untyped parameters](https://github.com/electric-sql/pglite/commit/0720cb6) | Retain generic value serialization and serialize Instant/java.util.Date as UTC ISO strings when inferred as text. |
| [BigInt JSON serialization](https://github.com/electric-sql/pglite/commit/21fc995) | Java/Jackson already serializes BigInteger without JS's BigInt exception or floating-point conversion. Preserve numeric JSON and verify adjacent integers above 2^53 through json_to_recordset. |
| [Raw response handling](https://github.com/electric-sql/pglite/commit/2ccbb4c) | Java already has separate raw-stream handling and finally-based state restoration. Correct the socket-write callback to acknowledge the bytes consumed, rather than buffer capacity. Verify raw stream → parsed stream → regular query. |
| [Do not inherit user FS into initdb](https://github.com/electric-sql/pglite/commit/6b6f28d) | Already covered by independent WasmProcess children. Add a custom filesystem test that rejects a second init() and verify create(options) initializes it exactly once. |
| [create(undefined, options)](https://github.com/electric-sql/pglite/commit/69b7d87) | The existing Java create(PGliteOptions) overload has no ambiguous first argument and already preserves options. Covered by the custom-filesystem test. |
| [Do not write process.exitCode](https://github.com/electric-sql/pglite/commit/c771db3) and Node/Electron environment detection | Java does not use the Node process object or those environment branches. The 0.5.8 WASM adds `emscripten_exit_with_live_runtime`: the shared Java host reads `pgl_getPGliteExitStatus` and unwinds with the existing status-carrying exception, preserving the instance for startup (99) and query-error recovery (100). |
| [convert_to fix](https://github.com/electric-sql/pglite/commit/2f9cf75) | Included by upgrading the official WASM and compiled artifacts to 0.5.8. Test ASCII and non-ASCII LATIN1 bytea output, invalid/untranslatable input SQLSTATEs, and subsequent query execution on both runtimes. |

The official encoding modules import `pg_utf8_islegal`,
`report_invalid_encoding` and `report_untranslatable_char`, which the main WASM
does not export. The shared Java host ports their PostgreSQL implementations
from `src/common/wchar.c`, `src/common/encnames.c` and
`src/backend/utils/mb/mbutils.c` at postgres-pglite commit
`b133782cd759f08b3aeb263b80a963b39c7b7af1`. Error reporting calls PostgreSQL's
`errstart`/`errcode`/`errmsg`/`errfinish`, preserving guest error recovery.
The official WASM files remain unmodified.

The data importer now accepts exponent notation in minified byte offsets
(e.g. `5705e3`) so the America/Santarem and America/Santiago timezone files
are retained. The integrity test checks both entries as well as every file hash.

The new `spi.tar.gz` bundle is registered as `spi`, including its `autoinc`,
`insert_username`, `moddatetime` and `refint` modules. A trigger test exercises
`autoinc` through the same dynamic linker on both runtimes.

Regression tests are in `UpstreamJsUnitTest` and `UpstreamJsIntegrationTest`.
The integration tests share a database so that the same scenarios can run under
both Wasmer and Endive without repeated database initialization:

```sh
PGLITE_TEST_HEAP=768m bash scripts/test-low-resource.sh \
  '-Dtest=UpstreamJsUnitTest,UpstreamJsIntegrationTest' -Dpglite.force_wasmer=true
PGLITE_TEST_HEAP=1800m bash scripts/test-low-resource.sh \
  '-Dtest=UpstreamJsUnitTest,UpstreamJsIntegrationTest' -Dpglite.force_endive=true
```
