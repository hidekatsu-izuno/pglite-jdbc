package io.github.hidekatsu_izuno.pglite_jdbc.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class PgResultSet implements InvocationHandler {
    private final PgConnection connection;
    private final java.sql.Statement statement;
    private final List<Column> columns;
    private final List<Map<String, Object>> rows;
    private final Map<String, Integer> labelIndex;
    private final int type;
    private final int concurrency;
    private final int holdability;
    private final int maxFieldSize;
    private int cursor = -1;
    private boolean closed;
    private boolean wasNull;
    private int fetchSize;
    private int fetchDirection;
    private SQLWarning warnings;

    private PgResultSet(
        PgConnection connection,
        java.sql.Statement statement,
        List<Column> columns,
        List<Map<String, Object>> rows,
        int type,
        int concurrency,
        int holdability,
        int maxFieldSize,
        int fetchSize,
        int fetchDirection
    ) {
        this.connection = connection;
        this.statement = statement;
        this.columns = columns;
        this.rows = rows;
        this.type = type;
        this.concurrency = concurrency;
        this.holdability = holdability;
        this.maxFieldSize = maxFieldSize;
        this.fetchSize = fetchSize;
        this.fetchDirection = fetchDirection;
        this.labelIndex = new HashMap<>();
        for (var i = 0; i < columns.size(); i++) {
            var label = columns.get(i).label();
            labelIndex.putIfAbsent(label, i + 1);
            labelIndex.putIfAbsent(label.toLowerCase(Locale.ROOT), i + 1);
        }
    }

    static ResultSet create(
        java.sql.Statement statement,
        List<Column> columns,
        List<Map<String, Object>> rows
    ) {
        return create(null, statement, columns, rows);
    }

    static ResultSet create(
        PgConnection connection,
        java.sql.Statement statement,
        List<Column> columns,
        List<Map<String, Object>> rows
    ) {
        return create(
            connection,
            statement,
            columns,
            rows,
            ResultSet.TYPE_FORWARD_ONLY,
            ResultSet.CONCUR_READ_ONLY,
            ResultSet.CLOSE_CURSORS_AT_COMMIT,
            0,
            0,
            ResultSet.FETCH_FORWARD
        );
    }

    static ResultSet create(
        PgConnection connection,
        java.sql.Statement statement,
        List<Column> columns,
        List<Map<String, Object>> rows,
        int type,
        int concurrency,
        int holdability
    ) {
        return create(
            connection,
            statement,
            columns,
            rows,
            type,
            concurrency,
            holdability,
            0,
            0,
            ResultSet.FETCH_FORWARD
        );
    }

    static ResultSet create(
        PgConnection connection,
        java.sql.Statement statement,
        List<Column> columns,
        List<Map<String, Object>> rows,
        int type,
        int concurrency,
        int holdability,
        int maxFieldSize,
        int fetchSize,
        int fetchDirection
    ) {
        return (ResultSet) Proxy.newProxyInstance(
            PgResultSet.class.getClassLoader(),
            new Class<?>[] { ResultSet.class },
            new PgResultSet(
                connection,
                statement,
                columns,
                rows,
                type,
                concurrency,
                holdability,
                maxFieldSize,
                fetchSize,
                fetchDirection
            )
        );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        var name = method.getName();
        if (method.getDeclaringClass() == Object.class) {
            return switch (name) {
                case "toString" -> "PgResultSet";
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
                if (closed) {
                    yield null;
                }
                closed = true;
                closeStatementOnCompletion();
                yield null;
            }
            case "isClosed" -> closed;
            case "wasNull" -> {
                ensureNotClosed();
                yield wasNull;
            }
            case "getRow" -> {
                ensureNotClosed();
                yield cursor >= 0 && cursor < rows.size() ? cursor + 1 : 0;
            }
            case "beforeFirst" -> {
                ensureNotClosed();
                ensureScrollable();
                cursor = -1;
                yield null;
            }
            case "afterLast" -> {
                ensureNotClosed();
                ensureScrollable();
                cursor = rows.size();
                yield null;
            }
            case "first" -> moveToRow(rows.isEmpty() ? rows.size() : 0);
            case "last" -> moveToRow(rows.isEmpty() ? rows.size() : rows.size() - 1);
            case "absolute" -> absolute((Integer) args[0]);
            case "relative" -> moveToRow(cursor + (Integer) args[0]);
            case "previous" -> moveToRow(cursor - 1);
            case "isBeforeFirst" -> {
                ensureNotClosed();
                yield cursor < 0 && !rows.isEmpty();
            }
            case "isAfterLast" -> {
                ensureNotClosed();
                yield cursor >= rows.size() && !rows.isEmpty();
            }
            case "isFirst" -> {
                ensureNotClosed();
                yield cursor == 0 && !rows.isEmpty();
            }
            case "isLast" -> {
                ensureNotClosed();
                yield cursor == rows.size() - 1 && !rows.isEmpty();
            }
            case "getMetaData" -> {
                ensureNotClosed();
                yield PgResultSetMetaData.create(columns);
            }
            case "getStatement" -> {
                ensureNotClosed();
                yield statement;
            }
            case "findColumn" -> {
                ensureNotClosed();
                yield findColumn((String) args[0]);
            }
            case "getObject" -> {
                ensureNotClosed();
                var column = columnIndex(args[0]);
                var value = getValue(column);
                if (args.length == 2 && args[1] instanceof Class<?> targetType) {
                    if (org.postgresql.util.PGobject.class.isAssignableFrom(targetType)) {
                        var typeName = JdbcCompat.oidToPgType(columns.get(column - 1).oid());
                        var objectClass = targetType.asSubclass(org.postgresql.util.PGobject.class);
                        if (targetType == org.postgresql.util.PGobject.class && connection != null) {
                            var registeredClass = connection.pgObjectClass(typeName);
                            if (registeredClass != null) {
                                objectClass = registeredClass;
                            }
                        }
                        yield JdbcCompat.toPgObject(typeName, value, objectClass);
                    }
                    yield JdbcCompat.coerce(value, targetType);
                }
                yield objectValue(column, value);
            }
            case "getArray" -> {
                var column = columnIndex(args[0]);
                var value = getValue(column);
                yield value == null
                    ? null
                    : new org.postgresql.jdbc.PgArray(
                        (org.postgresql.core.BaseConnection) statement.getConnection(),
                        columns.get(column - 1).oid(),
                        JdbcCompat.toArrayLiteral(value)
                    );
            }
            case "getBlob" -> {
                var value = getValue(args[0]);
                yield value == null ? null : new PgBlob(baseConnection(), JdbcCompat.toBytes(value));
            }
            case "getClob", "getNClob" -> {
                var value = getValue(args[0]);
                yield value == null ? null : new PgClob(baseConnection(), JdbcCompat.stringify(value));
            }
            case "getSQLXML" -> {
                var value = getValue(args[0]);
                yield value == null
                    ? null
                    : new org.postgresql.jdbc.PgSQLXML(
                        (org.postgresql.core.BaseConnection) statement.getConnection(),
                        JdbcCompat.stringify(value)
                    );
            }
            case "getString", "getNString" -> truncateString(columnIndex(args[0]), getValue(args[0]));
            case "getBoolean" -> JdbcCompat.toBoolean(getValue(args[0]));
            case "getByte" -> JdbcCompat.toByte(getValue(args[0]));
            case "getShort" -> JdbcCompat.toShort(getValue(args[0]));
            case "getInt" -> JdbcCompat.toInt(getValue(args[0]));
            case "getLong" -> JdbcCompat.toLong(getValue(args[0]));
            case "getFloat" -> JdbcCompat.toNumber(getValue(args[0])).floatValue();
            case "getDouble" -> JdbcCompat.toNumber(getValue(args[0])).doubleValue();
            case "getBigDecimal" -> args.length >= 2 && args[1] instanceof Integer scale
                ? JdbcCompat.toBigDecimal(getValue(args[0]), scale)
                : JdbcCompat.toBigDecimal(getValue(args[0]));
            case "getBytes" -> truncateBytes(columnIndex(args[0]), JdbcCompat.toBytes(getValue(args[0])));
            case "getURL" -> toUrl(getValue(args[0]));
            case "getBinaryStream", "getAsciiStream" -> {
                var bytes = JdbcCompat.toBytes(getValue(args[0]));
                yield bytes == null ? null : new ByteArrayInputStream(bytes);
            }
            case "getCharacterStream", "getNCharacterStream" -> {
                var value = JdbcCompat.stringify(getValue(args[0]));
                yield value == null ? null : new StringReader(value);
            }
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
                yield JdbcCompat.coerce(value, java.sql.Timestamp.class);
            }
            case "unwrap" -> {
                var iface = (Class<?>) args[0];
                if (iface.isInstance(proxy)) {
                    yield proxy;
                }
                throw new SQLException("Not a wrapper for " + iface.getName());
            }
            case "isWrapperFor" -> ((Class<?>) args[0]).isInstance(proxy);
            case "getType" -> {
                ensureNotClosed();
                yield type;
            }
            case "getConcurrency" -> {
                ensureNotClosed();
                yield concurrency;
            }
            case "getFetchDirection" -> {
                ensureNotClosed();
                yield fetchDirection;
            }
            case "setFetchDirection" -> {
                ensureNotClosed();
                var value = (Integer) args[0];
                validateFetchDirection(value);
                if (type == ResultSet.TYPE_FORWARD_ONLY && value != ResultSet.FETCH_FORWARD) {
                    throw new SQLException("Invalid fetch direction for forward-only result set: " + value);
                }
                fetchDirection = value;
                yield null;
            }
            case "getHoldability" -> {
                ensureNotClosed();
                yield holdability;
            }
            case "setFetchSize" -> {
                ensureNotClosed();
                var value = (Integer) args[0];
                if (value < 0) {
                    throw new SQLException("Invalid fetch size: " + value);
                }
                fetchSize = value;
                yield null;
            }
            case "getFetchSize" -> {
                ensureNotClosed();
                yield fetchSize;
            }
            case "getWarnings" -> {
                ensureNotClosed();
                yield warnings;
            }
            case "clearWarnings" -> {
                ensureNotClosed();
                warnings = null;
                yield null;
            }
            case "rowUpdated", "rowInserted", "rowDeleted" -> {
                ensureNotClosed();
                yield false;
            }
            case "updateRow", "insertRow", "deleteRow", "refreshRow", "cancelRowUpdates",
                "moveToInsertRow", "moveToCurrentRow" -> {
                ensureNotClosed();
                throw new SQLException("ResultSet is not updatable");
            }
            default -> {
                if (name.startsWith("update")) {
                    ensureNotClosed();
                    throw new SQLException("ResultSet is not updatable");
                }
                if (method.getReturnType() == void.class) {
                    yield null;
                }
                yield JdbcCompat.defaultReturn(method.getReturnType());
            }
        };
    }

    private void ensureNotClosed() throws SQLException {
        if (closed) {
            throw new SQLException("ResultSet is closed");
        }
    }

    private void closeStatementOnCompletion() throws SQLException {
        if (
            statement != null &&
            !statement.isClosed() &&
            statement.isCloseOnCompletion()
        ) {
            statement.close();
        }
    }

    private void validateFetchDirection(int value) throws SQLException {
        if (
            value != ResultSet.FETCH_FORWARD &&
            value != ResultSet.FETCH_REVERSE &&
            value != ResultSet.FETCH_UNKNOWN
        ) {
            throw new SQLException("Invalid fetch direction: " + value);
        }
    }

    private void ensureOnRow() throws SQLException {
        ensureNotClosed();
        if (cursor < 0 || cursor >= rows.size()) {
            throw new SQLException("ResultSet cursor is not positioned on a row");
        }
    }

    private void ensureScrollable() throws SQLException {
        if (type == ResultSet.TYPE_FORWARD_ONLY) {
            throw new SQLException("Operation requires a scrollable ResultSet");
        }
    }

    private Object getValue(Object columnArg) throws SQLException {
        var value = valueAt(columnIndex(columnArg));
        wasNull = value == null;
        return value;
    }

    private String truncateString(int column, Object value) {
        var text = JdbcCompat.stringify(value);
        if (text == null || maxFieldSize <= 0 || !isMaxFieldSizeColumn(column)) {
            return text;
        }
        return text.length() <= maxFieldSize ? text : text.substring(0, maxFieldSize);
    }

    private byte[] truncateBytes(int column, byte[] bytes) {
        if (bytes == null || maxFieldSize <= 0 || !isMaxFieldSizeColumn(column) || bytes.length <= maxFieldSize) {
            return bytes;
        }
        return java.util.Arrays.copyOf(bytes, maxFieldSize);
    }

    private boolean isMaxFieldSizeColumn(int column) {
        return switch (columns.get(column - 1).oid()) {
            case 17, 25, 1042, 1043 -> true;
            default -> false;
        };
    }

    private int columnIndex(Object columnArg) throws SQLException {
        return columnArg instanceof Integer idx ? idx : findColumn((String) columnArg);
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

    private Object objectValue(int column, Object value) throws SQLException {
        if (value == null || connection == null) {
            return value;
        }
        var typeName = JdbcCompat.oidToPgType(columns.get(column - 1).oid());
        var objectClass = connection.pgObjectClass(typeName);
        if (objectClass == null) {
            return value;
        }
        return JdbcCompat.toPgObject(typeName, value, objectClass);
    }

    private boolean absolute(int row) throws SQLException {
        ensureScrollable();
        if (row > 0) {
            return moveToRow(row - 1);
        }
        if (row < 0) {
            return moveToRow(rows.size() + row);
        }
        beforeFirst();
        return false;
    }

    private boolean moveToRow(int nextCursor) throws SQLException {
        ensureNotClosed();
        ensureScrollable();
        if (nextCursor < 0) {
            cursor = -1;
            return false;
        }
        if (nextCursor >= rows.size()) {
            cursor = rows.size();
            return false;
        }
        cursor = nextCursor;
        return true;
    }

    private void beforeFirst() throws SQLException {
        ensureNotClosed();
        ensureScrollable();
        cursor = -1;
    }

    private URL toUrl(Object value) throws SQLException {
        if (value == null) {
            return null;
        }
        try {
            return new URL(String.valueOf(value));
        } catch (MalformedURLException exception) {
            throw new SQLException("Invalid URL value: " + value, exception);
        }
    }

    private org.postgresql.core.BaseConnection baseConnection() throws SQLException {
        if (statement == null) {
            throw new SQLException("ResultSet is not associated with a connection");
        }
        return (org.postgresql.core.BaseConnection) statement.getConnection();
    }
}
