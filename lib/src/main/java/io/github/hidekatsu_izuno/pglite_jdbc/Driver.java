package io.github.hidekatsu_izuno.pglite_jdbc;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.pglite;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

public class Driver implements java.sql.Driver {
    private static final String URL_PREFIX = "jdbc:pglite:";

    static {
        try {
            DriverManager.registerDriver(new Driver());
        } catch (SQLException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return null;
        }

        var config = parseUrl(url, info);
        var options = new pglite.PGliteOptions();
        options.dataDir = config.dataDir();
        options.username = config.user();
        options.database = config.database();
        options.debug = config.debug();
        options.relaxedDurability = config.relaxedDurability();

        var db = new pglite(options);
        try {
            db.waitReady().join();
        } catch (Throwable error) {
            try {
                db.close().join();
            } catch (Throwable ignored) {
                // Keep the original startup failure.
            }
            throw toSqlException(error);
        }
        return ConnectionHandler.create(db, url, config.user(), config.database());
    }

    @Override
    public boolean acceptsURL(String url) {
        return url != null && url.startsWith(URL_PREFIX);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
        var user = new DriverPropertyInfo("user", info != null ? info.getProperty("user") : null);
        user.description = "Database user";
        var database = new DriverPropertyInfo(
            "database",
            info != null ? info.getProperty("database") : null
        );
        database.description = "Database name";
        var dataDir = new DriverPropertyInfo(
            "dataDir",
            info != null ? info.getProperty("dataDir") : null
        );
        dataDir.description = "pglite data directory (e.g. memory://, file:///tmp/db)";
        var debug = new DriverPropertyInfo("debug", info != null ? info.getProperty("debug") : null);
        debug.description = "Debug level";
        return new DriverPropertyInfo[] { user, database, dataDir, debug };
    }

    @Override
    public int getMajorVersion() {
        return 0;
    }

