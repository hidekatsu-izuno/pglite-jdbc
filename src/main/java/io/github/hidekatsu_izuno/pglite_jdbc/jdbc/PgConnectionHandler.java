package io.github.hidekatsu_izuno.pglite_jdbc.jdbc;

import io.github.hidekatsu_izuno.pglite_jdbc.core.QueryExecutor;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ClientInfoStatus;
import java.sql.SQLClientInfoException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.Timer;
import java.util.stream.Collectors;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.postgresql.core.Utils;
import org.postgresql.jdbc.AutoSave;
import org.postgresql.jdbc.PSQLSavepoint;
import org.postgresql.jdbc.PreferQueryMode;
import org.postgresql.util.LruCache;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;

public final class PgConnectionHandler implements InvocationHandler {
    private record QueryKey(
        String sql,
        boolean escapeProcessing,
        boolean isParameterized,
        String[] columnNames
    ) {}

    private static final org.postgresql.PGNotification[] EMPTY_NOTIFICATIONS =
        new org.postgresql.PGNotification[0];
    private static final org.postgresql.jdbc.TimestampUtils TIMESTAMP_UTILS =
        new org.postgresql.jdbc.TimestampUtils(false, TimeZone::getDefault);
    private static final int UNKNOWN_LENGTH = Integer.MAX_VALUE;

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
    private int holdability = ResultSet.CLOSE_CURSORS_AT_COMMIT;
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
    private final Properties clientInfo = new Properties();
    private final Map<String, String> parameterStatuses = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private Map<String, Class<?>> typeMap = new HashMap<>();
    private Set<Integer> binaryReceiveOids = new HashSet<>();
    private Set<Integer> binarySendOids = new HashSet<>();
    private final Map<String, Class<? extends org.postgresql.util.PGobject>> dataTypeObjects =
        new HashMap<>();
    private org.postgresql.core.TypeInfo typeInfo;
    private final LruCache<org.postgresql.jdbc.FieldMetadata.Key, org.postgresql.jdbc.FieldMetadata>
        fieldMetadataCache = new LruCache<>(0, 0, false);
    private final Timer sharedTimer = new Timer(true);
    private final org.postgresql.core.QueryExecutor coreQueryExecutor = createCoreQueryExecutor();
    private final org.postgresql.core.ReplicationProtocol replicationProtocol = createReplicationProtocol();

