package io.github.hidekatsu_izuno.pglite_jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Types;
import java.util.concurrent.atomic.AtomicLong;
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

    @Test
    void preparedStatementQuestionMarksInsideSqlLiteralsCommentsAndIdentifiersAreNotParameters() throws Exception {
        try (var prepared = connection.prepareStatement("""
            SELECT
              '?' AS single_quote,
              '?' AS "?",
              $$?$$ AS dollar_quote,
              $tag$?; still literal$tag$ AS tagged_dollar_quote,
              ?::int4 AS bound_value
            -- ? line comment
            /* ? block comment */
            """)) {
            prepared.setInt(1, 42);
            assertThrows(SQLException.class, () -> prepared.setInt(2, 7));

            try (var resultSet = prepared.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals("?", resultSet.getString("single_quote"));
                assertEquals("?", resultSet.getString("?"));
                assertEquals("?", resultSet.getString("dollar_quote"));
                assertEquals("?; still literal", resultSet.getString("tagged_dollar_quote"));
                assertEquals(42, resultSet.getInt("bound_value"));
                assertFalse(resultSet.next());
            }
        }
    }

    @Test
    void preparedStatementAsciiStreamAndBigDecimalScaleFollowPgjdbcCases() throws Exception {
        try (var prepared = connection.prepareStatement("SELECT ?::text AS body, ?::numeric AS amount")) {
            prepared.setAsciiStream(
                1,
                new ByteArrayInputStream("abc".getBytes(StandardCharsets.US_ASCII)),
                3
            );
            prepared.setObject(2, new BigDecimal("123.456"), Types.NUMERIC, 1);

            try (var resultSet = prepared.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals("abc", resultSet.getString("body"));
                assertEquals(new BigDecimal("123.5"), resultSet.getBigDecimal("amount"));
                assertFalse(resultSet.next());
            }
        }

        try (var prepared = connection.prepareStatement("SELECT ?::text AS body")) {
            assertThrows(
                SQLException.class,
                () -> prepared.setAsciiStream(
                    1,
                    new ByteArrayInputStream("xy".getBytes(StandardCharsets.US_ASCII)),
                    3
                )
            );
        }
    }

    @Test
    void preparedStatementBooleanObjectTargetsNumericTypesLikePgjdbc() throws Exception {
        try (var prepared = connection.prepareStatement("""
            SELECT
              ?::float8 AS true_double,
              ?::float8 AS false_double,
              ?::numeric AS true_numeric,
              ?::numeric AS false_numeric,
              ?::numeric AS true_decimal,
              ?::numeric AS false_decimal
            """)) {
            prepared.setObject(1, Boolean.TRUE, Types.DOUBLE);
            prepared.setObject(2, Boolean.FALSE, Types.DOUBLE);
            prepared.setObject(3, Boolean.TRUE, Types.NUMERIC, 2);
            prepared.setObject(4, Boolean.FALSE, Types.NUMERIC, 2);
            prepared.setObject(5, Boolean.TRUE, Types.DECIMAL, 2);
            prepared.setObject(6, Boolean.FALSE, Types.DECIMAL, 2);

            try (var resultSet = prepared.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals(1.0d, resultSet.getDouble("true_double"));
                assertEquals(0.0d, resultSet.getDouble("false_double"));
                assertEquals(0, BigDecimal.ONE.compareTo(resultSet.getBigDecimal("true_numeric")));
                assertEquals(0, BigDecimal.ZERO.compareTo(resultSet.getBigDecimal("false_numeric")));
                assertEquals(0, BigDecimal.ONE.compareTo(resultSet.getBigDecimal("true_decimal")));
                assertEquals(0, BigDecimal.ZERO.compareTo(resultSet.getBigDecimal("false_decimal")));
                assertFalse(resultSet.next());
            }
        }
    }

    @Test
    void preparedStatementObjectBigDecimalScaleAndNumberFallbacksLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TEMP TABLE pgjdbc_decimal_scale(n1 numeric, n2 numeric, n3 numeric, n4 numeric)");
        }

        var value = new BigDecimal("3.141593");
        var valueString = value.toPlainString();
        var valueFloat = Float.valueOf(valueString);
        var valueDouble = Double.valueOf(valueString);

        try (var insert = connection.prepareStatement("INSERT INTO pgjdbc_decimal_scale VALUES (?, ?, ?, ?)");
             var select = connection.prepareStatement("SELECT n1, n2, n3, n4 FROM pgjdbc_decimal_scale");
             var truncate = connection.prepareStatement("TRUNCATE TABLE pgjdbc_decimal_scale")) {
            for (var scale = 0; scale < 6; scale++) {
                insert.setObject(1, value, Types.NUMERIC, scale);
                insert.setObject(2, valueString, Types.NUMERIC, scale);
                insert.setObject(3, valueFloat, Types.NUMERIC, scale);
                insert.setObject(4, valueDouble, Types.NUMERIC, scale);
                assertEquals(1, insert.executeUpdate());

                try (var resultSet = select.executeQuery()) {
                    assertTrue(resultSet.next());
                    var scaled = value.setScale(scale, java.math.RoundingMode.HALF_UP);
                    assertEquals(0, scaled.compareTo(resultSet.getBigDecimal(1)));
                    assertEquals(0, scaled.compareTo(resultSet.getBigDecimal(2)));
                    assertEquals(0, scaled.compareTo(resultSet.getBigDecimal(3)));
                    assertEquals(0, scaled.compareTo(resultSet.getBigDecimal(4)));
                    assertFalse(resultSet.next());
                }
                truncate.executeUpdate();
            }
        }

        try (var prepared = connection.prepareStatement("SELECT ?::numeric AS big_integer, ?::numeric AS atomic_long")) {
            prepared.setObject(1, new BigInteger("733"));
            prepared.setObject(2, new AtomicLong(733));
            try (var resultSet = prepared.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals(0, new BigDecimal("733").compareTo(resultSet.getBigDecimal("big_integer")));
                assertEquals(0, new BigDecimal("733").compareTo(resultSet.getBigDecimal("atomic_long")));
                assertFalse(resultSet.next());
            }
        }
    }
}
