package io.github.hidekatsu_izuno.pglite_jdbc.jdbc;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PgArray implements Array {
    private final String baseTypeName;
    private Object[] array;
    private boolean freed;

    PgArray(String baseTypeName, Object array) {
        this.baseTypeName = baseTypeName;
        this.array = normalize(JdbcCompat.toObjectArray(array));
    }

    @Override
    public String getBaseTypeName() {
        return baseTypeName;
    }

    @Override
    public int getBaseType() {
        if (baseTypeName == null) {
            return Types.OTHER;
        }
        return switch (baseTypeName.toLowerCase()) {
            case "bool", "boolean" -> Types.BOOLEAN;
            case "int2", "smallint" -> Types.SMALLINT;
            case "int4", "integer", "int" -> Types.INTEGER;
            case "int8", "bigint" -> Types.BIGINT;
            case "float4", "real" -> Types.REAL;
            case "float8", "double", "double precision" -> Types.DOUBLE;
            case "numeric", "decimal" -> Types.NUMERIC;
            case "text", "varchar", "character varying", "char", "character" -> Types.VARCHAR;
            case "bytea" -> Types.BINARY;
            case "date" -> Types.DATE;
            case "time" -> Types.TIME;
            case "timestamp", "timestamptz" -> Types.TIMESTAMP;
            default -> Types.OTHER;
        };
    }

    @Override
    public Object getArray() throws SQLException {
        ensureActive();
        return array == null ? null : java.util.Arrays.copyOf(array, array.length);
    }

    @Override
    public Object getArray(java.util.Map<String, Class<?>> map) throws SQLException {
        return getArray();
    }

    @Override
    public Object getArray(long index, int count) throws SQLException {
        ensureActive();
        if (array == null) {
            return null;
        }
        var start = checkedStart(index);
        if (count < 0 || start + count > array.length) {
            throw new SQLException("Array slice is out of bounds");
        }
        return java.util.Arrays.copyOfRange(array, start, start + count);
    }

    @Override
    public Object getArray(long index, int count, java.util.Map<String, Class<?>> map)
        throws SQLException {
        return getArray(index, count);
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        ensureActive();
        return resultSet(1, array == null ? 0 : array.length);
    }

    @Override
    public ResultSet getResultSet(java.util.Map<String, Class<?>> map) throws SQLException {
        return getResultSet();
    }

    @Override
    public ResultSet getResultSet(long index, int count) throws SQLException {
        ensureActive();
        return resultSet(index, count);
    }

    @Override
    public ResultSet getResultSet(long index, int count, java.util.Map<String, Class<?>> map)
        throws SQLException {
        return getResultSet(index, count);
    }

    @Override
    public void free() {
        freed = true;
        array = null;
    }

    private ResultSet resultSet(long index, int count) throws SQLException {
        if (array == null) {
            return PgResultSet.create(null, List.of(new Column("INDEX", 23), new Column("VALUE", baseOid())), List.of());
        }
        var start = checkedStart(index);
        if (count < 0 || start + count > array.length) {
            throw new SQLException("Array result set slice is out of bounds");
        }
        var rows = new ArrayList<Map<String, Object>>(count);
        for (var i = 0; i < count; i++) {
            var row = new LinkedHashMap<String, Object>();
            row.put("INDEX", start + i + 1);
            row.put("VALUE", array[start + i]);
            rows.add(row);
        }
        return PgResultSet.create(null, List.of(new Column("INDEX", 23), new Column("VALUE", baseOid())), rows);
    }

    private int checkedStart(long index) throws SQLException {
        if (index < 1 || index > Integer.MAX_VALUE) {
            throw new SQLException("Array index out of bounds: " + index);
        }
        return (int) index - 1;
    }

    private int baseOid() {
        return switch (getBaseType()) {
            case Types.BOOLEAN -> 16;
            case Types.SMALLINT -> 21;
            case Types.INTEGER -> 23;
            case Types.BIGINT -> 20;
            case Types.REAL -> 700;
            case Types.DOUBLE, Types.FLOAT -> 701;
            case Types.NUMERIC, Types.DECIMAL -> 1700;
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY -> 17;
            case Types.DATE -> 1082;
            case Types.TIME, Types.TIME_WITH_TIMEZONE -> 1083;
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> 1114;
            default -> 25;
        };
    }

    private void ensureActive() throws SQLException {
        if (freed) {
            throw new SQLException("Array has been freed");
        }
    }

    private Object[] normalize(Object[] values) {
        if (values == null) {
            return null;
        }
        var out = new Object[values.length];
        for (var i = 0; i < values.length; i++) {
            out[i] = normalizeElement(values[i]);
        }
        return out;
    }

    private Object normalizeElement(Object value) {
        if (value == null) {
            return null;
        }
        return switch (getBaseType()) {
            case Types.SMALLINT -> value instanceof Number number ? number.shortValue() : Short.valueOf(String.valueOf(value));
            case Types.INTEGER -> value instanceof Number number ? number.intValue() : Integer.valueOf(String.valueOf(value));
            case Types.BIGINT -> value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
            case Types.REAL -> value instanceof Number number ? number.floatValue() : Float.valueOf(String.valueOf(value));
            case Types.DOUBLE, Types.FLOAT -> value instanceof Number number ? number.doubleValue() : Double.valueOf(String.valueOf(value));
            default -> value;
        };
    }
}
