package io.github.hidekatsu_izuno.pglite_jdbc.jdbc;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
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
    private List<interface_.Results<Map<String, Object>>> currentResults = List.of();
    private int currentResultIndex = -1;
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
                org.postgresql.core.BaseStatement.class,
            }
            : new Class<?>[] {
                PreparedStatement.class,
                org.postgresql.core.BaseStatement.class,
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
            case "getMoreResults" -> getMoreResults(args);
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
                yield PgResultSetMetaData.create(JdbcCompat.toResultFieldColumns(described.resultFields()));
            }
            case "getParameterMetaData" -> {
                if (preparedSql == null) {
                    throw JdbcCompat.unsupported(name);
                }
                var described = connection.describe(preparedProtocolSql);
                ParameterMetaData metadata = PgParameterMetaData.create(described.queryParams());
                yield metadata;
            }
            case "createDriverResultSet" -> createDriverResultSet(
                (org.postgresql.core.Field[]) args[0],
                (List<org.postgresql.core.Tuple>) args[1]
            );
            case "createResultSet" -> createDriverResultSet(
                (org.postgresql.core.Field[]) args[1],
                (List<org.postgresql.core.Tuple>) args[2]
            );
            case "executeWithFlags" -> {
                if (args[0] instanceof String sql) {
                    yield execute(sql);
                }
                yield execute(resolveSql(name, null));
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

    private Object setParameter(String methodName, Integer index, Object[] args) throws SQLException {
        var value = switch (methodName) {
            case "setNull" -> null;
            case "setArray" -> {
                if (args[1] == null) {
                    yield null;
                }
                yield Arrays.asList((Object[]) ((java.sql.Array) args[1]).getArray());
            }
            case "setBinaryStream" -> readBinaryStream(args);
            case "setAsciiStream" -> {
                var bytes = readBinaryStream(args);
                yield bytes == null ? null : new String(bytes, StandardCharsets.US_ASCII);
            }
            case "setCharacterStream", "setNCharacterStream" -> readCharacterStream(args);
            case "setBlob" -> blobParameter(args);
            case "setClob", "setNClob" -> clobParameter(args);
            case "setSQLXML" -> args[1] == null ? null : ((java.sql.SQLXML) args[1]).getString();
            case "setURL" -> args[1] == null ? null : args[1].toString();
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

    private byte[] readBinaryStream(Object[] args) throws SQLException {
        if (args[1] == null) {
            return null;
        }
        try {
            var input = (InputStream) args[1];
            if (args.length >= 3 && args[2] instanceof Number length && length.longValue() >= 0) {
                return input.readNBytes(Math.toIntExact(length.longValue()));
            }
            return input.readAllBytes();
        } catch (IOException | ArithmeticException exception) {
            throw new SQLException("Failed to read parameter stream", exception);
        }
    }

    private Object blobParameter(Object[] args) throws SQLException {
        if (args[1] == null) {
            return null;
        }
        if (args[1] instanceof java.sql.Blob blob) {
            return blob.getBytes(1, Math.toIntExact(blob.length()));
        }
        if (args[1] instanceof InputStream) {
            return readBinaryStream(args);
        }
        return args[1];
    }

    private Object clobParameter(Object[] args) throws SQLException {
        if (args[1] == null) {
            return null;
        }
        if (args[1] instanceof java.sql.Clob clob) {
            return clob.getSubString(1, Math.toIntExact(clob.length()));
        }
        if (args[1] instanceof Reader) {
            return readCharacterStream(args);
        }
        return args[1];
    }

    private String readCharacterStream(Object[] args) throws SQLException {
        if (args[1] == null) {
            return null;
        }
        try {
            var reader = (Reader) args[1];
            var out = new StringBuilder();
            var remaining = args.length >= 3 && args[2] instanceof Number length
                ? length.longValue()
                : Long.MAX_VALUE;
            var buffer = new char[2048];
            while (remaining > 0) {
                var request = (int) Math.min(buffer.length, remaining);
                var read = reader.read(buffer, 0, request);
                if (read < 0) {
                    break;
                }
                out.append(buffer, 0, read);
                remaining -= read;
            }
            return out.toString();
        } catch (IOException exception) {
            throw new SQLException("Failed to read parameter reader", exception);
        }
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
        currentResults = List.of();
        currentResultIndex = -1;
        currentResultSet = PgResultSet.create(self, JdbcCompat.toColumns(result.fields()), trimRows(result.rows()));
        updateCount = -1;
        return currentResultSet;
    }

    private int executeUpdate(String sql) throws SQLException {
        if (preparedSql != null) {
            var result = connection.query(sql, buildParams());
            currentResults = List.of();
            currentResultIndex = -1;
            currentResultSet = null;
            updateCount = JdbcCompat.safeAffectedRows(result);
            return updateCount;
        }
        var results = connection.exec(sql);
        currentResults = List.of();
        currentResultIndex = -1;
        currentResultSet = null;
        updateCount = results.isEmpty() ? 0 : JdbcCompat.safeAffectedRows(results.getLast());
        return updateCount;
    }

    private boolean execute(String sql) throws SQLException {
        if (preparedSql != null) {
            var result = connection.query(sql, buildParams());
            currentResults = List.of();
            currentResultIndex = -1;
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
        currentResults = results;
        currentResultIndex = -1;
        if (results.isEmpty()) {
            currentResultSet = null;
            updateCount = 0;
            return false;
        }
        return advanceResult();
    }

    private int[] executeBatch() throws SQLException {
        if (preparedSql != null) {
            var out = new int[preparedBatch.size()];
            for (var i = 0; i < preparedBatch.size(); i++) {
                var result = connection.query(preparedProtocolSql, preparedBatch.get(i));
                out[i] = JdbcCompat.safeAffectedRows(result);
            }
            preparedBatch.clear();
            currentResults = List.of();
            currentResultIndex = -1;
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
        currentResults = List.of();
        currentResultIndex = -1;
        updateCount = -1;
        currentResultSet = null;
        return out;
    }

    private ResultSet createDriverResultSet(
        org.postgresql.core.Field[] fields,
        List<org.postgresql.core.Tuple> tuples
    ) {
        var columns = new ArrayList<Column>(fields.length);
        for (var field : fields) {
            columns.add(new Column(field.getColumnLabel(), field.getOID()));
        }
        var rows = new ArrayList<Map<String, Object>>(tuples.size());
        for (var tuple : tuples) {
            var row = new LinkedHashMap<String, Object>();
            for (var i = 0; i < fields.length; i++) {
                var bytes = tuple.get(i);
                row.put(
                    fields[i].getColumnLabel(),
                    bytes == null ? null : new String(bytes, StandardCharsets.UTF_8)
                );
            }
            rows.add(row);
        }
        return PgResultSet.create(self, columns, rows);
    }

    private boolean getMoreResults(Object[] args) throws SQLException {
        if (args != null && args.length > 0) {
            var behavior = (Integer) args[0];
            if (behavior == Statement.CLOSE_CURRENT_RESULT && currentResultSet != null) {
                currentResultSet.close();
            }
        }
        return advanceResult();
    }

    private boolean advanceResult() {
        currentResultIndex++;
        if (currentResultIndex < 0 || currentResultIndex >= currentResults.size()) {
            currentResultSet = null;
            updateCount = -1;
            return false;
        }
        var result = currentResults.get(currentResultIndex);
        if (!result.fields().isEmpty()) {
            currentResultSet = PgResultSet.create(self, JdbcCompat.toColumns(result.fields()), trimRows(result.rows()));
            updateCount = -1;
            return true;
        }
        currentResultSet = null;
        updateCount = JdbcCompat.safeAffectedRows(result);
        return false;
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
        currentResults = List.of();
        currentResultIndex = -1;
        parameters.clear();
        sqlBatch.clear();
        preparedBatch.clear();
    }
}
