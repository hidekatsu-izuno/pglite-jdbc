package io.github.hidekatsu_izuno.pglite_jdbc.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

final class PgDatabaseMetaData implements InvocationHandler {
    private final PgConnection connection;

    private PgDatabaseMetaData(PgConnection connection) {
        this.connection = connection;
    }

    static DatabaseMetaData create(PgConnection connection) {
        return (DatabaseMetaData) Proxy.newProxyInstance(
            PgDatabaseMetaData.class.getClassLoader(),
            new Class<?>[] { DatabaseMetaData.class },
            new PgDatabaseMetaData(connection)
        );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        var name = method.getName();
        if (method.getDeclaringClass() == Object.class) {
            return switch (name) {
                case "toString" -> "PgDatabaseMetaData";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> null;
            };
        }

        return switch (name) {
            case "getConnection" -> connection.proxy();
            case "getURL" -> connection.url();
            case "getUserName" -> connection.user();
            case "getDatabaseProductName" -> "PGlite";
            case "getDatabaseProductVersion" -> "local";
            case "getDriverName" -> "pglite-jdbc";
            case "getDriverVersion" -> "0.1";
            case "getDriverMajorVersion" -> 0;
            case "getDriverMinorVersion" -> 1;
            case "supportsTransactions" -> true;
            case "supportsResultSetType" -> (Integer) args[0] == ResultSet.TYPE_FORWARD_ONLY;
            case "supportsResultSetConcurrency" ->
                (Integer) args[0] == ResultSet.TYPE_FORWARD_ONLY &&
                (Integer) args[1] == ResultSet.CONCUR_READ_ONLY;
            case "getDefaultTransactionIsolation" -> Connection.TRANSACTION_READ_COMMITTED;
            case "isReadOnly" -> connection.readOnly();
            case "unwrap" -> {
                var iface = (Class<?>) args[0];
                if (iface.isInstance(proxy)) {
                    yield proxy;
                }
                throw new IllegalArgumentException("Not a wrapper for " + iface.getName());
            }
            case "isWrapperFor" -> ((Class<?>) args[0]).isInstance(proxy);
            default -> JdbcCompat.defaultReturn(method.getReturnType());
        };
    }
}
