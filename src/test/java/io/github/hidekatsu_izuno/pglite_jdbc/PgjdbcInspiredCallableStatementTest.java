package io.github.hidekatsu_izuno.pglite_jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.sql.CallableStatement;
import java.sql.DriverManager;
import java.sql.JDBCType;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

class PgjdbcInspiredCallableStatementTest {
    @Test
    void prepareCallReturnsCallableStatementAndKeepsPreparedStatementBehavior() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000");
             var callable = connection.prepareCall("SELECT ?::int4 AS value")) {
            assertTrue(callable instanceof CallableStatement);
            callable.setInt(1, 42);

            try (var resultSet = callable.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals(42, resultSet.getInt("value"));
                assertFalse(resultSet.next());
            }
        }
    }

    @Test
    void callableStatementNamedParametersMatchPgjdbcUnsupportedMethods() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000");
             var callable = connection.prepareCall("SELECT 1")) {
            assertPgjdbcCallableNotImplemented(
                "registerOutParameter(String,int)",
                () -> callable.registerOutParameter("value", Types.INTEGER)
            );
            assertPgjdbcCallableNotImplemented(
                "registerOutParameter(String,int,int)",
                () -> callable.registerOutParameter("value", Types.NUMERIC, 2)
            );
            assertPgjdbcCallableNotImplemented(
                "registerOutParameter(String,int,String)",
                () -> callable.registerOutParameter("value", Types.OTHER, "text")
            );
            assertPgjdbcCallableNotImplemented("setString(String,String)", () -> callable.setString("value", "x"));
            assertPgjdbcCallableNotImplemented("setInt(String,int)", () -> callable.setInt("value", 1));
            assertPgjdbcCallableNotImplemented(
                "setObject(String,Object,int,int)",
                () -> callable.setObject("value", "x", Types.VARCHAR, 1)
            );
            assertPgjdbcCallableNotImplemented(
                "setObject",
                () -> callable.setObject("value", "x", JDBCType.VARCHAR)
            );
            assertPgjdbcCallableNotImplemented(
                "setAsciiStream(String, InputStream)",
                () -> callable.setAsciiStream("value", new ByteArrayInputStream(new byte[] { 1 }))
            );
            assertPgjdbcCallableNotImplemented(
                "setCharacterStream(String, Reader)",
                () -> callable.setCharacterStream("value", new StringReader("x"))
            );
            assertPgjdbcCallableNotImplemented("getString(String)", () -> callable.getString("value"));
            assertPgjdbcCallableNotImplemented("getObject(String)", () -> callable.getObject("value"));
            assertPgjdbcCallableNotImplemented(
                "getObject(String,Map)",
                () -> callable.getObject("value", new HashMap<String, Class<?>>())
            );
            assertPgjdbcCallableNotImplemented(
                "getObject(String, Class<T>)",
                () -> callable.getObject("value", String.class)
            );
            assertPgjdbcCallableNotImplemented("getURL(String)", () -> callable.getURL("value"));
        }
    }

    @Test
    void callableStatementOutParameterDefaultsDoNotSilentlyReturnValues() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000");
             var callable = connection.prepareCall("SELECT 1")) {
            var wasNullError = assertThrows(SQLException.class, callable::wasNull);
            assertEquals("wasNull cannot be call before fetching a result.", wasNullError.getMessage());
            assertEquals("55000", wasNullError.getSQLState());

            assertPgjdbcCallableNotImplemented(
                "registerOutParameter",
                () -> callable.registerOutParameter(1, JDBCType.INTEGER)
            );
            assertPgjdbcCallableNotImplemented(
                "registerOutParameter(int,int,String)",
                () -> callable.registerOutParameter(1, Types.OTHER, "text")
            );
            assertPgjdbcCallableNotImplemented("getNString(int)", () -> callable.getNString(1));
            assertPgjdbcCallableNotImplemented("getCharacterStream(int)", () -> callable.getCharacterStream(1));
            assertPgjdbcCallableNotImplemented("getRowId(int)", () -> callable.getRowId(1));
        }
    }

    private void assertPgjdbcCallableNotImplemented(String method, ThrowingSqlCall call) {
        var error = assertThrows(SQLFeatureNotSupportedException.class, call::run);
        assertEquals(
            "Method org.postgresql.jdbc.PgCallableStatement." + method + " is not yet implemented.",
            error.getMessage()
        );
        assertEquals(org.postgresql.util.PSQLState.NOT_IMPLEMENTED.getState(), error.getSQLState());
    }

    @FunctionalInterface
    private interface ThrowingSqlCall {
        void run() throws Exception;
    }
}
