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