    @Override
    public int getMinorVersion() {
        return 1;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() {
        return Logger.getLogger("io.github.hidekatsu_izuno.pglite_jdbc");
    }

    private record ParsedConfig(
        String dataDir,
        String user,
        String database,
        Integer debug,
        Boolean relaxedDurability
    ) {}

    private static ParsedConfig parseUrl(String url, Properties info) {
        var props = new HashMap<String, String>();
        if (info != null) {
            for (var name : info.stringPropertyNames()) {
                props.put(name, info.getProperty(name));
            }
        }

        var body = url.substring(URL_PREFIX.length());
        var queryIndex = body.indexOf('?');
        var rawPath = queryIndex >= 0 ? body.substring(0, queryIndex) : body;
        if (queryIndex >= 0 && queryIndex + 1 < body.length()) {
            parseQueryParams(body.substring(queryIndex + 1), props);
        }

        var normalizedPath = rawPath == null ? "" : rawPath.trim();
        if (normalizedPath.isEmpty()) {
            normalizedPath = props.getOrDefault("dataDir", "");
        }
        var dataDir = normalizedPath.isEmpty() ? null : normalizedPath;

        var user = coalesce(props.get("user"), "postgres");
        var database = coalesce(props.get("database"), "template1");
        var debug = parseInteger(props.get("debug"));
        var relaxedDurability = parseBoolean(props.get("relaxedDurability"));
        return new ParsedConfig(dataDir, user, database, debug, relaxedDurability);
    }

    private static void parseQueryParams(String query, Map<String, String> out) {
        for (var pair : query.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            var eq = pair.indexOf('=');
            if (eq < 0) {
                out.put(urlDecode(pair), "");
                continue;
            }
            var key = urlDecode(pair.substring(0, eq));
            var value = urlDecode(pair.substring(eq + 1));
            out.put(key, value);
        }
    }

    private static String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String coalesce(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private static Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Boolean parseBoolean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Boolean.valueOf(value.trim());
    }

    private static SQLException toSqlException(Throwable error) {
        var cause = unwrap(error);
        if (cause instanceof SQLException sqlException) {
            return sqlException;
        }
        var message = cause.getMessage() != null ? cause.getMessage() : cause.toString();
        return new SQLException(message, cause);
    }

    private static Throwable unwrap(Throwable error) {
        var current = error;
        while (
            current instanceof RuntimeException runtime &&
            runtime.getCause() != null &&
            runtime != runtime.getCause()
        ) {
            current = runtime.getCause();
        }
        return current;
    }

    private record Column(String label, int oid) {}

    private static final class ConnectionHandler implements InvocationHandler {
        private final pglite db;
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
        private SQLWarning warnings;

        private ConnectionHandler(pglite db, String url, String user, String database) {
            this.db = db;
            this.url = url;
            this.user = user;
            this.database = database;
        }

        private static Connection create(pglite db, String url, String user, String database) {
            var handler = new ConnectionHandler(db, url, user, database);
            var proxy = (Connection) Proxy.newProxyInstance(
                Driver.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                handler
            );
            handler.self = proxy;
            return proxy;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            var name = method.getName();
            if (method.getDeclaringClass() == Object.class) {
                return switch (name) {
                    case "toString" -> "PgliteConnection[" + url + "]";
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
                case "createStatement" -> StatementHandler.create(this, null);
                case "prepareStatement" -> StatementHandler.create(this, (String) args[0]);
                case "prepareCall" -> throw unsupported(name);
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
                    if (args == null || args.length == 0) {
                        rollbackTransaction();
                    } else {
                        rollbackTransaction();
                    }
                    yield null;
                }
                case "getMetaData" -> MetaDataHandler.create(this);
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
                    "createNClob", "createSQLXML", "createArrayOf", "createStruct" -> throw unsupported(name);
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
                default -> defaultReturn(method.getReturnType());
            };
        }

        private void ensureOpen() throws SQLException {
            if (closed) {
                throw new SQLException("Connection is closed");
            }
        }

        private void setAutoCommit(boolean value) throws SQLException {
            ensureOpen();
            if (this.autoCommit == value) {
                return;
            }
            if (value && txOpen) {
                execControl("COMMIT");
                txOpen = false;
            }
            this.autoCommit = value;
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
                db.close().join();
            } catch (Throwable error) {
                throw toSqlException(error);
            } finally {
                closed = true;
                txOpen = false;
            }
        }

        private void execControl(String sql) throws SQLException {
            try {
                db.exec(sql, null).join();
            } catch (Throwable error) {
                throw toSqlException(error);
            }
        }

        private interface_.Results<Map<String, Object>> query(String sql, Object[] params)
            throws SQLException {
            ensureTransactionIfNeeded();
            try {
                @SuppressWarnings("unchecked")
                var result = (interface_.Results<Map<String, Object>>) (interface_.Results<?>) db.query(
                    sql,
                    params,
                    null
                ).join();
                return result;
            } catch (Throwable error) {
                throw toSqlException(error);
            }
        }

        private List<interface_.Results<Map<String, Object>>> exec(String sql) throws SQLException {
            ensureTransactionIfNeeded();
            try {
                return db.exec(sql, null).join();
            } catch (Throwable error) {
                throw toSqlException(error);
            }
        }
    }

    private static final class MetaDataHandler implements InvocationHandler {
        private final ConnectionHandler connection;

        private MetaDataHandler(ConnectionHandler connection) {
            this.connection = connection;
        }

        private static DatabaseMetaData create(ConnectionHandler connection) {
            return (DatabaseMetaData) Proxy.newProxyInstance(
                Driver.class.getClassLoader(),
                new Class<?>[] { DatabaseMetaData.class },
                new MetaDataHandler(connection)
            );
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            var name = method.getName();
            if (method.getDeclaringClass() == Object.class) {
                return switch (name) {
                    case "toString" -> "PgliteDatabaseMetaData";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                };
            }

            return switch (name) {
                case "getConnection" -> connection.self;
                case "getURL" -> connection.url;
                case "getUserName" -> connection.user;
                case "getDatabaseProductName" -> "PGlite";
                case "getDatabaseProductVersion" -> "unknown";
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
                case "isReadOnly" -> connection.readOnly;
                case "unwrap" -> {
                    var iface = (Class<?>) args[0];
                    if (iface.isInstance(proxy)) {
                        yield proxy;
                    }
                    throw new IllegalArgumentException("Not a wrapper for " + iface.getName());
                }
                case "isWrapperFor" -> ((Class<?>) args[0]).isInstance(proxy);
                default -> defaultReturn(method.getReturnType());
            };
        }
    }

