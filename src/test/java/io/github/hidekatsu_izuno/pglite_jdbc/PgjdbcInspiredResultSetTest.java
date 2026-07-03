package io.github.hidekatsu_izuno.pglite_jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PgjdbcInspiredResultSetTest {
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
    void resultSetScrollPositionAndColumnLookupFollowPgjdbcExpectations() throws Exception {
        try (var statement = connection.createStatement(
                 ResultSet.TYPE_SCROLL_INSENSITIVE,
                 ResultSet.CONCUR_READ_ONLY
             );
             var resultSet = statement.executeQuery(
                 "SELECT x AS id, x AS \"ID2\" FROM generate_series(1, 6) AS t(x) ORDER BY x"
             )) {
            assertEquals(1, resultSet.findColumn("id"));
            assertEquals(1, resultSet.findColumn("ID"));
            assertEquals(2, resultSet.findColumn("id2"));
            assertThrows(SQLException.class, () -> resultSet.findColumn("id3"));

            assertFalse(resultSet.absolute(0));
            assertEquals(0, resultSet.getRow());

            assertTrue(resultSet.absolute(-1));
            assertEquals(6, resultSet.getRow());
            assertEquals(6, resultSet.getInt("ID"));

            assertTrue(resultSet.absolute(1));
            assertEquals(1, resultSet.getRow());

            assertFalse(resultSet.absolute(-10));
            assertEquals(0, resultSet.getRow());
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getRow());

            assertTrue(resultSet.relative(2));
            assertEquals(3, resultSet.getRow());
            assertTrue(resultSet.relative(0));
            assertEquals(3, resultSet.getRow());

            assertFalse(resultSet.relative(10));
            assertEquals(0, resultSet.getRow());
            assertTrue(resultSet.isAfterLast());
        }
    }

    @Test
    void resultSetNullAndClosedStateFollowJdbcContract() throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT NULL::int4 AS value")) {
            assertTrue(resultSet.next());
            assertEquals(0, resultSet.getInt("value"));
            assertTrue(resultSet.wasNull());

            resultSet.close();
            resultSet.close();
            assertTrue(resultSet.isClosed());
            assertThrows(SQLException.class, () -> resultSet.getInt("value"));
            assertThrows(SQLException.class, resultSet::wasNull);
            assertThrows(SQLException.class, resultSet::getMetaData);
            assertThrows(SQLException.class, resultSet::getType);
            assertThrows(SQLException.class, resultSet::getConcurrency);
            assertThrows(SQLException.class, resultSet::getHoldability);
            assertThrows(SQLException.class, () -> resultSet.findColumn("value"));
            assertThrows(SQLException.class, () -> resultSet.getObject("value"));
            assertThrows(SQLException.class, resultSet::getStatement);
            assertThrows(SQLException.class, resultSet::clearWarnings);
            assertThrows(SQLException.class, () -> resultSet.setFetchSize(1));
            assertThrows(SQLException.class, () -> resultSet.setFetchDirection(ResultSet.FETCH_FORWARD));
            assertThrows(SQLException.class, resultSet::rowUpdated);
            assertThrows(SQLException.class, () -> resultSet.updateInt(1, 1));
            assertThrows(SQLException.class, resultSet::moveToInsertRow);
        }
    }

    @Test
    void resultSetUpdateStatusDefaultsAndReadOnlyUpdatesAreRejectedLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT 1 AS value")) {
            assertTrue(resultSet.next());
            assertFalse(resultSet.rowUpdated());
            assertFalse(resultSet.rowInserted());
            assertFalse(resultSet.rowDeleted());
            assertThrows(SQLException.class, () -> resultSet.updateInt(1, 2));
            assertThrows(SQLException.class, resultSet::moveToInsertRow);
        }
    }

    @Test
    void resultSetFetchSizeRejectsNegativeValuesLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT 1 AS value")) {
            assertThrows(SQLException.class, () -> resultSet.setFetchSize(-1));
            resultSet.setFetchSize(2);
            assertEquals(2, resultSet.getFetchSize());
        }
    }

    @Test
    void forwardOnlyResultSetRejectsNonForwardFetchDirectionLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT 1 AS value")) {
            assertEquals(ResultSet.FETCH_FORWARD, resultSet.getFetchDirection());
            assertThrows(SQLException.class, () -> resultSet.setFetchDirection(ResultSet.FETCH_REVERSE));
            assertThrows(SQLException.class, () -> resultSet.setFetchDirection(ResultSet.FETCH_UNKNOWN));
            assertThrows(SQLException.class, () -> resultSet.setFetchDirection(-1));
            resultSet.setFetchDirection(ResultSet.FETCH_FORWARD);
            assertEquals(ResultSet.FETCH_FORWARD, resultSet.getFetchDirection());
        }
    }

    @Test
    void forwardOnlyResultSetRejectsScrollMovementLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
             var resultSet = statement.executeQuery("SELECT 1 AS value")) {
            assertThrows(SQLException.class, () -> resultSet.absolute(1));
            assertThrows(SQLException.class, resultSet::afterLast);
            assertThrows(SQLException.class, resultSet::beforeFirst);
            assertThrows(SQLException.class, resultSet::first);
            assertThrows(SQLException.class, resultSet::last);
            assertThrows(SQLException.class, resultSet::previous);
            assertThrows(SQLException.class, () -> resultSet.relative(1));

            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt("value"));
        }
    }

    @Test
    void statementMaxFieldSizeAppliesOnlyToCharacterAndBinaryColumnsLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.setMaxFieldSize(2);
            assertEquals(2, statement.getMaxFieldSize());

            try (var resultSet = statement.executeQuery("""
                SELECT
                  12345::int4 AS int_value,
                  '12345'::text AS text_value,
                  '12345'::varchar AS varchar_value,
                  decode('0102030405', 'hex') AS bytea_value
                """)) {
                assertTrue(resultSet.next());
                assertTrue(resultSet.getString("int_value").length() > 2);
                assertTrue(resultSet.getBytes("int_value").length >= 4);
                assertEquals("12", resultSet.getString("text_value"));
                assertEquals("12", resultSet.getString("varchar_value"));
                org.junit.jupiter.api.Assertions.assertArrayEquals(
                    new byte[] { 1, 2 },
                    resultSet.getBytes("bytea_value")
                );
                assertFalse(resultSet.next());
            }
        }
    }

    @Test
    void duplicateColumnNameFindsFirstColumnAndIndexesAreBoundsChecked() throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT 1 AS a, 2 AS a")) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.findColumn("a"));
            assertThrows(SQLException.class, () -> resultSet.getInt(0));
            assertThrows(SQLException.class, () -> resultSet.getInt(3));
        }
    }

    @Test
    void zeroRowResultPositioningMatchesPgjdbcExpectations() throws Exception {
        try (var statement = connection.createStatement(
                 ResultSet.TYPE_SCROLL_INSENSITIVE,
                 ResultSet.CONCUR_READ_ONLY
             );
             var resultSet = statement.executeQuery("SELECT 1 AS id WHERE false")) {
            assertFalse(resultSet.previous());
            assertFalse(resultSet.next());
            assertFalse(resultSet.first());
            assertFalse(resultSet.last());
            assertEquals(0, resultSet.getRow());
            assertFalse(resultSet.absolute(1));
            assertFalse(resultSet.relative(1));
            assertFalse(resultSet.isBeforeFirst());
            assertFalse(resultSet.isAfterLast());
            assertFalse(resultSet.isFirst());
            assertFalse(resultSet.isLast());
        }
    }

    @Test
    void booleanGetterAcceptsPgjdbcCompatibleValuesAndRejectsInvalidValues() throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("""
                 SELECT
                   true AS b_true,
                   false AS b_false,
                   '1'::text AS t_one,
                   '0'::text AS t_zero,
                   ' yes '::text AS t_yes,
                   'Off'::text AS t_off,
                   '2'::text AS t_bad_text,
                   2::int4 AS i_bad
                 """)) {
            assertTrue(resultSet.next());
            assertTrue(resultSet.getBoolean("b_true"));
            assertFalse(resultSet.getBoolean("b_false"));
            assertTrue(resultSet.getBoolean("t_one"));
            assertFalse(resultSet.getBoolean("t_zero"));
            assertTrue(resultSet.getBoolean("t_yes"));
            assertFalse(resultSet.getBoolean("t_off"));
            assertThrows(SQLException.class, () -> resultSet.getBoolean("t_bad_text"));
            assertThrows(SQLException.class, () -> resultSet.getBoolean("i_bad"));
        }
    }

    @Test
    void numericGettersRejectInvalidTextValues() throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT 'not a number'::text AS value")) {
            assertTrue(resultSet.next());
            assertThrows(SQLException.class, () -> resultSet.getInt("value"));
            assertThrows(SQLException.class, () -> resultSet.getLong("value"));
            assertThrows(SQLException.class, () -> resultSet.getBigDecimal("value"));
        }
    }

    @Test
    void numericGettersTruncateAndCheckOverflowLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("""
                 SELECT *
                 FROM (VALUES
                   ('1.2'::numeric),
                   ('-2.5'::numeric),
                   ('127'::numeric),
                   ('128'::numeric),
                   ('32767'::numeric),
                   ('32768'::numeric),
                   ('2147483647'::numeric),
                   ('2147483648'::numeric),
                   ('9223372036854775807'::numeric),
                   ('9223372036854775807.9'::numeric),
                   ('9223372036854775808'::numeric),
                   ('-9223372036854775809'::numeric)
                 ) AS values(value)
                 """)) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getByte("value"));
            assertEquals(1, resultSet.getShort("value"));
            assertEquals(1, resultSet.getInt("value"));
            assertEquals(1L, resultSet.getLong("value"));

            assertTrue(resultSet.next());
            assertEquals(-2, resultSet.getByte("value"));
            assertEquals(-2, resultSet.getShort("value"));
            assertEquals(-2, resultSet.getInt("value"));
            assertEquals(-2L, resultSet.getLong("value"));

            assertTrue(resultSet.next());
            assertEquals(127, resultSet.getByte("value"));

            assertTrue(resultSet.next());
            assertThrows(SQLException.class, () -> resultSet.getByte("value"));

            assertTrue(resultSet.next());
            assertEquals(32767, resultSet.getShort("value"));

            assertTrue(resultSet.next());
            assertThrows(SQLException.class, () -> resultSet.getShort("value"));

            assertTrue(resultSet.next());
            assertEquals(Integer.MAX_VALUE, resultSet.getInt("value"));

            assertTrue(resultSet.next());
            assertThrows(SQLException.class, () -> resultSet.getInt("value"));

            assertTrue(resultSet.next());
            assertEquals(Long.MAX_VALUE, resultSet.getLong("value"));

            assertTrue(resultSet.next());
            assertEquals(Long.MAX_VALUE, resultSet.getLong("value"));

            assertTrue(resultSet.next());
            assertThrows(SQLException.class, () -> resultSet.getLong("value"));

            assertTrue(resultSet.next());
            assertThrows(SQLException.class, () -> resultSet.getLong("value"));

            assertFalse(resultSet.next());
        }
    }

    @Test
    void typedGetObjectNumericConversionsCheckOverflowLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("""
                 SELECT *
                 FROM (VALUES
                   ('1.2'::numeric),
                   ('128'::numeric),
                   ('32768'::numeric),
                   ('2147483648'::numeric),
                   ('9223372036854775808'::numeric)
                 ) AS values(value)
                 """)) {
            assertTrue(resultSet.next());
            assertEquals((byte) 1, resultSet.getObject("value", Byte.class));
            assertEquals((short) 1, resultSet.getObject("value", Short.class));
            assertEquals(1, resultSet.getObject("value", Integer.class));
            assertEquals(1L, resultSet.getObject("value", Long.class));

            assertTrue(resultSet.next());
            assertThrows(SQLException.class, () -> resultSet.getObject("value", Byte.class));

            assertTrue(resultSet.next());
            assertThrows(SQLException.class, () -> resultSet.getObject("value", Short.class));

            assertTrue(resultSet.next());
            assertThrows(SQLException.class, () -> resultSet.getObject("value", Integer.class));

            assertTrue(resultSet.next());
            assertThrows(SQLException.class, () -> resultSet.getObject("value", Long.class));
            assertFalse(resultSet.next());
        }
    }

    @Test
    void typedGetObjectRejectsUnsupportedAndInvalidConversionsLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("""
                 SELECT
                   'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'::uuid AS uid,
                   'not-a-uuid'::text AS bad_uuid,
                   '2024-02-29'::date AS leap_day,
                   'not-a-date'::text AS bad_date,
                   42::int4 AS value
                 """)) {
            assertTrue(resultSet.next());
            assertEquals(
                UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"),
                resultSet.getObject("uid", UUID.class)
            );
            assertEquals(LocalDate.of(2024, 2, 29), resultSet.getObject("leap_day", LocalDate.class));
            assertThrows(SQLException.class, () -> resultSet.getObject("bad_uuid", UUID.class));
            assertThrows(SQLException.class, () -> resultSet.getObject("bad_date", LocalDate.class));
            assertThrows(SQLException.class, () -> resultSet.getObject("value", File.class));
            assertFalse(resultSet.next());
        }
    }

    @Test
    void booleanGetterRejectsPgjdbcBadBooleanSourceTypes() throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("""
                 SELECT
                   '2017-03-13 14:25:48.130861'::timestamp AS bad_timestamp,
                   '2017-03-13'::date AS bad_date,
                   '14:25:48.130861'::time AS bad_time,
                   ARRAY[[1,0],[0,1]] AS bad_array,
                   29::bit(4) AS bad_bit,
                   'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'::uuid AS bad_uuid
                 """)) {
            assertTrue(resultSet.next());
            assertThrows(SQLException.class, () -> resultSet.getBoolean("bad_timestamp"));
            assertThrows(SQLException.class, () -> resultSet.getBoolean("bad_date"));
            assertThrows(SQLException.class, () -> resultSet.getBoolean("bad_time"));
            assertThrows(SQLException.class, () -> resultSet.getBoolean("bad_array"));
            assertThrows(SQLException.class, () -> resultSet.getBoolean("bad_bit"));
            assertThrows(SQLException.class, () -> resultSet.getBoolean("bad_uuid"));
            assertFalse(resultSet.next());
        }
    }

    @Test
    void columnLookupIsLocaleIndependentLikePgjdbcTurkishLocaleCase() throws Exception {
        var previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            try (var statement = connection.createStatement();
                 var resultSet = statement.executeQuery("SELECT 7 AS id")) {
                assertTrue(resultSet.next());
                assertEquals(7, resultSet.getInt("ID"));
            }
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void resultSetGettersRequireCursorPositionedOnRow() throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT 1 AS value")) {
            assertThrows(SQLException.class, () -> resultSet.getInt("value"));
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt("value"));
            assertFalse(resultSet.next());
            assertThrows(SQLException.class, () -> resultSet.getInt("value"));
        }
    }

    @Test
    void connectionRejectsInvalidResultSetOptionsLikePgjdbc() throws Exception {
        assertThrows(SQLException.class, () -> connection.createStatement(-1, -1, -1));
        assertThrows(
            SQLException.class,
            () -> connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, -1)
        );
        assertThrows(
            SQLException.class,
            () -> connection.createStatement(
                ResultSet.TYPE_SCROLL_INSENSITIVE,
                ResultSet.CONCUR_READ_ONLY,
                -1
            )
        );

        assertThrows(SQLException.class, () -> connection.prepareStatement("SELECT 1", -1, -1, -1));
        assertThrows(
            SQLException.class,
            () -> connection.prepareStatement("SELECT 1", ResultSet.TYPE_SCROLL_INSENSITIVE, -1)
        );
        assertThrows(
            SQLException.class,
            () -> connection.prepareStatement(
                "SELECT 1",
                ResultSet.TYPE_SCROLL_INSENSITIVE,
                ResultSet.CONCUR_READ_ONLY,
                -1
            )
        );
    }

    @Test
    void resultSetReportsStatementRequestedOptionsLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement(
                 ResultSet.TYPE_SCROLL_SENSITIVE,
                 ResultSet.CONCUR_UPDATABLE,
                 ResultSet.HOLD_CURSORS_OVER_COMMIT
             );
             var resultSet = statement.executeQuery("SELECT 1 AS value")) {
            assertEquals(ResultSet.TYPE_SCROLL_SENSITIVE, resultSet.getType());
            assertEquals(ResultSet.CONCUR_UPDATABLE, resultSet.getConcurrency());
            assertEquals(ResultSet.HOLD_CURSORS_OVER_COMMIT, resultSet.getHoldability());
            assertEquals(statement, resultSet.getStatement());
        }

        try (var prepared = connection.prepareStatement(
                 "SELECT 1 AS value",
                 ResultSet.TYPE_SCROLL_INSENSITIVE,
                 ResultSet.CONCUR_READ_ONLY,
                 ResultSet.CLOSE_CURSORS_AT_COMMIT
             );
             var resultSet = prepared.executeQuery()) {
            assertEquals(ResultSet.TYPE_SCROLL_INSENSITIVE, resultSet.getType());
            assertEquals(ResultSet.CONCUR_READ_ONLY, resultSet.getConcurrency());
            assertEquals(ResultSet.CLOSE_CURSORS_AT_COMMIT, resultSet.getHoldability());
            assertEquals(prepared, resultSet.getStatement());
        }
    }
}