    private PgConnectionHandler(
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
        var handler = new PgConnectionHandler(queryExecutor, url, user, database, properties);
        var proxy = (Connection) Proxy.newProxyInstance(
            PgConnectionHandler.class.getClassLoader(),
            new Class<?>[] {
                Connection.class,
                org.postgresql.PGConnection.class,
                org.postgresql.core.BaseConnection.class,
            },
            handler
        );
        handler.self = proxy;
        handler.typeInfo = handler.createTypeInfo();
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

    int serverMajorVersion() {
        return serverVersionPart(0);
    }

    int serverMinorVersion() {
        return serverVersionPart(1);
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

    boolean getAdaptiveFetchInternal() {
        return adaptiveFetch;
    }

    org.postgresql.core.BaseConnection baseConnection() {
        return (org.postgresql.core.BaseConnection) self;
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

        if (!"close".equals(name) && !"isClosed".equals(name) && !"isValid".equals(name) &&
            !"setClientInfo".equals(name)) {
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
            case "prepareCall" -> prepareCall(args);
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
            case "getMetaData" -> PgDatabaseMetaDataHandler.create(this);
            case "setReadOnly" -> {
                setReadOnly((Boolean) args[0]);
                yield null;
            }
            case "isReadOnly" -> readOnly;
            case "setCatalog" -> {
                catalog = (String) args[0];
                yield null;
            }
            case "getCatalog" -> catalog;
            case "setTransactionIsolation" -> {
                setTransactionIsolation((Integer) args[0]);
                yield null;
            }
            case "getTransactionIsolation" -> transactionIsolation;
            case "getWarnings" -> warnings;
            case "clearWarnings" -> {
                warnings = null;
                yield null;
            }
            case "setHoldability" -> {
                setHoldability((Integer) args[0]);
                yield null;
            }
            case "getHoldability" -> holdability;
            case "setSavepoint" -> args == null || args.length == 0
                ? setSavepoint()
                : setSavepoint((String) args[0]);
            case "releaseSavepoint" -> {
                releaseSavepoint((Savepoint) args[0]);
                yield null;
            }
            case "createBlob" -> throw pgjdbcNotImplemented("createBlob()");
            case "createClob" -> throw pgjdbcNotImplemented("createClob()");
            case "createNClob" -> throw pgjdbcNotImplemented("createNClob()");
            case "createSQLXML" -> new org.postgresql.jdbc.PgSQLXML(
                (org.postgresql.core.BaseConnection) self
            );
            case "createStruct" -> throw pgjdbcNotImplemented("createStruct(String, Object[])");
            case "setTypeMap" -> {
                setTypeMap(castTypeMap(args[0]));
                yield null;
            }
            case "createArrayOf" -> createArrayOf((String) args[0], args[1]);
            case "getTypeMap" -> typeMap;
            case "isValid" -> isValid((Integer) args[0]);
            case "setClientInfo" -> {
                if (args[0] instanceof Properties properties) {
                    setClientInfo(properties);
                } else {
                    setClientInfo((String) args[0], (String) args[1]);
                }
                yield null;
            }
            case "getClientInfo" -> {
                if (args == null || args.length == 0) {
                    yield getClientInfo();
                }
                yield getClientInfo((String) args[0]);
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
                setNetworkTimeout((Integer) args[1]);
                yield null;
            }
            case "getNetworkTimeout" -> networkTimeout;
            case "setShardingKeyIfValid" -> throw new SQLFeatureNotSupportedException(
                "setShardingKeyIfValid not implemented"
            );
            case "setShardingKey" -> throw new SQLFeatureNotSupportedException(
                "setShardingKey not implemented"
            );
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
                setDefaultFetchSize((Integer) args[0]);
                yield null;
            }
            case "getDefaultFetchSize" -> defaultFetchSize;
            case "setQueryTimeout" -> {
                setQueryTimeout((Integer) args[0]);
                yield null;
            }
            case "getQueryTimeout" -> queryTimeout;
            case "escapeIdentifier" -> Utils.escapeIdentifier(null, (String) args[0]).toString();
            case "escapeLiteral" -> Utils.escapeLiteral(null, (String) args[0], true).toString();
            case "getCopyAPI" -> getCopyAPI();
            case "getFastpathAPI" -> getFastpathAPI();
            case "getLargeObjectAPI" -> getLargeObjectAPI();
            case "getReplicationAPI" -> new org.postgresql.replication.PGReplicationConnectionImpl(
                (org.postgresql.core.BaseConnection) self
            );
            case "alterUserPassword" -> {
                alterUserPassword((String) args[0], (char[]) args[1], (String) args[2]);
                yield null;
            }
            case "execSQLQuery" -> execSqlQuery((String) args[0]);
            case "execSQLUpdate" -> {
                execControl((String) args[0]);
                yield null;
            }
            case "getQueryExecutor" -> coreQueryExecutor;
            case "getReplicationProtocol" -> replicationProtocol;
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
            case "createQuery" -> createCachedQuery(
                (String) args[0],
                (Boolean) args[1],
                (Boolean) args[2],
                (String[]) args[3]
            );
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
                throw new SQLException("Cannot unwrap to " + iface.getName());
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
        setApplicationName(applicationName);
    }

    private boolean isValid(int timeout) throws SQLException {
        if (timeout < 0) {
            throw new PSQLException("Invalid timeout (" + timeout + "<0).", PSQLState.INVALID_PARAMETER_VALUE);
        }
        return !closed;
    }

    private void setClientInfo(String name, String value) throws SQLClientInfoException {
        try {
            ensureOpen();
        } catch (SQLException cause) {
            var failures = new HashMap<String, ClientInfoStatus>();
            failures.put(name, ClientInfoStatus.REASON_UNKNOWN);
            throw new SQLClientInfoException("This connection has been closed.", failures, cause);
        }

        if ("ApplicationName".equals(name)) {
            setApplicationName(value);
            return;
        }

        warnings = appendWarning(
            warnings,
            new SQLWarning("ClientInfo property not supported.", PSQLState.NOT_IMPLEMENTED.getState())
        );
    }

    private void setClientInfo(Properties properties) throws SQLClientInfoException {
        try {
            ensureOpen();
        } catch (SQLException cause) {
            var failures = new HashMap<String, ClientInfoStatus>();
            for (var entry : properties.entrySet()) {
                failures.put((String) entry.getKey(), ClientInfoStatus.REASON_UNKNOWN);
            }
            throw new SQLClientInfoException("This connection has been closed.", failures, cause);
        }

        setClientInfo("ApplicationName", properties.getProperty("ApplicationName", null));
    }

    private String getClientInfo(String name) {
        return clientInfo.getProperty(name);
    }

    private Properties getClientInfo() {
        return clientInfo;
    }

    private void setApplicationName(String applicationName) {
        var value = applicationName == null ? "" : applicationName;
        parameterStatuses.put("application_name", value);
        clientInfo.put("ApplicationName", value);
    }

    private SQLWarning appendWarning(SQLWarning current, SQLWarning warning) {
        if (current == null) {
            return warning;
        }
        current.setNextWarning(warning);
        return current;
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

    private int serverVersionPart(int index) {
        var version = parameterStatuses.get("server_version");
        if (version == null) {
            return 0;
        }
        var parts = version.split("\\.", 3);
        if (index >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index]);
        } catch (NumberFormatException ignored) {
            return 0;
        }
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

    private void setReadOnly(boolean value) throws SQLException {
        ensureOpen();
        if (txOpen) {
            throw new PSQLException(
                "Cannot change transaction read-only property in the middle of a transaction.",
                PSQLState.ACTIVE_SQL_TRANSACTION
            );
        }
        readOnly = value;
    }

    private void setTransactionIsolation(int level) throws SQLException {
        ensureOpen();
        if (txOpen) {
            throw new PSQLException(
                "Cannot change transaction isolation level in the middle of a transaction.",
                PSQLState.ACTIVE_SQL_TRANSACTION
            );
        }
        if (isolationLevelName(level) == null) {
            throw new PSQLException(
                "Transaction isolation level " + level + " not supported.",
                PSQLState.NOT_IMPLEMENTED
            );
        }
        transactionIsolation = level;
    }

    private String isolationLevelName(int level) {
        return switch (level) {
            case Connection.TRANSACTION_READ_COMMITTED -> "READ COMMITTED";
            case Connection.TRANSACTION_SERIALIZABLE -> "SERIALIZABLE";
            case Connection.TRANSACTION_READ_UNCOMMITTED -> "READ UNCOMMITTED";
            case Connection.TRANSACTION_REPEATABLE_READ -> "REPEATABLE READ";
            default -> null;
        };
    }

    private void setTypeMap(Map<String, Class<?>> map) {
        typeMap = map;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Class<?>> castTypeMap(Object value) {
        return (Map<String, Class<?>>) value;
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
        var savepoint = new PSQLSavepoint(nextSavepointId++);
        createSavepoint(savepoint);
        return savepoint;
    }

    private Savepoint setSavepoint(String name) throws SQLException {
        var savepoint = new PSQLSavepoint(name);
        createSavepoint(savepoint);
        return savepoint;
    }

    private void createSavepoint(PSQLSavepoint savepoint) throws SQLException {
        ensureOpen();
        if (autoCommit) {
            throw new PSQLException(
                "Cannot establish a savepoint in auto-commit mode.",
                PSQLState.NO_ACTIVE_SQL_TRANSACTION
            );
        }
        ensureTransactionIfNeeded();
        execControl("SAVEPOINT " + savepoint.getPGName());
    }

    private void rollbackToSavepoint(Savepoint savepoint) throws SQLException {
        var pgSavepoint = asPgSavepoint(savepoint);
        ensureOpen();
        if (autoCommit) {
            throw new SQLException("Cannot rollback to a savepoint when autoCommit is true");
        }
        execControl("ROLLBACK TO SAVEPOINT " + pgSavepoint.getPGName());
        txOpen = true;
    }

    private void releaseSavepoint(Savepoint savepoint) throws SQLException {
        var pgSavepoint = asPgSavepoint(savepoint);
        ensureOpen();
        if (autoCommit) {
            throw new SQLException("Cannot release a savepoint when autoCommit is true");
        }
        execControl("RELEASE SAVEPOINT " + pgSavepoint.getPGName());
        pgSavepoint.invalidate();
    }

    private PSQLSavepoint asPgSavepoint(Savepoint savepoint) throws SQLException {
        if (savepoint instanceof PSQLSavepoint pgSavepoint) {
            return pgSavepoint;
        }
        throw new SQLException("Savepoint was not created by this connection");
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

    private String quoteIdentifier(String identifier) throws SQLException {
        return Utils.escapeIdentifier(null, identifier).toString();
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
        return PgResultSetHandler.create(this, null, JdbcCompat.toColumns(result.fields()), result.rows());
    }

    private void addDataType(String typeName, Object objectClass) throws SQLException {
        ensureOpen();
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
            if (typeInfo != null) {
                typeInfo.addDataType(typeName, clazz.asSubclass(org.postgresql.util.PGobject.class));
            }
            return;
        }
        if (objectClass instanceof String className) {
            try {
                addDataType(typeName, Class.forName(className));
                return;
            } catch (Exception exception) {
                throw new RuntimeException("Cannot register new type " + typeName, exception);
            }
        }
        throw new PSQLException(
            "Unsupported custom type class: " + objectClass,
            PSQLState.INVALID_PARAMETER_VALUE
        );
    }

    private void setDefaultFetchSize(int fetchSize) throws SQLException {
        if (fetchSize < 0) {
            throw new PSQLException(
                "Fetch size must be a value greater than or equal to 0.",
                PSQLState.INVALID_PARAMETER_VALUE
            );
        }
        defaultFetchSize = fetchSize;
    }

    private void setQueryTimeout(int seconds) throws SQLException {
        if (seconds < 0) {
            throw new PSQLException(
                "Query timeout must be a value greater than or equal to 0.",
                PSQLState.INVALID_PARAMETER_VALUE
            );
        }
        queryTimeout = seconds;
    }

    private void setNetworkTimeout(int milliseconds) throws SQLException {
        if (milliseconds < 0) {
            throw new PSQLException(
                "Network timeout must be a value greater than or equal to 0.",
                PSQLState.INVALID_PARAMETER_VALUE
            );
        }
        networkTimeout = milliseconds;
    }

    private void setHoldability(int value) throws SQLException {
        if (
            value != ResultSet.CLOSE_CURSORS_AT_COMMIT &&
            value != ResultSet.HOLD_CURSORS_OVER_COMMIT
        ) {
            throw new PSQLException(
                "Unknown ResultSet holdability setting: " + value + ".",
                PSQLState.INVALID_PARAMETER_VALUE
            );
        }
        holdability = value;
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

    private void alterUserPassword(String user, char[] newPassword, String encryptionType)
        throws SQLException {
        try (var statement = self.createStatement()) {
            var resolvedEncryptionType = encryptionType;
            if (resolvedEncryptionType == null) {
                try (var result = statement.executeQuery("SHOW password_encryption")) {
                    if (!result.next()) {
                        throw new PSQLException(
                            "Expected a row when reading password_encryption but none was found",
                            PSQLState.NO_DATA
                        );
                    }
                    resolvedEncryptionType = result.getString(1);
                    if (resolvedEncryptionType == null) {
                        throw new PSQLException(
                            "SHOW password_encryption returned null value",
                            PSQLState.NO_DATA
                        );
                    }
                }
            }
            var sql = org.postgresql.util.PasswordUtil.genAlterUserPasswordSQL(
                user,
                newPassword,
                resolvedEncryptionType
            );
            statement.execute(sql);
        } finally {
            java.util.Arrays.fill(newPassword, (char) 0);
        }
    }

    private java.sql.Statement createStatement(Object[] args) throws SQLException {
        var options = statementOptions(args, 0);
        return PgStatementHandler.create(this, null, options.type(), options.concurrency(), options.holdability());
    }

    private java.sql.PreparedStatement prepareStatement(Object[] args) throws SQLException {
        var options = statementOptions(args, 1);
        if (args.length >= 2 && args[1] instanceof int[] columns && columns.length > 0) {
            throw new PSQLException(
                "Returning autogenerated keys is not supported.",
                PSQLState.NOT_IMPLEMENTED
            );
        }
        return (java.sql.PreparedStatement) PgStatementHandler.create(
            this,
            (String) args[0],
            options.type(),
            options.concurrency(),
            options.holdability(),
            generatedColumns(args)
        );
    }

    private java.sql.CallableStatement prepareCall(Object[] args) throws SQLException {
        var options = statementOptions(args, 1);
        return PgStatementHandler.createCallable(
            this,
            (String) args[0],
            options.type(),
            options.concurrency(),
            options.holdability()
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
                : this.holdability;
            validateStatementOptions(type, concurrency, holdability);
            return new StatementOptions(type, concurrency, holdability);
        }
        return defaultStatementOptions();
    }

    private StatementOptions defaultStatementOptions() {
        return new StatementOptions(
            ResultSet.TYPE_FORWARD_ONLY,
            ResultSet.CONCUR_READ_ONLY,
            holdability
        );
    }

    private void validateStatementOptions(int type, int concurrency, int holdability) throws SQLException {
        if (
            type != ResultSet.TYPE_FORWARD_ONLY &&
            type != ResultSet.TYPE_SCROLL_INSENSITIVE &&
            type != ResultSet.TYPE_SCROLL_SENSITIVE
        ) {
            throw new PSQLException(
                "Unknown value for ResultSet type",
                PSQLState.INVALID_PARAMETER_VALUE
            );
        }
        if (concurrency != ResultSet.CONCUR_READ_ONLY && concurrency != ResultSet.CONCUR_UPDATABLE) {
            throw new PSQLException(
                "Unknown value for ResultSet concurrency",
                PSQLState.INVALID_PARAMETER_VALUE
            );
        }
        if (
            holdability != ResultSet.CLOSE_CURSORS_AT_COMMIT &&
            holdability != ResultSet.HOLD_CURSORS_OVER_COMMIT
        ) {
            throw new PSQLException(
                "Unknown value for ResultSet holdability",
                PSQLState.INVALID_PARAMETER_VALUE
            );
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

    private SQLFeatureNotSupportedException pgjdbcNotImplemented(String methodName) {
        return new SQLFeatureNotSupportedException(
            "Method org.postgresql.jdbc.PgConnection." + methodName + " is not yet implemented.",
            PSQLState.NOT_IMPLEMENTED.getState()
        );
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
        return Utils.escapeLiteral(null, value, true).toString();
    }

    private org.postgresql.core.CachedQuery createCachedQuery(
        String sql,
        boolean escapeProcessing,
        boolean isParameterized,
        String[] columnNames
    ) throws SQLException {
        var parsedSql = sql;
        if (escapeProcessing) {
            parsedSql = org.postgresql.core.Parser.replaceProcessing(
                parsedSql,
                true,
                getStandardConformingStrings()
            );
        }
        var splitStatements = isParameterized
            || preferQueryMode.compareTo(PreferQueryMode.EXTENDED) >= 0;
        var returningColumnNames = columnNames == null ? new String[] { "*" } : columnNames;
        var nativeQueries = org.postgresql.core.Parser.parseJdbcSql(
            parsedSql,
            getStandardConformingStrings(),
            isParameterized,
            splitStatements,
            false,
            true,
            returningColumnNames
        );
        return new org.postgresql.core.CachedQuery(sql, wrapNativeQueries(nativeQueries), false);
    }

    private org.postgresql.core.CachedQuery createCallableCachedQuery(String sql) throws SQLException {
        var callInfo = org.postgresql.core.Parser.modifyJdbcCall(
            sql,
            getStandardConformingStrings(),
            180000,
            org.postgresql.jdbc.EscapeSyntaxCallMode.SELECT
        );
        var parsedSql = callInfo.getSql();
        var nativeQueries = org.postgresql.core.Parser.parseJdbcSql(
            parsedSql,
            getStandardConformingStrings(),
            true,
            true,
            false,
            true,
            new String[0]
        );
        return new org.postgresql.core.CachedQuery(sql, wrapNativeQueries(nativeQueries), callInfo.isFunction());
    }

    private Object createQueryKey(
        String sql,
        boolean escapeProcessing,
        boolean isParameterized,
        String[] columnNames
    ) {
        if ((columnNames == null || columnNames.length != 0) || !isParameterized || !escapeProcessing) {
            return new QueryKey(sql, escapeProcessing, isParameterized, columnNames);
        }
        return sql;
    }

    private org.postgresql.core.CachedQuery createCachedQueryByKey(Object key) throws SQLException {
        if (key instanceof String sql) {
            return createCachedQuery(sql, true, true, new String[0]);
        }
        if (key instanceof QueryKey queryKey) {
            return createCachedQuery(
                queryKey.sql(),
                queryKey.escapeProcessing(),
                queryKey.isParameterized(),
                queryKey.columnNames()
            );
        }
        throw new SQLFeatureNotSupportedException(
            "Query key is not supported: " + key.getClass().getName(),
            PSQLState.NOT_IMPLEMENTED.getState()
        );
    }

    private org.postgresql.copy.CopyOperation startCopy(String sql) throws SQLException {
        var normalized = sql.toUpperCase(java.util.Locale.ROOT);
        if (normalized.contains(" FROM STDIN")) {
            return new PgCopyManagerAdapter.CopyInOp(this, sql);
        }
        if (normalized.contains(" TO STDOUT")) {
            return new PgCopyManagerAdapter.CopyOutOp(this, sql);
        }
        throw new SQLFeatureNotSupportedException(
            "COPY operation is not supported: " + sql,
            PSQLState.NOT_IMPLEMENTED.getState()
        );
    }

    private org.postgresql.core.Query wrapNativeQueries(List<org.postgresql.core.NativeQuery> nativeQueries) {
        var queries = nativeQueries.stream()
            .map(this::wrapNativeQuery)
            .toArray(org.postgresql.core.Query[]::new);
        if (queries.length == 1) {
            return queries[0];
        }
        return createQueryProxy(nativeQueries, queries);
    }

    @SuppressWarnings("unchecked")
    private List<org.postgresql.core.NativeQuery> castNativeQueries(Object value) {
        return (List<org.postgresql.core.NativeQuery>) value;
    }

    private org.postgresql.core.Query wrapNativeQuery(org.postgresql.core.NativeQuery nativeQuery) {
        return createQueryProxy(List.of(nativeQuery), new org.postgresql.core.Query[0]);
    }

    private org.postgresql.core.Query createQueryProxy(
        List<org.postgresql.core.NativeQuery> nativeQueries,
        org.postgresql.core.Query[] subqueries
    ) {
        return (org.postgresql.core.Query) Proxy.newProxyInstance(
            PgConnectionHandler.class.getClassLoader(),
            new Class<?>[] { org.postgresql.core.Query.class },
            (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "toString" -> nativeQueries.stream()
                            .map(query -> query.toString(null))
                            .collect(Collectors.joining(";"));
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> null;
                    };
                }
                return switch (method.getName()) {
                    case "createParameterList" -> createParameterListProxy(nativeQueries);
                    case "toString" -> nativeQueries.stream()
                        .map(query -> query.toString(null))
                        .collect(Collectors.joining(";"));
                    case "getNativeSql" -> nativeQueries.stream()
                        .map(query -> query.nativeSql)
                        .collect(Collectors.joining(";"));
                    case "getSqlCommand" -> nativeQueries.size() == 1 ? nativeQueries.get(0).command : null;
                    case "close" -> null;
                    case "isStatementDescribed" -> false;
                    case "isEmpty" -> nativeQueries.stream().allMatch(query -> query.nativeSql.isBlank());
                    case "getBatchSize" -> nativeQueries.size() == 1 ? 1 : 0;
                    case "getResultSetColumnNameIndexMap" -> null;
                    case "getSubqueries" -> subqueries.length == 0 ? null : subqueries.clone();
                    default -> JdbcCompat.defaultReturn(method.getReturnType());
                };
            }
        );
    }

    private org.postgresql.core.ParameterList createParameterListProxy(
        List<org.postgresql.core.NativeQuery> nativeQueries
    ) {
        var parameterCount = nativeQueries.stream()
            .mapToInt(query -> query.bindPositions.length)
            .sum();
        return createParameterListProxy(parameterCount);
    }

    private org.postgresql.core.ParameterList createParameterListProxy(int parameterCount) {
        return createParameterListProxy(new Object[parameterCount], new int[parameterCount], new boolean[parameterCount]);
    }

    private org.postgresql.core.ParameterList createParameterListProxy(
        Object[] initialValues,
        int[] initialTypeOids,
        boolean[] initialOutParameters
    ) {
        var values = initialValues.clone();
        var typeOids = initialTypeOids.clone();
        var outParameters = initialOutParameters.clone();
        var parameterCount = values.length;
        return (org.postgresql.core.ParameterList) Proxy.newProxyInstance(
            PgConnectionHandler.class.getClassLoader(),
            new Class<?>[] { org.postgresql.core.ParameterList.class },
            (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "toString" -> "PgParameterList";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> null;
                    };
                }
                return switch (method.getName()) {
                    case "registerOutParameter" -> {
                        registerOutParameter(outParameters, (Integer) args[0]);
                        yield null;
                    }
                    case "getParameterCount" -> parameterCount;
                    case "getInParameterCount" -> inParameterCount(outParameters);
                    case "getOutParameterCount" -> outParameterCount(outParameters);
                    case "getTypeOIDs" -> typeOids.clone();
                    case "setIntParameter" -> {
                        markInParameter(outParameters, (Integer) args[0]);
                        setParameterValue(values, typeOids, (Integer) args[0], args[1], parameterTypeOid(args));
                        yield null;
                    }
                    case "setLiteralParameter", "setStringParameter", "setBinaryParameter", "setNull" -> {
                        markInParameter(outParameters, (Integer) args[0]);
                        setParameterValue(values, typeOids, (Integer) args[0], args[1], parameterTypeOid(args));
                        yield null;
                    }
                    case "setBytea" -> {
                        markInParameter(outParameters, (Integer) args[0]);
                        setByteaParameter(values, typeOids, args);
                        yield null;
                    }
                    case "setText" -> {
                        markInParameter(outParameters, (Integer) args[0]);
                        setParameterValue(values, typeOids, (Integer) args[0], readAllBytes((InputStream) args[1]), 0);
                        yield null;
                    }
                    case "copy" -> copyParameterList(values, typeOids, outParameters);
                    case "clear" -> {
                        java.util.Arrays.fill(values, null);
                        java.util.Arrays.fill(typeOids, 0);
                        java.util.Arrays.fill(outParameters, false);
                        yield null;
                    }
                    case "toString" -> parameterToString(values, (Integer) args[0]);
                    case "appendAll" -> {
                        appendParameterList(values, typeOids, outParameters, (org.postgresql.core.ParameterList) args[0]);
                        yield null;
                    }
                    case "getValues" -> values.clone();
                    default -> JdbcCompat.defaultReturn(method.getReturnType());
                };
            }
        );
    }

    private org.postgresql.core.ParameterList copyParameterList(
        Object[] values,
        int[] typeOids,
        boolean[] outParameters
    ) {
        return createParameterListProxy(values, typeOids, outParameters);
    }

    private void registerOutParameter(boolean[] outParameters, int index) throws SQLException {
        validateParameterListIndex(outParameters.length, index);
        outParameters[index - 1] = true;
    }

    private void markInParameter(boolean[] outParameters, int index) throws SQLException {
        validateParameterListIndex(outParameters.length, index);
        outParameters[index - 1] = false;
    }

    private int inParameterCount(boolean[] outParameters) {
        var count = 0;
        for (var outParameter : outParameters) {
            if (!outParameter) {
                count++;
            }
        }
        return count;
    }

    private int outParameterCount(boolean[] outParameters) {
        var count = 0;
        for (var outParameter : outParameters) {
            if (outParameter) {
                count++;
            }
        }
        return count == 0 ? 1 : count;
    }

    private void setParameterValue(Object[] values, int[] typeOids, int index, Object value, int typeOid)
        throws SQLException {
        validateParameterListIndex(values, index);
        values[index - 1] = value;
        typeOids[index - 1] = typeOid;
    }

    private void setByteaParameter(Object[] values, int[] typeOids, Object[] args) throws SQLException {
        var index = (Integer) args[0];
        if (args[1] instanceof byte[] bytes) {
            var offset = args.length > 2 && args[2] instanceof Integer value ? value : 0;
            var length = args.length > 3 && args[3] instanceof Integer value ? value : bytes.length - offset;
            setParameterValue(
                values,
                typeOids,
                index,
                java.util.Arrays.copyOfRange(bytes, offset, offset + length),
                0
            );
            return;
        }
        if (args[1] instanceof InputStream inputStream) {
            setParameterValue(values, typeOids, index, readAllBytes(inputStream), 0);
            return;
        }
        setParameterValue(values, typeOids, index, args[1], 0);
    }

    private int parameterTypeOid(Object[] args) {
        return args.length > 2 && args[2] instanceof Integer typeOid ? typeOid : 0;
    }

    private byte[] readAllBytes(InputStream inputStream) throws SQLException {
        try {
            return inputStream.readAllBytes();
        } catch (IOException exception) {
            throw new SQLException("Failed to read parameter stream", exception);
        }
    }

    private String parameterToString(Object[] values, int index) throws SQLException {
        validateParameterListIndex(values, index);
        var value = values[index - 1];
        if (value == null) {
            return "NULL";
        }
        if (value instanceof byte[] bytes) {
            return "\\x" + java.util.HexFormat.of().formatHex(bytes);
        }
        return value.toString();
    }

    private void appendParameterList(
        Object[] values,
        int[] typeOids,
        boolean[] outParameters,
        org.postgresql.core.ParameterList source
    ) throws SQLException {
        var sourceValues = source.getValues();
        var sourceTypes = source.getTypeOIDs();
        var inCount = source.getInParameterCount();
        if (inCount > values.length) {
            throw new PSQLException(
                "Added parameters index out of range: " + inCount + ", number of columns: " + values.length + ".",
                PSQLState.INVALID_PARAMETER_VALUE
            );
        }
        for (var i = 0; i < inCount; i++) {
            values[i] = sourceValues[i];
            typeOids[i] = i < sourceTypes.length ? sourceTypes[i] : 0;
            outParameters[i] = false;
        }
    }

    private void validateParameterListIndex(Object[] values, int index) throws SQLException {
        validateParameterListIndex(values.length, index);
    }

    private void validateParameterListIndex(int length, int index) throws SQLException {
        if (index < 1 || index > length) {
            throw new PSQLException(
                "The column index is out of range: " + index + ", number of columns: " + length + ".",
                PSQLState.INVALID_PARAMETER_VALUE
            );
        }
    }

    private boolean getStandardConformingStrings() {
        return true;
    }

    private org.postgresql.core.TypeInfo createTypeInfo() {
        return new org.postgresql.jdbc.TypeInfoCache(
            (org.postgresql.core.BaseConnection) self,
            UNKNOWN_LENGTH
        );
    }

    private org.postgresql.core.QueryExecutor createCoreQueryExecutor() {
        return (org.postgresql.core.QueryExecutor) Proxy.newProxyInstance(
            PgConnectionHandler.class.getClassLoader(),
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
                    case "createSimpleQuery" -> wrapNativeQuery(
                        new org.postgresql.core.NativeQuery(
                            (String) args[0],
                            org.postgresql.core.SqlCommand.createStatementTypeInfo(
                                org.postgresql.core.SqlCommandType.BLANK
                            )
                        )
                    );
                    case "createQuery" -> createCachedQuery(
                        (String) args[0],
                        (Boolean) args[1],
                        (Boolean) args[2],
                        (String[]) args[3]
                    );
                    case "createQueryKey" -> createQueryKey(
                        (String) args[0],
                        (Boolean) args[1],
                        (Boolean) args[2],
                        (String[]) args[3]
                    );
                    case "createQueryByKey", "borrowQueryByKey" -> createCachedQueryByKey(args[0]);
                    case "borrowQuery" -> createCachedQuery((String) args[0], true, true, new String[0]);
                    case "borrowCallableQuery" -> createCallableCachedQuery((String) args[0]);
                    case "borrowReturningQuery" -> createCachedQuery(
                        (String) args[0],
                        true,
                        true,
                        (String[]) args[1]
                    );
                    case "createFastpathParameters" -> createParameterListProxy((Integer) args[0]);
                    case "startCopy" -> startCopy((String) args[0]);
                    case "wrap" -> wrapNativeQueries(castNativeQueries(args[0]));
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

    private org.postgresql.core.ReplicationProtocol createReplicationProtocol() {
        return (org.postgresql.core.ReplicationProtocol) Proxy.newProxyInstance(
            PgConnectionHandler.class.getClassLoader(),
            new Class<?>[] { org.postgresql.core.ReplicationProtocol.class },
            (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "toString" -> "PgReplicationProtocol";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> null;
                    };
                }
                throw new SQLFeatureNotSupportedException(
                    "Replication is not supported by PGlite",
                    PSQLState.NOT_IMPLEMENTED.getState()
                );
            }
        );
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
            case "refcursor" -> 1790;
            case "point" -> 600;
            case "box" -> 603;
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
            case "refcursor[]", "_refcursor" -> 2201;
            case "point[]", "_point" -> 1017;
            case "box[]", "_box" -> 1020;
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
            case 1790 -> 2201;
            case 600 -> 1017;
            case 603 -> 1020;
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
            case 2201 -> 1790;
            case 1017 -> 600;
            case 1020 -> 603;
            case 2951 -> 2950;
            case 199 -> 114;
            case 3807 -> 3802;
            default -> 0;
        };
    }

}
