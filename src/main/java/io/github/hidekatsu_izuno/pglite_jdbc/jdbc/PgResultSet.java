package io.github.hidekatsu_izuno.pglite_jdbc.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class PgResultSet implements InvocationHandler {
    private final java.sql.Statement statement;
    private final List<Column> columns;
    private final List<Map<String, Object>> rows;
    private final Map<String, Integer> labelIndex;
    private int cursor = -1;
    private boolean closed;
    private boolean wasNull;

    private PgResultSet(
        java.sql.Statement statement,
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

    static ResultSet create(
        java.sql.Statement statement,
        List<Column> columns,
        List<Map<String, Object>> rows
    ) {
        return (ResultSet) Proxy.newProxyInstance(
            PgResultSet.class.getClassLoader(),
            new Class<?>[] { ResultSet.class },
            new PgResultSet(statement, columns, rows)
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
            case "afterLast" -> {
                ensureNotClosed();
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
            case "getMetaData" -> PgResultSetMetaData.create(columns);
            case "getStatement" -> statement;
            case "findColumn" -> findColumn((String) args[0]);
            case "getObject" -> {
                var value = args[0] instanceof Integer index
                    ? valueAt(index)
                    : valueAt(findColumn((String) args[0]));
                if (args.length == 2 && args[1] instanceof Class<?> targetType) {
                    yield JdbcCompat.coerce(value, targetType);
                }
                yield value;
            }
            case "getString" -> JdbcCompat.stringify(getValue(args[0]));
            case "getBoolean" -> JdbcCompat.toBoolean(getValue(args[0]));
            case "getByte" -> JdbcCompat.toNumber(getValue(args[0])).byteValue();
            case "getShort" -> JdbcCompat.toNumber(getValue(args[0])).shortValue();
            case "getInt" -> JdbcCompat.toNumber(getValue(args[0])).intValue();
            case "getLong" -> JdbcCompat.toNumber(getValue(args[0])).longValue();
            case "getFloat" -> JdbcCompat.toNumber(getValue(args[0])).floatValue();
            case "getDouble" -> JdbcCompat.toNumber(getValue(args[0])).doubleValue();
            case "getBigDecimal" -> JdbcCompat.toBigDecimal(getValue(args[0]));
            case "getBytes" -> JdbcCompat.toBytes(getValue(args[0]));
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
                yield JdbcCompat.defaultReturn(method.getReturnType());
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

    private boolean absolute(int row) throws SQLException {
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
        cursor = -1;
    }
}