    private static final class StatementHandler implements InvocationHandler {
        private final ConnectionHandler connection;
        private final String preparedSql;
        private final String preparedProtocolSql;
        private final TreeMap<Integer, Object> parameters = new TreeMap<>();
        private final List<String> sqlBatch = new ArrayList<>();
        private final List<Object[]> preparedBatch = new ArrayList<>();
        private Statement self;
        private boolean closed;
        private int updateCount = -1;
        private ResultSet currentResultSet;
        private SQLWarning warnings;
        private int fetchSize;
        private int maxRows;
        private int queryTimeout;

        private StatementHandler(ConnectionHandler connection, String preparedSql) {
            this.connection = connection;
            this.preparedSql = preparedSql;
            this.preparedProtocolSql = preparedSql != null
                ? rewriteJdbcParameters(preparedSql)
                : null;
        }

        private static Statement create(ConnectionHandler connection, String preparedSql) {
            var iface = preparedSql == null ? Statement.class : PreparedStatement.class;
            var handler = new StatementHandler(connection, preparedSql);
            var proxy = (Statement) Proxy.newProxyInstance(
                Driver.class.getClassLoader(),
                new Class<?>[] { iface },
                handler
            );
            handler.self = proxy;
            return proxy;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            var name = method.getName();
            if (method.getDeclaringClass() == Object.class) {
                return switch (name) {
                    case "toString" -> (preparedSql == null ? "PgliteStatement" : "PglitePreparedStatement");
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> method.invoke(this, args);
                };
            }

            if ("close".equals(name)) {
                closeStatement();
                return null;
            }
            if ("isClosed".equals(name)) {
                return closed;
            }
            ensureOpen();

            if (name.startsWith("set") && preparedSql != null && args != null && args.length >= 2 && args[0] instanceof Integer idx) {
                return setParameter(name, idx, args);
            }

            return switch (name) {
                case "clearParameters" -> {
                    parameters.clear();
                    yield null;
                }
                case "addBatch" -> {
                    if (preparedSql != null && (args == null || args.length == 0)) {
                        preparedBatch.add(buildParams());
                    } else if (args != null && args.length > 0) {
                        sqlBatch.add((String) args[0]);
                    } else {
                        throw new SQLException("addBatch requires SQL for Statement");
                    }
                    yield null;
                }
                case "clearBatch" -> {
                    sqlBatch.clear();
                    preparedBatch.clear();
                    yield null;
                }
                case "executeBatch" -> executeBatch();
                case "executeQuery" -> executeQuery(resolveSql(name, args));
                case "executeUpdate" -> executeUpdate(resolveSql(name, args));
                case "execute" -> execute(resolveSql(name, args));
                case "getResultSet" -> currentResultSet;
                case "getUpdateCount" -> updateCount;
                case "getMoreResults" -> false;
                case "getConnection" -> connection.self;
                case "getWarnings" -> warnings;
                case "clearWarnings" -> {
                    warnings = null;
                    yield null;
                }
                case "setFetchSize" -> {
                    fetchSize = (Integer) args[0];
                    yield null;
                }
                case "getFetchSize" -> fetchSize;
                case "setMaxRows" -> {
                    maxRows = (Integer) args[0];
                    yield null;
                }
                case "getMaxRows" -> maxRows;
                case "setQueryTimeout" -> {
                    queryTimeout = (Integer) args[0];
                    yield null;
                }
                case "getQueryTimeout" -> queryTimeout;
                case "cancel" -> null;
                case "setEscapeProcessing", "setCursorName", "setFetchDirection",
                    "setPoolable", "closeOnCompletion" -> null;
                case "getFetchDirection" -> ResultSet.FETCH_FORWARD;
                case "getResultSetConcurrency" -> ResultSet.CONCUR_READ_ONLY;
                case "getResultSetType" -> ResultSet.TYPE_FORWARD_ONLY;
                case "getResultSetHoldability" -> ResultSet.CLOSE_CURSORS_AT_COMMIT;
                case "isPoolable" -> false;
                case "isCloseOnCompletion" -> false;
                case "getLargeUpdateCount" -> (long) Math.max(updateCount, 0);
                case "setLargeMaxRows" -> null;
                case "getLargeMaxRows" -> (long) maxRows;
                case "executeLargeBatch" -> {
                    var batch = executeBatch();
                    var out = new long[batch.length];
                    for (var i = 0; i < batch.length; i++) {
                        out[i] = batch[i];
                    }
                    yield out;
                }
                case "executeLargeUpdate" -> (long) executeUpdate(resolveSql(name, args));
                case "getGeneratedKeys" -> ResultSetHandler.create(self, List.of(), List.of());
                case "getMetaData" -> {
                    if (preparedSql == null) {
                        yield null;
                    }
                    var described = connection.db.describeQuery(preparedProtocolSql).join();
                    var columns = toColumns(described.resultFields());
                    yield ResultSetMetaDataHandler.create(columns);
                }
                case "getParameterMetaData" -> {
                    if (preparedSql == null) {
                        throw unsupported(name);
                    }
                    throw unsupported(name);
                }
                case "unwrap" -> {
                    var iface = (Class<?>) args[0];
                    if (iface.isInstance(proxy)) {
                        yield proxy;
                    }
                    throw new SQLException("Not a wrapper for " + iface.getName());
                }
                case "isWrapperFor" -> ((Class<?>) args[0]).isInstance(proxy);
                default -> {
                    if (method.getReturnType() == void.class) {
                        yield null;
                    }
                    yield defaultReturn(method.getReturnType());
                }
            };
        }

