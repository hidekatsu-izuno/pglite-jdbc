package io.github.hidekatsu_izuno.pglite_jdbc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.postgresql.PGResultSetMetaData;
import org.postgresql.PGStatement;
import org.postgresql.jdbc.AutoSave;
import org.postgresql.jdbc.PreferQueryMode;
import org.postgresql.largeobject.LargeObjectManager;

class OrgPostgresqlCompatibilityTest {
    @Test
    void shouldExposeOrgPostgresqlInterfaces() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:pglite:?defaultRowFetchSize=16&queryTimeout=5&autosave=conservative&preferQueryMode=extended"
            )) {
            var pgConnection = connection.unwrap(org.postgresql.PGConnection.class);
            assertNotNull(pgConnection);
            assertEquals(16, pgConnection.getDefaultFetchSize());
            assertEquals(AutoSave.CONSERVATIVE, pgConnection.getAutosave());
            assertEquals(PreferQueryMode.EXTENDED, pgConnection.getPreferQueryMode());
            try (var statement = connection.createStatement()) {
                assertEquals(5, statement.getQueryTimeout());
            }

            try (var prepared = connection.prepareStatement("SELECT ?::int4 AS value")) {
                var pgStatement = prepared.unwrap(PGStatement.class);
                pgStatement.setPrepareThreshold(1);
                assertTrue(pgStatement.isUseServerPrepare());
                pgStatement.setAdaptiveFetch(true);
                assertTrue(pgStatement.getAdaptiveFetch());
                prepared.setInt(1, 42);
                try (var resultSet = prepared.executeQuery()) {
                    assertTrue(resultSet.next());
                    assertEquals(42, resultSet.getInt(1));
                    var metadata = resultSet.getMetaData().unwrap(PGResultSetMetaData.class);
                    assertEquals("value", metadata.getBaseColumnName(1));
                }
            }
        }
    }

    @Test
    void shouldCopyInAndCopyOutWithOrgPostgresqlCopyManager() throws Exception {
        assertTimeout(Duration.ofSeconds(180), () -> {
            try (var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000")) {
                var pgConnection = connection.unwrap(org.postgresql.PGConnection.class);
                try (var statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE IF NOT EXISTS pg_copy_test(id int, name text)");
                    statement.execute("DELETE FROM pg_copy_test");
                }

                var csv = "1,alice\n2,bob\n";
                var copied = pgConnection.getCopyAPI().copyIn(
                    "COPY pg_copy_test(id,name) FROM STDIN WITH (FORMAT csv)",
                    new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))
                );
                assertTrue(copied >= 0);

                var out = new ByteArrayOutputStream();
                pgConnection.getCopyAPI().copyOut(
                    "COPY (SELECT id, name FROM pg_copy_test ORDER BY id) TO STDOUT WITH (FORMAT csv)",
                    out
                );
                var dumped = out.toString(StandardCharsets.UTF_8);
                assertTrue(dumped.contains("1,alice"));
                assertTrue(dumped.contains("2,bob"));
            }
        });
    }

    @Test
    void shouldSupportLargeObjectViaOrgPostgresqlApi() throws Exception {
        assertTimeout(Duration.ofSeconds(180), () -> {
            try (var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000")) {
                connection.setAutoCommit(false);
                var pgConnection = connection.unwrap(org.postgresql.PGConnection.class);
                LargeObjectManager manager = pgConnection.getLargeObjectAPI();
                var oid = manager.createLO();
                var largeObject = manager.open(oid);
                var payload = "hello-lobject".getBytes(StandardCharsets.UTF_8);
                largeObject.write(payload);
                largeObject.seek(0);
                var loaded = largeObject.read(payload.length);
                assertArrayEquals(payload, loaded);
                largeObject.close();
                manager.delete(oid);
                connection.commit();
            }
        });
    }

    @Test
    void shouldExposeJdbcDatabaseMetadata() throws Exception {
        assertTimeout(Duration.ofSeconds(180), () -> {
            try (var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000")) {
                try (var statement = connection.createStatement()) {
                    statement.execute("""
                        CREATE TABLE meta_parent(
                          id int PRIMARY KEY,
                          name text NOT NULL,
                          payload jsonb
                        )
                        """);
                    statement.execute("""
                        CREATE TABLE meta_child(
                          id int PRIMARY KEY,
                          parent_id int REFERENCES meta_parent(id)
                        )
                        """);
                    statement.execute("CREATE INDEX meta_parent_name_idx ON meta_parent(name)");
                }

                var metadata = connection.getMetaData();
                try (var tables = metadata.getTables(null, "public", "meta_parent", new String[] { "TABLE" })) {
                    assertTrue(tables.next());
                    assertEquals("public", tables.getString("TABLE_SCHEM"));
                    assertEquals("meta_parent", tables.getString("TABLE_NAME"));
                    assertEquals("TABLE", tables.getString("TABLE_TYPE"));
                }

                try (var columns = metadata.getColumns(null, "public", "meta_parent", "%")) {
                    assertTrue(columns.next());
                    assertEquals("id", columns.getString("COLUMN_NAME"));
                    assertEquals(java.sql.Types.INTEGER, columns.getInt("DATA_TYPE"));
                    assertTrue(columns.next());
                    assertEquals("name", columns.getString("COLUMN_NAME"));
                    assertEquals("NO", columns.getString("IS_NULLABLE"));
                }

                try (var keys = metadata.getPrimaryKeys(null, "public", "meta_parent")) {
                    assertTrue(keys.next());
                    assertEquals("id", keys.getString("COLUMN_NAME"));
                    assertEquals(1, keys.getShort("KEY_SEQ"));
                }

                var foundIndex = false;
                try (var indexes = metadata.getIndexInfo(null, "public", "meta_parent", false, false)) {
                    while (indexes.next()) {
                        if ("meta_parent_name_idx".equals(indexes.getString("INDEX_NAME"))) {
                            foundIndex = true;
                            assertEquals("name", indexes.getString("COLUMN_NAME"));
                        }
                    }
                }
                assertTrue(foundIndex);

                var foundJsonb = false;
                try (var types = metadata.getTypeInfo()) {
                    while (types.next()) {
                        if ("jsonb".equals(types.getString("TYPE_NAME"))) {
                            foundJsonb = true;
                            assertEquals(java.sql.Types.OTHER, types.getInt("DATA_TYPE"));
                        }
                    }
                }
                assertTrue(foundJsonb);

                var baseConnection = connection.unwrap(org.postgresql.core.BaseConnection.class);
                var typeInfo = baseConnection.getTypeInfo();
                assertEquals(2950, typeInfo.getPGType("uuid"));
                assertEquals("jsonb", typeInfo.getPGType(3802));
                assertEquals(2950, typeInfo.getPGArrayElement(2951));
                assertEquals(2951, typeInfo.getPGArrayType("uuid"));
                assertEquals(java.sql.Types.TIMESTAMP_WITH_TIMEZONE, typeInfo.getSQLType("timestamptz"));

                try (var imported = metadata.getImportedKeys(null, "public", "meta_child")) {
                    assertTrue(imported.next());
                    assertEquals("meta_parent", imported.getString("PKTABLE_NAME"));
                    assertEquals("id", imported.getString("PKCOLUMN_NAME"));
                    assertEquals("meta_child", imported.getString("FKTABLE_NAME"));
                    assertEquals("parent_id", imported.getString("FKCOLUMN_NAME"));
                }
            }
        });
    }
}
