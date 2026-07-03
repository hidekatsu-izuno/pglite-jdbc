package io.github.hidekatsu_izuno.pglite_jdbc.jdbc;

import io.github.hidekatsu_izuno.pglite_jdbc.core.v3.QueryExecutorImpl;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;
import java.math.BigDecimal;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class JdbcCompat {
    private JdbcCompat() {
    }

    static SQLException toSqlException(Throwable error) {
        return QueryExecutorImpl.toSqlException(error);
    }

    static Object defaultReturn(Class<?> returnType) {
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
            return PgResultSet.create(null, List.of(), List.of());
        }
        if (returnType == ResultSetMetaData.class) {
            return PgResultSetMetaData.create(List.of());
        }
        if (returnType == int[].class) {
            return new int[0];
        }
        if (returnType == long[].class) {
            return new long[0];
        }
        return null;
    }

    static SQLFeatureNotSupportedException unsupported(String method) {
        return new SQLFeatureNotSupportedException(method + " is not supported");
    }

    static String rewriteJdbcParameters(String sql) {
        return rewriteJdbcParametersWithCount(sql).sql();
    }

    static int countJdbcParameters(String sql) {
        return rewriteJdbcParametersWithCount(sql).parameterCount();
    }

    private record RewrittenSql(String sql, int parameterCount) {
    }

    private static RewrittenSql rewriteJdbcParametersWithCount(String sql) {
        if (sql == null || sql.indexOf('?') < 0) {
            return new RewrittenSql(sql, 0);
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
                        if (!Character.isLetterOrDigit(tagChar) && tagChar != '_') {
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

        return new RewrittenSql(out.toString(), placeholderIndex);
    }

    static int safeAffectedRows(interface_.Results<java.util.Map<String, Object>> result) {
        return result.affectedRows() != null ? result.affectedRows() : 0;
    }

    static List<Column> toColumns(List<interface_.Field> fields) {
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

    static List<Column> toResultFieldColumns(List<interface_.ResultField> fields) {
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

    static Number toNumber(Object value) throws SQLException {
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
            throw new SQLException("Bad value for numeric type: " + value, e);
        }
    }

    static byte toByte(Object value) throws SQLException {
        return toIntegral(value, BigDecimal.valueOf(Byte.MIN_VALUE), BigDecimal.valueOf(Byte.MAX_VALUE), "byte")
            .byteValue();
    }

    static short toShort(Object value) throws SQLException {
        return toIntegral(value, BigDecimal.valueOf(Short.MIN_VALUE), BigDecimal.valueOf(Short.MAX_VALUE), "short")
            .shortValue();
    }

    static int toInt(Object value) throws SQLException {
        return toIntegral(value, BigDecimal.valueOf(Integer.MIN_VALUE), BigDecimal.valueOf(Integer.MAX_VALUE), "int")
            .intValue();
    }

    static long toLong(Object value) throws SQLException {
        return toIntegral(value, BigDecimal.valueOf(Long.MIN_VALUE), BigDecimal.valueOf(Long.MAX_VALUE), "long")
            .longValue();
    }

    private static BigDecimal toIntegral(Object value, BigDecimal min, BigDecimal max, String target)
        throws SQLException {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        var decimal = toBigDecimal(value).setScale(0, java.math.RoundingMode.DOWN);
        if (decimal.compareTo(min) < 0 || decimal.compareTo(max) > 0) {
            throw new SQLException("Value out of range for " + target + ": " + value);
        }
        return decimal;
    }

    static String stringify(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    static boolean toBoolean(Object value) throws SQLException {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            var numericValue = number.longValue();
            if (numericValue == 1L) {
                return true;
            }
            if (numericValue == 0L) {
                return false;
            }
            throw new SQLException("Cannot cast to boolean: " + value);
        }
        var text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return switch (text) {
            case "true", "t", "yes", "y", "on", "1" -> true;
            case "false", "f", "no", "n", "off", "0" -> false;
            default -> throw new SQLException("Cannot cast to boolean: " + value);
        };
    }

    static BigDecimal toBigDecimal(Object value) throws SQLException {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (
            value instanceof Byte ||
            value instanceof Short ||
            value instanceof Integer ||
            value instanceof Long
        ) {
            return BigDecimal.valueOf(((Number) value).longValue());
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new SQLException("Bad value for numeric type: " + value, e);
        }
    }

    static BigDecimal toBigDecimal(Object value, int scale) throws SQLException {
        var decimal = toBigDecimal(value);
        return decimal == null ? null : decimal.setScale(scale, java.math.RoundingMode.HALF_UP);
    }

    static byte[] toBytes(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return Arrays.copyOf(bytes, bytes.length);
        }
        return String.valueOf(value).getBytes(StandardCharsets.UTF_8);
    }

    static org.postgresql.util.PGobject toPgObject(String type, Object value) throws SQLException {
        return toPgObject(type, value, org.postgresql.util.PGobject.class);
    }

    static org.postgresql.util.PGobject toPgObject(
        String type,
        Object value,
        Class<? extends org.postgresql.util.PGobject> objectClass
    ) throws SQLException {
        if (value == null) {
            return null;
        }
        if (objectClass.isInstance(value)) {
            return objectClass.cast(value);
        }
        var object = createPgObject(objectClass);
        object.setType(type);
        object.setValue(String.valueOf(value));
        return object;
    }

    private static org.postgresql.util.PGobject createPgObject(
        Class<? extends org.postgresql.util.PGobject> objectClass
    ) throws SQLException {
        try {
            var constructor = objectClass.getDeclaredConstructor();
            if (!constructor.canAccess(null)) {
                constructor.setAccessible(true);
            }
            return constructor.newInstance();
        } catch (ReflectiveOperationException | SecurityException exception) {
            throw new SQLException("Cannot instantiate PGobject class: " + objectClass.getName(), exception);
        }
    }

    static Object[] toObjectArray(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Object[] objects) {
            return Arrays.copyOf(objects, objects.length);
        }
        if (value instanceof List<?> list) {
            return list.toArray();
        }
        var valueClass = value.getClass();
        if (!valueClass.isArray()) {
            return parsePgArray(String.valueOf(value));
        }
        var length = Array.getLength(value);
        var out = new Object[length];
        for (var i = 0; i < length; i++) {
            out[i] = Array.get(value, i);
        }
        return out;
    }

    static String toArrayLiteral(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text && text.startsWith("{") && text.endsWith("}")) {
            return text;
        }
        var out = new StringBuilder();
        appendArrayLiteral(out, value);
        return out.toString();
    }

    static String arrayElementTypeName(String typeName) {
        if (typeName == null) {
            return null;
        }
        var normalized = typeName.trim();
        if (normalized.endsWith("[]")) {
            return normalized.substring(0, normalized.length() - 2);
        }
        if (normalized.startsWith("_")) {
            return normalized.substring(1);
        }
        return normalized;
    }

    private static void appendArrayLiteral(StringBuilder out, Object value) {
        out.append('{');
        var values = toObjectArray(value);
        if (values != null) {
            for (var i = 0; i < values.length; i++) {
                if (i > 0) {
                    out.append(',');
                }
                appendArrayElement(out, values[i]);
            }
        }
        out.append('}');
    }

    private static void appendArrayElement(StringBuilder out, Object value) {
        if (value == null) {
            out.append("NULL");
            return;
        }
        var valueClass = value.getClass();
        if (value instanceof List<?> || valueClass.isArray()) {
            appendArrayLiteral(out, value);
            return;
        }
        var text = String.valueOf(value);
        out.append('"');
        for (var i = 0; i < text.length(); i++) {
            var ch = text.charAt(i);
            if (ch == '"' || ch == '\\') {
                out.append('\\');
            }
            out.append(ch);
        }
        out.append('"');
    }

    static Object[] parsePgArray(String text) {
        if (text == null) {
            return null;
        }
        var value = text.trim();
        if (value.length() < 2 || value.charAt(0) != '{' || value.charAt(value.length() - 1) != '}') {
            return new Object[] { text };
        }
        var out = new ArrayList<Object>();
        var current = new StringBuilder();
        var quoted = false;
        var escaped = false;
        var wasQuoted = false;
        for (var i = 1; i < value.length() - 1; i++) {
            var ch = value.charAt(i);
            if (escaped) {
                current.append(ch);
                escaped = false;
                continue;
            }
            if (quoted && ch == '\\') {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                quoted = !quoted;
                wasQuoted = true;
                continue;
            }
            if (!quoted && ch == ',') {
                out.add(pgArrayItem(current.toString(), wasQuoted));
                current.setLength(0);
                wasQuoted = false;
                continue;
            }
            current.append(ch);
        }
        out.add(pgArrayItem(current.toString(), wasQuoted));
        return out.toArray();
    }

    private static Object pgArrayItem(String value, boolean quoted) {
        if (!quoted && "NULL".equals(value)) {
            return null;
        }
        return value;
    }

    static Object coerce(Object value, Class<?> targetType) throws SQLException {
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
        if (targetType == UUID.class) {
            return UUID.fromString(String.valueOf(value));
        }
        if (targetType == LocalDate.class) {
            return LocalDate.parse(String.valueOf(value));
        }
        if (targetType == LocalTime.class) {
            return LocalTime.parse(String.valueOf(value));
        }
        if (targetType == LocalDateTime.class) {
            var text = String.valueOf(value).replace(' ', 'T');
            if (text.endsWith("Z") || text.matches(".*[+-][0-9]{2}:[0-9]{2}$")) {
                return OffsetDateTime.parse(text).toLocalDateTime();
            }
            return LocalDateTime.parse(text);
        }
        if (targetType == OffsetDateTime.class) {
            var text = String.valueOf(value).replace(' ', 'T');
            return OffsetDateTime.parse(text);
        }
        if (targetType == java.sql.Timestamp.class) {
            var text = String.valueOf(value).replace('T', ' ');
            if (text.endsWith("Z") || text.matches(".*[+-][0-9]{2}:[0-9]{2}$")) {
                return java.sql.Timestamp.from(OffsetDateTime.parse(text.replace(' ', 'T')).toInstant());
            }
            return java.sql.Timestamp.valueOf(LocalDateTime.parse(text.replace(' ', 'T')));
        }
        return value;
    }

    static int oidToJdbcType(int oid) {
        return switch (oid) {
            case 16 -> Types.BOOLEAN;
            case 20, 26 -> Types.BIGINT;
            case 21 -> Types.SMALLINT;
            case 23 -> Types.INTEGER;
            case 25, 1042, 1043 -> Types.VARCHAR;
            case 700 -> Types.REAL;
            case 701 -> Types.DOUBLE;
            case 1082 -> Types.DATE;
            case 1083 -> Types.TIME;
            case 1266 -> Types.TIME_WITH_TIMEZONE;
            case 1114 -> Types.TIMESTAMP;
            case 1184 -> Types.TIMESTAMP_WITH_TIMEZONE;
            case 1700 -> Types.NUMERIC;
            case 17 -> Types.BINARY;
            case 1000, 1001, 1005, 1007, 1009, 1014, 1015, 1016, 1021, 1022, 1028,
                1115, 1182, 1183, 1185, 1231, 1270, 199, 2951, 3807 -> Types.ARRAY;
            default -> Types.OTHER;
        };
    }

    static String oidToPgType(int oid) {
        return switch (oid) {
            case 16 -> "bool";
            case 17 -> "bytea";
            case 20 -> "int8";
            case 21 -> "int2";
            case 23 -> "int4";
            case 25 -> "text";
            case 26 -> "oid";
            case 114 -> "json";
            case 650 -> "cidr";
            case 700 -> "float4";
            case 701 -> "float8";
            case 774 -> "macaddr8";
            case 829 -> "macaddr";
            case 869 -> "inet";
            case 1042 -> "bpchar";
            case 1043 -> "varchar";
            case 1082 -> "date";
            case 1083 -> "time";
            case 1114 -> "timestamp";
            case 1184 -> "timestamptz";
            case 1266 -> "timetz";
            case 1700 -> "numeric";
            case 2950 -> "uuid";
            case 3802 -> "jsonb";
            case 1000 -> "_bool";
            case 1001 -> "_bytea";
            case 1005 -> "_int2";
            case 1007 -> "_int4";
            case 1009 -> "_text";
            case 1014 -> "_bpchar";
            case 1015 -> "_varchar";
            case 1016 -> "_int8";
            case 1021 -> "_float4";
            case 1022 -> "_float8";
            case 1028 -> "_oid";
            case 1115 -> "_timestamp";
            case 1182 -> "_date";
            case 1183 -> "_time";
            case 1185 -> "_timestamptz";
            case 1231 -> "_numeric";
            case 1270 -> "_timetz";
            case 199 -> "_json";
            case 2951 -> "_uuid";
            case 3807 -> "_jsonb";
            default -> "oid:" + oid;
        };
    }
}