        private Object setParameter(String methodName, Integer index, Object[] args) {
            var value = switch (methodName) {
                case "setNull" -> null;
                case "setDate" -> args[1] instanceof java.sql.Date date
                    ? date.toLocalDate().toString()
                    : args[1];
                case "setTime" -> args[1] instanceof java.sql.Time time
                    ? time.toLocalTime().toString()
                    : args[1];
                case "setTimestamp" -> args[1] instanceof java.sql.Timestamp ts
                    ? ts.toLocalDateTime().toString()
                    : args[1];
                default -> args[1];
            };
            parameters.put(index, value);
            return null;
        }

        private String resolveSql(String methodName, Object[] args) throws SQLException {
            if (preparedSql != null && (args == null || args.length == 0)) {
                return preparedProtocolSql;
            }
            if (args != null && args.length > 0 && args[0] instanceof String sql) {
                return sql;
            }
            throw new SQLException(methodName + " requires SQL text");
        }

        private Object[] buildParams() {
            if (parameters.isEmpty()) {
                return new Object[0];
            }
            var max = parameters.lastKey();
            var values = new Object[max];
            for (var entry : parameters.entrySet()) {
                values[entry.getKey() - 1] = entry.getValue();
            }
            return values;
        }

        private ResultSet executeQuery(String sql) throws SQLException {
            var params = preparedSql != null ? buildParams() : null;
            var result = connection.query(sql, params);
            currentResultSet = ResultSetHandler.create(self, toColumns(result.fields()), trimRows(result.rows()));
            updateCount = -1;
            return currentResultSet;
        }

        private int executeUpdate(String sql) throws SQLException {
            if (preparedSql != null) {
                var result = connection.query(sql, buildParams());
                currentResultSet = null;
                updateCount = safeAffectedRows(result);
                return updateCount;
            }
            var results = connection.exec(sql);
            currentResultSet = null;
            updateCount = results.isEmpty() ? 0 : safeAffectedRows(results.getLast());
            return updateCount;
        }

        private boolean execute(String sql) throws SQLException {
            if (preparedSql != null) {
                var result = connection.query(sql, buildParams());
                if (!result.fields().isEmpty()) {
                    currentResultSet = ResultSetHandler.create(self, toColumns(result.fields()), trimRows(result.rows()));
                    updateCount = -1;
                    return true;
                }
                currentResultSet = null;
                updateCount = safeAffectedRows(result);
                return false;
            }

            var results = connection.exec(sql);
            if (results.isEmpty()) {
                currentResultSet = null;
                updateCount = 0;
                return false;
            }
            var first = results.getFirst();
            if (!first.fields().isEmpty()) {
                currentResultSet = ResultSetHandler.create(self, toColumns(first.fields()), trimRows(first.rows()));
                updateCount = -1;
                return true;
            }
            currentResultSet = null;
            updateCount = safeAffectedRows(first);
            return false;
        }

