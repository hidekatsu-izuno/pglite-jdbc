package io.github.hidekatsu_izuno.pglite_jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import org.junit.jupiter.api.Test;

public class JdbcSmokeTest {
    @Test
    void shouldConnectWithDriverManagerAndExecutePreparedQuery() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:pglite:")) {
            try (var statement = connection.prepareStatement("SELECT ?::int4 AS value")) {
                statement.setInt(1, 7);
                try (var resultSet = statement.executeQuery()) {
                    assertTrue(resultSet.next());
                    assertEquals(7, resultSet.getInt(1));
                }
            }
        }
    }

    @Test
    void shouldUseDataSourceAndRollbackTransaction() throws Exception {
        var dataSource = new PgliteDataSource();
        dataSource.setUrl("jdbc:pglite:");

        try (var connection = dataSource.getConnection()) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS jdbc_tx_test (id INT PRIMARY KEY)");
                statement.execute("DELETE FROM jdbc_tx_test");
            }

            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("INSERT INTO jdbc_tx_test (id) VALUES (1)");
            }
            connection.rollback();

            try (var statement = connection.createStatement();
                 var resultSet = statement.executeQuery("SELECT COUNT(*) FROM jdbc_tx_test")) {
                assertTrue(resultSet.next());
                assertEquals(0, resultSet.getInt(1));
            }
        }
    }
}
