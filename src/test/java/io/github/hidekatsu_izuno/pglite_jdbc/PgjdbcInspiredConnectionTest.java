package io.github.hidekatsu_izuno.pglite_jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLClientInfoException;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class PgjdbcInspiredConnectionTest {
    @Test
    void connectionRejectsOperationsAfterCloseLikePgjdbc() throws Exception {
        var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000");
        connection.close();
        connection.close();

        assertTrue(connection.isClosed());
        assertThrows(SQLException.class, connection::createStatement);
        assertThrows(SQLException.class, () -> connection.prepareStatement("SELECT 1"));
        assertThrows(SQLException.class, connection::getAutoCommit);
        assertThrows(SQLException.class, connection::getMetaData);
        assertThrows(SQLException.class, connection::commit);
        assertThrows(SQLException.class, connection::rollback);
        assertThrows(SQLException.class, () -> connection.setReadOnly(true));
    }

    @Test
    void connectionValidationAndClientInfoMatchPgjdbc() throws Exception {
        var properties = new Properties();
        properties.setProperty("ApplicationName", "initial-app");

        var connection = DriverManager.getConnection(
            "jdbc:pglite:?protocolTimeoutMs=5000",
            properties
        );
        assertTrue(connection.isValid(0));
        assertThrows(SQLException.class, () -> connection.isValid(-1));
        assertEquals("initial-app", connection.getClientInfo("ApplicationName"));
        assertEquals("initial-app", connection.unwrap(org.postgresql.PGConnection.class)
            .getParameterStatus("application_name"));

        connection.setClientInfo("ApplicationName", "updated-app");
        assertEquals("updated-app", connection.getClientInfo().getProperty("ApplicationName"));
        assertEquals("updated-app", connection.unwrap(org.postgresql.PGConnection.class)
            .getParameterStatus("application_name"));

        connection.clearWarnings();
        connection.setClientInfo("unsupported", "ignored");
        assertEquals("ClientInfo property not supported.", connection.getWarnings().getMessage());
        assertEquals("updated-app", connection.getClientInfo("ApplicationName"));

        var replacement = new Properties();
        replacement.setProperty("ApplicationName", "replacement-app");
        connection.setClientInfo(replacement);
        assertEquals("replacement-app", connection.getClientInfo("ApplicationName"));

        connection.close();
        assertFalse(connection.isValid(0));
        assertThrows(SQLClientInfoException.class, () -> connection.setClientInfo("ApplicationName", "closed"));
    }

    @Test
    void connectionTimeoutAndFetchSizeValidationMatchesPgjdbc() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000")) {
            var pgConnection = connection.unwrap(org.postgresql.PGConnection.class);

            var fetchSizeError = assertThrows(SQLException.class, () -> pgConnection.setDefaultFetchSize(-1));
            assertEquals("Fetch size must be a value greater than or equal to 0.", fetchSizeError.getMessage());
            assertEquals("22023", fetchSizeError.getSQLState());

            var networkTimeoutError = assertThrows(SQLException.class, () -> connection.setNetworkTimeout(null, -1));
            assertEquals(
                "Network timeout must be a value greater than or equal to 0.",
                networkTimeoutError.getMessage()
            );
            assertEquals("22023", networkTimeoutError.getSQLState());

            pgConnection.setDefaultFetchSize(7);
            assertEquals(7, pgConnection.getDefaultFetchSize());
            connection.setNetworkTimeout(null, 9);
            assertEquals(9, connection.getNetworkTimeout());
        }
    }
}
