package io.github.hidekatsu_izuno.pglite_jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PgjdbcInspiredStatementTest {
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
    void statementCloseClosesCurrentResultSetAndRejectsReuse() throws Exception {
        var statement = connection.createStatement();
        var resultSet = statement.executeQuery("SELECT 1");

        statement.close();
        statement.close();

        assertTrue(statement.isClosed());
        assertTrue(resultSet.isClosed());
        assertThrows(SQLException.class, statement::getResultSet);
    }

    @Test
    void statementSupportsPgjdbcStyleMultipleResultsAndUpdateCounts() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TEMP TABLE pgjdbc_statement_test(i int)");

            assertTrue(statement.execute("""
                SELECT 1 AS a;
                INSERT INTO pgjdbc_statement_test VALUES (7);
                SELECT i, i + 1 AS next_i FROM pgjdbc_statement_test
                """));

            try (var resultSet = statement.getResultSet()) {
                assertTrue(resultSet.next());
                assertEquals(1, resultSet.getInt(1));
            }

            assertFalse(statement.getMoreResults());
            assertEquals(1, statement.getUpdateCount());

            assertTrue(statement.getMoreResults());
            try (var resultSet = statement.getResultSet()) {
                assertTrue(resultSet.next());
                assertEquals(7, resultSet.getInt("i"));
                assertEquals(8, resultSet.getInt("next_i"));
            }

            assertFalse(statement.getMoreResults());
            assertEquals(-1, statement.getUpdateCount());
        }
    }

    @Test
    void statementUpdateCountsAndMaxRowsMatchCommonPgjdbcCases() throws Exception {
        try (var statement = connection.createStatement()) {
            assertEquals(0, statement.executeUpdate("CREATE TEMP TABLE pgjdbc_update_test(i int)"));
            assertEquals(1, statement.executeUpdate("INSERT INTO pgjdbc_update_test VALUES (1)"));
            assertEquals(1, statement.executeUpdate("INSERT INTO pgjdbc_update_test VALUES (2)"));
            assertEquals(2, statement.executeUpdate("UPDATE pgjdbc_update_test SET i = i + 10"));
            assertEquals(2L, statement.getLargeUpdateCount());

            statement.setMaxRows(1);
            try (var resultSet = statement.executeQuery("SELECT i FROM pgjdbc_update_test ORDER BY i")) {
                assertTrue(resultSet.next());
                assertEquals(11, resultSet.getInt(1));
                assertFalse(resultSet.next());
            }
            assertEquals(-1, statement.getUpdateCount());
            assertEquals(-1L, statement.getLargeUpdateCount());

            statement.setLargeMaxRows(2);
            assertEquals(2L, statement.getLargeMaxRows());
            try (var resultSet = statement.executeQuery("SELECT i FROM pgjdbc_update_test ORDER BY i")) {
                assertTrue(resultSet.next());
                assertTrue(resultSet.next());
                assertFalse(resultSet.next());
            }
        }
    }

    @Test
    void statementGeneratedKeysFollowPgjdbcReturningBehavior() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TEMP TABLE pgjdbc_statement_keys(id serial primary key, body text)");

            assertEquals(
                1,
                statement.executeUpdate(
                    "INSERT INTO pgjdbc_statement_keys(body) VALUES ('one')",
                    Statement.RETURN_GENERATED_KEYS
                )
            );
            try (var keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                assertEquals(1, keys.getInt("id"));
                assertEquals("one", keys.getString("body"));
                assertFalse(keys.next());
            }

            assertEquals(
                1,
                statement.executeUpdate(
                    "INSERT INTO pgjdbc_statement_keys(body) VALUES ('two')",
                    new String[] { "id" }
                )
            );
            try (var keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                assertEquals(2, keys.getInt("id"));
                assertThrows(SQLException.class, () -> keys.findColumn("body"));
                assertFalse(keys.next());
            }
        }
    }

    @Test
    void emptyQueryProducesNoResultSetAndNoMoreResults() throws Exception {
        try (var statement = connection.createStatement()) {
            assertFalse(statement.execute(""));
            assertFalse(statement.getMoreResults());
            assertEquals(-1, statement.getUpdateCount());
        }
    }

    @Test
    void executeUpdateRejectsQueriesThatReturnRows() throws Exception {
        try (var statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.executeUpdate("SELECT 1"));
            assertThrows(SQLException.class, () -> statement.executeUpdate("/* empty */; SELECT 1"));
        }
    }

    @Test
    void statementReexecutionAndMoreResultsCloseCurrentResultSetByDefault() throws Exception {
        try (var statement = connection.createStatement()) {
            var first = statement.executeQuery("SELECT 1 AS value");
            assertTrue(first.next());

            var second = statement.executeQuery("SELECT 2 AS value");
            assertTrue(first.isClosed());
            assertTrue(second.next());
            assertEquals(2, second.getInt("value"));
        }

        try (var statement = connection.createStatement()) {
            assertTrue(statement.execute("SELECT 1 AS value; SELECT 2 AS value"));
            var first = statement.getResultSet();
            assertTrue(first.next());

            assertTrue(statement.getMoreResults());
            assertTrue(first.isClosed());
            try (var second = statement.getResultSet()) {
                assertTrue(second.next());
                assertEquals(2, second.getInt("value"));
            }
        }
    }

    @Test
    void statementMoreResultsCanKeepOrCloseCurrentResultSetLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement()) {
            assertTrue(statement.execute("SELECT 1 AS value; SELECT 2 AS value; SELECT 3 AS value"));
            var first = statement.getResultSet();
            assertTrue(first.next());

            assertTrue(statement.getMoreResults(Statement.KEEP_CURRENT_RESULT));
            assertFalse(first.isClosed());

            var second = statement.getResultSet();
            assertTrue(second.next());
            assertTrue(statement.getMoreResults(Statement.CLOSE_ALL_RESULTS));
            assertTrue(second.isClosed());

            try (var third = statement.getResultSet()) {
                assertTrue(third.next());
                assertEquals(3, third.getInt("value"));
            }
        }
    }

    @Test
    void statementRetainsRequestedResultSetOptions() throws Exception {
        try (var statement = connection.createStatement(
                 ResultSet.TYPE_SCROLL_INSENSITIVE,
                 ResultSet.CONCUR_UPDATABLE,
                 ResultSet.HOLD_CURSORS_OVER_COMMIT
             )) {
            assertEquals(ResultSet.TYPE_SCROLL_INSENSITIVE, statement.getResultSetType());
            assertEquals(ResultSet.CONCUR_UPDATABLE, statement.getResultSetConcurrency());
            assertEquals(ResultSet.HOLD_CURSORS_OVER_COMMIT, statement.getResultSetHoldability());
        }

        try (var prepared = connection.prepareStatement(
                 "SELECT 1",
                 ResultSet.TYPE_SCROLL_SENSITIVE,
                 ResultSet.CONCUR_READ_ONLY,
                 ResultSet.CLOSE_CURSORS_AT_COMMIT
             )) {
            assertEquals(ResultSet.TYPE_SCROLL_SENSITIVE, prepared.getResultSetType());
            assertEquals(ResultSet.CONCUR_READ_ONLY, prepared.getResultSetConcurrency());
            assertEquals(ResultSet.CLOSE_CURSORS_AT_COMMIT, prepared.getResultSetHoldability());
        }
    }

    @Test
    void statementFetchDirectionAndSizeAreInheritedByResultSetLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement(
                 ResultSet.TYPE_SCROLL_SENSITIVE,
                 ResultSet.CONCUR_UPDATABLE
             )) {
            statement.setFetchSize(100);
            statement.setFetchDirection(ResultSet.FETCH_UNKNOWN);

            try (var resultSet = statement.executeQuery("SELECT 1 AS value")) {
                assertEquals(100, statement.getFetchSize());
                assertEquals(ResultSet.FETCH_UNKNOWN, statement.getFetchDirection());
                assertEquals(100, resultSet.getFetchSize());
                assertEquals(ResultSet.FETCH_UNKNOWN, resultSet.getFetchDirection());
            }
        }
    }

    @Test
    void statementRejectsNegativeLimitsAndTimeoutsLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.setFetchSize(-1));
            assertThrows(SQLException.class, () -> statement.setMaxRows(-1));
            assertThrows(SQLException.class, () -> statement.setLargeMaxRows(-1));
            assertThrows(SQLException.class, () -> statement.setQueryTimeout(-1));

            statement.setFetchSize(2);
            statement.setMaxRows(3);
            statement.setLargeMaxRows(4);
            statement.setQueryTimeout(5);

            assertEquals(2, statement.getFetchSize());
            assertEquals(4, statement.getMaxRows());
            assertEquals(4L, statement.getLargeMaxRows());
            assertEquals(5, statement.getQueryTimeout());
        }
    }

    @Test
    void statementRejectsInvalidFetchDirectionLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.setFetchDirection(-1));
            statement.setFetchDirection(ResultSet.FETCH_REVERSE);
            assertEquals(ResultSet.FETCH_REVERSE, statement.getFetchDirection());
        }
    }
}
