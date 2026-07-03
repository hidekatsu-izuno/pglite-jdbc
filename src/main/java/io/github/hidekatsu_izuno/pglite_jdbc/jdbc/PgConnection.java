package io.github.hidekatsu_izuno.pglite_jdbc.jdbc;

import io.github.hidekatsu_izuno.pglite_jdbc.core.QueryExecutor;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.Timer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.postgresql.jdbc.AutoSave;
import org.postgresql.jdbc.PreferQueryMode;
import org.postgresql.util.LruCache;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;

public final class PgConnection implements InvocationHandler {
    private static final org.postgresql.PGNotification[] EMPTY_NOTIFICATIONS =
        new org.postgresql.PGNotification[0];
    private static final org.postgresql.jdbc.TimestampUtils TIMESTAMP_UTILS =
        new org.postgresql.jdbc.TimestampUtils(false, TimeZone::getDefault);

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
    private int networkTimeout;
    private long protocolTimeoutMillis = 10_000L;
    private AutoSave autosave = AutoSave.NEVER;
    private PreferQueryMode preferQueryMode = PreferQueryMode.EXTENDED;
    private String currentSchema;
    private boolean adaptiveFetch;
    private int nextSavepointId = 1;
    private org.postgresql.copy.CopyManager copyApi;
    private org.postgresql.fastpath.Fastpath fastpathApi;
    private org.postgresql.largeobject.LargeObjectManager largeObjectApi;
    private final Map<String, String> parameterStatuses = new HashMap<>();
    private Set<Integer> binaryReceiveOids = new HashSet<>();
    private Set<Integer> binarySendOids = new HashSet<>();
    private final Map<String, Class<? extends org.postgresql.util.PGobject>> dataTypeObjects =
        new HashMap<>();
    private final org.postgresql.core.TypeInfo typeInfo = createTypeInfo();
    private final LruCache<org.postgresql.jdbc.FieldMetadata.Key, org.postgresql.jdbc.FieldMetadata>
        fieldMetadataCache = new LruCache<>(0, 0, false);
    private final Timer sharedTimer = new Timer(true);
    private final org.postgresql.core.QueryExecutor coreQueryExecutor = createCoreQueryExecutor();

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
        initializeParameterStatuses(properties);
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
                org.postgresql.PGConnection.class,
                org.postgresql.core.BaseConnection.class,
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