        private int[] executeBatch() throws SQLException {
            if (preparedSql != null) {
                var out = new int[preparedBatch.size()];
                for (var i = 0; i < preparedBatch.size(); i++) {
                    var result = connection.query(preparedProtocolSql, preparedBatch.get(i));
                    out[i] = safeAffectedRows(result);
                }
                preparedBatch.clear();
                updateCount = -1;
                currentResultSet = null;
                return out;
            }
            var out = new int[sqlBatch.size()];
            for (var i = 0; i < sqlBatch.size(); i++) {
                var results = connection.exec(sqlBatch.get(i));
                out[i] = results.isEmpty() ? 0 : safeAffectedRows(results.getLast());
            }
            sqlBatch.clear();
            updateCount = -1;
            currentResultSet = null;
            return out;
        }

        private List<Map<String, Object>> trimRows(List<Map<String, Object>> rows) {
            if (maxRows <= 0 || rows.size() <= maxRows) {
                return rows;
            }
            return rows.subList(0, maxRows);
        }

        private void ensureOpen() throws SQLException {
            if (closed) {
                throw new SQLException("Statement is closed");
            }
        }

        private void closeStatement() {
            closed = true;
            currentResultSet = null;
            parameters.clear();
            sqlBatch.clear();
            preparedBatch.clear();
        }
    }

    private static final class ResultSetHandler implements InvocationHandler {
        private final Statement statement;
        private final List<Column> columns;
        private final List<Map<String, Object>> rows;
        private final Map<String, Integer> labelIndex;
        private int cursor = -1;
        private boolean closed;
        private boolean wasNull;

        private ResultSetHandler(
            Statement statement,
            List<Column> columns,
            List<Map<String, Object>> rows
        ) {
            this.statement = statement;
            this.columns = columns;
            this.rows = rows;
            this.labelIndex = new HashMap<>();
            for (var i = 0; i < columns.size(); i++) {
                var label = columns.get(i).label();
                labelIndex.putIfAbsent(label, i + 1);
                labelIndex.putIfAbsent(label.toLowerCase(Locale.ROOT), i + 1);
            }
        }

