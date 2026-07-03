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
import java.sql.JDBCType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLType;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class PgStatement implements InvocationHandler {
    private record StreamParameter(Object value) {}

    private final PgConnection connection;
    private final String preparedSql;
    private final String preparedProtocolSql;
    private final int parameterCount;
    private final int resultSetType;
    private final int resultSetConcurrency;
    private final int resultSetHoldability;
    private final String[] preparedGeneratedColumns;
    private final TreeMap<Integer, Object> parameters = new TreeMap<>();
    private final TreeMap<Integer, Integer> parameterTypeOverrides = new TreeMap<>();
    private final List<String> sqlBatch = new ArrayList<>();
    private final List<Object[]> preparedBatch = new ArrayList<>();
    private Statement self;
    private boolean closed;
    private int updateCount = -1;
    private ResultSet currentResultSet;
    private ResultSet generatedKeys;
    private List<interface_.Results<List<Object>>> currentResults = List.of();
    private int currentResultIndex = -1;
    private SQLWarning warnings;
    private int fetchSize;
    private int fetchDirection = ResultSet.FETCH_FORWARD;
    private int maxFieldSize;
    private int maxRows;
    private int queryTimeout;
    private int prepareThreshold;
    private boolean adaptiveFetch;
    private boolean closeOnCompletion;
    private boolean escapeProcessing = true;

    private PgStatement(
        PgConnection connection,
        String preparedSql,
        int resultSetType,
        int resultSetConcurrency,
        int resultSetHoldability,
        String[] preparedGeneratedColumns
    ) throws SQLException {
        this.connection = connection;
        this.preparedSql = preparedSql != null ? JdbcCompat.replaceJdbcEscapes(preparedSql, true) : null;
        this.preparedProtocolSql = preparedSql != null
            ? JdbcCompat.rewriteJdbcParameters(this.preparedSql)
            : null;
        this.parameterCount = preparedSql != null ? JdbcCompat.countJdbcParameters(this.preparedSql) : 0;
        this.resultSetType = resultSetType;
        this.resultSetConcurrency = resultSetConcurrency;
        this.resultSetHoldability = resultSetHoldability;
        this.preparedGeneratedColumns = preparedGeneratedColumns;
        this.prepareThreshold = connection.getPrepareThresholdInternal();
        this.fetchSize = connection.getDefaultFetchSizeInternal();
        this.queryTimeout = connection.getQueryTimeoutInternal();
    }

    static Statement create(PgConnection connection, String preparedSql) throws SQLException {
        return create(
            connection,
            preparedSql,
            ResultSet.TYPE_FORWARD_ONLY,
            ResultSet.CONCUR_READ_ONLY,
            ResultSet.CLOSE_CURSORS_AT_COMMIT,
            null
        );
    }

    static Statement create(
        PgConnection connection,
        String preparedSql,
        int resultSetType,
        int resultSetConcurrency,
        int resultSetHoldability
    ) throws SQLException {
        return create(
            connection,
            preparedSql,
            resultSetType,
            resultSetConcurrency,
            resultSetHoldability,
            null
        );
    }

    static Statement create(
        PgConnection connection,
        String preparedSql,
        int resultSetType,
        int resultSetConcurrency,
        int resultSetHoldability,
        String[] preparedGeneratedColumns
    ) throws SQLException {
        var interfaces = preparedSql == null
            ? new Class<?>[] {
                org.postgresql.core.BaseStatement.class,
            }
            : new Class<?>[] {
                PreparedStatement.class,
                org.postgresql.core.BaseStatement.class,
            };
        var handler = new PgStatement(
            connection,
            preparedSql,
            resultSetType,
            resultSetConcurrency,
            resultSetHoldability,
            preparedGeneratedColumns
        );
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
                case "toString" -> preparedSql == null ? "PgStatement" : preparedStatementToString();
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
            return setParameter(method, idx, args);
        }

        return switch (name) {
            case "clearParameters" -> {
                parameters.clear();
                parameterTypeOverrides.clear();
                yield null;
            }
            case "addBatch" -> {
                if (preparedSql != null && (args == null || args.length == 0)) {
                    preparedBatch.add(buildDisplayParams());
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
            case "executeUpdate" -> executeUpdate(resolveSql(name, args), args);
            case "execute" -> execute(resolveSql(name, args), args);
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
                fetchSize = nonNegativeInt((Integer) args[0], "fetch size");
                yield null;
            }
            case "getFetchSize" -> fetchSize;
            case "setMaxFieldSize" -> {
                maxFieldSize = nonNegativeInt((Integer) args[0], "max field size");
                yield null;
            }
            case "getMaxFieldSize" -> maxFieldSize;
            case "setMaxRows" -> {
                maxRows = nonNegativeInt((Integer) args[0], "max rows");
                yield null;
            }
            case "getMaxRows" -> maxRows;
            case "setQueryTimeout" -> {
                queryTimeout = nonNegativeInt((Integer) args[0], "query timeout");
                yield null;
            }
            case "getQueryTimeout" -> queryTimeout;
            case "cancel" -> null;
            case "setEscapeProcessing" -> {
                escapeProcessing = (Boolean) args[0];
                yield null;
            }
            case "setCursorName", "setPoolable" -> null;
            case "closeOnCompletion" -> {
                closeOnCompletion = true;
                yield null;
            }
            case "setFetchDirection" -> {
                fetchDirection = fetchDirection((Integer) args[0]);
                yield null;
            }
            case "getFetchDirection" -> fetchDirection;
            case "getResultSetConcurrency" -> resultSetConcurrency;
            case "getResultSetType" -> resultSetType;
            case "getResultSetHoldability" -> resultSetHoldability;
            case "isPoolable" -> false;
            case "isCloseOnCompletion" -> closeOnCompletion;
            case "getLargeUpdateCount" -> (long) updateCount;
            case "setLargeMaxRows" -> {
                maxRows = nonNegativeLongAsInt((Long) args[0], "large max rows");
                yield null;
            }
            case "getLargeMaxRows" -> (long) maxRows;
            case "executeLargeBatch" -> {
                var batch = executeBatch();
                var out = new long[batch.length];
                for (var i = 0; i < batch.length; i++) {
                    out[i] = batch[i];
                }
                yield out;
            }
            case "executeLargeUpdate" -> (long) executeUpdate(resolveSql(name, args), args);
            case "getGeneratedKeys" -> generatedKeys != null
                ? generatedKeys
                : PgResultSet.create(self, List.of(), List.of());
            case "getMetaData" -> {
                if (preparedSql == null) {
                    yield null;
                }
                var described = connection.describe(typedPreparedProtocolSql());
                yield PgResultSetMetaData.create(JdbcCompat.toResultFieldColumns(described.resultFields()));
            }
            case "getParameterMetaData" -> {
                if (preparedSql == null) {
                    throw JdbcCompat.unsupported(name);
                }
                var described = connection.describe(typedPreparedProtocolSql());
                ParameterMetaData metadata = PgParameterMetaData.create(parameterTypes(described.queryParams()));
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
                    yield execute(sql, args);
                }
                yield execute(resolveSql(name, null), null);
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

    private Object setParameter(Method method, Integer index, Object[] args) throws SQLException {
        validateParameterIndex(index);
        var methodName = method.getName();
        var value = switch (methodName) {
            case "setNull" -> null;
            case "setArray" -> {
                if (args[1] == null) {
                    yield null;
                }
                yield Arrays.asList((Object[]) ((java.sql.Array) args[1]).getArray());
            }
            case "setBinaryStream" -> readBinaryStream(method, args);
            case "setAsciiStream" -> {
                var bytes = readBinaryStream(method, args);
                yield bytes == null ? null : new StreamParameter(new String(bytes, StandardCharsets.US_ASCII));
            }
            case "setCharacterStream", "setNCharacterStream" -> {
                var text = readCharacterStream(method, args);
                yield text == null ? null : new StreamParameter(text);
            }
            case "setBlob" -> blobParameter(method, args);
            case "setClob", "setNClob" -> clobParameter(method, args);
            case "setSQLXML" -> args[1] == null ? null : ((java.sql.SQLXML) args[1]).getString();
            case "setURL" -> args[1] == null ? null : args[1].toString();
            case "setObject" -> objectParameter(args);
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
        parameters.put(index, "setBinaryStream".equals(methodName) && value != null ? new StreamParameter(value) : value);
        updateParameterTypeOverride(methodName, index, args);
        return null;
    }

    private int[] parameterTypes(List<interface_.QueryParamField> params) {
        var types = new int[params != null ? params.size() : 0];
        for (var i = 0; i < types.length; i++) {
            types[i] = params.get(i).dataTypeID();
            var override = parameterTypeOverrides.get(i + 1);
            if (override != null) {
                types[i] = override;
            }
        }
        return types;
    }

    private void updateParameterTypeOverride(String methodName, Integer index, Object[] args) throws SQLException {
        var oid = switch (methodName) {
            case "setNull" -> nullParameterOid(args);
            case "setFloat" -> 700;
            case "setDouble" -> 701;
            case "setDate" -> 1082;
            case "setTime" -> 1083;
            case "setTimestamp" -> 1114;
            case "setObject" -> objectParameterOid(args);
            default -> null;
        };
        if (oid != null) {
            parameterTypeOverrides.put(index, oid);
        }
    }

    private Integer nullParameterOid(Object[] args) throws SQLException {
        if (args.length >= 3 && args[2] != null) {
            var oid = connection.pgTypeToOid(String.valueOf(args[2]));
            return oid == 0 ? null : oid;
        }
        return args.length >= 2 && args[1] != null ? jdbcTypeToOid(targetSqlType(args[1])) : null;
    }

    private Integer objectParameterOid(Object[] args) throws SQLException {
        if (args.length < 3 || args[2] == null) {
            if (args.length >= 2 && args[1] instanceof Float) {
                return 700;
            }
            if (args.length >= 2 && args[1] instanceof Double) {
                return 701;
            }
            return null;
        }
        var targetType = targetSqlType(args[2]);
        if (targetType == Types.OTHER && args[1] instanceof java.util.UUID) {
            return 2950;
        }
        return jdbcTypeToOid(targetType);
    }

    private Integer jdbcTypeToOid(int type) {
        return switch (type) {
            case Types.BOOLEAN, Types.BIT -> 16;
            case Types.TINYINT, Types.SMALLINT -> 21;
            case Types.INTEGER -> 23;
            case Types.BIGINT -> 20;
            case Types.REAL, Types.FLOAT -> 700;
            case Types.DOUBLE -> 701;
            case Types.NUMERIC, Types.DECIMAL -> 1700;
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> 17;
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR,
                Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR, Types.CLOB, Types.NCLOB -> 25;
            case Types.DATE -> 1082;
            case Types.TIME, Types.TIME_WITH_TIMEZONE -> 1083;
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> 1114;
            default -> null;
        };
    }

    private Object objectParameter(Object[] args) throws SQLException {
        var value = args[1];
        if (args.length < 3 || value == null) {
            return value;
        }
        var targetType = targetSqlType(args[2]);
        if (targetType == null) {
            return value;
        }
        var scaleOrLength = args.length >= 4 && args[3] instanceof Number number
            ? number.intValue()
            : null;
        return convertObjectParameter(value, targetType, scaleOrLength);
    }

    private Integer targetSqlType(Object value) throws SQLException {
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof SQLType sqlType) {
            var vendorType = sqlType.getVendorTypeNumber();
            if (vendorType != null) {
                return vendorType;
            }
            if (sqlType instanceof JDBCType jdbcType) {
                return jdbcType.getVendorTypeNumber();
            }
        }
        throw new SQLException("Unsupported SQLType: " + value);
    }

    private Object convertObjectParameter(Object value, int targetType, Integer scaleOrLength) throws SQLException {
        value = unwrapPgObject(value);
        if (value == null) {
            return null;
        }
        return switch (targetType) {
            case Types.NUMERIC, Types.DECIMAL -> scaleOrLength == null
                ? JdbcCompat.toBigDecimal(value)
                : JdbcCompat.toBigDecimal(value, scaleOrLength, java.math.RoundingMode.HALF_UP);
            case Types.TINYINT -> JdbcCompat.toByte(value);
            case Types.SMALLINT -> JdbcCompat.toShort(value);
            case Types.INTEGER -> JdbcCompat.toInt(value);
            case Types.BIGINT -> JdbcCompat.toLong(value);
            case Types.REAL, Types.FLOAT -> JdbcCompat.toNumber(value).floatValue();
            case Types.DOUBLE -> JdbcCompat.toNumber(value).doubleValue();
            case Types.BIT, Types.BOOLEAN -> JdbcCompat.toBoolean(value);
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY -> JdbcCompat.toBytes(value);
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR,
                Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR -> JdbcCompat.stringify(value);
            case Types.DATE -> value instanceof java.sql.Date date ? date.toLocalDate().toString() : value;
            case Types.TIME, Types.TIME_WITH_TIMEZONE -> value instanceof java.sql.Time time
                ? time.toLocalTime().toString()
                : value;
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> value instanceof java.sql.Timestamp timestamp
                ? timestamp.toLocalDateTime().toString()
                : timestampParameter(value);
            case Types.ARRAY -> arrayParameter(value);
            case Types.SQLXML -> value instanceof java.sql.SQLXML xml ? xml.getString() : value;
            case Types.BLOB -> blobParameter(new Object[] { null, value });
            case Types.CLOB, Types.NCLOB -> clobParameter(new Object[] { null, value });
            default -> value;
        };
    }

    private Object unwrapPgObject(Object value) {
        if (value instanceof org.postgresql.util.PGobject object) {
            if ("bytea".equalsIgnoreCase(object.getType())) {
                return pgObjectByteaValue(object.getValue());
            }
            return object.getValue();
        }
        return value;
    }

    private byte[] pgObjectByteaValue(String value) {
        if (value == null) {
            return null;
        }
        if (value.startsWith("\\x")) {
            var hex = value.substring(2);
            var out = new byte[hex.length() / 2];
            for (var i = 0; i < out.length; i++) {
                out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            }
            return out;
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private Object timestampParameter(Object value) {
        return value instanceof CharSequence text ? text.toString().replace(' ', 'T') : value;
    }

    private Object arrayParameter(Object value) throws SQLException {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Array array) {
            return Arrays.asList((Object[]) array.getArray());
        }
        return value;
    }

    private byte[] readBinaryStream(Method method, Object[] args) throws SQLException {
        if (args[1] == null) {
            return null;
        }
        try {
            var input = (InputStream) args[1];
            if (hasDeclaredStreamLength(method, args)) {
                var length = (Number) args[2];
                var expected = Math.toIntExact(length.longValue());
                var bytes = input.readNBytes(expected);
                if (bytes.length != expected) {
                    throw new SQLException(
                        "Premature end of stream: expected " + expected + " bytes, got " + bytes.length
                    );
                }
                return bytes;
            }
            return input.readAllBytes();
        } catch (IOException | ArithmeticException exception) {
            throw new SQLException("Failed to read parameter stream", exception);
        }
    }

    private Object blobParameter(Method method, Object[] args) throws SQLException {
        if (args[1] == null) {
            return null;
        }
        if (args[1] instanceof java.sql.Blob blob) {
            return blob.getBytes(1, Math.toIntExact(blob.length()));
        }
        if (args[1] instanceof InputStream) {
            return readBinaryStream(method, args);
        }
        return args[1];
    }

    private Object blobParameter(Object[] args) throws SQLException {
        return blobParameter(null, args);
    }

    private Object clobParameter(Method method, Object[] args) throws SQLException {
        if (args[1] == null) {
            return null;
        }
        if (args[1] instanceof java.sql.Clob clob) {
            return clob.getSubString(1, Math.toIntExact(clob.length()));
        }
        if (args[1] instanceof Reader) {
            return readCharacterStream(method, args);
        }
        return args[1];
    }

    private Object clobParameter(Object[] args) throws SQLException {
        return clobParameter(null, args);
    }

    private String readCharacterStream(Method method, Object[] args) throws SQLException {
        if (args[1] == null) {
            return null;
        }
        try {
            var reader = (Reader) args[1];
            var out = new StringBuilder();
            var enforceLength = hasDeclaredStreamLength(method, args);
            var remaining = enforceLength ? ((Number) args[2]).longValue() : Long.MAX_VALUE;
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
            if (enforceLength && remaining > 0) {
                throw new SQLException(
                    "Premature end of reader: expected " + (out.length() + remaining)
                        + " characters, got " + out.length()
                );
            }
            return out.toString();
        } catch (IOException exception) {
            throw new SQLException("Failed to read parameter reader", exception);
        }
    }

    private boolean hasDeclaredStreamLength(Method method, Object[] args) {
        return method != null
            && method.getParameterCount() >= 3
            && args.length >= 3
            && args[2] instanceof Number length
            && length.longValue() >= 0
            && length.longValue() != Integer.MAX_VALUE
            && length.longValue() != Long.MAX_VALUE;
    }

    private String resolveSql(String methodName, Object[] args) throws SQLException {
        if (preparedSql != null && (args == null || args.length == 0)) {
            return typedPreparedProtocolSql();
        }
        if (preparedSql != null) {
            throw new SQLException(methodName + " does not accept SQL text on a PreparedStatement");
        }
        if (args != null && args.length > 0 && args[0] instanceof String sql) {
            return JdbcCompat.replaceJdbcEscapes(sql, escapeProcessing);
        }
        throw new SQLException(methodName + " requires SQL text");
    }

    private String typedPreparedProtocolSql() {
        if (parameterTypeOverrides.isEmpty()) {
            return preparedProtocolSql;
        }
        var matcher = java.util.regex.Pattern.compile("\\$([0-9]+)(?![0-9])").matcher(preparedProtocolSql);
        var out = new StringBuffer();
        while (matcher.find()) {
            var index = Integer.parseInt(matcher.group(1));
            var oid = parameterTypeOverrides.get(index);
            var typeName = oid != null ? JdbcCompat.oidToPgType(oid) : null;
            if (typeName == null || typeName.startsWith("oid:")) {
                continue;
            }
            matcher.appendReplacement(
                out,
                java.util.regex.Matcher.quoteReplacement("($" + index + "::" + typeName + ")")
            );
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private String preparedStatementToString() {
        if (preparedBatch.isEmpty()) {
            return renderPreparedSql(parameters);
        }
        var out = new StringBuilder();
        for (var i = 0; i < preparedBatch.size(); i++) {
            if (i > 0) {
                out.append(";\n");
            }
            out.append(renderPreparedSql(preparedBatchParameters(preparedBatch.get(i))));
        }
        return out.toString();
    }

    private Map<Integer, Object> preparedBatchParameters(Object[] values) {
        var out = new TreeMap<Integer, Object>();
        for (var i = 0; i < values.length; i++) {
            out.put(i + 1, values[i]);
        }
        return out;
    }

    private String renderPreparedSql(Map<Integer, Object> values) {
        var out = new StringBuilder(preparedSql.length() + 32);
        var parameterIndex = 0;
        var inSingleQuote = false;
        var inDoubleQuote = false;
        var inLineComment = false;
        var blockCommentDepth = 0;
        String dollarQuoteTag = null;

        for (var i = 0; i < preparedSql.length(); i++) {
            var ch = preparedSql.charAt(i);
            var next = i + 1 < preparedSql.length() ? preparedSql.charAt(i + 1) : '\0';

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
                if (preparedSql.startsWith(dollarQuoteTag, i)) {
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
                var closing = preparedSql.indexOf('$', i + 1);
                if (closing > i && isDollarQuoteTag(preparedSql, i, closing)) {
                    dollarQuoteTag = preparedSql.substring(i, closing + 1);
                    out.append(dollarQuoteTag);
                    i = closing;
                    continue;
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
                parameterIndex++;
                out.append(renderParameter(values, parameterIndex));
                continue;
            }
            out.append(ch);
        }
        return out.toString();
    }

    private boolean isDollarQuoteTag(String sql, int start, int closing) {
        for (var i = start + 1; i < closing; i++) {
            var ch = sql.charAt(i);
            if (!Character.isLetterOrDigit(ch) && ch != '_') {
                return false;
            }
        }
        return true;
    }

    private boolean hasMultipleTopLevelStatements(String sql) {
        var foundStatementTerminator = false;
        var inSingleQuote = false;
        var inDoubleQuote = false;
        var inLineComment = false;
        var blockCommentDepth = 0;
        String dollarQuoteTag = null;

        for (var i = 0; i < sql.length(); i++) {
            var ch = sql.charAt(i);
            var next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (ch == '\n' || ch == '\r') {
                    inLineComment = false;
                }
                continue;
            }
            if (blockCommentDepth > 0) {
                if (ch == '/' && next == '*') {
                    blockCommentDepth++;
                    i++;
                    continue;
                }
                if (ch == '*' && next == '/') {
                    blockCommentDepth--;
                    i++;
                }
                continue;
            }
            if (dollarQuoteTag != null) {
                if (sql.startsWith(dollarQuoteTag, i)) {
                    i += dollarQuoteTag.length() - 1;
                    dollarQuoteTag = null;
                }
                continue;
            }
            if (inSingleQuote) {
                if (ch == '\'' && next == '\'') {
                    i++;
                    continue;
                }
                if (ch == '\'') {
                    inSingleQuote = false;
                }
                continue;
            }
            if (inDoubleQuote) {
                if (ch == '"' && next == '"') {
                    i++;
                    continue;
                }
                if (ch == '"') {
                    inDoubleQuote = false;
                }
                continue;
            }

            if (foundStatementTerminator && !Character.isWhitespace(ch)) {
                return true;
            }
            if (ch == '\'') {
                inSingleQuote = true;
                continue;
            }
            if (ch == '"') {
                inDoubleQuote = true;
                continue;
            }
            if (ch == '-' && next == '-') {
                inLineComment = true;
                i++;
                continue;
            }
            if (ch == '/' && next == '*') {
                blockCommentDepth = 1;
                i++;
                continue;
            }
            if (ch == '$') {
                var closing = sql.indexOf('$', i + 1);
                if (closing > i && isDollarQuoteTag(sql, i, closing)) {
                    dollarQuoteTag = sql.substring(i, closing + 1);
                    i = closing;
                    continue;
                }
            }
            if (ch == ';') {
                foundStatementTerminator = true;
            }
        }
        return false;
    }

    private String renderParameter(Map<Integer, Object> values, int index) {
        if (!values.containsKey(index)) {
            return "?";
        }
        var value = values.get(index);
        if (value == null) {
            return "NULL";
        }
        if (value instanceof StreamParameter) {
            return "?";
        }
        if (value instanceof byte[] bytes) {
            return "'\\x" + hex(bytes) + "'::bytea";
        }
        if (value instanceof org.postgresql.util.PGobject object) {
            return renderPgObjectParameter(object);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return "('" + String.valueOf(value).replace("'", "''") + "')";
    }

    private String renderPgObjectParameter(org.postgresql.util.PGobject object) {
        var value = object.getValue();
        if ("bytea".equalsIgnoreCase(object.getType()) && value != null) {
            if (value.startsWith("\\x")) {
                return "'" + value.replace("'", "''") + "'::bytea";
            }
            return "('" + value.replace("'", "''") + "'::bytea)";
        }
        return value == null
            ? "NULL"
            : "('" + value.replace("'", "''") + "')";
    }

    private String hex(byte[] bytes) {
        var digits = "0123456789abcdef";
        var out = new StringBuilder(bytes.length * 2);
        for (var b : bytes) {
            out.append(digits.charAt((b >> 4) & 0x0f));
            out.append(digits.charAt(b & 0x0f));
        }
        return out.toString();
    }

    private String[] generatedColumns(Object[] args, int offset) {
        if (args == null || args.length <= offset) {
            return null;
        }
        if (args[offset] instanceof Integer flag) {
            return flag == Statement.RETURN_GENERATED_KEYS ? new String[0] : null;
        }
        if (args[offset] instanceof String[] columns) {
            return columns.clone();
        }
        if (args[offset] instanceof int[] columns && columns.length > 0) {
            var out = new String[columns.length];
            for (var i = 0; i < columns.length; i++) {
                out[i] = String.valueOf(columns[i]);
            }
            return out;
        }
        return null;
    }

    private String withGeneratedKeys(String sql, String[] columns) {
        if (columns == null || !sql.stripLeading().regionMatches(true, 0, "insert", 0, 6)) {
            return sql;
        }
        if (sql.toLowerCase(java.util.Locale.ROOT).contains(" returning ")) {
            return sql;
        }
        var trimmed = sql.stripTrailing();
        var suffix = " RETURNING *";
        if (columns.length > 0) {
            suffix = " RETURNING " + String.join(", ", Arrays.stream(columns)
                .map(column -> '"' + column.replace("\"", "\"\"") + '"')
                .toList());
        }
        if (trimmed.endsWith(";")) {
            return trimmed.substring(0, trimmed.length() - 1) + suffix;
        }
        return trimmed + suffix;
    }

    private void setGeneratedKeys(interface_.Results<Map<String, Object>> result) throws SQLException {
        if (result.fields().isEmpty()) {
            generatedKeys = PgResultSet.create(self, List.of(), List.of());
            return;
        }
        generatedKeys = PgResultSet.createMappedRows(
            connection,
            self,
            JdbcCompat.toColumns(result.fields()),
            trimRows(result.rows()),
            resultSetType,
            resultSetConcurrency,
            resultSetHoldability,
            maxFieldSize,
            fetchSize,
            fetchDirection
        );
    }

    private Object[] buildParams() {
        return executionParameters(buildDisplayParams());
    }

    private Object[] buildDisplayParams() {
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

    private Object[] executionParameters(Object[] values) {
        var out = values.clone();
        for (var i = 0; i < out.length; i++) {
            out[i] = executionParameter(out[i]);
        }
        return out;
    }

    private Object executionParameter(Object value) {
        if (value instanceof StreamParameter streamParameter) {
            return streamParameter.value();
        }
        return unwrapPgObject(value);
    }

    private void validateParameterIndex(int index) throws SQLException {
        if (index < 1 || index > parameterCount) {
            throw new SQLException(
                "Parameter index out of range: " + index + ", parameter count: " + parameterCount
            );
        }
    }

    private int nonNegativeInt(int value, String name) throws SQLException {
        if (value < 0) {
            throw new SQLException("Invalid " + name + ": " + value);
        }
        return value;
    }

    private int nonNegativeLongAsInt(long value, String name) throws SQLException {
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new SQLException("Invalid " + name + ": " + value);
        }
        return (int) value;
    }

    private int fetchDirection(int value) throws SQLException {
        if (
            value != ResultSet.FETCH_FORWARD &&
            value != ResultSet.FETCH_REVERSE &&
            value != ResultSet.FETCH_UNKNOWN
        ) {
            throw new SQLException("Invalid fetch direction: " + value);
        }
        return value;
    }

    private ResultSet executeQuery(String sql) throws SQLException {
        closeCurrentResultSet();
        clearExecutionWarnings();
        generatedKeys = null;
        var params = preparedSql != null ? buildParams() : null;
        var result = connection.queryArray(sql, params, this::addWarning);
        if (result.fields().isEmpty()) {
            currentResults = List.of();
            currentResultIndex = -1;
            currentResultSet = null;
            updateCount = -1;
            throw new SQLException("No results were returned by the query");
        }
        currentResults = List.of();
        currentResultIndex = -1;
        currentResultSet = createStatementArrayResultSet(result);
        updateCount = -1;
        return currentResultSet;
    }

    private int executeUpdate(String sql, Object[] args) throws SQLException {
        closeCurrentResultSet();
        clearExecutionWarnings();
        var generatedColumns = preparedSql != null ? preparedGeneratedColumns : generatedColumns(args, 1);
        if (preparedSql != null) {
            var result = connection.query(withGeneratedKeys(sql, generatedColumns), buildParams(), this::addWarning);
            if (!result.fields().isEmpty() && generatedColumns == null) {
                clearResultState();
                throw new SQLException("A result was returned when none was expected");
            }
            if (generatedColumns != null) {
                setGeneratedKeys(result);
            } else {
                generatedKeys = null;
            }
            currentResults = List.of();
            currentResultIndex = -1;
            currentResultSet = null;
            updateCount = JdbcCompat.safeAffectedRows(result);
            return updateCount;
        }
        var results = connection.exec(withGeneratedKeys(sql, generatedColumns), this::addWarning);
        for (var result : results) {
            if (!result.fields().isEmpty() && generatedColumns == null) {
                clearResultState();
                throw new SQLException("A result was returned when none was expected");
            }
        }
        if (generatedColumns != null && !results.isEmpty()) {
            setGeneratedKeys(results.getLast());
        } else {
            generatedKeys = null;
        }
        currentResults = List.of();
        currentResultIndex = -1;
        currentResultSet = null;
        updateCount = results.isEmpty() ? -1 : JdbcCompat.safeAffectedRows(results.getLast());
        return updateCount;
    }

    private boolean execute(String sql, Object[] args) throws SQLException {
        closeCurrentResultSet();
        clearExecutionWarnings();
        var generatedColumns = preparedSql != null ? preparedGeneratedColumns : generatedColumns(args, 1);
        if (preparedSql != null) {
            if (generatedColumns == null) {
                if (hasMultipleTopLevelStatements(preparedSql)) {
                    var results = connection.execArray(renderPreparedSql(parameters), this::addWarning);
                    generatedKeys = null;
                    currentResults = results;
                    currentResultIndex = -1;
                    if (results.isEmpty()) {
                        currentResultSet = null;
                        updateCount = -1;
                        return false;
                    }
                    return advanceResult();
                }
                var result = connection.queryArray(sql, buildParams(), this::addWarning);
                currentResults = List.of();
                currentResultIndex = -1;
                if (!result.fields().isEmpty()) {
                    currentResultSet = createStatementArrayResultSet(result);
                    updateCount = -1;
                    return true;
                }
                currentResultSet = null;
                updateCount = JdbcCompat.safeAffectedRows(result);
                return false;
            }
            var result = connection.query(withGeneratedKeys(sql, generatedColumns), buildParams(), this::addWarning);
            currentResults = List.of();
            currentResultIndex = -1;
            setGeneratedKeys(result);
            currentResultSet = null;
            updateCount = JdbcCompat.safeAffectedRows(result);
            return false;
        }

        if (generatedColumns != null) {
            var results = connection.exec(withGeneratedKeys(sql, generatedColumns), this::addWarning);
            if (!results.isEmpty()) {
                setGeneratedKeys(results.getLast());
                updateCount = JdbcCompat.safeAffectedRows(results.getLast());
            } else {
                generatedKeys = PgResultSet.create(self, List.of(), List.of());
                updateCount = -1;
            }
            currentResults = List.of();
            currentResultIndex = -1;
            currentResultSet = null;
            return false;
        }
        var results = connection.execArray(sql, this::addWarning);
        generatedKeys = null;
        currentResults = results;
        currentResultIndex = -1;
        if (results.isEmpty()) {
            currentResultSet = null;
            updateCount = -1;
            return false;
        }
        return advanceResult();
    }

    private int[] executeBatch() throws SQLException {
        closeCurrentResultSet();
        clearExecutionWarnings();
        if (preparedSql != null) {
            var out = new int[preparedBatch.size()];
            var sql = typedPreparedProtocolSql();
            for (var i = 0; i < preparedBatch.size(); i++) {
                var result = connection.query(sql, executionParameters(preparedBatch.get(i)), this::addWarning);
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
            var results = connection.exec(sqlBatch.get(i), this::addWarning);
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
            columns.add(new Column(
                field.getColumnLabel(),
                field.getOID(),
                field.getMod(),
                field.getTableOid(),
                field.getPositionInTable()
            ));
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
        return PgResultSet.create(connection, self, columns, rows);
    }

    private boolean getMoreResults(Object[] args) throws SQLException {
        var behavior = args != null && args.length > 0
            ? (Integer) args[0]
            : Statement.CLOSE_CURRENT_RESULT;
        if (
            behavior != Statement.CLOSE_CURRENT_RESULT &&
            behavior != Statement.KEEP_CURRENT_RESULT &&
            behavior != Statement.CLOSE_ALL_RESULTS
        ) {
            throw new SQLException("Invalid getMoreResults flag: " + behavior);
        }
        if (behavior == Statement.CLOSE_CURRENT_RESULT || behavior == Statement.CLOSE_ALL_RESULTS) {
            closeCurrentResultSet();
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
            currentResultSet = createStatementArrayResultSet(result);
            updateCount = -1;
            return true;
        }
        currentResultSet = null;
        updateCount = JdbcCompat.safeAffectedRows(result);
        return false;
    }

    private ResultSet createStatementArrayResultSet(interface_.Results<List<Object>> result) {
        return PgResultSet.createArrayRows(
            connection,
            self,
            JdbcCompat.toColumns(result.fields()),
            trimRows(result.rows()),
            resultSetType,
            resultSetConcurrency,
            resultSetHoldability,
            maxFieldSize,
            fetchSize,
            fetchDirection
        );
    }

    private <T> List<T> trimRows(List<T> rows) {
        if (maxRows <= 0 || rows.size() <= maxRows) {
            return rows;
        }
        return rows.subList(0, maxRows);
    }

    private void clearExecutionWarnings() {
        warnings = null;
    }

    private void addWarning(io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.NoticeMessage notice) {
        var warning = new SQLWarning(notice.message(), notice.code());
        if (warnings == null) {
            warnings = warning;
            return;
        }
        warnings.setNextWarning(warning);
    }

    private void ensureOpen() throws SQLException {
        if (closed) {
            throw new SQLException("Statement is closed");
        }
    }

    private void closeCurrentResultSet() throws SQLException {
        if (currentResultSet != null) {
            currentResultSet.close();
            currentResultSet = null;
        }
    }

    private void clearResultState() {
        currentResults = List.of();
        currentResultIndex = -1;
        currentResultSet = null;
        updateCount = -1;
        generatedKeys = null;
    }

    private void closeStatement() throws SQLException {
        closeCurrentResultSet();
        closed = true;
        currentResults = List.of();
        currentResultIndex = -1;
        parameters.clear();
        sqlBatch.clear();
        preparedBatch.clear();
    }
}
