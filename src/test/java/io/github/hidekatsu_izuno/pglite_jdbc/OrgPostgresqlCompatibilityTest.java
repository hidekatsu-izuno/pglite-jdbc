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
}
