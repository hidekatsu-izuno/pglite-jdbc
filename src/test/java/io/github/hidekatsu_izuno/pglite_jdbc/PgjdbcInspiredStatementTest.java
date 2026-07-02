package io.github.hidekatsu_izuno.pglite_jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
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

            statement.setMaxRows(1);
            try (var resultSet = statement.executeQuery("SELECT i FROM pgjdbc_update_test ORDER BY i")) {
                assertTrue(resultSet.next());
                assertEquals(11, resultSet.getInt(1));
                assertFalse(resultSet.next());
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
}