        private static ResultSet create(
            Statement statement,
            List<Column> columns,
            List<Map<String, Object>> rows
        ) {
            return (ResultSet) Proxy.newProxyInstance(
                Driver.class.getClassLoader(),
                new Class<?>[] { ResultSet.class },
                new ResultSetHandler(statement, columns, rows)
            );
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            var name = method.getName();
            if (method.getDeclaringClass() == Object.class) {
                return switch (name) {
                    case "toString" -> "PgliteResultSet";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                };
            }

            return switch (name) {
                case "next" -> {
                    ensureNotClosed();
                    if (cursor + 1 < rows.size()) {
                        cursor++;
                        yield true;
                    }
                    cursor = rows.size();
                    yield false;
                }
                case "close" -> {
                    closed = true;
                    yield null;
                }
                case "isClosed" -> closed;
                case "wasNull" -> wasNull;
                case "getRow" -> cursor >= 0 && cursor < rows.size() ? cursor + 1 : 0;
                case "beforeFirst" -> {
                    ensureNotClosed();
                    cursor = -1;
                    yield null;
                }
                case "getMetaData" -> ResultSetMetaDataHandler.create(columns);
                case "getStatement" -> statement;
                case "findColumn" -> findColumn((String) args[0]);
                case "getObject" -> {
                    var value = args[0] instanceof Integer index
                        ? valueAt(index)
                        : valueAt(findColumn((String) args[0]));
                    if (args.length == 2 && args[1] instanceof Class<?> targetType) {
                        yield coerce(value, targetType);
                    }
                    yield value;
                }
                case "getString" -> stringify(getValue(args[0]));
                case "getBoolean" -> toBoolean(getValue(args[0]));
                case "getByte" -> toNumber(getValue(args[0])).byteValue();
                case "getShort" -> toNumber(getValue(args[0])).shortValue();
                case "getInt" -> toNumber(getValue(args[0])).intValue();
                case "getLong" -> toNumber(getValue(args[0])).longValue();
                case "getFloat" -> toNumber(getValue(args[0])).floatValue();
                case "getDouble" -> toNumber(getValue(args[0])).doubleValue();
                case "getBigDecimal" -> toBigDecimal(getValue(args[0]));
                case "getBytes" -> toBytes(getValue(args[0]));
                case "getDate" -> {
                    var value = getValue(args[0]);
                    if (value == null) {
                        yield null;
                    }
                    yield java.sql.Date.valueOf(LocalDate.parse(String.valueOf(value)));
                }
                case "getTime" -> {
                    var value = getValue(args[0]);
                    if (value == null) {
                        yield null;
                    }
                    yield java.sql.Time.valueOf(LocalTime.parse(String.valueOf(value)));
                }
                case "getTimestamp" -> {
                    var value = getValue(args[0]);
                    if (value == null) {
                        yield null;
                    }
                    var text = String.valueOf(value).replace('T', ' ');
                    yield java.sql.Timestamp.valueOf(LocalDateTime.parse(text.replace(' ', 'T')));
                }
                case "unwrap" -> {
                    var iface = (Class<?>) args[0];
                    if (iface.isInstance(proxy)) {
                        yield proxy;
                    }
                    throw new SQLException("Not a wrapper for " + iface.getName());
                }
                case "isWrapperFor" -> ((Class<?>) args[0]).isInstance(proxy);
                case "getType" -> ResultSet.TYPE_FORWARD_ONLY;
                case "getConcurrency" -> ResultSet.CONCUR_READ_ONLY;
                case "getFetchDirection" -> ResultSet.FETCH_FORWARD;
                case "getHoldability" -> ResultSet.CLOSE_CURSORS_AT_COMMIT;
                default -> {
                    if (method.getReturnType() == void.class) {
                        yield null;
                    }
                    yield defaultReturn(method.getReturnType());
                }
            };
        }

        private void ensureNotClosed() throws SQLException {
            if (closed) {
                throw new SQLException("ResultSet is closed");
            }
        }

        private void ensureOnRow() throws SQLException {
            ensureNotClosed();
            if (cursor < 0 || cursor >= rows.size()) {
                throw new SQLException("ResultSet cursor is not positioned on a row");
            }
        }

        private Object getValue(Object columnArg) throws SQLException {
            var value = columnArg instanceof Integer idx
                ? valueAt(idx)
                : valueAt(findColumn((String) columnArg));
            wasNull = value == null;
            return value;
        }

        private int findColumn(String label) throws SQLException {
            var direct = labelIndex.get(label);
            if (direct != null) {
                return direct;
            }
            var folded = labelIndex.get(label.toLowerCase(Locale.ROOT));
            if (folded != null) {
                return folded;
            }
            throw new SQLException("Unknown column label: " + label);
        }

