package io.github.hidekatsu_izuno.pglite_jdbc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.extensionCatalog;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.index;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.pglite;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PGliteSmokeTest {
    @Test
    void pgliteCoreFeaturesWork() {
        try (var db = closeable(new pglite())) {
            var pg = db.pg();
            pg.execSync(
                """
                CREATE TABLE smoke_core (
                  id SERIAL PRIMARY KEY,
                  text_value TEXT,
                  json_value JSONB,
                  bytea_value BYTEA,
                  int_values INT[],
                  created_at TIMESTAMP
                );
                """,
                null
            );

            pg.querySync(
                """
                INSERT INTO smoke_core (text_value, json_value, bytea_value, int_values, created_at)
                VALUES ($1, $2, $3, $4, $5);
                """,
                new Object[] {
                    "hello",
                    Map.of("ok", true),
                    new byte[] { 1, 2, 3 },
                    List.of(1, 2, 3),
                    "2026-07-05T08:00:00",
                },
                null
            );

            var selected = pg.<Map<String, Object>>querySync(
                "SELECT * FROM smoke_core WHERE id = ANY($1);",
                new Object[] { List.of(1, 2) },
                null
            );
            assertEquals(1, selected.rows().size());
            var row = selected.rows().get(0);
            assertEquals("hello", row.get("text_value"));
            assertEquals(Map.of("ok", true), row.get("json_value"));
            assertArrayEquals(new byte[] { 1, 2, 3 }, (byte[]) row.get("bytea_value"));
            assertEquals(List.of(1.0, 2.0, 3.0), row.get("int_values"));
            assertEquals(Instant.parse("2026-07-05T08:00:00Z"), row.get("created_at"));

            pg.transaction(tx -> {
                tx.query("INSERT INTO smoke_core (text_value) VALUES ('rolled back');", null, null).join();
                return tx.rollback();
            }).join();
            assertEquals(1.0, scalar(pg, "SELECT count(*)::int FROM smoke_core;"));

            var copyTo = pg.<Map<String, Object>>querySync("COPY smoke_core (id, text_value) TO '/dev/blob' WITH (FORMAT csv);", null, null);
            assertEquals("1,hello\n", new String(copyTo.blob(), StandardCharsets.UTF_8));

            pg.execSync("CREATE TABLE smoke_copy (id INT, text_value TEXT);", null);
            pg.querySync(
                "COPY smoke_copy FROM '/dev/blob' WITH (FORMAT csv);",
                new Object[] {},
                new interface_.QueryOptions(null, null, null, copyTo.blob(), null, null)
            );
            assertEquals(List.of(Map.of("id", 1.0, "text_value", "hello")),
                pg.<Map<String, Object>>querySync("SELECT * FROM smoke_copy;", null, null).rows());
        }
    }

    @Test
    void bundledExtensionResourcesAreMapped() {
        var descriptors = extensionCatalog.descriptors();
        assertEquals(33, descriptors.size());
        assertEquals(descriptors.keySet(), index.extensions().keySet());

        for (var descriptor : descriptors.values()) {
            var resource = Thread.currentThread().getContextClassLoader().getResource(
                extensionCatalog.RELEASE_RESOURCE_ROOT + descriptor.bundle()
            );
            assertTrue(resource != null, "missing extension resource for " + descriptor.name());
        }
    }

    @Test
    void bundledIndexAndInspectionExtensionsWork() {
        var options = new pglite.PGliteOptions();
        options.extensions = extensions(
            "amcheck",
            "bloom",
            "btree_gin",
            "btree_gist",
            "pageinspect",
            "pg_buffercache",
            "pg_freespacemap",
            "pg_surgery",
            "pg_visibility",
            "pg_walinspect"
        );
        try (var db = closeable(new pglite(options))) {
            assertBundledExtensionsAvailable(db.pg(), options.extensions.keySet());
        }
    }

    @Test
    void bundledTextTypeAndSearchExtensionsWork() {
        var options = new pglite.PGliteOptions();
        options.extensions = extensions(
            "citext",
            "cube",
            "dict_int",
            "dict_xsyn",
            "earthdistance",
            "fuzzystrmatch",
            "hstore",
            "intarray",
            "isn",
            "ltree",
            "pg_trgm",
            "seg",
            "unaccent"
        );
        try (var db = closeable(new pglite(options))) {
            assertBundledExtensionsAvailable(db.pg(), options.extensions.keySet());
        }
    }

    @Test
    void bundledIoCryptoAndRuntimeExtensionsWork() {
        var options = new pglite.PGliteOptions();
        options.extensions = extensions(
            "auto_explain",
            "file_fdw",
            "lo",
            "pgcrypto",
            "tablefunc",
            "tcn"
        );
        try (var db = closeable(new pglite(options))) {
            assertBundledExtensionsAvailable(db.pg(), options.extensions.keySet());
        }
    }

    @Test
    void bundledSamplingStatsAndUuidExtensionsWork() {
        var options = new pglite.PGliteOptions();
        options.extensions = extensions(
            "pg_stat_statements",
            "tsm_system_rows",
            "tsm_system_time",
            "uuid_ossp"
        );
        try (var db = closeable(new pglite(options))) {
            assertBundledExtensionsAvailable(db.pg(), options.extensions.keySet());
        }
    }

    private static Map<String, interface_.Extension> extensions(String... names) {
        var extensions = new LinkedHashMap<String, interface_.Extension>();
        for (var name : names) {
            extensions.put(name, index.extension(name));
        }
        return extensions;
    }

    private static void assertBundledExtensionsAvailable(pglite pg, Iterable<String> names) {
        assertTrue(pg != null);
        for (var name : names) {
            assertTrue(index.extension(name) != null, "missing extension catalog entry for " + name);
        }
    }

    private static Object scalar(pglite pg, String sql) {
        var rows = pg.<Map<String, Object>>querySync(sql, null, null).rows();
        assertEquals(1, rows.size(), "expected one row for " + sql);
        assertEquals(1, rows.get(0).size(), "expected one column for " + sql);
        return rows.get(0).values().iterator().next();
    }

    private static Db closeable(pglite pg) {
        pg.waitReady().join();
        return new Db(pg);
    }

    private record Db(pglite pg) implements AutoCloseable {
        @Override
        public void close() {
            pg.close().join();
        }
    }
}
