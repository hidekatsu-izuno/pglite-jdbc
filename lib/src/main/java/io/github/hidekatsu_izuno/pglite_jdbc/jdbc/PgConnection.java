package io.github.hidekatsu_izuno.pglite_jdbc.jdbc;

import io.github.hidekatsu_izuno.pglite_jdbc.PGConnection;
import io.github.hidekatsu_izuno.pglite_jdbc.PGNotification;
import io.github.hidekatsu_izuno.pglite_jdbc.core.BaseConnection;
import io.github.hidekatsu_izuno.pglite_jdbc.core.QueryExecutor;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class PgConnection implements InvocationHandler {
    private static final PGNotification[] EMPTY_NOTIFICATIONS = new PGNotification[0];

    private final QueryExecutor queryExecutor;
    private final String url;
    private final String user;
    private final String database;
    private Connection self;
    private boolean closed;
    private boolean autoCommit = true;
    private boolean txOpen;
    private boolean readOnly;
    private String catalog;
    private int transactionIsolation = Connection.TRANSACTION_READ_COMMITTED;
    private java.sql.SQLWarning warnings;
    private int prepareThreshold = 5;
    private int defaultFetchSize;
    private int queryTimeout;

    private PgConnection(QueryExecutor queryExecutor, String url, String user, String database) {
        this.queryExecutor = queryExecutor;
        this.url = url;
        this.user = user;
        this.database = database;
    }

    public static Connection create(
        QueryExecutor queryExecutor,
        String url,
        String user,
        String database
    ) {
        var handler = new PgConnection(queryExecutor, url, user, database);
        var proxy = (Connection) Proxy.newProxyInstance(
            PgConnection.class.getClassLoader(),
            new Class<?>[] { Connection.class, BaseConnection.class, PGConnection.class },
            handler
        );
        handler.self = proxy;
        return proxy;
    }

    Connection proxy() {
        return self;
    }

    String url() {
        return url;
    }

    String user() {
        return user;
    }

    boolean readOnly() {
        return readOnly;
    }

    int getPrepareThresholdInternal() {
        return prepareThreshold;
    }

    int getDefaultFetchSizeInternal() {
        return defaultFetchSize;
    }

    int getQueryTimeoutInternal() {
        return queryTimeout;
    }

    interface_.DescribeQueryResult describe(String sql) throws SQLException {
        return queryExecutor.describe(sql);
    }

    interface_.Results<Map<String, Object>> query(String sql, Object[] params) throws SQLException {
        ensureTransactionIfNeeded();
        return queryExecutor.query(sql, params);
    }

    List<interface_.Results<Map<String, Object>>> exec(String sql) throws SQLException {
        ensureTransactionIfNeeded();
        return queryExecutor.exec(sql);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        var name = method.getName();
        if (method.getDeclaringClass() == Object.class) {
            return switch (name) {
                case "toString" -> "PgConnection[" + url + "]";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> method.invoke(this, args);
            };
        }

        return switch (name) {
            case "close" -> {
                closeConnection();
                yield null;
            }
            case "isClosed" -> closed;
            case "createStatement" -> PgStatement.create(this, null);
            case "prepareStatement" -> PgPreparedStatement.create(this, (String) args[0]);
            case "prepareCall" -> throw JdbcCompat.unsupported(name);
            case "nativeSQL" -> args[0];
            case "setAutoCommit" -> {
                setAutoCommit((Boolean) args[0]);
                yield null;
            }
            case "getAutoCommit" -> autoCommit;
            case "commit" -> {
                commitTransaction();
                yield null;
            }
            case "rollback" -> {
                rollbackTransaction();
                yield null;
            }
            case "getMetaData" -> PgDatabaseMetaData.create(this);
            case "setReadOnly" -> {
                readOnly = (Boolean) args[0];
                yield null;
            }
            case "isReadOnly" -> readOnly;
            case "setCatalog" -> {
                catalog = (String) args[0];
                yield null;
            }
            case "getCatalog" -> catalog;
            case "setTransactionIsolation" -> {
                transactionIsolation = (Integer) args[0];
                yield null;
            }
            case "getTransactionIsolation" -> transactionIsolation;
            case "getWarnings" -> warnings;
            case "clearWarnings" -> {
                warnings = null;
                yield null;
            }
            case "setHoldability" -> null;
            case "getHoldability" -> ResultSet.CLOSE_CURSORS_AT_COMMIT;
            case "setSavepoint", "releaseSavepoint", "setTypeMap", "createClob", "createBlob",
                "createNClob", "createSQLXML", "createArrayOf", "createStruct" -> throw JdbcCompat.unsupported(name);
            case "getTypeMap" -> new HashMap<String, Class<?>>();
            case "isValid" -> !closed;
            case "setClientInfo" -> null;
            case "getClientInfo" -> {
                if (args == null || args.length == 0) {
                    yield new Properties();
                }
                yield null;
            }
            case "setSchema" -> null;
            case "getSchema" -> database;
            case "abort" -> {
                closeConnection();
                yield null;
            }
            case "setNetworkTimeout" -> null;
            case "getNetworkTimeout" -> 0;
            case "getQueryExecutor" -> queryExecutor;
            case "getNotifications", "getNotifications" + "\u0000" -> EMPTY_NOTIFICATIONS;
            case "getBackendPID" -> 0;
            case "cancelQuery" -> null;
            case "setPrepareThreshold" -> {
                prepareThreshold = (Integer) args[0];
                yield null;
            }
            case "getPrepareThreshold" -> prepareThreshold;
            case "setDefaultFetchSize" -> {
                defaultFetchSize = (Integer) args[0];
                yield null;
            }
            case "getDefaultFetchSize" -> defaultFetchSize;
            case "setQueryTimeout" -> {
                queryTimeout = (Integer) args[0];
                yield null;
            }
            case "getQueryTimeout" -> queryTimeout;
            case "escapeIdentifier" -> '"' + String.valueOf(args[0]).replace("\"", "\"\"") + '"';
            case "escapeLiteral" -> '\'' + String.valueOf(args[0]).replace("'", "''") + '\'';
            case "unwrap" -> {
                var iface = (Class<?>) args[0];
                if (iface.isInstance(proxy)) {
                    yield proxy;
                }
                throw new SQLException("Not a wrapper for " + iface.getName());
            }
            case "isWrapperFor" -> {
                var iface = (Class<?>) args[0];
                yield iface.isInstance(proxy);
            }
            default -> JdbcCompat.defaultReturn(method.getReturnType());
        };
    }

    private void ensureOpen() throws SQLException {
        if (closed) {
            throw new SQLException("Connection is closed");
        }
    }

    private void setAutoCommit(boolean value) throws SQLException {
        ensureOpen();
        if (autoCommit == value) {
            return;
        }
        if (value && txOpen) {
            execControl("COMMIT");
            txOpen = false;
        }
        autoCommit = value;
    }

    private void ensureTransactionIfNeeded() throws SQLException {
        ensureOpen();
        if (!autoCommit && !txOpen) {
            execControl("BEGIN");
            txOpen = true;
        }
    }

    private void commitTransaction() throws SQLException {
        ensureOpen();
        if (autoCommit) {
            throw new SQLException("Cannot call commit when autoCommit is true");
        }
        if (txOpen) {
            execControl("COMMIT");
            txOpen = false;
        }
    }

    private void rollbackTransaction() throws SQLException {
        ensureOpen();
        if (autoCommit) {
            throw new SQLException("Cannot call rollback when autoCommit is true");
        }
        if (txOpen) {
            execControl("ROLLBACK");
            txOpen = false;
        }
    }

    private void closeConnection() throws SQLException {
        if (closed) {
            return;
        }
        try {
            if (!autoCommit && txOpen) {
                try {
                    execControl("ROLLBACK");
                } catch (SQLException ignored) {
                    // Keep close robust.
                }
            }
            queryExecutor.close();
        } finally {
            closed = true;
            txOpen = false;
        }
    }

    private void execControl(String sql) throws SQLException {
        queryExecutor.exec(sql);
    }
}
