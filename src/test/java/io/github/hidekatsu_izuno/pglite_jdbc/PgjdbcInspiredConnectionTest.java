package io.github.hidekatsu_izuno.pglite_jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLClientInfoException;
import java.sql.SQLFeatureNotSupportedException;
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
        var pgConnection = connection.unwrap(org.postgresql.PGConnection.class);
        assertEquals("initial-app", pgConnection.getParameterStatus("application_name"));
        assertEquals("initial-app", pgConnection.getParameterStatus("Application_Name"));
        assertThrows(
            UnsupportedOperationException.class,
            () -> pgConnection.getParameterStatuses().put("extra", "value")
        );

        connection.setClientInfo("ApplicationName", "updated-app");
        assertEquals("updated-app", connection.getClientInfo().getProperty("ApplicationName"));
        assertEquals("updated-app", pgConnection.getParameterStatus("application_name"));

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
    void connectionAddDataTypeValidationMatchesPgjdbc() throws Exception {
        var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000");
        var pgConnection = connection.unwrap(org.postgresql.PGConnection.class);

        var missing = assertThrows(
            RuntimeException.class,
            () -> pgConnection.addDataType("missing_type", "example.MissingPgObject")
        );
        assertEquals("Cannot register new type missing_type", missing.getMessage());

        connection.close();
        assertThrows(
            SQLException.class,
            () -> pgConnection.addDataType("closed_type", org.postgresql.util.PGobject.class)
        );
    }

    @Test
    void connectionReplicationApiShapeMatchesPgjdbc() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000")) {
            var pgConnection = connection.unwrap(org.postgresql.PGConnection.class);
            var replication = pgConnection.getReplicationAPI();
            assertTrue(replication instanceof org.postgresql.replication.PGReplicationConnectionImpl);

            var baseConnection = connection.unwrap(org.postgresql.core.BaseConnection.class);
            var protocol = baseConnection.getReplicationProtocol();
            assertTrue(protocol instanceof org.postgresql.core.ReplicationProtocol);

            var error = assertThrows(
                SQLFeatureNotSupportedException.class,
                () -> replication.replicationStream().logical().withSlotName("slot").start()
            );
            assertEquals("Replication is not supported by PGlite", error.getMessage());
            assertEquals("0A000", error.getSQLState());
        }
    }

    @Test
    void fastpathNameLookupMatchesPgjdbc() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000")) {
            var pgConnection = connection.unwrap(org.postgresql.PGConnection.class);
            var fastpath = pgConnection.getFastpathAPI();

            var error = assertThrows(
                SQLException.class,
                () -> fastpath.getData("lo_open", new org.postgresql.fastpath.FastpathArg[0])
            );
            assertEquals("The fastpath function lo_open is unknown.", error.getMessage());
            assertEquals("99999", error.getSQLState());
        }
    }

    @Test
    void alterUserPasswordValidationMatchesPgjdbc() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000")) {
            var pgConnection = connection.unwrap(org.postgresql.PGConnection.class);
            var password = "secret".toCharArray();

            var error = assertThrows(
                SQLException.class,
                () -> pgConnection.alterUserPassword("postgres", password, "unknown")
            );
            assertEquals("Unable to determine encryption type: unknown", error.getMessage());
            assertEquals("60000", error.getSQLState());
            assertEquals(new String(new char[password.length]), new String(password));
        }
    }

    @Test
    void baseConnectionCreateQueryUsesPgjdbcParser() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000")) {
            var baseConnection = connection.unwrap(org.postgresql.core.BaseConnection.class);

            var parameterized = baseConnection.createQuery("select ?", false, true);
            assertEquals("select $1", parameterized.query.getNativeSql());
            assertEquals(1, parameterized.query.createParameterList().getParameterCount());

            var returning = baseConnection.createQuery(
                "insert into example(name) values (?)",
                false,
                true,
                "generated id"
            );
            assertEquals(
                "insert into example(name) values ($1)\nRETURNING \"generated id\"",
                returning.query.getNativeSql()
            );

            var simple = baseConnection.getQueryExecutor().createSimpleQuery("select 1");
            assertEquals("select 1", simple.getNativeSql());
        }
    }

    @Test
    void queryExecutorCachedQueryFactoriesUsePgjdbcParser() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000")) {
            var queryExecutor = connection.unwrap(org.postgresql.core.BaseConnection.class).getQueryExecutor();

            var key = queryExecutor.createQueryKey("select ?", true, true);
            var cached = queryExecutor.createQueryByKey(key);
            assertEquals("select $1", cached.query.getNativeSql());

            var borrowed = queryExecutor.borrowQuery("select ?");
            assertEquals("select $1", borrowed.query.getNativeSql());

            var returning = queryExecutor.borrowReturningQuery(
                "insert into example(name) values (?)",
                new String[] { "generated id" }
            );
            assertEquals(
                "insert into example(name) values ($1)\nRETURNING \"generated id\"",
                returning.query.getNativeSql()
            );
        }
    }

    @Test
    void queryExecutorCreatesFastpathParameterListsLikePgjdbc() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000")) {
            var queryExecutor = connection.unwrap(org.postgresql.core.BaseConnection.class).getQueryExecutor();
            var parameters = queryExecutor.createFastpathParameters(2);

            parameters.setIntParameter(1, 7);
            parameters.setBytea(2, new byte[] { 1, 2, 3, 4 }, 1, 2);

            assertEquals(2, parameters.getParameterCount());
            assertEquals(2, parameters.getInParameterCount());
            assertEquals(0, parameters.getOutParameterCount());
            assertEquals(7, parameters.getValues()[0]);
            assertArrayEquals(new byte[] { 2, 3 }, (byte[]) parameters.getValues()[1]);

            var copy = parameters.copy();
            parameters.clear();
            assertEquals(7, copy.getValues()[0]);
            assertNull(parameters.getValues()[0]);
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
            assertThrows(SQLException.class, () -> connection.setSchema("bad\0schema"));
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
            assertTrue(savepoint instanceof org.postgresql.jdbc.PSQLSavepoint);
            assertEquals("named", savepoint.getSavepointName());
            var idError = assertThrows(SQLException.class, savepoint::getSavepointId);
            assertEquals("Cannot retrieve the id of a named savepoint.", idError.getMessage());
            assertEquals("42809", idError.getSQLState());
            connection.rollback(savepoint);
            connection.releaseSavepoint(savepoint);
            var releasedError = assertThrows(SQLException.class, savepoint::getSavepointName);
            assertEquals("Cannot reference a savepoint after it has been released.", releasedError.getMessage());
            assertEquals("3B000", releasedError.getSQLState());
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
