package io.github.hidekatsu_izuno.pglite_jdbc;

import run.endive.wasm.Parser;
import run.endive.wasm.WasmModule;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.extensionCatalog;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.index;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.pglite;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.templating;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.types;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.utils;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class PGlitePortedTest {
    @Test
    void basicExecReturnsEachStatementResult() {
        try (var db = closeable(new pglite())) {
            var pg = db.pg();
            pg.execSync(
                """
                CREATE TABLE IF NOT EXISTS test (
                  id SERIAL PRIMARY KEY,
                  name TEXT
                );
                """,
                null
            );

            var results = pg.execSync(
                """
                INSERT INTO test (name) VALUES ('test');
                UPDATE test SET name = 'test2';
                SELECT * FROM test;
                """,
                null
            );

            assertEquals(3, results.size());
            assertEquals(1, results.get(0).affectedRows());
            assertEquals(2, results.get(1).affectedRows());
            assertEquals(List.of(Map.of("id", 1.0, "name", "test2")), results.get(2).rows());
            assertEquals("id", results.get(2).fields().get(0).name());
            assertEquals(23, results.get(2).fields().get(0).dataTypeID());
            assertEquals("name", results.get(2).fields().get(1).name());
            assertEquals(25, results.get(2).fields().get(1).dataTypeID());
        }
    }

    @Test
    void basicQuerySupportsParamsAndFields() {
        try (var db = closeable(new pglite())) {
            var pg = db.pg();
            pg.querySync(
                """
                CREATE TABLE IF NOT EXISTS test (
                  id SERIAL PRIMARY KEY,
                  name TEXT
                );
                """,
                null,
                null
            );
            pg.querySync("INSERT INTO test (name) VALUES ($1);", new Object[] { "test2" }, null);

            var result = pg.<Map<String, Object>>querySync("SELECT * FROM test;", null, null);

            assertEquals(List.of(Map.of("id", 1.0, "name", "test2")), result.rows());
            assertEquals(0, result.affectedRows());
            assertEquals("id", result.fields().get(0).name());
            assertEquals(23, result.fields().get(0).dataTypeID());
            assertEquals("name", result.fields().get(1).name());
            assertEquals(25, result.fields().get(1).dataTypeID());
        }
    }

    @Test
    void queryRowModeArrayReturnsRowsAsArrays() {
        try (var db = closeable(new pglite())) {
            var pg = db.pg();
            var options = new interface_.QueryOptions(
                interface_.RowMode.array,
                null,
                null,
                null,
                null,
                null
            );

            var result = pg.<Object>querySync("SELECT 1 AS one, 'two' AS two;", null, options);

            assertEquals(List.of(List.of(1.0, "two")), result.rows());
            assertEquals("one", result.fields().get(0).name());
            assertEquals("two", result.fields().get(1).name());
            assertEquals(0, result.affectedRows());
        }
    }

    @Test
    void describeQueryReturnsParameterAndResultTypes() {
        try (var db = closeable(new pglite())) {
            var pg = db.pg();
            pg.querySync(
                """
                CREATE TABLE users (
                  id INTEGER PRIMARY KEY,
                  name TEXT,
                  age INTEGER,
                  active BOOLEAN
                )
                """,
                null,
                null
            );

            var description = pg.describeQuerySync(
                "SELECT name, age FROM users WHERE id = $1 AND active = $2",
                null
            );

            assertEquals(2, description.queryParams().size());
            assertEquals(23, description.queryParams().get(0).dataTypeID());
            assertEquals(16, description.queryParams().get(1).dataTypeID());
            assertTrue(description.queryParams().get(0).serializer() != null);
            assertTrue(description.queryParams().get(1).serializer() != null);

            assertEquals(2, description.resultFields().size());
            assertEquals("name", description.resultFields().get(0).name());
            assertEquals(25, description.resultFields().get(0).dataTypeID());
            assertEquals("age", description.resultFields().get(1).name());
            assertEquals(23, description.resultFields().get(1).dataTypeID());
            assertTrue(description.resultFields().get(0).parser() != null);
            assertTrue(description.resultFields().get(1).parser() != null);
        }
    }

    @Test
    void describeQueryHandlesQueriesWithNoParametersOrResults() {
        try (var db = closeable(new pglite())) {
            var pg = db.pg();

            var description = pg.describeQuerySync("SELECT 1", null);

            assertEquals(0, description.queryParams().size());
            assertEquals(1, description.resultFields().size());
            assertEquals(23, description.resultFields().get(0).dataTypeID());
        }
    }

    @Test
    void describeQueryHandlesInsertQueries() {
        try (var db = closeable(new pglite())) {
            var pg = db.pg();
            pg.querySync(
                """
                CREATE TABLE test (
                  id INTEGER PRIMARY KEY,
                  value TEXT
                );
                """,
                null,
                null
            );

            var description = pg.describeQuerySync("INSERT INTO test (id, value) VALUES ($1, $2)", null);

            assertEquals(2, description.queryParams().size());
            assertEquals(23, description.queryParams().get(0).dataTypeID());
            assertEquals(25, description.queryParams().get(1).dataTypeID());
            assertEquals(0, description.resultFields().size());
        }
    }

    @Test
    void describeQueryHandlesInvalidQueries() {
        try (var db = closeable(new pglite())) {
            var pg = db.pg();

            var error = assertThrows(
                RuntimeException.class,
                () -> pg.describeQuerySync("SELECT * FROM nonexistent_table", null)
            );
            assertTrue(
                error.getMessage().contains("relation \"nonexistent_table\" does not exist"),
                () -> error.getClass().getName() + ": " + error.getMessage()
            );
        }
    }

    @Test
    void notifyListenUnlistenAndGlobalNotificationCallbacks() {
        try (var db = closeable(new pglite())) {
            var pg = db.pg();
            var payloads = new ArrayList<String>();
            var unsubscribe = pg.listen("test", payloads::add, null).join();

            pg.execSync("NOTIFY test, '321'", null);
            assertEquals(List.of("321"), payloads);

            unsubscribe.apply(null).join();
            pg.execSync("NOTIFY test, 'ignored'", null);
            assertEquals(List.of("321"), payloads);

            var global = new ArrayList<String>();
            pg.onNotification((channel, payload) -> global.add(channel + ":" + payload));
            pg.execSync("LISTEN test_global", null);
            pg.execSync("NOTIFY test_global, '123'", null);
            assertEquals(List.of("test_global:123"), global);
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void arrayParamsRoundTripJsonAndTextArrays() {
        try (var db = closeable(new pglite())) {
            var pg = db.pg();
            pg.querySync(
                """
                CREATE TABLE IF NOT EXISTS test (
                  id SERIAL PRIMARY KEY,
                  json JSONB,
                  array_text TEXT[]
                );
                """,
                null,
                null
            );

            pg.querySync(
                "INSERT INTO test (json, array_text) VALUES ($1, $2);",
                new Object[] { List.of("hello", "world"), List.of("yolo", "fam") },
                null
            );

            var result = pg.<Map<String, Object>>querySync(
                "SELECT * FROM test WHERE id = ANY($1);",
                new Object[] { List.of(0, 1, 2, 3) },
                null
            );

            assertEquals(1, result.rows().size());
            assertEquals(1.0, result.rows().get(0).get("id"));
            assertEquals(List.of("hello", "world"), result.rows().get(0).get("json"));
            assertEquals(List.of("yolo", "fam"), result.rows().get(0).get("array_text"));
            assertEquals(3802, result.fields().get(1).dataTypeID());
            assertEquals(1009, result.fields().get(2).dataTypeID());
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void transactionRollbackMatchesBasicTest() {
        try (var db = closeable(new pglite())) {
            var pg = db.pg();
            pg.querySync(
                """
                CREATE TABLE IF NOT EXISTS test (
                  id SERIAL PRIMARY KEY,
                  name TEXT
                );
                """,
                null,
                null
            );
            pg.querySync("INSERT INTO test (name) VALUES ('test');", null, null);

            pg.transaction(tx -> {
                tx.query("INSERT INTO test (name) VALUES ('test2');", null, null).join();
                var inTx = tx.<Map<String, Object>>query("SELECT * FROM test;", null, null).join();
                assertEquals(List.of(Map.of("id", 1.0, "name", "test"), Map.of("id", 2.0, "name", "test2")), inTx.rows());
                return tx.rollback();
            }).join();

            var afterRollback = pg.<Map<String, Object>>querySync("SELECT * FROM test;", null, null);
            assertEquals(List.of(Map.of("id", 1.0, "name", "test")), afterRollback.rows());
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void denoBasicTypesRoundTripScalarsArraysNullsAndBytea() {
        try (var db = closeable(new pglite())) {
            var pg = db.pg();
            pg.querySync(
                """
                CREATE TABLE IF NOT EXISTS test (
                  id SERIAL PRIMARY KEY,
                  text TEXT,
                  number INT,
                  float FLOAT,
                  bigint BIGINT,
                  bool BOOLEAN,
                  date DATE,
                  timestamp TIMESTAMP,
                  json JSONB,
                  blob BYTEA,
                  array_text TEXT[],
                  array_number INT[],
                  nested_array_float FLOAT[][],
                  test_null INT,
                  test_undefined INT
                );
                """,
                null,
                null
            );

            pg.querySync(
                """
                INSERT INTO test (
                  text, number, float, bigint, bool, date, timestamp, json, blob,
                  array_text, array_number, nested_array_float, test_null, test_undefined
                )
                VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14);
                """,
                new Object[] {
                    "test",
                    1,
                    1.5,
                    9223372036854775807L,
                    true,
                    "2021-01-01",
                    "2021-01-01T12:00:00",
                    Map.of("test", "test"),
                    new byte[] { 1, 2, 3 },
                    List.of("test1", "test2", "test,3"),
                    List.of(1, 2, 3),
                    List.of(List.of(1.1, 2.2), List.of(3.3, 4.4)),
                    null,
                    null,
                },
                null
            );

            var result = pg.<Map<String, Object>>querySync("SELECT * FROM test;", null, null);
            assertEquals(1, result.rows().size());
            var row = result.rows().get(0);
            assertEquals("test", row.get("text"));
            assertEquals(1.0, row.get("number"));
            assertEquals(1.5, row.get("float"));
            assertEquals(9223372036854775807L, row.get("bigint"));
            assertEquals(true, row.get("bool"));
            assertEquals(Instant.parse("2021-01-01T00:00:00Z"), row.get("date"));
            assertEquals(Instant.parse("2021-01-01T12:00:00Z"), row.get("timestamp"));
            assertEquals(Map.of("test", "test"), row.get("json"));
            assertArrayEquals(new byte[] { 1, 2, 3 }, (byte[]) row.get("blob"));
            assertEquals(List.of("test1", "test2", "test,3"), row.get("array_text"));
            assertEquals(List.of(1.0, 2.0, 3.0), row.get("array_number"));
            assertEquals(List.of(List.of(1.1, 2.2), List.of(3.3, 4.4)), row.get("nested_array_float"));
            assertEquals(null, row.get("test_null"));
            assertEquals(null, row.get("test_undefined"));
            assertEquals(List.of(23, 25, 23, 701, 20, 16, 1082, 1114, 3802, 17, 1009, 1007, 1022, 23, 23),
                result.fields().stream().map(interface_.Field::dataTypeID).toList());
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void denoBasicCopyToAndFromBlobDevice() {
        try (var db = closeable(new pglite())) {
            var pg = db.pg();
            pg.execSync(
                """
                CREATE TABLE IF NOT EXISTS test (
                  id SERIAL PRIMARY KEY,
                  test TEXT
                );
                INSERT INTO test (test) VALUES ('test'), ('test2');
                """,
                null
            );

            var copyTo = pg.<Map<String, Object>>querySync("COPY test TO '/dev/blob' WITH (FORMAT csv);", null, null);
            assertEquals("1,test\n2,test2\n", new String(copyTo.blob(), java.nio.charset.StandardCharsets.UTF_8));

            pg.execSync(
                """
                CREATE TABLE IF NOT EXISTS test2 (
                  id SERIAL PRIMARY KEY,
                  test TEXT
                );
                """,
                null
            );
            pg.querySync(
                "COPY test2 FROM '/dev/blob' WITH (FORMAT csv);",
                new Object[] {},
                new interface_.QueryOptions(null, null, null, copyTo.blob(), null, null)
            );

            var result = pg.<Map<String, Object>>querySync("SELECT * FROM test2;", null, null);
            assertEquals(List.of(
                Map.of("id", 1.0, "test", "test"),
                Map.of("id", 2.0, "test", "test2")
            ), result.rows());
        }
    }

    @Test
    void denoBasicClosedDatabaseRejectsFurtherQueries() {
        var pg = new pglite();
        pg.waitReady().join();
        pg.querySync(
            """
            CREATE TABLE IF NOT EXISTS test (
              id SERIAL PRIMARY KEY,
              name TEXT
            );
            """,
            null,
            null
        );
        pg.close().join();

        var error = assertThrows(RuntimeException.class, () -> pg.querySync("SELECT * FROM test;", null, null));
        assertTrue(error.getMessage().contains("PGlite is closed"));
    }

    @Test
    void targetRuntimeFilesystemPersistsAcrossReopen() throws IOException {
        var dataDir = Files.createTempDirectory("pglite-target-runtime-fs-").resolve("pgdata").toString();
        try (var db = closeable(new pglite(dataDir))) {
            var pg = db.pg();
            pg.execSync(
                """
                CREATE TABLE IF NOT EXISTS test (
                  id SERIAL PRIMARY KEY,
                  name TEXT
                );
                INSERT INTO test (name) VALUES ('test');
                UPDATE test SET name = 'test2';
                """,
                null
            );
        }

        try (var reopened = closeable(new pglite(dataDir))) {
            var result = reopened.pg().execSync("SELECT * FROM test;", null);
            assertEquals(1, result.size());
            assertEquals(List.of(Map.of("id", 1.0, "name", "test2")), result.get(0).rows());
            assertEquals(0, result.get(0).affectedRows());
        }
    }

    @Test
    void queryHandlesRepresentativeLargePayloads() {
        try (var db = closeable(new pglite())) {
            var pg = db.pg();
            pg.execSync(
                """
                CREATE TABLE IF NOT EXISTS size_test (
                  id SERIAL PRIMARY KEY,
                  data TEXT
                );
                """,
                null
            );

            var testData = "a".repeat(8 * 1024);
            pg.querySync("INSERT INTO size_test (data) VALUES ($1);", new Object[] { testData }, null);
            var result = pg.<Map<String, Object>>querySync(
                "SELECT * FROM size_test WHERE data = $1;",
                new Object[] { testData },
                null
            );

            assertEquals(1, result.rows().size());
            assertEquals(testData, result.rows().get(0).get("data"));
            assertEquals(8 * 1024, ((String) result.rows().get(0).get("data")).length());
        }
    }

    @Test
    void querySizesExecQueryParamsGeneratedDataAndRowCounts() {
        try (var db = closeable(new pglite())) {
            var pg = db.pg();
            pg.execSync(
                """
                CREATE TABLE IF NOT EXISTS size_test (
                  id SERIAL PRIMARY KEY,
                  data TEXT
                );
                """,
                null
            );

            var execData = "a".repeat(8 * 1024);
            var execResults = pg.execSync(
                "INSERT INTO size_test (data) VALUES ('" + execData + "'); SELECT * FROM size_test;",
                null
            );
            assertEquals(2, execResults.size());
            assertEquals(1, execResults.get(1).rows().size());
            assertEquals(execData, execResults.get(1).rows().get(0).get("data"));

            var paramData = "a".repeat(100 * 1024);
            pg.querySync("INSERT INTO size_test (data) VALUES ($1);", new Object[] { paramData }, null);
            var paramResult = pg.<Map<String, Object>>querySync(
                "SELECT * FROM size_test WHERE data = $1;",
                new Object[] { paramData },
                null
            );
            assertEquals(1, paramResult.rows().size());
            assertEquals(paramData.length(), ((String) paramResult.rows().get(0).get("data")).length());

            var generated = pg.<Map<String, Object>>querySync("SELECT 1 as id, repeat('a', 1024) as data;", null, null);
            assertEquals(1, generated.rows().size());
            assertEquals(1024, ((String) generated.rows().get(0).get("data")).length());

            var manyRows = pg.<Map<String, Object>>querySync(
                "SELECT generate_series(1, 1000) as id, repeat('a', 100) as data;",
                null,
                null
            );
            assertEquals(1000, manyRows.rows().size());
            assertEquals(100, ((String) manyRows.rows().get(0).get("data")).length());
            assertEquals(100, ((String) manyRows.rows().get(999).get("data")).length());
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void contribPgStatStatementsCanLoadExtension() {
        var options = new pglite.PGliteOptions();
        options.extensions = Map.of("pg_stat_statements", index.extension("pg_stat_statements"));
        try (var db = closeable(new pglite(options))) {
            var pg = db.pg();
            pg.execSync("CREATE EXTENSION IF NOT EXISTS pg_stat_statements;", null);
            var result = pg.<Map<String, Object>>querySync(
                "SELECT extname FROM pg_extension WHERE extname = 'pg_stat_statements';",
                null,
                null
            );
            assertEquals(1, result.rows().size());
            assertEquals("pg_stat_statements", result.rows().get(0).get("extname"));
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void contribUuidOsspGeneratesExpectedUuidValues() {
        var options = new pglite.PGliteOptions();
        options.extensions = Map.of("uuid-ossp", index.extension("uuid_ossp"));
        try (var db = closeable(new pglite(options))) {
            var pg = db.pg();
            pg.execSync("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";", null);
            var generated = pg.<Map<String, Object>>querySync("SELECT uuid_generate_v4() as value;", null, null);
            assertEquals(36, ((String) generated.rows().get(0).get("value")).length());

            var namespace = pg.<Map<String, Object>>querySync("SELECT uuid_ns_dns() as value;", null, null);
            assertEquals("6ba7b810-9dad-11d1-80b4-00c04fd430c8", namespace.rows().get(0).get("value"));

            var nil = pg.<Map<String, Object>>querySync("SELECT uuid_nil() as value;", null, null);
            assertEquals("00000000-0000-0000-0000-000000000000", nil.rows().get(0).get("value"));
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void contribPgcryptoDigestHmacAndRandomBytes() {
        var options = new pglite.PGliteOptions();
        options.extensions = Map.of("pgcrypto", index.extension("pgcrypto"));
        try (var db = closeable(new pglite(options))) {
            var pg = db.pg();
            pg.execSync("CREATE EXTENSION IF NOT EXISTS pgcrypto;", null);
            var digest = pg.<Map<String, Object>>querySync(
                "SELECT encode(digest(convert_to('test', 'UTF8'), 'sha1'), 'hex') as value;",
                null,
                null
            );
            assertEquals("a94a8fe5ccb19ba61c4c0873d391e987982fbbd3", digest.rows().get(0).get("value"));

            var hmac = pg.<Map<String, Object>>querySync(
                "SELECT encode(hmac(convert_to('test', 'UTF8'), convert_to('key', 'UTF8'), 'sha1'), 'hex') as value;",
                null,
                null
            );
            assertEquals("671f54ce0c540f78ffe1e26dcf9c2a047aea4fda", hmac.rows().get(0).get("value"));

            var randomBytes = pg.<Map<String, Object>>querySync(
                "SELECT length(gen_random_bytes(32)) as len;",
                null,
                null
            );
            assertEquals(32.0, randomBytes.rows().get(0).get("len"));
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void contribPgTrgmSupportsGinAndGistSimilarityQueries() {
        for (var indexMethod : List.of("gin", "gist")) {
            var options = new pglite.PGliteOptions();
            options.extensions = Map.of("pg_trgm", index.extension("pg_trgm"));
            try (var db = closeable(new pglite(options))) {
                var pg = db.pg();
                pg.execSync("CREATE EXTENSION IF NOT EXISTS pg_trgm;", null);
                pg.execSync(
                    """
                    CREATE TABLE IF NOT EXISTS test (
                      id SERIAL PRIMARY KEY,
                      name TEXT
                    );
                    """,
                    null
                );
                pg.execSync(
                    "CREATE INDEX IF NOT EXISTS test_name_trgm_idx ON test USING " + indexMethod + " (name " + indexMethod + "_trgm_ops);",
                    null
                );
                pg.execSync("INSERT INTO test (name) VALUES ('test1'), ('test2'), ('text3');", null);

                var rows = pg.<Map<String, Object>>querySync(
                    """
                    SELECT
                      name,
                      name % 'test' AS similarity,
                      name <-> 'test' AS distance
                    FROM test;
                    """,
                    null,
                    null
                );

                assertEquals(List.of(
                    Map.of("name", "test1", "similarity", true, "distance", 0.4285714),
                    Map.of("name", "test2", "similarity", true, "distance", 0.4285714),
                    Map.of("name", "text3", "similarity", false, "distance", 0.7777778)
                ), rows.rows());

                var similar = pg.<Map<String, Object>>querySync(
                    "SELECT name FROM test WHERE name % 'test';",
                    null,
                    null
                );
                assertEquals(List.of(Map.of("name", "test1"), Map.of("name", "test2")), similar.rows());
            }
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void contribCitextMatchesCaseInsensitively() {
        var options = new pglite.PGliteOptions();
        options.extensions = Map.of("citext", index.extension("citext"));
        try (var db = closeable(new pglite(options))) {
            var pg = db.pg();
            pg.execSync("CREATE EXTENSION IF NOT EXISTS citext;", null);
            pg.execSync(
                """
                CREATE TABLE IF NOT EXISTS test (
                  id SERIAL PRIMARY KEY,
                  name CITEXT
                );
                INSERT INTO test (name) VALUES ('tEsT1'), ('TeSt2'), ('TEST3');
                """,
                null
            );

            var result = pg.<Map<String, Object>>querySync("SELECT name FROM test WHERE name = 'test1';", null, null);
            assertEquals(List.of(Map.of("name", "tEsT1")), result.rows());
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void contribHstoreFiltersAndCastsToJson() {
        var options = new pglite.PGliteOptions();
        options.extensions = Map.of("hstore", index.extension("hstore"));
        try (var db = closeable(new pglite(options))) {
            var pg = db.pg();
            pg.execSync("CREATE EXTENSION IF NOT EXISTS hstore;", null);
            pg.execSync(
                """
                CREATE TABLE IF NOT EXISTS test (
                  id SERIAL PRIMARY KEY,
                  data HSTORE
                );
                INSERT INTO test (data) VALUES
                  ('"name" => "test1"'),
                  ('"name" => "test2"'),
                  ('"name" => "test3"');
                """,
                null
            );

            var result = pg.<Map<String, Object>>querySync(
                "SELECT data::JSONB FROM test WHERE data->'name' = 'test1';",
                null,
                null
            );
            assertEquals(List.of(Map.of("data", Map.of("name", "test1"))), result.rows());
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void contribIntarraySupportsArrayOperatorsAndQueryInt() {
        var options = new pglite.PGliteOptions();
        options.extensions = Map.of("intarray", index.extension("intarray"));
        try (var db = closeable(new pglite(options))) {
            var pg = db.pg();
            pg.execSync("CREATE EXTENSION IF NOT EXISTS intarray;", null);
            pg.execSync(
                """
                CREATE TABLE articles (
                  id SERIAL PRIMARY KEY,
                  title TEXT NOT NULL,
                  tag_ids INTEGER[]
                );
                INSERT INTO articles (title, tag_ids) VALUES
                  ('Postgres Performance Tips', '{1,2,3}'),
                  ('Introduction to SQL', '{2,4}'),
                  ('Advanced intarray Usage', '{1,3,5}'),
                  ('Database Normalization', '{4,6}');
                """,
                null
            );

            var overlaps = pg.<Map<String, Object>>querySync(
                "SELECT title, tag_ids FROM articles WHERE tag_ids && '{2,5}'::integer[];",
                null,
                null
            );
            assertEquals(List.of(
                Map.of("title", "Postgres Performance Tips", "tag_ids", List.of(1.0, 2.0, 3.0)),
                Map.of("title", "Introduction to SQL", "tag_ids", List.of(2.0, 4.0)),
                Map.of("title", "Advanced intarray Usage", "tag_ids", List.of(1.0, 3.0, 5.0))
            ), overlaps.rows());

            var queryInt = pg.<Map<String, Object>>querySync(
                "SELECT title, tag_ids FROM articles WHERE tag_ids @@ '1 & (3|4)'::query_int;",
                null,
                null
            );
            assertEquals(List.of(
                Map.of("title", "Postgres Performance Tips", "tag_ids", List.of(1.0, 2.0, 3.0)),
                Map.of("title", "Advanced intarray Usage", "tag_ids", List.of(1.0, 3.0, 5.0))
            ), queryInt.rows());
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void contribFuzzystrmatchComputesLevenshteinAndSoundex() {
        var options = new pglite.PGliteOptions();
        options.extensions = Map.of("fuzzystrmatch", index.extension("fuzzystrmatch"));
        try (var db = closeable(new pglite(options))) {
            var pg = db.pg();
            pg.execSync("CREATE EXTENSION IF NOT EXISTS fuzzystrmatch;", null);

            var distance = pg.<Map<String, Object>>querySync(
                "SELECT levenshtein('kitten', 'sitting') AS distance;",
                null,
                null
            );
            assertEquals(List.of(Map.of("distance", 3.0)), distance.rows());

            var soundex = pg.<Map<String, Object>>querySync("SELECT soundex('kitten') AS soundex;", null, null);
            assertEquals(List.of(Map.of("soundex", "K350")), soundex.rows());
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void contribFileFdwReadsBundledAndCopiedFiles() {
        var options = new pglite.PGliteOptions();
        options.extensions = Map.of("file_fdw", index.extension("file_fdw"));
        try (var db = closeable(new pglite(options))) {
            var pg = db.pg();
            pg.execSync("CREATE EXTENSION IF NOT EXISTS file_fdw;", null);
            pg.execSync("CREATE SERVER file_server FOREIGN DATA WRAPPER file_fdw;", null);
            pg.execSync(
                """
                CREATE FOREIGN TABLE file_contents (line text)
                SERVER file_server
                OPTIONS (
                    filename '/pglite/bin/postgres',
                    format 'text'
                );
                """,
                null
            );

            var bundled = pg.<Map<String, Object>>querySync("SELECT * FROM file_contents;", null, null);
            assertEquals(List.of(Map.of("line", "PGlite is the best!")), bundled.rows());

            pg.Module().FS().writeFile(
                "/tmp/test.txt",
                "PGlite says hi!".getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );
            pg.execSync(
                """
                CREATE FOREIGN TABLE temp_test_file_contents (line text)
                SERVER file_server
                OPTIONS (
                    filename '/tmp/test.txt',
                    format 'text'
                );
                """,
                null
            );

            var copied = pg.<Map<String, Object>>querySync("SELECT * FROM temp_test_file_contents;", null, null);
            assertEquals(List.of(Map.of("line", "PGlite says hi!")), copied.rows());
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void contribUnaccentLexizesAccentedText() {
        var options = new pglite.PGliteOptions();
        options.extensions = Map.of("unaccent", index.extension("unaccent"));
        try (var db = closeable(new pglite(options))) {
            var pg = db.pg();
            pg.execSync("CREATE EXTENSION IF NOT EXISTS unaccent;", null);

            var result = pg.<Map<String, Object>>querySync("SELECT ts_lexize('unaccent','H\u00f4tel') AS ts_lexize;", null, null);
            assertEquals(List.of(Map.of("ts_lexize", List.of("Hotel"))), result.rows());
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void contribTablefuncRunsNormalRandAndCrosstab() {
        var options = new pglite.PGliteOptions();
        options.extensions = Map.of("tablefunc", index.extension("tablefunc"));
        try (var db = closeable(new pglite(options))) {
            var pg = db.pg();
            pg.execSync("CREATE EXTENSION IF NOT EXISTS tablefunc;", null);

            var randomRows = pg.<Map<String, Object>>querySync("SELECT * FROM normal_rand(10, 5, 3);", null, null);
            assertEquals(10, randomRows.rows().size());

            pg.execSync(
                """
                CREATE TABLE ct(id SERIAL, rowid TEXT, attribute TEXT, value TEXT);
                INSERT INTO ct(rowid, attribute, value) VALUES
                  ('test1','att1','val1'),
                  ('test1','att2','val2'),
                  ('test1','att3','val3'),
                  ('test1','att4','val4'),
                  ('test2','att1','val5'),
                  ('test2','att2','val6'),
                  ('test2','att3','val7'),
                  ('test2','att4','val8');
                """,
                null
            );

            var crosstab = pg.<Map<String, Object>>querySync(
                """
                SELECT *
                FROM crosstab(
                  'select rowid, attribute, value
                   from ct
                   where attribute = ''att2'' or attribute = ''att3''
                   order by 1,2')
                AS ct(row_name text, category_1 text, category_2 text, category_3 text);
                """,
                null,
                null
            );
            assertEquals(List.of(
                mapOfNullable("row_name", "test1", "category_1", "val2", "category_2", "val3", "category_3", null),
                mapOfNullable("row_name", "test2", "category_1", "val6", "category_2", "val7", "category_3", null)
            ), crosstab.rows());
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void contribBloomUsesBloomIndexForEqualityPlan() {
        var options = new pglite.PGliteOptions();
        options.extensions = Map.of("bloom", index.extension("bloom"));
        try (var db = closeable(new pglite(options))) {
            var pg = db.pg();
            pg.execSync("CREATE EXTENSION IF NOT EXISTS bloom;", null);
            pg.execSync(
                """
                CREATE TABLE IF NOT EXISTS test (
                  id SERIAL PRIMARY KEY,
                  name TEXT
                );
                CREATE INDEX IF NOT EXISTS test_name_bloom_idx ON test USING bloom (name);
                INSERT INTO test (name) VALUES ('test1'), ('test2'), ('test3');
                SET enable_seqscan = off;
                """,
                null
            );

            var selected = pg.<Map<String, Object>>querySync("SELECT name FROM test WHERE name = 'test1';", null, null);
            assertEquals(List.of(Map.of("name", "test1")), selected.rows());

            var plan = pg.<Map<String, Object>>querySync(
                "EXPLAIN ANALYZE SELECT name FROM test WHERE name = 'test1';",
                null,
                null
            );
            assertTrue(plan.rows().stream().anyMatch(row -> String.valueOf(row.get("QUERY PLAN")).contains("test_name_bloom_idx")));
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void contribBtreeGinAndGistUseExpectedIndexes() {
        for (var extensionName : List.of("btree_gin", "btree_gist")) {
            var indexType = extensionName.equals("btree_gin") ? "GIN" : "GIST";
            var indexName = "test_number_" + extensionName + "_idx";
            var options = new pglite.PGliteOptions();
            options.extensions = Map.of(extensionName, index.extension(extensionName));
            try (var db = closeable(new pglite(options))) {
                var pg = db.pg();
                pg.execSync("CREATE EXTENSION IF NOT EXISTS " + extensionName + ";", null);
                pg.execSync(
                    """
                    CREATE TABLE IF NOT EXISTS test (
                      id SERIAL PRIMARY KEY,
                      number int4
                    );
                    """,
                    null
                );
                pg.execSync("CREATE INDEX IF NOT EXISTS " + indexName + " ON test USING " + indexType + " (number);", null);
                pg.execSync("INSERT INTO test (number) VALUES (1), (2), (3);", null);

                var selected = pg.<Map<String, Object>>querySync("SELECT number FROM test WHERE number = 1;", null, null);
                assertEquals(List.of(Map.of("number", 1.0)), selected.rows());

                var plan = pg.<Map<String, Object>>querySync(
                    "EXPLAIN ANALYZE SELECT number FROM test WHERE number = 1;",
                    null,
                    null
                );
                assertTrue(plan.rows().stream().anyMatch(row -> String.valueOf(row.get("QUERY PLAN")).contains(indexName)));
            }
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void contribCubeCalculatesDistances() {
        var options = new pglite.PGliteOptions();
        options.extensions = Map.of("cube", index.extension("cube"));
        try (var db = closeable(new pglite(options))) {
            var pg = db.pg();
            pg.execSync("CREATE EXTENSION IF NOT EXISTS cube;", null);
            pg.execSync(
                """
                CREATE TABLE IF NOT EXISTS test (
                  id SERIAL PRIMARY KEY,
                  point CUBE
                );
                INSERT INTO test (point) VALUES ('(1, 2, 3)'), ('(4, 5, 6)'), ('(7, 8, 9)');
                """,
                null
            );

            var result = pg.<Map<String, Object>>querySync(
                "SELECT point, point <-> cube(array[1, 2, 3]) AS distance FROM test;",
                null,
                null
            );
            assertEquals(List.of(
                Map.of("point", "(1, 2, 3)", "distance", 0.0),
                Map.of("point", "(4, 5, 6)", "distance", 5.196152422706632),
                Map.of("point", "(7, 8, 9)", "distance", 10.392304845413264)
            ), result.rows());
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void contribEarthdistanceOrdersLocationsByDistance() {
        var options = new pglite.PGliteOptions();
        options.extensions = Map.of("cube", index.extension("cube"), "earthdistance", index.extension("earthdistance"));
        try (var db = closeable(new pglite(options))) {
            var pg = db.pg();
            pg.execSync("CREATE EXTENSION IF NOT EXISTS cube;", null);
            pg.execSync("CREATE EXTENSION IF NOT EXISTS earthdistance;", null);
            pg.execSync(
                """
                CREATE TABLE locations (
                  id SERIAL PRIMARY KEY,
                  name VARCHAR(100),
                  latitude DOUBLE PRECISION,
                  longitude DOUBLE PRECISION
                );
                INSERT INTO locations (name, latitude, longitude)
                VALUES
                  ('Location A', 40.7128, -74.0060),
                  ('Location B', 40.730610, -73.935242),
                  ('Location C', 34.052235, -118.243683),
                  ('Location D', 40.758896, -73.985130),
                  ('Location E', 51.507351, -0.127758);
                """,
                null
            );

            var result = pg.<Map<String, Object>>querySync(
                """
                SELECT
                  name,
                  earth_distance(
                    ll_to_earth(40.7128, -74.0060),
                    ll_to_earth(latitude, longitude)
                  ) AS distance
                FROM locations
                ORDER BY distance;
                """,
                null,
                null
            );
            assertEquals(List.of(
                Map.of("name", "Location A", "distance", 0.0),
                Map.of("name", "Location D", "distance", 5424.971028170555),
                Map.of("name", "Location B", "distance", 6290.327117342975),
                Map.of("name", "Location C", "distance", 3940171.3340000752),
                Map.of("name", "Location E", "distance", 5576493.70395964)
            ), result.rows());
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void contribLtreeQueriesDescendantPaths() {
        var options = new pglite.PGliteOptions();
        options.extensions = Map.of("ltree", index.extension("ltree"));
        try (var db = closeable(new pglite(options))) {
            var pg = db.pg();
            pg.execSync("CREATE EXTENSION IF NOT EXISTS ltree;", null);
            pg.execSync(
                """
                CREATE TABLE test (path ltree);
                INSERT INTO test VALUES ('Top');
                INSERT INTO test VALUES ('Top.Science');
                INSERT INTO test VALUES ('Top.Science.Astronomy');
                INSERT INTO test VALUES ('Top.Science.Astronomy.Astrophysics');
                INSERT INTO test VALUES ('Top.Science.Astronomy.Cosmology');
                INSERT INTO test VALUES ('Top.Hobbies');
                INSERT INTO test VALUES ('Top.Hobbies.Amateurs_Astronomy');
                INSERT INTO test VALUES ('Top.Collections');
                INSERT INTO test VALUES ('Top.Collections.Pictures');
                INSERT INTO test VALUES ('Top.Collections.Pictures.Astronomy');
                INSERT INTO test VALUES ('Top.Collections.Pictures.Astronomy.Stars');
                INSERT INTO test VALUES ('Top.Collections.Pictures.Astronomy.Galaxies');
                INSERT INTO test VALUES ('Top.Collections.Pictures.Astronomy.Astronauts');
                CREATE INDEX path_gist_idx ON test USING GIST (path);
                CREATE INDEX path_idx ON test USING BTREE (path);
                """,
                null
            );

            var result = pg.<Map<String, Object>>querySync("SELECT path FROM test WHERE path <@ 'Top.Science';", null, null);
            assertEquals(List.of(
                Map.of("path", "Top.Science"),
                Map.of("path", "Top.Science.Astronomy"),
                Map.of("path", "Top.Science.Astronomy.Astrophysics"),
                Map.of("path", "Top.Science.Astronomy.Cosmology")
            ), result.rows());
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void contribDictIntLexizesIntegers() {
        var options = new pglite.PGliteOptions();
        options.extensions = Map.of("dict_int", index.extension("dict_int"));
        try (var db = closeable(new pglite(options))) {
            var pg = db.pg();
            pg.execSync("CREATE EXTENSION IF NOT EXISTS dict_int;", null);

            var sixDigits = pg.<Map<String, Object>>querySync("SELECT ts_lexize('intdict', '511673') AS ts_lexize;", null, null);
            assertEquals(List.of(Map.of("ts_lexize", List.of("511673"))), sixDigits.rows());

            var threeDigits = pg.<Map<String, Object>>querySync("SELECT ts_lexize('intdict', '129') AS ts_lexize;", null, null);
            assertEquals(List.of(Map.of("ts_lexize", List.of("129"))), threeDigits.rows());

            var truncated = pg.<Map<String, Object>>querySync("SELECT ts_lexize('intdict', '40865854') AS ts_lexize;", null, null);
            assertEquals(List.of(Map.of("ts_lexize", List.of("408658"))), truncated.rows());
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void contribDictXsynLexizesConfiguredSynonyms() {
        var options = new pglite.PGliteOptions();
        options.extensions = Map.of("dict_xsyn", index.extension("dict_xsyn"));
        try (var db = closeable(new pglite(options))) {
            var pg = db.pg();
            pg.execSync("CREATE EXTENSION IF NOT EXISTS dict_xsyn;", null);
            pg.execSync(
                """
                ALTER TEXT SEARCH DICTIONARY xsyn (
                  RULES='xsyn_sample',
                  KEEPORIG=true,
                  MATCHORIG=true,
                  KEEPSYNONYMS=true,
                  MATCHSYNONYMS=false
                );
                """,
                null
            );

            var supernova = pg.<Map<String, Object>>querySync("SELECT ts_lexize('xsyn', 'supernova') AS ts_lexize;", null, null);
            assertEquals(List.of(Map.of("ts_lexize", List.of("supernova", "sn", "sne", "1987a"))), supernova.rows());

            var synonym = pg.<Map<String, Object>>querySync("SELECT ts_lexize('xsyn', 'sn') AS ts_lexize;", null, null);
            assertEquals(List.of(mapOfNullable("ts_lexize", null)), synonym.rows());

            var unknown = pg.<Map<String, Object>>querySync("SELECT ts_lexize('xsyn', 'grb') AS ts_lexize;", null, null);
            assertEquals(List.of(mapOfNullable("ts_lexize", null)), unknown.rows());
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void contribIsnNormalizesBookAndSerialNumbers() {
        var options = new pglite.PGliteOptions();
        options.extensions = Map.of("isn", index.extension("isn"));
        try (var db = closeable(new pglite(options))) {
            var pg = db.pg();
            pg.execSync("CREATE EXTENSION IF NOT EXISTS isn;", null);

            var isbn = pg.<Map<String, Object>>querySync("SELECT isbn('978-0-393-04002-9') AS isbn;", null, null);
            assertEquals(List.of(Map.of("isbn", "0-393-04002-X")), isbn.rows());

            var isbn13 = pg.<Map<String, Object>>querySync("SELECT isbn13('0901690546') AS isbn13;", null, null);
            assertEquals(List.of(Map.of("isbn13", "978-0-901690-54-8")), isbn13.rows());

            var issn = pg.<Map<String, Object>>querySync("SELECT issn('1436-4522') AS issn;", null, null);
            assertEquals(List.of(Map.of("issn", "1436-4522")), issn.rows());

            pg.execSync(
                """
                CREATE TABLE test (id isbn);
                INSERT INTO test VALUES('9780393040029');
                """,
                null
            );
            var stored = pg.<Map<String, Object>>querySync("SELECT * FROM test;", null, null);
            assertEquals(List.of(Map.of("id", "0-393-04002-X")), stored.rows());
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void contribSegParsesIntervalSyntax() {
        var options = new pglite.PGliteOptions();
        options.extensions = Map.of("seg", index.extension("seg"));
        try (var db = closeable(new pglite(options))) {
            var pg = db.pg();
            pg.execSync("CREATE EXTENSION IF NOT EXISTS seg;", null);

            var ph = pg.<Map<String, Object>>querySync("SELECT '6.25 .. 6.50'::seg AS \"pH\";", null, null);
            assertEquals(List.of(Map.of("pH", "6.25 .. 6.50")), ph.rows());

            var set = pg.<Map<String, Object>>querySync("SELECT '7(+-)1'::seg AS \"set\";", null, null);
            assertEquals(List.of(Map.of("set", "6 .. 8")), set.rows());
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void contribTcnPublishesTriggeredChangeNotifications() {
        var options = new pglite.PGliteOptions();
        options.extensions = Map.of("tcn", index.extension("tcn"));
        try (var db = closeable(new pglite(options))) {
            var pg = db.pg();
            var payloads = new ArrayList<String>();
            pg.listen("tcn", payloads::add, null).join();
            pg.execSync("CREATE EXTENSION IF NOT EXISTS tcn;", null);
            pg.execSync(
                """
                CREATE TABLE test (
                  id SERIAL PRIMARY KEY,
                  name TEXT
                );
                CREATE TRIGGER test_tcn
                AFTER INSERT OR UPDATE OR DELETE ON test
                FOR EACH ROW
                EXECUTE FUNCTION triggered_change_notification();
                """,
                null
            );

            pg.execSync("INSERT INTO test (name) VALUES ('test1');", null);
            assertEquals(List.of("\"test\",I,\"id\"='1'"), payloads);
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void contribTsmSystemRowsAndTimeSampleTables() {
        for (var extensionName : List.of("tsm_system_rows", "tsm_system_time")) {
            var options = new pglite.PGliteOptions();
            options.extensions = Map.of(extensionName, index.extension(extensionName));
            try (var db = closeable(new pglite(options))) {
                var pg = db.pg();
                pg.execSync("CREATE EXTENSION IF NOT EXISTS " + extensionName + ";", null);
                pg.execSync(
                    """
                    CREATE TABLE test (
                      id SERIAL PRIMARY KEY,
                      name TEXT
                    );
                    INSERT INTO test (name)
                    SELECT 'test' || i
                    FROM generate_series(1, 10) AS i;
                    """,
                    null
                );

                var sampleMethod = extensionName.equals("tsm_system_rows") ? "SYSTEM_ROWS(5)" : "SYSTEM_TIME(50)";
                var expectedCount = extensionName.equals("tsm_system_rows") ? 5 : 10;
                var result = pg.<Map<String, Object>>querySync("SELECT * FROM test TABLESAMPLE " + sampleMethod + ";", null, null);
                assertEquals(expectedCount, result.rows().size());
            }
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void contribLoManagesLargeObjectLifecycle() {
        var options = new pglite.PGliteOptions();
        options.extensions = Map.of("lo", index.extension("lo"));
        try (var db = closeable(new pglite(options))) {
            var pg = db.pg();
            var text = "hello world";
            pg.execSync("CREATE EXTENSION IF NOT EXISTS lo;", null);
            pg.execSync(
                """
                CREATE TABLE test (id SERIAL PRIMARY KEY, data OID);
                CREATE TRIGGER test_data_lo BEFORE UPDATE OR DELETE ON test
                FOR EACH ROW EXECUTE FUNCTION lo_manage(data);
                """,
                null
            );

            pg.querySync(
                "INSERT INTO test (data) VALUES (lo_import('/dev/blob'));",
                new Object[] {},
                new interface_.QueryOptions(null, null, null, text.getBytes(java.nio.charset.StandardCharsets.UTF_8), null, null)
            );
            var result = pg.<Map<String, Object>>querySync("SELECT lo_export(data, '/dev/blob') AS data FROM test;", null, null);
            assertArrayEquals(text.getBytes(java.nio.charset.StandardCharsets.UTF_8), result.blob());

            pg.execSync("DELETE FROM test;", null);
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void contribAmcheckChecksCatalogBtreeIndexes() {
        var options = new pglite.PGliteOptions();
        options.extensions = Map.of("amcheck", index.extension("amcheck"));
        try (var db = closeable(new pglite(options))) {
            var pg = db.pg();
            pg.execSync("CREATE EXTENSION IF NOT EXISTS amcheck;", null);

            var result = pg.<Map<String, Object>>querySync(
                """
                SELECT bt_index_check(index => c.oid, heapallindexed => i.indisunique) AS bt_index_check,
                       c.relname,
                       c.relpages
                FROM pg_index i
                JOIN pg_opclass op ON i.indclass[0] = op.oid
                JOIN pg_am am ON op.opcmethod = am.oid
                JOIN pg_class c ON i.indexrelid = c.oid
                JOIN pg_namespace n ON c.relnamespace = n.oid
                WHERE am.amname = 'btree' AND n.nspname = 'pg_catalog'
                AND c.relpersistence != 't'
                AND c.relkind = 'i' AND i.indisready AND i.indisvalid
                ORDER BY c.relpages DESC LIMIT 10;
                """,
                null,
                null
            );
            assertEquals(10, result.rows().size());
            assertTrue(result.rows().stream().allMatch(row -> "".equals(row.get("bt_index_check"))));
            assertTrue(result.rows().stream().anyMatch(row -> "pg_proc_proname_args_nsp_index".equals(row.get("relname"))));
            assertTrue(result.rows().stream().anyMatch(row -> "pg_attribute_relid_attnam_index".equals(row.get("relname"))));
            assertTrue(result.rows().stream().allMatch(row -> ((Number) row.get("relpages")).doubleValue() > 0.0));
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void contribAutoExplainEmitsExecutorEndNotice() {
        var options = new pglite.PGliteOptions();
        options.extensions = Map.of("auto_explain", index.extension("auto_explain"));
        try (var db = closeable(new pglite(options))) {
            var pg = db.pg();
            pg.execSync(
                """
                LOAD 'auto_explain';
                SET auto_explain.log_min_duration = '0';
                SET auto_explain.log_analyze = 'true';
                SET auto_explain.log_level = 'NOTICE';
                """,
                null
            );

            var notices = new ArrayList<messages.NoticeMessage>();
            pg.querySync(
                """
                SELECT count(*)
                FROM pg_class, pg_index
                WHERE oid = indrelid AND indisunique;
                """,
                null,
                new interface_.QueryOptions(null, null, null, null, notices::add, null)
            );
            assertTrue(notices.stream().anyMatch(notice -> "explain_ExecutorEnd".equals(notice.routine())));
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void contribPageinspectReadsHeapPageMetadata() {
        var options = new pglite.PGliteOptions();
        options.extensions = Map.of("pageinspect", index.extension("pageinspect"));
        try (var db = closeable(new pglite(options))) {
            var pg = db.pg();
            pg.execSync("CREATE EXTENSION IF NOT EXISTS pageinspect;", null);
            pg.execSync(
                """
                CREATE TABLE pageinspect_test (
                  id serial PRIMARY KEY,
                  name text,
                  value integer
                );
                INSERT INTO pageinspect_test (name, value)
                SELECT 'row_' || g, (random() * 100)::int
                FROM generate_series(1, 5) AS g;
                CHECKPOINT;
                """,
                null
            );

            var classInfo = pg.<Map<String, Object>>querySync(
                "SELECT relfilenode, relname FROM pg_class WHERE relname = 'pageinspect_test';",
                null,
                null
            );
            assertEquals(List.of(Map.of("relname", "pageinspect_test")), classInfo.rows().stream().map(row -> Map.of("relname", row.get("relname"))).toList());

            var items = pg.<Map<String, Object>>querySync(
                "SELECT * FROM heap_page_items(get_raw_page('pageinspect_test', 0));",
                null,
                null
            );
            assertFalse(items.rows().isEmpty());

            var header = pg.<Map<String, Object>>querySync(
                "SELECT * FROM page_header(get_raw_page('pageinspect_test', 0));",
                null,
                null
            );
            assertEquals(1, header.rows().size());
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void contribPgBuffercacheReportsSummaryAndUsageCounts() {
        var options = new pglite.PGliteOptions();
        options.extensions = Map.of("pg_buffercache", index.extension("pg_buffercache"));
        try (var db = closeable(new pglite(options))) {
            var pg = db.pg();
            pg.execSync("CREATE EXTENSION IF NOT EXISTS pg_buffercache;", null);

            var buffers = pg.<Map<String, Object>>querySync(
                """
                SELECT n.nspname, c.relname, count(*) AS buffers
                FROM pg_buffercache b JOIN pg_class c
                ON b.relfilenode = pg_relation_filenode(c.oid) AND
                   b.reldatabase IN (0, (SELECT oid FROM pg_database
                                         WHERE datname = current_database()))
                JOIN pg_namespace n ON n.oid = c.relnamespace
                GROUP BY n.nspname, c.relname
                ORDER BY 3 DESC
                LIMIT 10;
                """,
                null,
                null
            );
            assertEquals(10, buffers.rows().size());

            var summary = pg.<Map<String, Object>>querySync("SELECT * FROM pg_buffercache_summary();", null, null);
            assertEquals(1, summary.rows().size());

            var usageCounts = pg.<Map<String, Object>>querySync("SELECT * FROM pg_buffercache_usage_counts();", null, null);
            assertFalse(usageCounts.rows().isEmpty());
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void contribPgFreespacemapReportsFreeSpace() {
        var options = new pglite.PGliteOptions();
        options.extensions = Map.of("pg_freespacemap", index.extension("pg_freespacemap"));
        try (var db = closeable(new pglite(options))) {
            var pg = db.pg();
            pg.execSync("CREATE EXTENSION IF NOT EXISTS pg_freespacemap;", null);
            pg.execSync(
                """
                CREATE TABLE test_fsm(id serial PRIMARY KEY, data text);
                INSERT INTO test_fsm (data) SELECT repeat('x', 100) FROM generate_series(1, 1000);
                DELETE FROM test_fsm WHERE id <= 500;
                """,
                null
            );

            var freeSpace = pg.<Map<String, Object>>querySync("SELECT * FROM pg_freespace('test_fsm');", null, null);
            assertFalse(freeSpace.rows().isEmpty());

            var freeSpace0 = pg.<Map<String, Object>>querySync("SELECT pg_freespace('test_fsm', 0);", null, null);
            assertFalse(freeSpace0.rows().isEmpty());
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void contribPgSurgeryCanCreateExtension() {
        var options = new pglite.PGliteOptions();
        options.extensions = Map.of("pg_surgery", index.extension("pg_surgery"));
        try (var db = closeable(new pglite(options))) {
            db.pg().execSync("CREATE EXTENSION IF NOT EXISTS pg_surgery;", null);
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void contribPgVisibilityReportsVisibilityMapState() {
        var options = new pglite.PGliteOptions();
        options.extensions = Map.of("pg_visibility", index.extension("pg_visibility"));
        try (var db = closeable(new pglite(options))) {
            var pg = db.pg();
            pg.execSync("CREATE EXTENSION IF NOT EXISTS pg_visibility;", null);
            pg.execSync(
                """
                CREATE TABLE IF NOT EXISTS test (
                  id SERIAL PRIMARY KEY,
                  name TEXT
                );
                INSERT INTO test (name) VALUES ('test');
                UPDATE test SET name = 'test2';
                SELECT * FROM test;
                """,
                null
            );

            var visible = pg.<Map<String, Object>>querySync(
                "SELECT * FROM pg_visibility('test') WHERE all_visible = false;",
                null,
                null
            );
            assertEquals(1, visible.rows().size());
            var visibleRow = visible.rows().get(0);
            assertEquals(0, ((Number) visibleRow.get("blkno")).intValue());
            assertEquals(false, visibleRow.get("all_visible"));
            assertEquals(false, visibleRow.get("all_frozen"));
            assertEquals(false, visibleRow.get("pd_all_visible"));

            var visibilityMap = pg.<Map<String, Object>>querySync("SELECT * FROM pg_visibility_map('test');", null, null);
            assertEquals(1, visibilityMap.rows().size());
            var visibilityMapRow = visibilityMap.rows().get(0);
            assertEquals(0, ((Number) visibilityMapRow.get("blkno")).intValue());
            assertEquals(false, visibilityMapRow.get("all_visible"));
            assertEquals(false, visibilityMapRow.get("all_frozen"));

            var frozen = pg.<Map<String, Object>>querySync(
                "SELECT * FROM pg_visibility('test') WHERE all_frozen = true;",
                null,
                null
            );
            assertEquals(List.of(), frozen.rows());
        }
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void contribPgWalinspectReadsWalBlockInfoBetweenLsns() {
        var options = new pglite.PGliteOptions();
        options.extensions = Map.of("pg_walinspect", index.extension("pg_walinspect"));
        try (var db = closeable(new pglite(options))) {
            var pg = db.pg();
            pg.execSync("CREATE EXTENSION IF NOT EXISTS pg_walinspect;", null);
            pg.execSync(
                """
                CREATE TABLE test_wal (
                  id SERIAL PRIMARY KEY,
                  data TEXT
                );
                """,
                null
            );

            var before = pg.<Map<String, Object>>querySync("SELECT pg_current_wal_lsn() AS before_lsn;", null, null);
            pg.execSync(
                """
                INSERT INTO test_wal(data)
                SELECT 'row ' || generate_series::text
                FROM generate_series(1,5);
                """,
                null
            );
            var after = pg.<Map<String, Object>>querySync("SELECT pg_current_wal_lsn() AS after_lsn;", null, null);
            var infos = pg.<Map<String, Object>>querySync(
                """
                SELECT * FROM pg_get_wal_block_info($1, $2)
                ORDER BY start_lsn, block_id
                LIMIT 200;
                """,
                new Object[] { before.rows().get(0).get("before_lsn"), after.rows().get(0).get("after_lsn") },
                null
            );
            assertFalse(infos.rows().isEmpty());
        }
    }

    @Test
    void instantiationSupportsDefaultsDataDirAndOptions() throws IOException {
        try (var defaultDb = closeable(new pglite())) {
            var result = defaultDb.pg().<Map<String, Object>>querySync("SELECT 1 as one;", null, null);
            assertEquals(1.0, result.rows().get(0).get("one"));
        }

        var dataDir = Files.createTempDirectory("pglite-instantiation-").toString();
        try (var dataDirDb = closeable(new pglite(dataDir))) {
            var result = dataDirDb.pg().<Map<String, Object>>querySync("SELECT 1 as one;", null, null);
            assertEquals(1.0, result.rows().get(0).get("one"));
        }

        var options = new pglite.PGliteOptions();
        options.dataDir = Files.createTempDirectory("pglite-instantiation-options-").toString();
        try (var optionsDb = closeable(new pglite(options))) {
            var result = optionsDb.pg().<Map<String, Object>>querySync("SELECT 1 as one;", null, null);
            assertEquals(1.0, result.rows().get(0).get("one"));
        }
    }

    @Test
    void userSwitchingHonorsUsernameOptionAndPrivileges() throws IOException {
        var dataDir = Files.createTempDirectory("pglite-user-").toString();
        try (var db = closeable(new pglite(dataDir))) {
            var pg = db.pg();
            pg.execSync("CREATE USER test_user WITH PASSWORD 'md5abdbecd56d5fbd2cdaee3d0fa9e4f434';", null);
            pg.execSync(
                """
                CREATE TABLE test (
                  id SERIAL PRIMARY KEY,
                  number INT
                );
                INSERT INTO test (number) VALUES (42);
                CREATE TABLE test2 (
                  id SERIAL PRIMARY KEY,
                  number INT
                );
                INSERT INTO test2 (number) VALUES (42);
                ALTER TABLE test2 OWNER TO test_user;
                """,
                null
            );
        }

        var options = new pglite.PGliteOptions();
        options.dataDir = dataDir;
        options.username = "test_user";
        try (var db = closeable(new pglite(options))) {
            var pg = db.pg();
            var currentUser = pg.<Map<String, Object>>querySync("SELECT current_user;", null, null);
            assertEquals("test_user", currentUser.rows().get(0).get("current_user"));
            var owned = pg.<Map<String, Object>>querySync("SELECT * FROM test2;", null, null);
            assertEquals(List.of(Map.of("id", 1.0, "number", 42.0)), owned.rows());
        }
    }

    @Test
    void largeObjectsRoundTripThroughBlobDevice() {
        try (var db = closeable(new pglite())) {
            var pg = db.pg();
            var text = "hello world";
            pg.execSync("CREATE TABLE test (id SERIAL PRIMARY KEY, data OID);", null);
            pg.querySync(
                "INSERT INTO test (data) VALUES (lo_import('/dev/blob'));",
                new Object[] {},
                new interface_.QueryOptions(null, null, null, text.getBytes(java.nio.charset.StandardCharsets.UTF_8), null, null)
            );

            var result = pg.<Map<String, Object>>querySync(
                "SELECT lo_export(data, '/dev/blob') AS data FROM test;",
                null,
                null
            );
            assertArrayEquals(text.getBytes(java.nio.charset.StandardCharsets.UTF_8), result.blob());
        }
    }

    @Test
    void fullTextSearchSimpleQueriesMatchExpectedTextSearchOutput() {
        try (var db = closeable(new pglite())) {
            var pg = db.pg();
            var match = pg.<Map<String, Object>>querySync(
                "SELECT 'a fat cat sat on a mat and ate a fat rat'::tsvector @@ 'cat & rat'::tsquery AS match;",
                null,
                null
            );
            assertEquals(true, match.rows().get(0).get("match"));

            var tsquery = pg.<Map<String, Object>>querySync(
                "SELECT to_tsquery('simple', 'Fat | Rats:AB') as value;",
                null,
                null
            );
            assertEquals("'fat' | 'rats':AB", tsquery.rows().get(0).get("value"));

            var websearch = pg.<Map<String, Object>>querySync(
                "SELECT websearch_to_tsquery('simple', '\"sad cat\" or \"fat rat\"') as value;",
                null,
                null
            );
            assertEquals("'sad' <-> 'cat' | 'fat' <-> 'rat'", websearch.rows().get(0).get("value"));
        }
    }

    @Test
    void fullTextSearchEnglishStemmingAndRankingMatchExpectedOutput() {
        try (var db = closeable(new pglite())) {
            var pg = db.pg();
            var match = pg.<Map<String, Object>>querySync(
                "SELECT to_tsvector('english', 'fat cats ate fat rats') @@ to_tsquery('english', 'fat & rat') AS match;",
                null,
                null
            );
            assertEquals(true, match.rows().get(0).get("match"));

            var normalized = pg.<Map<String, Object>>querySync(
                "SELECT to_tsquery('english', 'The & Fat & Rats') as value;",
                null,
                null
            );
            assertEquals("'fat' & 'rat'", normalized.rows().get(0).get("value"));

            pg.execSync(
                """
                CREATE TABLE fts_ranking (
                  id serial PRIMARY KEY,
                  title text,
                  body text
                );
                INSERT INTO fts_ranking (title, body)
                VALUES
                  ('The Fat Rats', 'The fat rats ate the fat cats.'),
                  ('The Fat Cats', 'The fat cats ate the fat rats.'),
                  ('The Fat Cats and Rats', 'The fat cats and rats ate the fat rats and cats.');
                """,
                null
            );

            var ranking = pg.<Map<String, Object>>querySync(
                """
                SELECT title, ts_rank_cd(to_tsvector('english', body), to_tsquery('english', 'fat & rat')) as rank
                FROM fts_ranking
                ORDER BY rank DESC;
                """,
                null,
                null
            );
            assertEquals(List.of(
                Map.of("rank", 0.16666667, "title", "The Fat Cats and Rats"),
                Map.of("rank", 0.13333334, "title", "The Fat Rats"),
                Map.of("rank", 0.1, "title", "The Fat Cats")
            ), ranking.rows());
        }
    }

    @Test
    void xmlDocumentsXpathAndAggregationMatchXmlTest() throws IOException {
        var dataDir = Files.createTempDirectory("pglite-xml-").toString();
        try (var db = closeable(new pglite(dataDir))) {
            var pg = db.pg();
            pg.execSync(
                """
                CREATE TABLE xml_test (
                  id SERIAL PRIMARY KEY,
                  data XML
                );
                INSERT INTO xml_test (data) VALUES
                  ('<root><element>value1</element></root>'),
                  ('<root><element>value2</element></root>');
                """,
                null
            );

            var rows = pg.<Map<String, Object>>querySync("SELECT * FROM xml_test;", null, null);
            assertEquals("<root><element>value1</element></root>", rows.rows().get(0).get("data"));
            assertEquals("<root><element>value2</element></root>", rows.rows().get(1).get("data"));

            var xpath = pg.<Map<String, Object>>querySync(
                "SELECT xpath('/root/element/text()', data) AS elements FROM xml_test;",
                null,
                null
            );
            assertEquals(List.of("value1"), xpath.rows().get(0).get("elements"));
            assertEquals(List.of("value2"), xpath.rows().get(1).get("elements"));

            var aggregate = pg.<Map<String, Object>>querySync(
                "SELECT xmlelement(name \"aggregated\", xmlagg(data)) AS aggregated_data FROM xml_test;",
                null,
                null
            );
            assertEquals(
                "<aggregated><root><element>value1</element></root><root><element>value2</element></root></aggregated>",
                aggregate.rows().get(0).get("aggregated_data")
            );
        }
    }

    @Test
    void plpgsqlCanCreateAndCallFunctionsAndRecoverAfterException() {
        try (var db = closeable(new pglite())) {
            var pg = db.pg();
            pg.execSync(
                """
                CREATE EXTENSION IF NOT EXISTS plpgsql;
                CREATE OR REPLACE FUNCTION calculate_factorial(n INT) RETURNS INT AS $$
                DECLARE
                    result INT := 1;
                BEGIN
                    IF n < 0 THEN
                        RAISE EXCEPTION 'The input cannot be negative.';
                    ELSIF n = 0 OR n = 1 THEN
                        RETURN result;
                    ELSE
                        FOR i IN 2..n LOOP
                            result := result * i;
                        END LOOP;
                        RETURN result;
                    END IF;
                END;
                $$ LANGUAGE plpgsql;

                CREATE OR REPLACE PROCEDURE raise_exception() LANGUAGE plpgsql AS $$
                BEGIN
                  RAISE 'exception';
                END;
                $$;
                """,
                null
            );

            var result = pg.<Map<String, Object>>querySync("SELECT calculate_factorial(5) AS result;", null, null);
            assertEquals(120.0, result.rows().get(0).get("result"));

            try {
                pg.execSync("CALL raise_exception();", null);
            } catch (RuntimeException error) {
                assertTrue(error.getMessage().contains("exception"));
            }
            var afterException = pg.<Map<String, Object>>querySync("SELECT calculate_factorial(1) AS result;", null, null);
            assertEquals(1.0, afterException.rows().get(0).get("result"));
        }
    }

    @Test
    void triggersNotifyOnTableAndDdlEvents() {
        try (var db = closeable(new pglite())) {
            var pg = db.pg();
            var messages = new ArrayList<String>();
            pg.listen("messages", messages::add, null).join();

            pg.execSync(
                """
                CREATE EXTENSION IF NOT EXISTS plpgsql;
                CREATE TABLE foo_table (id TEXT, value TEXT);

                CREATE OR REPLACE FUNCTION foo_table_notify() RETURNS trigger AS $$
                BEGIN
                  PERFORM pg_notify('messages', 'table changed');
                  RETURN NULL;
                END;
                $$ LANGUAGE plpgsql;

                CREATE OR REPLACE TRIGGER table_trigger
                AFTER INSERT OR UPDATE OR DELETE ON foo_table
                EXECUTE FUNCTION foo_table_notify();
                """,
                null
            );
            pg.querySync("INSERT INTO foo_table (id, value) VALUES ('foo', 'bar');", null, null);
            assertTrue(messages.contains("table changed"));

            pg.execSync(
                """
                CREATE OR REPLACE FUNCTION ddl_notify() RETURNS event_trigger AS $$
                BEGIN
                  PERFORM pg_notify('messages','table created or dropped');
                END;
                $$ LANGUAGE plpgsql;

                CREATE EVENT TRIGGER ddl_trigger
                ON ddl_command_end
                EXECUTE FUNCTION ddl_notify();
                """,
                null
            );
            pg.execSync("CREATE TABLE ddl_table (id TEXT);", null);
            pg.execSync("DROP TABLE ddl_table;", null);
            assertTrue(messages.stream().filter("table created or dropped"::equals).count() >= 2);
        }
    }

    @Test
    void dropDatabaseCreateFromTemplateAndReopen() throws IOException {
        var dataDir = Files.createTempDirectory("pglite-drop-db-").toString();
        try (var db = closeable(new pglite(dataDir))) {
            var pg = db.pg();
            pg.execSync(
                """
                CREATE TABLE IF NOT EXISTS test (
                  id SERIAL PRIMARY KEY,
                  name TEXT
                );
                INSERT INTO test (name) VALUES ('test');
                """,
                null
            );
            pg.execSync("DROP DATABASE IF EXISTS mypostgres;", null);
            pg.execSync("CREATE DATABASE mypostgres TEMPLATE postgres;", null);
        }

        var options = new pglite.PGliteOptions();
        options.dataDir = dataDir;
        options.database = "mypostgres";
        try (var db = closeable(new pglite(options))) {
            var rows = db.pg().<Map<String, Object>>querySync("SELECT * FROM test;", null, null);
            assertEquals(List.of(Map.of("id", 1.0, "name", "test")), rows.rows());
        }
    }

    @Test
    void dropDatabaseCreateDropAndRestartAfterUncleanShutdown() throws IOException {
        var dataDir = Files.createTempDirectory("pglite-drop-db-unclean-").toString();
        var pg = new pglite(dataDir);
        pg.waitReady().join();
        pg.execSync(
            """
            CREATE TABLE IF NOT EXISTS test (
              id SERIAL PRIMARY KEY,
              name TEXT
            );
            INSERT INTO test (name) VALUES ('test');
            """,
            null
        );
        pg.execSync("DROP DATABASE IF EXISTS mypostgres;", null);
        pg.execSync("CREATE DATABASE mypostgres TEMPLATE template1;", null);
        pg = null;

        var options = new pglite.PGliteOptions();
        options.dataDir = dataDir;
        options.database = "postgres";
        try (var reopened = closeable(new pglite(options))) {
            var rows = reopened.pg().<Map<String, Object>>querySync("SELECT * FROM test;", null, null);
            assertEquals(List.of(Map.of("id", 1.0, "name", "test")), rows.rows());
        }
    }

    @Test
    void messageContextDoesNotAccumulateAcrossLargeJsonQueries() {
        try (var db = closeable(new pglite())) {
            var pg = db.pg();
            pg.execSync(
                """
                CREATE TABLE IF NOT EXISTS leak_test (
                  id SERIAL PRIMARY KEY,
                  blob jsonb NOT NULL
                );
                """,
                null
            );
            var blob = "{\"padding\":\"" + "x".repeat(100 * 1024) + "\"}";
            for (var i = 0; i < 300; i++) {
                pg.execSync("INSERT INTO leak_test (blob) VALUES ('" + blob + "')", null);
            }
            var memory = pg.<Map<String, Object>>querySync(
                """
                SELECT used_bytes
                FROM pg_backend_memory_contexts
                WHERE name = 'MessageContext'
                ORDER BY level
                LIMIT 1
                """,
                null,
                null
            );
            assertEquals(1, memory.rows().size());
            var usedBytes = ((Number) memory.rows().get(0).get("used_bytes")).longValue();
            assertTrue(usedBytes < 5L * 1024L * 1024L);
        }
    }

    @Test
    void templatingPortsParameterizedValuesIdentifiersAndRawSql() {
        var plain = templating.query(List.of("SELECT * FROM test WHERE value = $1;"));
        assertEquals("SELECT * FROM test WHERE value = $1;", plain.query());
        assertEquals(List.of(), plain.params());

        var parametrized = templating.query(
            List.of("SELECT * FROM test WHERE value = ", " AND num = ", ";"),
            "foo",
            3
        );
        assertEquals("SELECT * FROM test WHERE value = $1 AND num = $2;", parametrized.query());
        assertEquals(List.of("foo", 3), parametrized.params());

        var identifier = templating.query(
            List.of("SELECT * FROM ", ";"),
            templating.identifier("test_5_dance")
        );
        assertEquals("SELECT * FROM \"test_5_dance\";", identifier.query());
        assertEquals(List.of(), identifier.params());

        var mixed = templating.query(
            List.of("SELECT * FROM ", " ", " AND num = ", ";"),
            templating.identifier("test"),
            templating.raw("WHERE value = 'foo'"),
            3
        );
        assertEquals("SELECT * FROM \"test\" WHERE value = 'foo' AND num = $1;", mixed.query());
        assertEquals(List.of(3), mixed.params());

        var nested = templating.query(
            List.of("SELECT * FROM ", "", ";"),
            templating.identifier("test"),
            templating.sql(
                List.of(" WHERE ", " = ", ""),
                templating.identifier("foo"),
                "foo"
            )
        );
        assertEquals("SELECT * FROM \"test\" WHERE \"foo\" = $1;", nested.query());
        assertEquals(List.of("foo"), nested.params());

        var nestedEmpty = templating.query(
            List.of("SELECT * FROM ", "", ";"),
            templating.identifier("test"),
            templating.sql(List.of(""))
        );
        assertEquals("SELECT * FROM \"test\";", nestedEmpty.query());
        assertEquals(List.of(), nestedEmpty.params());
    }

    @Test
    void typeParsersAndSerializersPortedFromTypesTest() {
        assertEquals("test", types.parseType("test", types.TEXT, null));
        assertEquals("test", types.parseType("test", types.VARCHAR, null));
        assertEquals(1.0, types.parseType("1", types.INT4, null));
        assertEquals(1.0, types.parseType("1", types.INT2, null));
        assertEquals(1.0, types.parseType("1", types.OID, null));
        assertEquals(1L, types.parseType("1", types.INT8, null));
        assertEquals(1.1, types.parseType("1.1", types.FLOAT4, null));
        assertEquals(1.1, types.parseType("1.1", types.FLOAT8, null));
        assertEquals(true, types.parseType("t", types.BOOL, null));
        assertEquals(Instant.parse("2021-01-01T00:00:00Z"), types.parseType("2021-01-01", types.DATE, null));
        assertEquals(Instant.parse("2021-01-01T12:00:00Z"), types.parseType("2021-01-01T12:00:00", types.TIMESTAMP, null));
        assertEquals(Instant.parse("2021-01-01T12:00:00Z"), types.parseType("2021-01-01T12:00:00", types.TIMESTAMPTZ, null));
        assertEquals(Map.of("test", java.math.BigDecimal.ONE), types.parseType("{\"test\":1}", types.JSON, null));
        assertEquals(Map.of("test", java.math.BigDecimal.ONE), types.parseType("{\"test\":1}", types.JSONB, null));
        assertArrayEquals(new byte[] { 1, 2, 3 }, (byte[]) types.parseType("\\x010203", types.BYTEA, null));
        assertEquals("test", types.parseType("test", 0, null));

        assertEquals("test", types.serializers.get(types.TEXT).serialize("test"));
        assertEquals("1", types.serializers.get(types.TEXT).serialize(1));
        assertEquals("1", types.serializers.get(0).serialize(1));
        assertEquals("1.1", types.serializers.get(0).serialize(1.1));
        assertEquals("1", types.serializers.get(types.INT4).serialize(1));
        assertEquals("1", types.serializers.get(types.INT8).serialize(1L));
        assertEquals("t", types.serializers.get(types.BOOL).serialize(true));
        assertEquals("f", types.serializers.get(types.BOOL).serialize(false));
        assertEquals("t", types.serializers.get(types.BOOL).serialize(1));
        assertEquals("f", types.serializers.get(types.BOOL).serialize(0));
        assertThrows(IllegalArgumentException.class, () -> types.serializers.get(types.BOOL).serialize(-1));
        assertThrows(IllegalArgumentException.class, () -> types.serializers.get(types.BOOL).serialize(Double.NaN));
        assertEquals("t", types.serializers.get(types.BOOL).serialize("TRUE"));
        assertEquals("t", types.serializers.get(types.BOOL).serialize(" t "));
        assertEquals("t", types.serializers.get(types.BOOL).serialize("yes"));
        assertEquals("t", types.serializers.get(types.BOOL).serialize("on"));
        assertEquals("f", types.serializers.get(types.BOOL).serialize(" f "));
        assertEquals("f", types.serializers.get(types.BOOL).serialize("FALSE"));
        assertEquals("f", types.serializers.get(types.BOOL).serialize("no"));
        assertEquals("f", types.serializers.get(types.BOOL).serialize("off"));
        assertThrows(IllegalArgumentException.class, () -> types.serializers.get(types.BOOL).serialize(2));
        assertThrows(IllegalArgumentException.class, () -> types.serializers.get(types.BOOL).serialize("ture"));
        assertThrows(IllegalArgumentException.class, () -> types.serializers.get(types.BOOL).serialize("maybe"));
        assertThrows(IllegalArgumentException.class, () -> types.serializers.get(types.BOOL).serialize(""));
        assertThrows(IllegalArgumentException.class, () -> types.serializers.get(types.BOOL).serialize(Map.of()));
        assertEquals("2021-01-01T00:00:00.000Z", types.serializers.get(types.TIMESTAMPTZ).serialize(Instant.parse("2021-01-01T00:00:00Z")));
        assertEquals("2023-01-01T00:00:00.000Z", types.serializers.get(types.TIMESTAMPTZ).serialize(1672531200000L));
        assertEquals("2021-01-01T00:00:00.000Z", types.serializers.get(types.TIMESTAMPTZ).serialize("2021-01-01T00:00:00.000Z"));
        assertThrows(IllegalArgumentException.class, () -> types.serializers.get(types.TIMESTAMPTZ).serialize(true));
        assertEquals("{\"test\":1}", types.serializers.get(types.JSON).serialize(Map.of("test", 1)));
        assertEquals("{\"test\":1}", types.serializers.get(types.JSON).serialize("{\"test\":1}"));
        assertEquals("\\x010203", types.serializers.get(types.BYTEA).serialize(new byte[] { 1, 2, 3 }));
        assertThrows(IllegalArgumentException.class, () -> types.serializers.get(types.BYTEA).serialize(1));
    }

    @Test
    void arrayParserAndSerializerSupportNestedValuesAndEscaping() {
        assertEquals("{}", types.arraySerializer(List.of(), types.serializers.get(types.TEXT), 1009));
        assertEquals(
            "{\"test1\",\"test2\",\"test,3\"}",
            types.arraySerializer(List.of("test1", "test2", "test,3"), types.serializers.get(types.TEXT), 1009)
        );
        assertEquals(
            "{{\"1.1\",\"2.2\"},{\"3.3\",\"4.4\"}}",
            types.arraySerializer(
                List.of(List.of(1.1, 2.2), List.of(3.3, 4.4)),
                types.serializers.get(types.FLOAT8),
                1022
            )
        );
        assertEquals(
            "{\"sad\",\"happy\"}",
            types.arraySerializer(List.of("sad", "happy"), value -> String.valueOf(value), 0)
        );
        assertEquals(
            List.of("test1", "test2", "test,3"),
            types.arrayParser("{\"test1\",\"test2\",\"test,3\"}", (value, typeId) -> value, 1009)
        );
    }

    @Disabled("Covered by PGliteSmokeTest.")
    @Test
    void extensionMappingsComeFromPropertiesResources() throws Exception {
        var descriptors = extensionCatalog.descriptors();
        assertTrue(descriptors.containsKey("amcheck"));
        assertTrue(descriptors.containsKey("pg_trgm"));
        assertTrue(descriptors.containsKey("pg_stat_statements"));
        assertEquals("pg_stat_statements.tar.gz", descriptors.get("pg_stat_statements").bundle());
        assertEquals(List.of("pg_stat_statements"), descriptors.get("pg_stat_statements").sharedPreloadLibraries());
        for (var descriptor : descriptors.values()) {
            var resource = Thread.currentThread().getContextClassLoader().getResource(
                extensionCatalog.RELEASE_RESOURCE_ROOT + descriptor.bundle()
            );
            assertTrue(resource != null, "missing extension resource for " + descriptor.name());
        }

        var extension = index.extension("pg_stat_statements");
        assertEquals("pg_stat_statements", extension.name());
        var setup = extension.setup().setup(null, Map.of(), false).toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(List.of("pg_stat_statements"), setup.sharedPreloadLibraries());
        assertTrue(setup.bundlePath().toString().contains("io/github/hidekatsu_izuno/pglite_jdbc/pglite/release/pg_stat_statements.tar.gz"));
        assertInstanceOf(Map.class, index.extensions());
        assertFalse(index.extensions().isEmpty());
    }

    @Test
    void allContribTestsHavePropertiesBackedExtensions() {
        var expectedContribTests = List.of(
            "amcheck",
            "auto_explain",
            "bloom",
            "btree_gin",
            "btree_gist",
            "citext",
            "cube",
            "dict_int",
            "dict_xsyn",
            "earthdistance",
            "file_fdw",
            "fuzzystrmatch",
            "hstore",
            "intarray",
            "isn",
            "lo",
            "ltree",
            "pageinspect",
            "pg_buffercache",
            "pg_freespacemap",
            "pg_stat_statements",
            "pg_surgery",
            "pg_trgm",
            "pg_visibility",
            "pg_walinspect",
            "pgcrypto",
            "seg",
            "tablefunc",
            "tcn",
            "tsm_system_rows",
            "tsm_system_time",
            "unaccent",
            "uuid_ossp"
        );

        var descriptors = extensionCatalog.descriptors();
        for (var extensionName : expectedContribTests) {
            assertTrue(descriptors.containsKey(extensionName), "missing descriptor for " + extensionName);
            var descriptor = descriptors.get(extensionName);
            assertTrue(descriptor.bundle().endsWith(".tar.gz"), "bundle should be tar.gz for " + extensionName);
            assertTrue(
                Thread.currentThread().getContextClassLoader().getResource(
                    extensionCatalog.RELEASE_RESOURCE_ROOT + descriptor.bundle()
                ) != null,
                "missing bundle resource for " + extensionName
            );
        }
    }

    @Test
    void portedTestManifestTracksSourcePgliteTests() {
        var topLevelSourceTests = List.of(
            "array-types.test.ts",
            "basic.test.ts",
            "clone.test.js",
            "describe-query.test.ts",
            "drop-database.test.ts",
            "dump.test.js",
            "fts.english.test.js",
            "fts.simple.test.js",
            "instantiation.test.ts",
            "largeobjects.test.js",
            "message-context-leak.test.ts",
            "notify.test.ts",
            "plpgsql.test.js",
            "query-sizes.test.ts",
            "templating.test.js",
            "triggers.test.js",
            "types.test.ts",
            "user.test.ts",
            "utils.test.ts",
            "xml.test.ts"
        );
        var contribSourceTests = List.of(
            "contrib/amcheck.test.js",
            "contrib/auto_explain.test.js",
            "contrib/bloom.test.js",
            "contrib/btree_gin.test.js",
            "contrib/btree_gist.test.js",
            "contrib/citext.test.js",
            "contrib/cube.test.js",
            "contrib/dict_int.test.js",
            "contrib/dict_xsyn.test.ts",
            "contrib/earthdistance.test.js",
            "contrib/file_fdw.test.ts",
            "contrib/fuzzystrmatch.test.js",
            "contrib/hstore.test.js",
            "contrib/intarray.test.js",
            "contrib/isn.test.js",
            "contrib/lo.test.js",
            "contrib/ltree.test.js",
            "contrib/pageinspect.test.js",
            "contrib/pg_buffercache.test.js",
            "contrib/pg_freespacemap.test.ts",
            "contrib/pg_stat_statements.test.ts",
            "contrib/pg_surgery.test.js",
            "contrib/pg_trgm.test.js",
            "contrib/pg_visibility.test.js",
            "contrib/pg_walinspect.test.js",
            "contrib/pgcrypto.test.ts",
            "contrib/seg.test.js",
            "contrib/tablefunc.test.js",
            "contrib/tcn.test.js",
            "contrib/tsm_system_rows.test.js",
            "contrib/tsm_system_time.test.js",
            "contrib/unaccent.test.js",
            "contrib/uuid_ossp.test.ts"
        );
        var targetSourceTests = List.of(
            "targets/deno/basic.test.deno.js",
            "targets/deno/fs.test.deno.js",
            "targets/deno/pgvector.test.deno.js",
            "targets/runtimes/node-fs.test.js",
            "targets/runtimes/node-memory.test.js"
        );
        var portedWithoutRuntime = List.of(
            "templating.test.js",
            "types.test.ts",
            "utils.test.ts"
        );
        var allSourceTests = new ArrayList<String>();
        allSourceTests.addAll(topLevelSourceTests);
        allSourceTests.addAll(contribSourceTests);
        allSourceTests.addAll(targetSourceTests);
        var runtimeRequired = allSourceTests
            .stream()
            .filter(test -> !portedWithoutRuntime.contains(test))
            .toList();

        assertTrue(allSourceTests.containsAll(portedWithoutRuntime));
        assertEquals(20, topLevelSourceTests.size());
        assertEquals(33, contribSourceTests.size());
        assertEquals(5, targetSourceTests.size());
        assertEquals(58, allSourceTests.size());
        assertEquals(55, runtimeRequired.size());
        assertTrue(runtimeRequired.contains("basic.test.ts"));
        assertTrue(runtimeRequired.contains("describe-query.test.ts"));
        assertTrue(runtimeRequired.contains("array-types.test.ts"));
        assertTrue(runtimeRequired.contains("contrib/pg_stat_statements.test.ts"));
        assertTrue(runtimeRequired.contains("targets/runtimes/node-memory.test.js"));
        assertTrue(runtimeRequired.contains("notify.test.ts"));
        assertTrue(runtimeRequired.contains("targets/deno/pgvector.test.deno.js"));
    }

    @Test
    void disabledRuntimeTestCountMatchesRuntimeRequiredManifest() {
        var disabledRuntimeTests = java.util.Arrays.stream(PGlitePortedTest.class.getDeclaredMethods())
            .filter(method -> method.isAnnotationPresent(Test.class))
            .filter(method -> method.isAnnotationPresent(Disabled.class))
            .filter(method -> "Requires release.PostgresModFactory to implement the Endive-backed runtime instead of the current stub".equals(method.getAnnotation(Disabled.class).value()))
            .toList();

        assertEquals(0, disabledRuntimeTests.size());
    }

    @Test
    void pgvectorTargetIsTrackedButNotMappedInBundledExtensions() {
        assertThrows(IllegalArgumentException.class, () -> index.extension("vector"));
        assertFalse(extensionCatalog.descriptors().containsKey("vector"));
        assertTrue(
            Thread.currentThread().getContextClassLoader().getResource(
                extensionCatalog.RELEASE_RESOURCE_ROOT + "vector.tar.gz"
            ) == null
        );
    }

    @Test
    void wasmArtifactsExposeRuntimeContractNeededByDatabaseTests() throws Exception {
        var pgliteWasm = wasm("pglite.wasm");
        var initdbWasm = wasm("initdb.wasm");

        assertEquals(Map.of("env", 77, "pglite", 9, "wasi_snapshot_preview1", 39), importCountsByModule(pgliteWasm));
        assertEquals(Map.of("env", 12, "pglite", 3, "wasi_snapshot_preview1", 26), importCountsByModule(initdbWasm));

        assertTrue(importNames(pgliteWasm, "pglite").containsAll(Set.of(
            "blob_read",
            "blob_write",
            "blob_llseek",
            "random",
            "system",
            "popen",
            "pclose",
            "socket_read",
            "socket_write"
        )));
        assertEquals(Set.of("blob_read", "popen", "pclose"), importNames(initdbWasm, "pglite"));

        var pgliteExports = exportNames(pgliteWasm);
        assertTrue(pgliteExports.contains("pgl_startPGlite"));
        assertTrue(pgliteExports.contains("pgl_set_rw_cbs"));
        assertTrue(pgliteExports.contains("pgl_setPGliteActive"));
        assertFalse(pgliteExports.contains("_pgl_initdb"));
        assertFalse(pgliteExports.contains("_pgl_backend"));

        var initdbExports = exportNames(initdbWasm);
        assertTrue(initdbExports.contains("__main_argc_argv"));
        assertTrue(initdbExports.contains("pgl_chdir"));
        assertTrue(initdbExports.contains("pgl_freopen"));
    }

    @Test
    void debounceMutexExecutesFirstAndLastCalls() {
        var results = new ArrayList<Integer>();
        var debounced = utils.<Integer, Integer>debounceMutex(args ->
            new Promise<>((resolve, reject) ->
                Promise.executor().submit(() -> {
                    try {
                        Thread.sleep(10);
                        results.add(args.get(0));
                        resolve.run(args.get(0));
                    } catch (Throwable e) {
                        reject.run(e);
                    }
                })
            )
        );

        var call1 = debounced.call(List.of(1));
        var call2 = debounced.call(List.of(2));
        var call3 = debounced.call(List.of(3));

        assertEquals(1, call1.join());
        assertEquals(null, call2.join());
        assertEquals(3, call3.join());
        assertEquals(List.of(1, 3), results);
    }

    @Test
    void debounceMutexPreservesOrderAcrossDifferentDelaysAndRejectsErrors() {
        var results = new ArrayList<Integer>();
        var debounced = utils.<Integer, Integer>debounceMutex(args ->
            new Promise<>((resolve, reject) ->
                Promise.executor().submit(() -> {
                    try {
                        var value = args.get(0);
                        var delayMs = args.get(1);
                        Thread.sleep(delayMs);
                        results.add(value);
                        resolve.run(value);
                    } catch (Throwable e) {
                        reject.run(e);
                    }
                })
            )
        );

        var call1 = debounced.call(List.of(1, 50));
        var call2 = debounced.call(List.of(2, 10));
        var call3 = debounced.call(List.of(3, 10));

        assertEquals(1, call1.join());
        assertEquals(null, call2.join());
        assertEquals(3, call3.join());
        assertEquals(List.of(1, 3), results);

        var failing = utils.<Integer, Integer>debounceMutex(args -> Promise.reject(new IllegalStateException("Test error")));
        var error = assertThrows(java.util.concurrent.CompletionException.class, () -> failing.call(List.of(1)).join());
        assertTrue(error.getCause() instanceof IllegalStateException);
        assertEquals("Test error", error.getCause().getMessage());
    }

    private static Db closeable(pglite pg) {
        pg.waitReady().join();
        return new Db(pg);
    }

    private static WasmModule wasm(String name) throws Exception {
        var resource = extensionCatalog.RELEASE_RESOURCE_ROOT + name;
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            assertTrue(in != null, "missing wasm resource " + resource);
            return Parser.parse(in.readAllBytes());
        }
    }

    private static Map<String, Integer> importCountsByModule(WasmModule module) {
        var counts = new TreeMap<String, Integer>();
        module.importSection().stream().forEach(imp -> counts.merge(imp.module(), 1, Integer::sum));
        return counts;
    }

    private static Set<String> importNames(WasmModule module, String moduleName) {
        return module.importSection()
            .stream()
            .filter(imp -> moduleName.equals(imp.module()))
            .map(imp -> imp.name())
            .collect(Collectors.toSet());
    }

    private static Set<String> exportNames(WasmModule module) {
        var names = new java.util.HashSet<String>();
        for (var i = 0; i < module.exportSection().exportCount(); i++) {
            names.add(module.exportSection().getExport(i).name());
        }
        return names;
    }

    private static Map<String, Object> mapOfNullable(Object... entries) {
        var map = new java.util.LinkedHashMap<String, Object>();
        for (var i = 0; i < entries.length; i += 2) {
            map.put((String) entries[i], entries[i + 1]);
        }
        return map;
    }

    private record Db(pglite pg) implements AutoCloseable {
        @Override
        public void close() {
            pg.close().join();
        }
    }
}
