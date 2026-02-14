package io.github.hidekatsu_izuno.pglite_jdbc.jdbc;

import io.github.hidekatsu_izuno.pglite_jdbc.PGConnection;
import io.github.hidekatsu_izuno.pglite_jdbc.PGNotification;
import io.github.hidekatsu_izuno.pglite_jdbc.core.BaseConnection;
import io.github.hidekatsu_izuno.pglite_jdbc.core.QueryExecutor;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;
import io.github.hidekatsu_izuno.pglite_jdbc.util.PSQLException;
import io.github.hidekatsu_izuno.pglite_jdbc.util.PSQLState;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.postgresql.jdbc.AutoSave;
import org.postgresql.jdbc.PreferQueryMode;

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
    private long protocolTimeoutMillis = 10_000L;
    private AutoSave autosave = AutoSave.NEVER;
    private PreferQueryMode preferQueryMode = PreferQueryMode.EXTENDED;
    private String currentSchema;
    private String applicationName;
    private org.postgresql.copy.CopyManager copyApi;
    private org.postgresql.fastpath.Fastpath fastpathApi;
    private org.postgresql.largeobject.LargeObjectManager largeObjectApi;

    private PgConnection(
        QueryExecutor queryExecutor,
        String url,
        String user,
        String database,
        Properties properties
    ) {
        this.queryExecutor = queryExecutor;
        this.url = url;
        this.user = user;
        this.database = database;
        initializeProperties(properties);
    }

    public static Connection create(
        QueryExecutor queryExecutor,
        String url,
        String user,
        String database,
        Properties properties
    ) throws SQLException {
        var handler = new PgConnection(queryExecutor, url, user, database, properties);
        var proxy = (Connection) Proxy.newProxyInstance(
            PgConnection.class.getClassLoader(),
            new Class<?>[] {
                Connection.class,
                BaseConnection.class,
                PGConnection.class,
                org.postgresql.PGConnection.class,
            },
            handler
        );
        handler.self = proxy;
        handler.initializeSession();
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

    interface_.ExecProtocolResult execProtocol(byte[] message, boolean throwOnError) throws SQLException {
        return execProtocol(message, throwOnError, "execProtocol");
    }

    interface_.ExecProtocolResult execProtocol(byte[] message, boolean throwOnError, String stage)
        throws SQLException {
        try {
            return queryExecutor.getDatabase().execProtocol(
                message,
                new interface_.ExecProtocolOptions(false, throwOnError, null)
            ).toCompletableFuture().get(protocolTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeoutException) {
            throw new java.sql.SQLTimeoutException(
                "Protocol stage timed out: " + stage + " (" + protocolTimeoutMillis + "ms)",
                timeoutException
            );
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted during protocol stage: " + stage, interruptedException);
        } catch (ExecutionException executionException) {
            throw JdbcCompat.toSqlException(executionException.getCause());
        } catch (Throwable error) {
            throw JdbcCompat.toSqlException(error);
        }
    }

    @FunctionalInterface
    interface SqlStage<T> {
        T execute() throws SQLException;
    }

    <T> T runProtocolStage(String stage, SqlStage<T> stageCall) throws SQLException {
        var future = CompletableFuture.supplyAsync(
            () -> {
                try {
                    return stageCall.execute();
                } catch (SQLException sqlException) {
                    throw new CompletionException(sqlException);
                }
            },
            io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise.executor()
        );

        try {
            return future.get(protocolTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeoutException) {
            future.cancel(true);
            throw new java.sql.SQLTimeoutException(
                "Protocol stage timed out: " + stage + " (" + protocolTimeoutMillis + "ms)",
                timeoutException
            );
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted during protocol stage: " + stage, interruptedException);
        } catch (ExecutionException executionException) {
            var cause = executionException.getCause();
            if (cause instanceof SQLException sqlException) {
                throw sqlException;
            }
            throw JdbcCompat.toSqlException(cause);
        }
    }

    long protocolTimeoutMillis() {
        return protocolTimeoutMillis;
    }

    int protocolTimeoutSeconds() {
        return (int) Math.max(1L, (protocolTimeoutMillis + 999L) / 1000L);
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
                "createNClob", "createSQLXML", "createStruct" -> throw JdbcCompat.unsupported(name);
            case "createArrayOf" -> createArrayOf((String) args[0], args[1]);
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
                setQueryTimeoutSeconds((Integer) args[0]);
                yield null;
            }
            case "getQueryTimeout" -> queryTimeout;
            case "escapeIdentifier" -> '"' + String.valueOf(args[0]).replace("\"", "\"\"") + '"';
            case "escapeLiteral" -> '\'' + String.valueOf(args[0]).replace("'", "''") + '\'';
            case "getCopyAPI" -> getCopyAPI();
            case "getFastpathAPI" -> getFastpathAPI();
            case "getLargeObjectAPI" -> getLargeObjectAPI();
            case "getPreferQueryMode" -> preferQueryMode;
            case "getAutosave" -> autosave;
            case "setAutosave" -> {
                autosave = (AutoSave) args[0];
                yield null;
            }
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
            default -> {
                if (method.getDeclaringClass().getName().startsWith("org.postgresql")) {
                    throw new SQLFeatureNotSupportedException(name + " is not supported");
                }
                yield JdbcCompat.defaultReturn(method.getReturnType());
            }
        };
    }

    private void initializeProperties(Properties properties) {
        if (properties == null) {
            return;
        }
        defaultFetchSize = parseIntProperty(properties, "defaultFetchSize", 0);
        queryTimeout = parseIntProperty(properties, "queryTimeout", 0);
        prepareThreshold = parseIntProperty(properties, "prepareThreshold", 5);
        protocolTimeoutMillis = parseLongProperty(properties, "protocolTimeoutMs", protocolTimeoutMillis);
        protocolTimeoutMillis = parseLongProperty(properties, "pgliteProtocolTimeoutMs", protocolTimeoutMillis);
        if (queryTimeout > 0 && properties.getProperty("protocolTimeoutMs") == null &&
            properties.getProperty("pgliteProtocolTimeoutMs") == null) {
            protocolTimeoutMillis = Math.max(protocolTimeoutMillis, queryTimeout * 1000L);
        }
        applicationName = trimToNull(properties.getProperty("applicationName"));
        currentSchema = trimToNull(properties.getProperty("currentSchema"));

        var autosaveValue = trimToNull(properties.getProperty("autosave"));
        if (autosaveValue != null) {
            autosave = AutoSave.of(autosaveValue);
        }

        var preferQueryModeValue = trimToNull(properties.getProperty("preferQueryMode"));
        if (preferQueryModeValue != null) {
            preferQueryMode = PreferQueryMode.of(preferQueryModeValue);
        }
    }

    private void initializeSession() throws SQLException {
        if (currentSchema != null) {
            execControl("SET search_path TO " + currentSchema);
        }
    }

    private int parseIntProperty(Properties properties, String name, int defaultValue) {
        var value = trimToNull(properties.getProperty(name));
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private long parseLongProperty(Properties properties, String name, long defaultValue) {
        var value = trimToNull(properties.getProperty(name));
        if (value == null) {
            return defaultValue;
        }
        try {
            var parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    private void setQueryTimeoutSeconds(int seconds) throws SQLException {
        if (seconds < 0) {
            throw new PSQLException(
                "queryTimeout must be >= 0",
                PSQLState.INVALID_PARAMETER_VALUE
            );
        }
        queryTimeout = seconds;
    }

    private org.postgresql.copy.CopyManager getCopyAPI() throws SQLException {
        ensureOpen();
        if (copyApi == null) {
            copyApi = new PgCopyManagerAdapter(this);
        }
        return copyApi;
    }

    private org.postgresql.fastpath.Fastpath getFastpathAPI() throws SQLException {
        ensureOpen();
        if (fastpathApi == null) {
            fastpathApi = PgFastpathAdapter.create(this);
        }
        return fastpathApi;
    }

    private org.postgresql.largeobject.LargeObjectManager getLargeObjectAPI() throws SQLException {
        ensureOpen();
        if (largeObjectApi == null) {
            largeObjectApi = PgLargeObjectManagerAdapter.create(this);
        }
        return largeObjectApi;
    }

    private java.sql.Array createArrayOf(String typeName, Object elements) throws SQLException {
        if (elements == null) {
            return new PgArray(typeName, null);
        }
        if (elements instanceof Object[] objectArray) {
            return new PgArray(typeName, objectArray);
        }
        var arrayClass = elements.getClass();
        if (!arrayClass.isArray()) {
            throw new PSQLException(
                "createArrayOf expects array input",
                PSQLState.INVALID_PARAMETER_VALUE
            );
        }
        var length = Array.getLength(elements);
        var boxed = new Object[length];
        for (var i = 0; i < length; i++) {
            boxed[i] = Array.get(elements, i);
        }
        return new PgArray(typeName, boxed);
    }
}
