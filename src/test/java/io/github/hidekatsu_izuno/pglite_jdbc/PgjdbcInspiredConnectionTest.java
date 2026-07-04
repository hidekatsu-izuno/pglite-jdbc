package io.github.hidekatsu_izuno.pglite_jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import java.sql.ResultSet;
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
    void connectionUnsupportedFactoryMethodsMatchPgjdbc() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000")) {
            assertPgjdbcConnectionNotImplemented("createBlob()", connection::createBlob);
            assertPgjdbcConnectionNotImplemented("createClob()", connection::createClob);
            assertPgjdbcConnectionNotImplemented("createNClob()", connection::createNClob);
            assertPgjdbcConnectionNotImplemented(
                "createStruct(String, Object[])",
                () -> connection.createStruct("example", new Object[] { "value" })
            );
        }
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

    @Test
    void connectionHoldabilityMatchesPgjdbc() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000")) {
            assertEquals(ResultSet.CLOSE_CURSORS_AT_COMMIT, connection.getHoldability());

            connection.setHoldability(ResultSet.HOLD_CURSORS_OVER_COMMIT);
            assertEquals(ResultSet.HOLD_CURSORS_OVER_COMMIT, connection.getHoldability());
            try (var statement = connection.createStatement()) {
                assertEquals(ResultSet.HOLD_CURSORS_OVER_COMMIT, statement.getResultSetHoldability());
            }
            try (var statement = connection.createStatement(
                     ResultSet.TYPE_FORWARD_ONLY,
                     ResultSet.CONCUR_READ_ONLY
                 )) {
                assertEquals(ResultSet.HOLD_CURSORS_OVER_COMMIT, statement.getResultSetHoldability());
            }

            var error = assertThrows(SQLException.class, () -> connection.setHoldability(-1));
            assertEquals("Unknown ResultSet holdability setting: -1.", error.getMessage());
            assertEquals("22023", error.getSQLState());
            assertEquals(ResultSet.HOLD_CURSORS_OVER_COMMIT, connection.getHoldability());
        }
    }

    @Test
    void connectionEscapingMatchesPgjdbc() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000")) {
            var pgConnection = connection.unwrap(org.postgresql.PGConnection.class);
            var baseConnection = connection.unwrap(org.postgresql.core.BaseConnection.class);

            assertEquals("\"needs\"\"quote\"", pgConnection.escapeIdentifier("needs\"quote"));
            assertEquals("it''s ok", pgConnection.escapeLiteral("it's ok"));
            assertEquals("it''s ok", baseConnection.escapeString("it's ok"));
            assertThrows(SQLException.class, () -> pgConnection.escapeIdentifier("bad\0identifier"));
            assertThrows(SQLException.class, () -> pgConnection.escapeLiteral("bad\0literal"));
            assertThrows(SQLException.class, () -> baseConnection.escapeString("bad\0string"));
        }
    }

    @Test
    void statementOptionValidationMatchesPgjdbc() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000")) {
            var typeError = assertThrows(
                SQLException.class,
                () -> connection.createStatement(-1, ResultSet.CONCUR_READ_ONLY)
            );
            assertEquals("Unknown value for ResultSet type", typeError.getMessage());
            assertEquals("22023", typeError.getSQLState());

            var concurrencyError = assertThrows(
                SQLException.class,
                () -> connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, -1)
            );
            assertEquals("Unknown value for ResultSet concurrency", concurrencyError.getMessage());
            assertEquals("22023", concurrencyError.getSQLState());

            var holdabilityError = assertThrows(
                SQLException.class,
                () -> connection.createStatement(
                    ResultSet.TYPE_FORWARD_ONLY,
                    ResultSet.CONCUR_READ_ONLY,
                    -1
                )
            );
            assertEquals("Unknown value for ResultSet holdability", holdabilityError.getMessage());
            assertEquals("22023", holdabilityError.getSQLState());

            var preparedTypeError = assertThrows(
                SQLException.class,
                () -> connection.prepareStatement("SELECT 1", -1, ResultSet.CONCUR_READ_ONLY)
            );
            assertEquals("Unknown value for ResultSet type", preparedTypeError.getMessage());
            assertEquals("22023", preparedTypeError.getSQLState());
        }
    }

    @Test
    void savepointAutoCommitValidationMatchesPgjdbc() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000")) {
            var unnamedError = assertThrows(SQLException.class, connection::setSavepoint);
            assertEquals("Cannot establish a savepoint in auto-commit mode.", unnamedError.getMessage());
            assertEquals("25P01", unnamedError.getSQLState());

            var namedError = assertThrows(SQLException.class, () -> connection.setSavepoint("named"));
            assertEquals("Cannot establish a savepoint in auto-commit mode.", namedError.getMessage());
            assertEquals("25P01", namedError.getSQLState());

            connection.setAutoCommit(false);
            var savepoint = connection.setSavepoint("named");
            connection.rollback(savepoint);
            connection.releaseSavepoint(savepoint);
            connection.rollback();
        }
    }

    private void assertPgjdbcConnectionNotImplemented(String method, ThrowingSqlCall call) {
        var error = assertThrows(java.sql.SQLFeatureNotSupportedException.class, call::run);
        assertEquals(
            "Method org.postgresql.jdbc.PgConnection." + method + " is not yet implemented.",
            error.getMessage()
        );
        assertEquals(org.postgresql.util.PSQLState.NOT_IMPLEMENTED.getState(), error.getSQLState());
    }

    @FunctionalInterface
    private interface ThrowingSqlCall {
        void run() throws Exception;
    }
}
