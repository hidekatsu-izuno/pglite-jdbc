package io.github.hidekatsu_izuno.pglite_jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.UUID;
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
    void preparedExecuteQueryRejectsUpdatesThatReturnNoRowsLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TEMP TABLE pgjdbc_prepared_execute_query_update(i int4)");
        }

        try (var prepared = connection.prepareStatement(
            "INSERT INTO pgjdbc_prepared_execute_query_update VALUES (?)"
        )) {
            prepared.setInt(1, 1);
            assertThrows(SQLException.class, prepared::executeQuery);
            assertNull(prepared.getResultSet());
            assertEquals(-1, prepared.getUpdateCount());
        }
    }

    @Test
    void preparedExecuteUpdateRejectsQueriesThatReturnRowsAndClearsState() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TEMP TABLE pgjdbc_prepared_execute_update_state(i int4)");
        }

        try (var insert = connection.prepareStatement("INSERT INTO pgjdbc_prepared_execute_update_state VALUES (?)")) {
            insert.setInt(1, 1);
            assertEquals(1, insert.executeUpdate());
        }

        try (var prepared = connection.prepareStatement("SELECT i FROM pgjdbc_prepared_execute_update_state")) {
            assertThrows(SQLException.class, prepared::executeUpdate);
            assertNull(prepared.getResultSet());
            assertEquals(-1, prepared.getUpdateCount());
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
    void preparedStatementSetNullWithTypeNameFollowsPgjdbcUuidCase() throws Exception {
        try (var prepared = connection.prepareStatement("SELECT 'ok' WHERE ? = ? OR (? IS NULL)")) {
            var uuid = UUID.randomUUID();
            prepared.setObject(1, uuid, Types.OTHER);
            prepared.setNull(2, Types.OTHER, "uuid");
            prepared.setNull(3, Types.OTHER, "uuid");

            try (var resultSet = prepared.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals("ok", resultSet.getString(1));
                assertFalse(resultSet.next());
            }
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
    void preparedStatementGeneratedKeysFollowPgjdbcReturningBehavior() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TEMP TABLE pgjdbc_prepared_keys(id serial primary key, body text)");
        }

        try (var prepared = connection.prepareStatement(
            "INSERT INTO pgjdbc_prepared_keys(body) VALUES (?)",
            Statement.RETURN_GENERATED_KEYS
        )) {
            prepared.setString(1, "one");
            assertEquals(1, prepared.executeUpdate());
            try (var keys = prepared.getGeneratedKeys()) {
                assertTrue(keys.next());
                assertEquals(1, keys.getInt("id"));
                assertEquals("one", keys.getString("body"));
                assertFalse(keys.next());
            }
        }

        try (var prepared = connection.prepareStatement(
            "INSERT INTO pgjdbc_prepared_keys(body) VALUES (?)",
            new String[] { "id" }
        )) {
            prepared.setString(1, "two");
            assertEquals(1, prepared.executeUpdate());
            try (var keys = prepared.getGeneratedKeys()) {
                assertTrue(keys.next());
                assertEquals(2, keys.getInt("id"));
                assertThrows(SQLException.class, () -> keys.findColumn("body"));
                assertFalse(keys.next());
            }
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
    void preparedStatementToStringRendersParametersLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TEMP TABLE IF NOT EXISTS pgjdbc_stream_test(payload bytea, body text)");
        }

        try (var prepared = connection.prepareStatement("INSERT INTO pgjdbc_stream_test VALUES (?, ?)")) {
            var bytes = new byte[] { 0, 1, 2, 3 };
            prepared.setBytes(1, bytes);
            assertEquals("INSERT INTO pgjdbc_stream_test VALUES ('\\x00010203'::bytea, ?)", prepared.toString());

            prepared.setString(2, "a'b");
            var expected = "INSERT INTO pgjdbc_stream_test VALUES ('\\x00010203'::bytea, ('a''b'))";
            assertEquals(expected, prepared.toString());
            assertEquals(1, prepared.executeUpdate());
            assertEquals(expected, prepared.toString());
        }
    }

    @Test
    void preparedStatementBatchToStringRendersParameterRowsLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TEMP TABLE IF NOT EXISTS pgjdbc_stream_test(payload bytea, body text)");
        }

        try (var prepared = connection.prepareStatement("INSERT INTO pgjdbc_stream_test VALUES (?, ?)")) {
            prepared.setBytes(1, new byte[] { 0, 1 });
            prepared.setString(2, "line1");
            prepared.addBatch();

            prepared.setBytes(1, new byte[] { 0, 1, 2 });
            prepared.setString(2, "line2");
            prepared.addBatch();

            assertEquals(
                """
                INSERT INTO pgjdbc_stream_test VALUES ('\\x0001'::bytea, ('line1'));
                INSERT INTO pgjdbc_stream_test VALUES ('\\x000102'::bytea, ('line2'))\
                """,
                prepared.toString()
            );

            org.junit.jupiter.api.Assertions.assertArrayEquals(new int[] { 1, 1 }, prepared.executeBatch());
            assertEquals(
                "INSERT INTO pgjdbc_stream_test VALUES ('\\x000102'::bytea, ('line2'))",
                prepared.toString()
            );
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
    void preparedStatementDoubleQuestionMarkEscapesPostgresqlOperatorsLikePgjdbc() throws Exception {
        try (var prepared = connection.prepareStatement("SELECT ??- lseg '((-1,0),(1,0))'")) {
            assertThrows(SQLException.class, () -> prepared.setInt(1, 7));

            try (var resultSet = prepared.executeQuery()) {
                assertTrue(resultSet.next());
                assertTrue(resultSet.getBoolean(1));
                assertEquals("true", resultSet.getString(1));
                assertFalse(resultSet.next());
            }
        }

        try (var prepared = connection.prepareStatement(
                 "SELECT lseg '((-1,0),(1,0))' ??# box '((-2,-2),(2,2))'"
             )) {
            assertThrows(SQLException.class, () -> prepared.setInt(1, 7));

            try (var resultSet = prepared.executeQuery()) {
                assertTrue(resultSet.next());
                assertTrue(resultSet.getBoolean(1));
                assertEquals("true", resultSet.getString(1));
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
    void preparedStatementCharacterStreamLengthMustBeSatisfiedAndConnectionRemainsUsable() throws Exception {
        try (var prepared = connection.prepareStatement("SELECT ?::text AS body")) {
            assertThrows(
                SQLException.class,
                () -> prepared.setCharacterStream(1, new StringReader("xy"), 3)
            );
        }

        try (var prepared = connection.prepareStatement("SELECT ?::text AS body")) {
            prepared.setCharacterStream(1, new StringReader("abc"), 3);
            try (var resultSet = prepared.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals("abc", resultSet.getString("body"));
                try (var reader = resultSet.getCharacterStream("body")) {
                    var buffer = new char[8];
                    var length = reader.read(buffer);
                    assertEquals("abc", new String(buffer, 0, length));
                }
                assertFalse(resultSet.next());
            }
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
    void preparedStatementRejectsBadBooleanObjectCastsLikePgjdbc() throws Exception {
        try (var prepared = connection.prepareStatement("SELECT ?::boolean AS value")) {
            assertThrows(SQLException.class, () -> prepared.setObject(1, "this is not boolean", Types.BOOLEAN));
            assertThrows(SQLException.class, () -> prepared.setObject(1, 'X', Types.BOOLEAN));
            assertThrows(SQLException.class, () -> prepared.setObject(1, new File(""), Types.BOOLEAN));
            assertThrows(SQLException.class, () -> prepared.setObject(1, "1.0", Types.BOOLEAN));
            assertThrows(SQLException.class, () -> prepared.setObject(1, "-1", Types.BOOLEAN));
            assertThrows(SQLException.class, () -> prepared.setObject(1, "ok", Types.BOOLEAN));
            assertThrows(SQLException.class, () -> prepared.setObject(1, 0.99f, Types.BOOLEAN));
            assertThrows(SQLException.class, () -> prepared.setObject(1, -0.01d, Types.BOOLEAN));
            assertThrows(SQLException.class, () -> prepared.setObject(1, new java.sql.Date(0), Types.BOOLEAN));
            assertThrows(SQLException.class, () -> prepared.setObject(1, new BigInteger("1000"), Types.BOOLEAN));
            assertThrows(SQLException.class, () -> prepared.setObject(1, Math.PI, Types.BOOLEAN));
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

    @Test
    void preparedStatementFloatObjectsTargetIntegralTypesLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("""
                CREATE TEMP TABLE pgjdbc_integral_targets(
                  tiny_value int4,
                  small_value int4,
                  int_value int4,
                  bigint_value int8
                )
                """);
        }

        try (var insert = connection.prepareStatement("INSERT INTO pgjdbc_integral_targets VALUES (?, ?, ?, ?)")) {
            insert.setObject(1, 127.9f, Types.TINYINT);
            insert.setObject(2, 32767.9f, Types.SMALLINT);
            insert.setObject(3, 1000.9f, Types.INTEGER);
            insert.setObject(4, 10000000000.9d, Types.BIGINT);
            assertEquals(1, insert.executeUpdate());

            assertThrows(SQLException.class, () -> insert.setObject(1, 128f, Types.TINYINT));
            assertThrows(SQLException.class, () -> insert.setObject(2, 32768f, Types.SMALLINT));
            assertThrows(SQLException.class, () -> insert.setObject(3, 2147483648d, Types.INTEGER));
            assertThrows(SQLException.class, () -> insert.setObject(4, new BigDecimal("9223372036854775808"), Types.BIGINT));
        }

        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT * FROM pgjdbc_integral_targets")) {
            assertTrue(resultSet.next());
            assertEquals(127, resultSet.getInt("tiny_value"));
            assertEquals(32767, resultSet.getInt("small_value"));
            assertEquals(1000, resultSet.getInt("int_value"));
            assertEquals(10000000000L, resultSet.getLong("bigint_value"));
            assertFalse(resultSet.next());
        }
    }

    @Test
    void preparedStatementTimestampBindingIsNotNarrowedAfterDateNullLikePgjdbc() throws Exception {
        try (var prepared = connection.prepareStatement("SELECT ?::timestamp AS value")) {
            var timestamp = Timestamp.valueOf("2016-09-27 16:13:34.836");
            assertEquals("timestamp", prepared.getParameterMetaData().getParameterTypeName(1));
            for (var i = 0; i < 3; i++) {
                prepared.setNull(1, Types.DATE);
                assertEquals("date", prepared.getParameterMetaData().getParameterTypeName(1));
                try (var resultSet = prepared.executeQuery()) {
                    assertTrue(resultSet.next());
                    assertNull(resultSet.getObject("value"));
                    assertNull(resultSet.getTimestamp("value"));
                    assertFalse(resultSet.next());
                }

                prepared.setTimestamp(1, timestamp);
                assertEquals("timestamp", prepared.getParameterMetaData().getParameterTypeName(1));
                try (var resultSet = prepared.executeQuery()) {
                    assertTrue(resultSet.next());
                    assertEquals(timestamp, resultSet.getTimestamp("value"));
                    assertEquals(timestamp, resultSet.getObject("value", Timestamp.class));
                    assertFalse(resultSet.next());
                }
            }
        }
    }

    @Test
    void preparedStatementSpecialFloatAndDoubleParametersFollowPgjdbc() throws Exception {
        var sql = """
            SELECT
              ? AS nan_real,
              ? AS nan_double,
              ? AS inf_real,
              ? AS inf_double,
              ? AS neg_inf_real,
              ? AS neg_inf_double
            """;

        try (var prepared = connection.prepareStatement(sql)) {
            prepared.setFloat(1, Float.NaN);
            prepared.setDouble(2, Double.NaN);
            prepared.setFloat(3, Float.POSITIVE_INFINITY);
            prepared.setDouble(4, Double.POSITIVE_INFINITY);
            prepared.setFloat(5, Float.NEGATIVE_INFINITY);
            prepared.setDouble(6, Double.NEGATIVE_INFINITY);

            try (var resultSet = prepared.executeQuery()) {
                assertTrue(resultSet.next());
                assertTrue(Float.isNaN((Float) resultSet.getObject(1)));
                assertTrue(Float.isNaN(resultSet.getFloat(1)));
                assertTrue(Double.isNaN((Double) resultSet.getObject(2)));
                assertTrue(Double.isNaN(resultSet.getDouble(2)));
                assertEquals(Float.POSITIVE_INFINITY, resultSet.getObject(3));
                assertEquals(Float.POSITIVE_INFINITY, resultSet.getFloat(3));
                assertEquals(Double.POSITIVE_INFINITY, resultSet.getObject(4));
                assertEquals(Double.POSITIVE_INFINITY, resultSet.getDouble(4));
                assertEquals(Float.NEGATIVE_INFINITY, resultSet.getObject(5));
                assertEquals(Float.NEGATIVE_INFINITY, resultSet.getFloat(5));
                assertEquals(Double.NEGATIVE_INFINITY, resultSet.getObject(6));
                assertEquals(Double.NEGATIVE_INFINITY, resultSet.getDouble(6));
                assertFalse(resultSet.next());
            }
        }

        try (var prepared = connection.prepareStatement(sql)) {
            prepared.setObject(1, Float.NaN);
            prepared.setObject(2, Double.NaN);
            prepared.setObject(3, Float.POSITIVE_INFINITY);
            prepared.setObject(4, Double.POSITIVE_INFINITY);
            prepared.setObject(5, Float.NEGATIVE_INFINITY);
            prepared.setObject(6, Double.NEGATIVE_INFINITY);

            try (var resultSet = prepared.executeQuery()) {
                assertTrue(resultSet.next());
                assertTrue(Float.isNaN((Float) resultSet.getObject(1)));
                assertTrue(Double.isNaN((Double) resultSet.getObject(2)));
                assertEquals(Float.POSITIVE_INFINITY, resultSet.getObject(3));
                assertEquals(Double.POSITIVE_INFINITY, resultSet.getObject(4));
                assertEquals(Float.NEGATIVE_INFINITY, resultSet.getObject(5));
                assertEquals(Double.NEGATIVE_INFINITY, resultSet.getObject(6));
                assertFalse(resultSet.next());
            }
        }
    }

    @Test
    void preparedStatementJdbcEscapeFunctionsFollowPgjdbc() throws Exception {
        try (var prepared = connection.prepareStatement("SELECT {fn concat('a', ?)} AS value")) {
            prepared.setInt(1, 5);

            try (var resultSet = prepared.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals("a5", resultSet.getString("value"));
                assertFalse(resultSet.next());
            }
        }
    }
}
