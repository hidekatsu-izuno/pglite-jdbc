package io.github.hidekatsu_izuno.pglite_jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Types;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PgjdbcInspiredPreparedStatementTest {
    private Connection connection;

    @BeforeAll
    void connect() throws Exception {
        connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000");
    }

    @AfterAll
    void disconnect() throws Exception {
        connection.close();
    }

    @Test
    void preparedStatementRejectsStatementSqlOverloads() throws Exception {
        try (var prepared = connection.prepareStatement("SELECT 1")) {
            assertThrows(SQLException.class, () -> prepared.executeQuery("SELECT 2"));
            assertThrows(SQLException.class, () -> prepared.executeUpdate("CREATE TEMP TABLE pgjdbc_bad_ps(i int)"));
            assertThrows(SQLException.class, () -> prepared.execute("SELECT 2"));
        }
    }

    @Test
    void preparedStatementRejectsOutOfRangeParameterIndexes() throws Exception {
        try (var noParams = connection.prepareStatement("SELECT 1")) {
            assertThrows(SQLException.class, () -> noParams.setString(1, "unused"));
        }

        try (var oneParam = connection.prepareStatement("SELECT ?::int4")) {
            assertThrows(SQLException.class, () -> oneParam.setInt(0, 1));
            assertThrows(SQLException.class, () -> oneParam.setInt(2, 1));
        }
    }

    @Test
    void preparedStatementSupportsTypedNullsObjectsAndCharacterParameters() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TEMP TABLE pgjdbc_prepared_types(id int4, amount numeric, body text)");
        }

        try (var prepared = connection.prepareStatement(
            "INSERT INTO pgjdbc_prepared_types(id, amount, body) VALUES (?, ?, ?)"
        )) {
            prepared.setObject(1, 42L, Types.INTEGER);
            prepared.setObject(2, new BigDecimal("3.14159"), Types.NUMERIC, 2);
            prepared.setObject(3, 'z');
            assertEquals(1, prepared.executeUpdate());

            prepared.setNull(1, Types.INTEGER);
            prepared.setObject(2, null, Types.NUMERIC);
            prepared.setObject(3, null);
            assertEquals(1, prepared.executeUpdate());
        }

        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery(
                 "SELECT id, amount, body FROM pgjdbc_prepared_types ORDER BY body NULLS LAST"
             )) {
            assertTrue(resultSet.next());
            assertEquals(42, resultSet.getInt("id"));
            assertEquals(new BigDecimal("3.14"), resultSet.getBigDecimal("amount"));
            assertEquals("z", resultSet.getString("body"));

            assertTrue(resultSet.next());
            assertEquals(0, resultSet.getInt("id"));
            assertTrue(resultSet.wasNull());
            assertNull(resultSet.getBigDecimal("amount"));
            assertNull(resultSet.getString("body"));
            assertFalse(resultSet.next());
        }
    }

    @Test
    void preparedStatementBatchCountsAndClearBatchMatchPgjdbcCases() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TEMP TABLE pgjdbc_prepared_batch(id int4, body text)");
        }

        try (var prepared = connection.prepareStatement("INSERT INTO pgjdbc_prepared_batch VALUES (?, ?)")) {
            prepared.setInt(1, 1);
            prepared.setString(2, "cleared");
            prepared.addBatch();
            prepared.clearBatch();
            assertEquals(0, prepared.executeBatch().length);

            prepared.setInt(1, 1);
            prepared.setString(2, "one");
            prepared.addBatch();
            prepared.setInt(1, 2);
            prepared.setString(2, "two");
            prepared.addBatch();
            assertEquals(2, prepared.executeBatch().length);
        }

        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery(
                 "SELECT string_agg(body, ',' ORDER BY id) AS bodies FROM pgjdbc_prepared_batch"
             )) {
            assertTrue(resultSet.next());
            assertEquals("one,two", resultSet.getString("bodies"));
        }
    }

    @Test
    void preparedStatementBinaryStreamLengthMustBeSatisfiedAndConnectionRemainsUsable() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TEMP TABLE pgjdbc_stream_test(payload bytea, body text)");
        }

        try (var prepared = connection.prepareStatement("INSERT INTO pgjdbc_stream_test VALUES (?, ?)")) {
            assertThrows(
                SQLException.class,
                () -> prepared.setBinaryStream(1, new ByteArrayInputStream(new byte[] { 1, 2, 3 }), 4)
            );
        }

        try (var prepared = connection.prepareStatement("INSERT INTO pgjdbc_stream_test VALUES (?, ?)")) {
            prepared.setBinaryStream(1, new ByteArrayInputStream(new byte[] { 1, 2, 3 }), 3);
            prepared.setString(2, "ok");
            assertEquals(1, prepared.executeUpdate());
        }

        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT payload, body FROM pgjdbc_stream_test")) {
            assertTrue(resultSet.next());
            org.junit.jupiter.api.Assertions.assertArrayEquals(
                new byte[] { 1, 2, 3 },
                resultSet.getBytes("payload")
            );
            assertEquals("ok", resultSet.getString("body"));
            assertFalse(resultSet.next());
        }

        try (var prepared = connection.prepareStatement("SELECT ?::bytea AS payload")) {
            prepared.setBytes(1, "ready".getBytes(StandardCharsets.UTF_8));
            try (var resultSet = prepared.executeQuery()) {
                assertTrue(resultSet.next());
                org.junit.jupiter.api.Assertions.assertArrayEquals(
                    "ready".getBytes(StandardCharsets.UTF_8),
                    resultSet.getBytes("payload")
                );
            }
        }
    }
}
