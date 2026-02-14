package io.github.hidekatsu_izuno.pglite_jdbc.jdbc;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class PgStatement implements InvocationHandler {
    private final PgConnection connection;
    private final String preparedSql;
    private final String preparedProtocolSql;
    private final TreeMap<Integer, Object> parameters = new TreeMap<>();
    private final List<String> sqlBatch = new ArrayList<>();
    private final List<Object[]> preparedBatch = new ArrayList<>();
    private Statement self;
    private boolean closed;
    private int updateCount = -1;
    private ResultSet currentResultSet;
    private java.sql.SQLWarning warnings;
    private int fetchSize;
    private int maxRows;
    private int queryTimeout;
    private int prepareThreshold;
    private boolean adaptiveFetch;

    private PgStatement(PgConnection connection, String preparedSql) {
        this.connection = connection;
        this.preparedSql = preparedSql;
        this.preparedProtocolSql = preparedSql != null
            ? JdbcCompat.rewriteJdbcParameters(preparedSql)
            : null;
        this.prepareThreshold = connection.getPrepareThresholdInternal();
        this.fetchSize = connection.getDefaultFetchSizeInternal();
        this.queryTimeout = connection.getQueryTimeoutInternal();
    }

    static Statement create(PgConnection connection, String preparedSql) {
        var interfaces = preparedSql == null
            ? new Class<?>[] {
                Statement.class,
                org.postgresql.PGStatement.class,
            }
            : new Class<?>[] {
                PreparedStatement.class,
                Statement.class,
                org.postgresql.PGStatement.class,
            };
        var handler = new PgStatement(connection, preparedSql);
        var proxy = (Statement) Proxy.newProxyInstance(
            PgStatement.class.getClassLoader(),
            interfaces,
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
                case "toString" -> preparedSql == null ? "PgStatement" : "PgPreparedStatement";
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

        if (
            name.startsWith("set") &&
            preparedSql != null &&
            args != null &&
            args.length >= 2 &&
            args[0] instanceof Integer idx
        ) {
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
            case "getConnection" -> connection.proxy();
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
            case "setEscapeProcessing", "setCursorName", "setFetchDirection", "setPoolable", "closeOnCompletion" -> null;
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
            case "getGeneratedKeys" -> PgResultSet.create(self, List.of(), List.of());
            case "getMetaData" -> {
                if (preparedSql == null) {
                    yield null;
                }
                var described = connection.describe(preparedProtocolSql);
                yield PgResultSetMetaData.create(JdbcCompat.toColumns(described.resultFields()));
            }
            case "getParameterMetaData" -> {
                if (preparedSql == null) {
                    throw JdbcCompat.unsupported(name);
                }
                var described = connection.describe(preparedProtocolSql);
                ParameterMetaData metadata = PgParameterMetaData.create(described.queryParams());
                yield metadata;
            }
            case "getLastOID" -> 0L;
            case "setPrepareThreshold" -> {
                prepareThreshold = (Integer) args[0];
                yield null;
            }
            case "getPrepareThreshold" -> prepareThreshold;
            case "setUseServerPrepare" -> {
                prepareThreshold = Boolean.TRUE.equals(args[0]) ? 1 : 0;
                yield null;
            }
            case "isUseServerPrepare" -> prepareThreshold != 0;
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
            case "isWrapperFor" -> ((Class<?>) args[0]).isInstance(proxy);
            default -> {
                if (method.getReturnType() == void.class) {
                    yield null;
                }
                yield JdbcCompat.defaultReturn(method.getReturnType());
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
        currentResultSet = PgResultSet.create(self, JdbcCompat.toColumns(result.fields()), trimRows(result.rows()));
        updateCount = -1;
        return currentResultSet;
    }

    private int executeUpdate(String sql) throws SQLException {
        if (preparedSql != null) {
            var result = connection.query(sql, buildParams());
            currentResultSet = null;
            updateCount = JdbcCompat.safeAffectedRows(result);
            return updateCount;
        }
        var results = connection.exec(sql);
        currentResultSet = null;
        updateCount = results.isEmpty() ? 0 : JdbcCompat.safeAffectedRows(results.getLast());
        return updateCount;
    }

    private boolean execute(String sql) throws SQLException {
        if (preparedSql != null) {
            var result = connection.query(sql, buildParams());
            if (!result.fields().isEmpty()) {
                currentResultSet = PgResultSet.create(self, JdbcCompat.toColumns(result.fields()), trimRows(result.rows()));
                updateCount = -1;
                return true;
            }
            currentResultSet = null;
            updateCount = JdbcCompat.safeAffectedRows(result);
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
            currentResultSet = PgResultSet.create(self, JdbcCompat.toColumns(first.fields()), trimRows(first.rows()));
            updateCount = -1;
            return true;
        }
        currentResultSet = null;
        updateCount = JdbcCompat.safeAffectedRows(first);
        return false;
    }

    private int[] executeBatch() throws SQLException {
        if (preparedSql != null) {
            var out = new int[preparedBatch.size()];
            for (var i = 0; i < preparedBatch.size(); i++) {
                var result = connection.query(preparedProtocolSql, preparedBatch.get(i));
                out[i] = JdbcCompat.safeAffectedRows(result);
            }
            preparedBatch.clear();
            updateCount = -1;
            currentResultSet = null;
            return out;
        }

        var out = new int[sqlBatch.size()];
        for (var i = 0; i < sqlBatch.size(); i++) {
            var results = connection.exec(sqlBatch.get(i));
            out[i] = results.isEmpty() ? 0 : JdbcCompat.safeAffectedRows(results.getLast());
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