        private Object valueAt(int index) throws SQLException {
            if (index < 1 || index > columns.size()) {
                throw new SQLException("Column index out of bounds: " + index);
            }
            ensureOnRow();
            var row = rows.get(cursor);
            var label = columns.get(index - 1).label();
            if (row.containsKey(label)) {
                return row.get(label);
            }
            for (var entry : row.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(label)) {
                    return entry.getValue();
                }
            }
            return null;
        }
    }

    private static final class ResultSetMetaDataHandler implements InvocationHandler {
        private final List<Column> columns;

        private ResultSetMetaDataHandler(List<Column> columns) {
            this.columns = columns;
        }

        private static ResultSetMetaData create(List<Column> columns) {
            return (ResultSetMetaData) Proxy.newProxyInstance(
                Driver.class.getClassLoader(),
                new Class<?>[] { ResultSetMetaData.class },
                new ResultSetMetaDataHandler(columns)
            );
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            var name = method.getName();
            if (method.getDeclaringClass() == Object.class) {
                return switch (name) {
                    case "toString" -> "PgliteResultSetMetaData";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                };
            }

            return switch (name) {
                case "getColumnCount" -> columns.size();
                case "getColumnLabel", "getColumnName" -> columns.get(((Integer) args[0]) - 1).label();
                case "getColumnType" -> oidToJdbcType(columns.get(((Integer) args[0]) - 1).oid());
                case "getColumnTypeName" -> "oid:" + columns.get(((Integer) args[0]) - 1).oid();
                case "isNullable" -> ResultSetMetaData.columnNullableUnknown;
                case "isAutoIncrement" -> false;
                case "isCaseSensitive" -> true;
                case "isSearchable" -> true;
                case "isCurrency" -> false;
                case "isSigned" -> true;
                case "getColumnDisplaySize", "getPrecision", "getScale" -> 0;
                case "getSchemaName", "getTableName", "getCatalogName" -> "";
                case "isReadOnly" -> true;
                case "isWritable", "isDefinitelyWritable" -> false;
                case "getColumnClassName" -> Object.class.getName();
                case "unwrap" -> {
                    var iface = (Class<?>) args[0];
                    if (iface.isInstance(proxy)) {
                        yield proxy;
                    }
                    throw new IllegalArgumentException("Not a wrapper for " + iface.getName());
                }
                case "isWrapperFor" -> ((Class<?>) args[0]).isInstance(proxy);
                default -> defaultReturn(method.getReturnType());
            };
        }
    }

    private static List<Column> toColumns(List<interface_.Field> fields) {
        if (fields == null || fields.isEmpty()) {
            return List.of();
        }
        var out = new ArrayList<Column>(fields.size());
        for (var i = 0; i < fields.size(); i++) {
            var field = fields.get(i);
            var label = field.name();
            if (label == null || label.isBlank()) {
                label = "column" + (i + 1);
            }
            out.add(new Column(label, field.dataTypeID()));
        }
        return out;
    }

    private static List<Column> toColumns(interface_.DescribeQueryResult result) {
        if (result == null) {
            return List.of();
        }
        return toColumns(result.resultFields());
    }

    private static Object defaultReturn(Class<?> returnType) {
        if (returnType == void.class) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0f;
        }
        if (returnType == double.class) {
            return 0d;
        }
        if (returnType == char.class) {
            return '\0';
        }
        if (returnType == ResultSet.class) {
            return ResultSetHandler.create(null, List.of(), List.of());
        }
        if (returnType == ResultSetMetaData.class) {
            return ResultSetMetaDataHandler.create(List.of());
        }
        if (returnType == int[].class) {
            return new int[0];
        }
        if (returnType == long[].class) {
            return new long[0];
        }
        return null;
    }

    private static SQLFeatureNotSupportedException unsupported(String method) {
        return new SQLFeatureNotSupportedException(method + " is not supported");
    }

    private static String rewriteJdbcParameters(String sql) {
        if (sql == null || sql.indexOf('?') < 0) {
            return sql;
        }
        var out = new StringBuilder(sql.length() + 16);
        var placeholderIndex = 0;
        var inSingleQuote = false;
        var inDoubleQuote = false;
        var inLineComment = false;
        var blockCommentDepth = 0;
        String dollarQuoteTag = null;

        for (var i = 0; i < sql.length(); i++) {
            var ch = sql.charAt(i);
            var next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';

            if (inLineComment) {
                out.append(ch);
                if (ch == '\n' || ch == '\r') {
                    inLineComment = false;
                }
                continue;
            }

            if (blockCommentDepth > 0) {
                out.append(ch);
                if (ch == '/' && next == '*') {
                    out.append(next);
                    blockCommentDepth++;
                    i++;
                    continue;
                }
                if (ch == '*' && next == '/') {
                    out.append(next);
                    blockCommentDepth--;
                    i++;
                }
                continue;
            }

            if (dollarQuoteTag != null) {
                if (sql.startsWith(dollarQuoteTag, i)) {
                    out.append(dollarQuoteTag);
                    i += dollarQuoteTag.length() - 1;
                    dollarQuoteTag = null;
                    continue;
                }
                out.append(ch);
                continue;
            }

            if (inSingleQuote) {
                out.append(ch);
                if (ch == '\'' && next == '\'') {
                    out.append(next);
                    i++;
                    continue;
                }
                if (ch == '\'') {
                    inSingleQuote = false;
                }
                continue;
            }

            if (inDoubleQuote) {
                out.append(ch);
                if (ch == '"' && next == '"') {
                    out.append(next);
                    i++;
                    continue;
                }
                if (ch == '"') {
                    inDoubleQuote = false;
                }
                continue;
            }

            if (ch == '\'') {
                inSingleQuote = true;
                out.append(ch);
                continue;
            }
            if (ch == '"') {
                inDoubleQuote = true;
                out.append(ch);
                continue;
            }
            if (ch == '-' && next == '-') {
                inLineComment = true;
                out.append(ch).append(next);
                i++;
                continue;
            }
            if (ch == '/' && next == '*') {
                blockCommentDepth = 1;
                out.append(ch).append(next);
                i++;
                continue;
            }
            if (ch == '$') {
                var closing = sql.indexOf('$', i + 1);
                if (closing > i) {
                    var candidate = sql.substring(i, closing + 1);
                    var validTag = true;
                    for (var t = 1; t < candidate.length() - 1; t++) {
                        var tagChar = candidate.charAt(t);
                        if (
                            !Character.isLetterOrDigit(tagChar) &&
                            tagChar != '_'
                        ) {
                            validTag = false;
                            break;
                        }
                    }
                    if (validTag) {
                        out.append(candidate);
                        i = closing;
                        dollarQuoteTag = candidate;
                        continue;
                    }
                }
                out.append(ch);
                continue;
            }

            if (ch == '?') {
                if (next == '?') {
                    out.append('?');
                    i++;
                    continue;
                }
                placeholderIndex++;
                out.append('$').append(placeholderIndex);
                continue;
            }

            out.append(ch);
        }

        return out.toString();
    }

    private static int safeAffectedRows(interface_.Results<Map<String, Object>> result) {
        return result.affectedRows() != null ? result.affectedRows() : 0;
    }

    private static Number toNumber(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number;
        }
        var text = String.valueOf(value);
        try {
            if (text.contains(".") || text.contains("e") || text.contains("E")) {
                return Double.parseDouble(text);
            }
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String stringify(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private static boolean toBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        var text = String.valueOf(value).toLowerCase(Locale.ROOT);
        return "true".equals(text) || "t".equals(text) || "1".equals(text);
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static byte[] toBytes(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return Arrays.copyOf(bytes, bytes.length);
        }
        return String.valueOf(value).getBytes(StandardCharsets.UTF_8);
    }

    private static Object coerce(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        if (targetType == String.class) {
            return String.valueOf(value);
        }
        if (Number.class.isAssignableFrom(targetType) || targetType.isPrimitive()) {
            var number = toNumber(value);
            if (targetType == Integer.class || targetType == int.class) {
                return number.intValue();
            }
            if (targetType == Long.class || targetType == long.class) {
                return number.longValue();
            }
            if (targetType == Short.class || targetType == short.class) {
                return number.shortValue();
            }
            if (targetType == Byte.class || targetType == byte.class) {
                return number.byteValue();
            }
            if (targetType == Float.class || targetType == float.class) {
                return number.floatValue();
            }
            if (targetType == Double.class || targetType == double.class) {
                return number.doubleValue();
            }
            if (targetType == BigDecimal.class) {
                return toBigDecimal(value);
            }
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return toBoolean(value);
        }
        if (targetType == byte[].class) {
            return toBytes(value);
        }
        return value;
    }

    private static int oidToJdbcType(int oid) {
        return switch (oid) {
            case 16 -> Types.BOOLEAN;
            case 20 -> Types.BIGINT;
            case 21 -> Types.SMALLINT;
            case 23 -> Types.INTEGER;
            case 25, 1043 -> Types.VARCHAR;
            case 700 -> Types.REAL;
            case 701 -> Types.DOUBLE;
            case 1082 -> Types.DATE;
            case 1083 -> Types.TIME;
            case 1114, 1184 -> Types.TIMESTAMP;
            case 1700 -> Types.NUMERIC;
            case 17 -> Types.BINARY;
            default -> Types.OTHER;
        };
    }
}
