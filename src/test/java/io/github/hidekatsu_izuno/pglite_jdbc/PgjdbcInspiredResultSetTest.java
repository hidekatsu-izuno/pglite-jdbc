package io.github.hidekatsu_izuno.pglite_jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
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
}