    String database() {
        return database;
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

    Class<? extends org.postgresql.util.PGobject> pgObjectClass(String typeName) {
        if (typeName == null) {
            return null;
        }
        return dataTypeObjects.get(normalizeTypeName(typeName));
    }

    org.postgresql.util.PGobject createPgObject(String typeName, Object value) throws SQLException {
        var objectClass = pgObjectClass(typeName);
        return JdbcCompat.toPgObject(
            typeName,
            value,
            objectClass != null ? objectClass : org.postgresql.util.PGobject.class
        );
    }

    interface_.DescribeQueryResult describe(String sql) throws SQLException {
        return queryExecutor.describe(sql);
    }

    interface_.Results<Map<String, Object>> query(String sql, Object[] params) throws SQLException {
        return query(sql, params, null);
    }

    interface_.Results<Map<String, Object>> query(
        String sql,
        Object[] params,
        Consumer<messages.NoticeMessage> onNotice
    ) throws SQLException {
        ensureTransactionIfNeeded();
        return queryExecutor.query(sql, params, onNotice);
    }

    interface_.Results<List<Object>> queryArray(String sql, Object[] params) throws SQLException {
        return queryArray(sql, params, null);
    }

    interface_.Results<List<Object>> queryArray(
        String sql,
        Object[] params,
        Consumer<messages.NoticeMessage> onNotice
    ) throws SQLException {
        ensureTransactionIfNeeded();
        return queryExecutor.queryArray(sql, params, onNotice);
    }

    List<interface_.Results<Map<String, Object>>> exec(String sql) throws SQLException {
        return exec(sql, null);
    }

    List<interface_.Results<Map<String, Object>>> exec(
        String sql,
        Consumer<messages.NoticeMessage> onNotice
    ) throws SQLException {
        ensureTransactionIfNeeded();
        return queryExecutor.exec(sql, onNotice);
    }

    List<interface_.Results<List<Object>>> execArray(String sql) throws SQLException {
        return execArray(sql, null);
    }

    List<interface_.Results<List<Object>>> execArray(
        String sql,
        Consumer<messages.NoticeMessage> onNotice
    ) throws SQLException {
        ensureTransactionIfNeeded();
        return queryExecutor.execArray(sql, onNotice);
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

        if (!"close".equals(name) && !"isClosed".equals(name)) {
            ensureOpen();
        }

        return switch (name) {
            case "close" -> {
                closeConnection();
                yield null;
            }
            case "isClosed" -> closed;
            case "createStatement" -> createStatement(args);
            case "prepareStatement" -> prepareStatement(args);
            case "prepareCall" -> throw JdbcCompat.unsupported(name);
            case "nativeSQL" -> JdbcCompat.replaceJdbcEscapes((String) args[0], true);
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
                if (args == null || args.length == 0) {
                    rollbackTransaction();
                } else {
                    rollbackToSavepoint((Savepoint) args[0]);
                }
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
            case "setSavepoint" -> args == null || args.length == 0
                ? setSavepoint()
                : setSavepoint((String) args[0]);
            case "releaseSavepoint" -> {
                releaseSavepoint((Savepoint) args[0]);
                yield null;
            }
            case "createBlob" -> new PgBlob((org.postgresql.core.BaseConnection) self, null);
            case "createClob", "createNClob" -> new PgClob((org.postgresql.core.BaseConnection) self, null);
            case "createSQLXML" -> new org.postgresql.jdbc.PgSQLXML(
                (org.postgresql.core.BaseConnection) self
            );
            case "setTypeMap", "createStruct" ->
                throw JdbcCompat.unsupported(name);
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
            case "setSchema" -> {
                setSchema((String) args[0]);
                yield null;
            }
            case "getSchema" -> getSchema();
            case "abort" -> {
                closeConnection();
                yield null;
            }
            case "setNetworkTimeout" -> {
                networkTimeout = (Integer) args[1];
                yield null;
            }
            case "getNetworkTimeout" -> networkTimeout;
            case "getNotifications", "getNotifications" + "\u0000" -> EMPTY_NOTIFICATIONS;
            case "getParameterStatuses" -> Collections.unmodifiableMap(parameterStatuses);
            case "getParameterStatus" -> parameterStatuses.get((String) args[0]);
            case "getBackendPID" -> 0;
            case "cancelQuery" -> null;
            case "addDataType" -> {
                addDataType((String) args[0], args[1]);
                yield null;
            }
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
            case "getReplicationAPI" -> throw new UnsupportedOperationException(
                "Replication is not supported by PGlite"
            );
            case "execSQLQuery" -> execSqlQuery((String) args[0]);
            case "execSQLUpdate" -> {
                execControl((String) args[0]);
                yield null;
            }
            case "getQueryExecutor" -> coreQueryExecutor;
            case "getReplicationProtocol" -> unsupportedCore("getReplicationProtocol");
            case "getObject" -> getObjectValue((String) args[0], (String) args[1], (byte[]) args[2]);
            case "getEncoding" -> org.postgresql.core.Encoding.getJVMEncoding("UTF-8");
            case "getTypeInfo" -> typeInfo;
            case "haveMinimumServerVersion" -> true;
            case "encodeString" -> encodeString((String) args[0]);
            case "escapeString" -> escapeString((String) args[0]);
            case "getStandardConformingStrings" -> true;
            case "getTimestampUtils" -> TIMESTAMP_UTILS;
            case "getLogger" -> Logger.getLogger("io.github.hidekatsu_izuno.pglite_jdbc");
            case "getStringVarcharFlag" -> true;
            case "getTransactionState" -> txOpen
                ? org.postgresql.core.TransactionState.OPEN
                : org.postgresql.core.TransactionState.IDLE;
            case "binaryTransferSend" -> false;
            case "isColumnSanitiserDisabled" -> false;
            case "addTimerTask" -> {
                sharedTimer.schedule((java.util.TimerTask) args[0], (Long) args[1]);
                yield null;
            }
            case "purgeTimerTasks" -> {
                sharedTimer.purge();
                yield null;
            }
            case "getFieldMetadataCache" -> fieldMetadataCache;
            case "createQuery" -> unsupportedCore("createQuery");
            case "setFlushCacheOnDeallocate" -> null;
            case "hintReadOnly" -> readOnly;
            case "getXmlFactoryFactory" -> org.postgresql.xml.DefaultPGXmlFactoryFactory.INSTANCE;
            case "getLogServerErrorDetail" -> true;
            case "getConvertBooleanToNumeric" -> false;
            case "getPreferQueryMode" -> preferQueryMode;
            case "getAutosave" -> autosave;
            case "setAutosave" -> {
                autosave = (AutoSave) args[0];
                yield null;
            }
            case "setAdaptiveFetch" -> {
                adaptiveFetch = (Boolean) args[0];
                yield null;
            }
            case "getAdaptiveFetch" -> adaptiveFetch;
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
            setSearchPath(currentSchema);
        }
    }

    private void initializeParameterStatuses(Properties properties) {
        parameterStatuses.put("server_version", "18.3");
        parameterStatuses.put("server_encoding", "UTF8");
        parameterStatuses.put("client_encoding", "UTF8");
        parameterStatuses.put("DateStyle", "ISO, MDY");
        parameterStatuses.put("TimeZone", "UTC");
        parameterStatuses.put("integer_datetimes", "on");
        parameterStatuses.put("standard_conforming_strings", "on");
        var applicationName = properties == null ? null : trimToNull(properties.getProperty("ApplicationName"));
        if (applicationName == null && properties != null) {
            applicationName = trimToNull(properties.getProperty("applicationName"));
        }
        parameterStatuses.put("application_name", applicationName == null ? "" : applicationName);
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

    private Savepoint setSavepoint() throws SQLException {
        var savepoint = PgSavepoint.unnamed(nextSavepointId++);
        createSavepoint(savepoint);
        return savepoint;
    }

    private Savepoint setSavepoint(String name) throws SQLException {
        if (name == null || name.isBlank()) {
            throw new PSQLException("Savepoint name must not be empty", PSQLState.INVALID_PARAMETER_VALUE);
        }
        var savepoint = PgSavepoint.named(nextSavepointId++, name);
        createSavepoint(savepoint);
        return savepoint;
    }

    private void createSavepoint(PgSavepoint savepoint) throws SQLException {
        ensureOpen();
        if (autoCommit) {
            throw new SQLException("Cannot establish a savepoint when autoCommit is true");
        }
        ensureTransactionIfNeeded();
        execControl("SAVEPOINT " + savepointIdentifier(savepoint));
    }

    private void rollbackToSavepoint(Savepoint savepoint) throws SQLException {
        var pgSavepoint = asPgSavepoint(savepoint);
        pgSavepoint.ensureActive();
        ensureOpen();
        if (autoCommit) {
            throw new SQLException("Cannot rollback to a savepoint when autoCommit is true");
        }
        execControl("ROLLBACK TO SAVEPOINT " + savepointIdentifier(pgSavepoint));
        txOpen = true;
    }

    private void releaseSavepoint(Savepoint savepoint) throws SQLException {
        var pgSavepoint = asPgSavepoint(savepoint);
        pgSavepoint.ensureActive();
        ensureOpen();
        if (autoCommit) {
            throw new SQLException("Cannot release a savepoint when autoCommit is true");
        }
        execControl("RELEASE SAVEPOINT " + savepointIdentifier(pgSavepoint));
        pgSavepoint.markReleased();
    }

    private PgSavepoint asPgSavepoint(Savepoint savepoint) throws SQLException {
        if (savepoint instanceof PgSavepoint pgSavepoint) {
            return pgSavepoint;
        }
        throw new SQLException("Savepoint was not created by this connection");
    }

    private String savepointIdentifier(PgSavepoint savepoint) {
        return '"' + savepoint.sqlIdentifier().replace("\"", "\"\"") + '"';
    }

    private void setSchema(String schema) throws SQLException {
        ensureOpen();
        var schemaName = trimToNull(schema);
        if (schemaName == null) {
            currentSchema = null;
            execControl("RESET search_path");
            return;
        }
        setSearchPath(schemaName);
        currentSchema = schemaName;
    }

    private String getSchema() throws SQLException {
        ensureOpen();
        return currentSchema != null ? currentSchema : "public";
    }

    private void setSearchPath(String schema) throws SQLException {
        execControl("SET search_path TO " + quoteIdentifier(schema));
    }

    private String quoteIdentifier(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }

    private String normalizeTypeName(String typeName) {
        return typeName.trim().toLowerCase(java.util.Locale.ROOT);
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

    private ResultSet execSqlQuery(String sql) throws SQLException {
        var result = queryExecutor.query(sql, null);
        return PgResultSet.create(this, null, JdbcCompat.toColumns(result.fields()), result.rows());
    }

    private void addDataType(String typeName, Object objectClass) throws SQLException {
        if (objectClass instanceof Class<?> clazz) {
            if (!org.postgresql.util.PGobject.class.isAssignableFrom(clazz)) {
                throw new PSQLException(
                    "Custom type class must extend PGobject: " + clazz.getName(),
                    PSQLState.INVALID_PARAMETER_VALUE
                );
            }
            dataTypeObjects.put(
                normalizeTypeName(typeName),
                clazz.asSubclass(org.postgresql.util.PGobject.class)
            );
            return;
        }
        if (objectClass instanceof String className) {
            try {
                addDataType(typeName, Class.forName(className));
                return;
            } catch (ClassNotFoundException exception) {
                throw new PSQLException(
                    "Custom type class not found: " + className,
                    PSQLState.INVALID_PARAMETER_VALUE,
                    exception
                );
            }
        }
        throw new PSQLException(
            "Unsupported custom type class: " + objectClass,
            PSQLState.INVALID_PARAMETER_VALUE
        );
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
            fastpathApi = new PgFastpathAdapter(this);
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

    private java.sql.Statement createStatement(Object[] args) throws SQLException {
        var options = statementOptions(args, 0);
        return PgStatement.create(this, null, options.type(), options.concurrency(), options.holdability());
    }

    private java.sql.PreparedStatement prepareStatement(Object[] args) throws SQLException {
        var options = statementOptions(args, 1);
        return (java.sql.PreparedStatement) PgStatement.create(
            this,
            (String) args[0],
            options.type(),
            options.concurrency(),
            options.holdability(),
            generatedColumns(args)
        );
    }

    private String[] generatedColumns(Object[] args) {
        if (args == null || args.length < 2) {
            return null;
        }
        if (args[1] instanceof Integer flag) {
            return flag == java.sql.Statement.RETURN_GENERATED_KEYS ? new String[0] : null;
        }
        if (args[1] instanceof String[] columns) {
            return columns.clone();
        }
        if (args[1] instanceof int[] columns && columns.length > 0) {
            var out = new String[columns.length];
            for (var i = 0; i < columns.length; i++) {
                out[i] = String.valueOf(columns[i]);
            }
            return out;
        }
        return null;
    }

    private StatementOptions statementOptions(Object[] args, int offset) throws SQLException {
        if (args == null || args.length <= offset) {
            return defaultStatementOptions();
        }
        if (args.length - offset == 2 || args.length - offset == 3) {
            var type = (Integer) args[offset];
            var concurrency = (Integer) args[offset + 1];
            var holdability = args.length - offset == 3
                ? (Integer) args[offset + 2]
                : ResultSet.CLOSE_CURSORS_AT_COMMIT;
            validateStatementOptions(type, concurrency, holdability);
            return new StatementOptions(type, concurrency, holdability);
        }
        return defaultStatementOptions();
    }

    private StatementOptions defaultStatementOptions() {
        return new StatementOptions(
            ResultSet.TYPE_FORWARD_ONLY,
            ResultSet.CONCUR_READ_ONLY,
            ResultSet.CLOSE_CURSORS_AT_COMMIT
        );
    }

    private void validateStatementOptions(int type, int concurrency, int holdability) throws SQLException {
        if (
            type != ResultSet.TYPE_FORWARD_ONLY &&
            type != ResultSet.TYPE_SCROLL_INSENSITIVE &&
            type != ResultSet.TYPE_SCROLL_SENSITIVE
        ) {
            throw new SQLException("Invalid result set type: " + type);
        }
        if (concurrency != ResultSet.CONCUR_READ_ONLY && concurrency != ResultSet.CONCUR_UPDATABLE) {
            throw new SQLException("Invalid result set concurrency: " + concurrency);
        }
        if (
            holdability != ResultSet.CLOSE_CURSORS_AT_COMMIT &&
            holdability != ResultSet.HOLD_CURSORS_OVER_COMMIT
        ) {
            throw new SQLException("Invalid result set holdability: " + holdability);
        }
    }

    private record StatementOptions(int type, int concurrency, int holdability) {
    }

    private java.sql.Array createArrayOf(String typeName, Object elements) throws SQLException {
        var oid = pgTypeToOid(typeName);
        var arrayOid = arrayOidToElementOid(oid) != 0 ? oid : elementOidToArrayOid(oid);
        if (arrayOid == 0) {
            arrayOid = elementOidToArrayOid(pgTypeToOid(JdbcCompat.arrayElementTypeName(typeName)));
        }
        if (arrayOid == 0) {
            throw new PSQLException(
                "Unsupported array type: " + typeName,
                PSQLState.INVALID_PARAMETER_VALUE
            );
        }
        return new org.postgresql.jdbc.PgArray(
            (org.postgresql.core.BaseConnection) self,
            arrayOid,
            JdbcCompat.toArrayLiteral(elements)
        );
    }

    private Object unsupportedCore(String method) throws SQLException {
        throw new SQLFeatureNotSupportedException(method + " is not supported");
    }

    private Object getObjectValue(String type, String value, byte[] byteValue) {
        if (value != null) {
            return value;
        }
        if (byteValue != null) {
            return new String(byteValue, StandardCharsets.UTF_8);
        }
        return null;
    }

    private byte[] encodeString(String value) throws SQLException {
        if (value == null) {
            return null;
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private String escapeString(String value) throws SQLException {
        if (value == null) {
            return null;
        }
        if (value.indexOf('\0') >= 0) {
            throw new PSQLException("Zero bytes are not allowed in strings", PSQLState.INVALID_PARAMETER_VALUE);
        }
        return value.replace("\\", "\\\\").replace("'", "''");
    }

    private org.postgresql.core.TypeInfo createTypeInfo() {
        return (org.postgresql.core.TypeInfo) Proxy.newProxyInstance(
            PgConnection.class.getClassLoader(),
            new Class<?>[] { org.postgresql.core.TypeInfo.class },
            (proxy, method, args) -> {
                var name = method.getName();
                if (method.getDeclaringClass() == Object.class) {
                    return switch (name) {
                        case "toString" -> "PgTypeInfo";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> null;
                    };
                }
                return switch (name) {
                    case "getSQLType" -> {
                        if (args[0] instanceof Integer oid) {
                            yield JdbcCompat.oidToJdbcType(oid);
                        }
                        yield pgTypeToSqlType((String) args[0]);
                    }
            case "getPGType" -> {
                        if (args[0] instanceof String typeName) {
                            yield pgTypeToOid(typeName);
                        }
                        var type = JdbcCompat.oidToPgType((Integer) args[0]);
                        yield type.startsWith("oid:") ? null : type;
                    }
                    case "getPGArrayElement" -> arrayOidToElementOid((Integer) args[0]);
                    case "getPGArrayType" -> elementOidToArrayOid(pgTypeToOid((String) args[0]));
                    case "getArrayDelimiter" -> ',';
                    case "getTypeForAlias" -> args[0];
                    case "getJavaArrayType" -> pgTypeToSqlType((String) args[0]);
                    case "getJavaClass" -> javaClassForOid((Integer) args[0]);
                    case "getPrecision" -> precision((Integer) args[0], (Integer) args[1]);
                    case "getScale" -> scale((Integer) args[0], (Integer) args[1]);
                    case "getDisplaySize" -> displaySize((Integer) args[0], (Integer) args[1]);
                    case "getMaximumPrecision" -> maximumPrecision((Integer) args[0]);
                    case "requiresQuoting" -> requiresQuotingOid((Integer) args[0]);
                    case "requiresQuotingSqlType" -> requiresQuotingSqlType((Integer) args[0]);
                    case "longOidToInt" -> (int) (long) (Long) args[0];
                    case "intOidToLong" -> ((Integer) args[0]) & 0xFFFFFFFFL;
                    case "getPGTypeNamesWithSQLTypes", "getPGTypeOidsWithSQLTypes" ->
                        java.util.Collections.emptyIterator();
                    case "getPGobject" -> pgObjectClass((String) args[0]);
                    case "isCaseSensitive" -> isCaseSensitiveOid((Integer) args[0]);
                    case "isSigned" -> isSignedOid((Integer) args[0]);
                    case "addDataType" -> {
                        addDataType((String) args[0], args[1]);
                        yield null;
                    }
                    case "addCoreType" -> null;
                    default -> JdbcCompat.defaultReturn(method.getReturnType());
                };
            }
        );
    }

    private org.postgresql.core.QueryExecutor createCoreQueryExecutor() {
        return (org.postgresql.core.QueryExecutor) Proxy.newProxyInstance(
            PgConnection.class.getClassLoader(),
            new Class<?>[] { org.postgresql.core.QueryExecutor.class },
            (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "toString" -> "PgCoreQueryExecutor";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> null;
                    };
                }
                if (method.getName().equals("getAutoSave")) {
                    return autosave;
                }
                return switch (method.getName()) {
                    case "getProtocolVersion" -> org.postgresql.core.ProtocolVersion.v3_0;
                    case "isReWriteBatchedInsertsEnabled" -> false;
                    case "processNotifies", "sendQueryCancel", "setPreferQueryMode", "setAutoSave",
                        "setFlushCacheOnDeallocate", "setNetworkTimeout", "addQueryToAdaptiveFetchCache",
                        "removeQueryFromAdaptiveFetchCache", "releaseQuery" -> {
                        if ("setPreferQueryMode".equals(method.getName())) {
                            preferQueryMode = (PreferQueryMode) args[0];
                        } else if ("setAutoSave".equals(method.getName())) {
                            autosave = (AutoSave) args[0];
                        } else if ("setNetworkTimeout".equals(method.getName())) {
                            networkTimeout = (Integer) args[0];
                        }
                        yield null;
                    }
                    case "getIntegerDateTimes", "getStandardConformingStrings",
                        "getQuoteReturningIdentifiers" -> true;
                    case "getHostSpec", "getCloseAction" -> null;
                    case "getUser" -> user;
                    case "getDatabase" -> database;
                    case "getBackendPID" -> 0;
                    case "abort", "close" -> {
                        closeConnection();
                        yield null;
                    }
                    case "isClosed" -> closed;
                    case "getServerVersion" -> parameterStatuses.get("server_version");
                    case "getServerVersionNum" -> 180003;
                    case "getNotifications" -> EMPTY_NOTIFICATIONS;
                    case "getWarnings" -> warnings;
                    case "getTransactionState" -> txOpen
                        ? org.postgresql.core.TransactionState.OPEN
                        : org.postgresql.core.TransactionState.IDLE;
                    case "getTimeZone" -> TimeZone.getTimeZone(parameterStatuses.get("TimeZone"));
                    case "getEncoding" -> org.postgresql.core.Encoding.getJVMEncoding("UTF-8");
                    case "getApplicationName" -> parameterStatuses.get("application_name");
                    case "isColumnSanitiserDisabled" -> false;
                    case "getEscapeSyntaxCallMode" -> org.postgresql.jdbc.EscapeSyntaxCallMode.SELECT;
                    case "getPreferQueryMode" -> preferQueryMode;
                    case "willHealOnRetry" -> false;
                    case "getNetworkTimeout" -> networkTimeout;
                    case "getParameterStatuses" -> Collections.unmodifiableMap(parameterStatuses);
                    case "getParameterStatus" -> parameterStatuses.get((String) args[0]);
                    case "getAdaptiveFetchSize" -> 0;
                    case "getAdaptiveFetch" -> adaptiveFetch;
                    case "setAdaptiveFetch" -> {
                        adaptiveFetch = (Boolean) args[0];
                        yield null;
                    }
                    case "addBinaryReceiveOid" -> {
                        binaryReceiveOids.add((Integer) args[0]);
                        yield null;
                    }
                    case "removeBinaryReceiveOid" -> {
                        binaryReceiveOids.remove((Integer) args[0]);
                        yield null;
                    }
                    case "getBinaryReceiveOids" -> Collections.unmodifiableSet(binaryReceiveOids);
                    case "setBinaryReceiveOids" -> {
                        Object arg = args[0];
                        binaryReceiveOids = new HashSet<>();
                        if (arg instanceof Set<?> set) {
                            for (Object item : set) {
                                binaryReceiveOids.add((Integer) item);
                            }
                        }
                        yield null;
                    }
                    case "addBinarySendOid" -> {
                        binarySendOids.add((Integer) args[0]);
                        yield null;
                    }
                    case "removeBinarySendOid" -> {
                        binarySendOids.remove((Integer) args[0]);
                        yield null;
                    }
                    case "getBinarySendOids" -> Collections.unmodifiableSet(binarySendOids);
                    case "setBinarySendOids" -> {
                        Object arg = args[0];
                        binarySendOids = new HashSet<>();
                        if (arg instanceof Set<?> set) {
                            for (Object item : set) {
                                binarySendOids.add((Integer) item);
                            }
                        }
                        yield null;
                    }
                    default -> throw new SQLFeatureNotSupportedException(
                        "Core QueryExecutor method is not supported: " + method.getName()
                    );
                };
            }
        );
    }

    private int pgTypeToSqlType(String typeName) {
        if (typeName == null) {
            return Types.OTHER;
        }
        return switch (typeName.toLowerCase()) {
            case "bool", "boolean" -> Types.BOOLEAN;
            case "int2", "smallint" -> Types.SMALLINT;
            case "int4", "integer", "int" -> Types.INTEGER;
            case "int8", "bigint" -> Types.BIGINT;
            case "oid" -> Types.BIGINT;
            case "float4", "real" -> Types.REAL;
            case "float8", "double", "double precision" -> Types.DOUBLE;
            case "money" -> Types.DOUBLE;
            case "numeric", "decimal" -> Types.NUMERIC;
            case "char" -> Types.CHAR;
            case "name", "text", "varchar", "character varying", "bpchar", "character" -> Types.VARCHAR;
            case "bit", "varbit" -> Types.BIT;
            case "bytea" -> Types.BINARY;
            case "date" -> Types.DATE;
            case "time" -> Types.TIME;
            case "timetz", "time with time zone" -> Types.TIME_WITH_TIMEZONE;
            case "timestamp" -> Types.TIMESTAMP;
            case "timestamptz", "timestamp with time zone" -> Types.TIMESTAMP_WITH_TIMEZONE;
            case "uuid", "json", "jsonb", "inet", "cidr", "macaddr", "macaddr8" -> Types.OTHER;
            case "bool[]", "_bool", "int2[]", "_int2", "int4[]", "_int4", "int8[]", "_int8",
                "text[]", "_text", "varchar[]", "_varchar", "bytea[]", "_bytea", "uuid[]", "_uuid",
                "json[]", "_json", "jsonb[]", "_jsonb" -> Types.ARRAY;
            default -> Types.OTHER;
        };
    }

    int pgTypeToOid(String typeName) {
        if (typeName == null) {
            return 0;
        }
        return switch (typeName.toLowerCase()) {
            case "bool", "boolean" -> 16;
            case "char" -> 18;
            case "name" -> 19;
            case "int2", "smallint" -> 21;
            case "int4", "integer", "int" -> 23;
            case "int8", "bigint" -> 20;
            case "oid" -> 26;
            case "text" -> 25;
            case "float4", "real" -> 700;
            case "float8", "double", "double precision" -> 701;
            case "money" -> 790;
            case "date" -> 1082;
            case "time" -> 1083;
            case "timetz", "time with time zone" -> 1266;
            case "timestamp" -> 1114;
            case "timestamptz", "timestamp with time zone" -> 1184;
            case "numeric", "decimal" -> 1700;
            case "varchar", "character varying" -> 1043;
            case "bpchar", "character" -> 1042;
            case "bit" -> 1560;
            case "varbit" -> 1562;
            case "bytea" -> 17;
            case "uuid" -> 2950;
            case "json" -> 114;
            case "jsonb" -> 3802;
            case "inet" -> 869;
            case "cidr" -> 650;
            case "macaddr" -> 829;
            case "macaddr8" -> 774;
            case "bool[]", "_bool" -> 1000;
            case "int2[]", "_int2" -> 1005;
            case "int4[]", "_int4" -> 1007;
            case "int8[]", "_int8" -> 1016;
            case "text[]", "_text" -> 1009;
            case "varchar[]", "_varchar" -> 1015;
            case "bytea[]", "_bytea" -> 1001;
            case "bit[]", "_bit" -> 1561;
            case "varbit[]", "_varbit" -> 1563;
            case "uuid[]", "_uuid" -> 2951;
            case "json[]", "_json" -> 199;
            case "jsonb[]", "_jsonb" -> 3807;
            default -> 0;
        };
    }

    private int elementOidToArrayOid(int oid) {
        return switch (oid) {
            case 16 -> 1000;
            case 18 -> 1002;
            case 19 -> 1003;
            case 21 -> 1005;
            case 23 -> 1007;
            case 20 -> 1016;
            case 26 -> 1028;
            case 25 -> 1009;
            case 700 -> 1021;
            case 701 -> 1022;
            case 790 -> 791;
            case 1082 -> 1182;
            case 1083 -> 1183;
            case 1266 -> 1270;
            case 1114 -> 1115;
            case 1184 -> 1185;
            case 1700 -> 1231;
            case 1043 -> 1015;
            case 1042 -> 1014;
            case 1560 -> 1561;
            case 1562 -> 1563;
            case 17 -> 1001;
            case 2950 -> 2951;
            case 114 -> 199;
            case 3802 -> 3807;
            default -> 0;
        };
    }

    private int arrayOidToElementOid(int oid) {
        return switch (oid) {
            case 1000 -> 16;
            case 1002 -> 18;
            case 1003 -> 19;
            case 1005 -> 21;
            case 1007 -> 23;
            case 1016 -> 20;
            case 1028 -> 26;
            case 1009 -> 25;
            case 1021 -> 700;
            case 1022 -> 701;
            case 791 -> 790;
            case 1182 -> 1082;
            case 1183 -> 1083;
            case 1270 -> 1266;
            case 1115 -> 1114;
            case 1185 -> 1184;
            case 1231 -> 1700;
            case 1015 -> 1043;
            case 1014 -> 1042;
            case 1561 -> 1560;
            case 1563 -> 1562;
            case 1001 -> 17;
            case 2951 -> 2950;
            case 199 -> 114;
            case 3807 -> 3802;
            default -> 0;
        };
    }

    private String javaClassForOid(int oid) {
        if (oid == 1560) {
            return Boolean.class.getName();
        }
        return switch (JdbcCompat.oidToJdbcType(oid)) {
            case Types.BOOLEAN -> Boolean.class.getName();
            case Types.SMALLINT -> Short.class.getName();
            case Types.INTEGER -> Integer.class.getName();
            case Types.BIGINT -> Long.class.getName();
            case Types.REAL -> Float.class.getName();
            case Types.DOUBLE, Types.FLOAT -> Double.class.getName();
            case Types.NUMERIC, Types.DECIMAL -> java.math.BigDecimal.class.getName();
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY -> byte[].class.getName();
            case Types.DATE -> java.sql.Date.class.getName();
            case Types.TIME, Types.TIME_WITH_TIMEZONE -> java.sql.Time.class.getName();
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> java.sql.Timestamp.class.getName();
            case Types.ARRAY -> java.sql.Array.class.getName();
            default -> String.class.getName();
        };
    }

    private boolean requiresQuotingOid(int oid) {
        return requiresQuotingSqlType(JdbcCompat.oidToJdbcType(oid));
    }

    private boolean requiresQuotingSqlType(int sqlType) {
        return switch (sqlType) {
            case Types.INTEGER, Types.SMALLINT, Types.BIGINT, Types.DECIMAL, Types.NUMERIC,
                Types.REAL, Types.DOUBLE, Types.FLOAT, Types.BOOLEAN -> false;
            default -> true;
        };
    }

    private int precision(int oid, int typmod) {
        oid = arrayOidToElementOidOrSelf(oid);
        return switch (oid) {
            case 16, 18 -> 1;
            case 21 -> 5;
            case 23, 26 -> 10;
            case 20 -> 19;
            case 700 -> 8;
            case 701, 790 -> 17;
            case 1042, 1043 -> typmod == -1 ? 0 : typmod - 4;
            case 1082, 1083, 1114, 1184, 1186, 1266 -> displaySize(oid, typmod);
            case 1560 -> typmod;
            case 1562 -> typmod == -1 ? 0 : typmod;
            case 1700 -> typmod == -1 ? 0 : ((typmod - 4) & 0xffff0000) >> 16;
            default -> 0;
        };
    }

    private int scale(int oid, int typmod) {
        oid = arrayOidToElementOidOrSelf(oid);
        return switch (oid) {
            case 700 -> 8;
            case 701, 790 -> 17;
            case 1083, 1114, 1184, 1266 -> typmod == -1 ? 6 : typmod;
            case 1186 -> typmod == -1 ? 6 : typmod & 0xffff;
            case 1700 -> typmod == -1 ? 0 : (typmod - 4) & 0xffff;
            default -> 0;
        };
    }

    private int displaySize(int oid, int typmod) {
        oid = arrayOidToElementOidOrSelf(oid);
        return switch (oid) {
            case 16, 18 -> 1;
            case 21 -> 6;
            case 23 -> 11;
            case 26 -> 10;
            case 20 -> 20;
            case 700 -> 15;
            case 701, 790 -> 25;
            case 1082 -> 13;
            case 1083 -> 8 + timeSecondSize(typmod);
            case 1266 -> 8 + timeSecondSize(typmod) + 6;
            case 1114 -> 22 + timeSecondSize(typmod);
            case 1184 -> 22 + timeSecondSize(typmod) + 6;
            case 1186 -> 49;
            case 1042, 1043 -> typmod == -1 ? 0 : typmod - 4;
            case 1560 -> typmod;
            case 1562 -> typmod == -1 ? 0 : typmod;
            case 1700 -> numericDisplaySize(typmod);
            default -> 0;
        };
    }

    private int maximumPrecision(int oid) {
        oid = arrayOidToElementOidOrSelf(oid);
        return switch (oid) {
            case 1042, 1043 -> 10485760;
            case 1083, 1266 -> 6;
            case 1114, 1184, 1186 -> 6;
            case 1560, 1562 -> 83886080;
            case 1700 -> 1000;
            default -> 0;
        };
    }

    private int arrayOidToElementOidOrSelf(int oid) {
        var elementOid = arrayOidToElementOid(oid);
        return elementOid == 0 ? oid : elementOid;
    }

    private int timeSecondSize(int typmod) {
        return switch (typmod) {
            case -1 -> 7;
            case 0 -> 0;
            case 1 -> 3;
            default -> typmod + 1;
        };
    }

    private int numericDisplaySize(int typmod) {
        if (typmod == -1) {
            return 131089;
        }
        var precision = ((typmod - 4) >> 16) & 0xffff;
        var scale = (typmod - 4) & 0xffff;
        return 1 + precision + (scale == 0 ? 0 : 1);
    }

    private boolean isSignedOid(int oid) {
        oid = arrayOidToElementOidOrSelf(oid);
        return switch (oid) {
            case 20, 21, 23, 700, 701, 1700 -> true;
            default -> false;
        };
    }

    private boolean isCaseSensitiveOid(int oid) {
        return switch (oid) {
            case 16, 20, 21, 23, 26, 700, 701, 1082, 1083, 1114, 1184, 1186, 1266, 1560, 1562, 1700 ->
                false;
            default -> true;
        };
    }
}
