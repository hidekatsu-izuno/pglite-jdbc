package io.github.hidekatsu_izuno.pglite_jdbc.jdbc;

import io.github.hidekatsu_izuno.pglite_jdbc.core.v3.QueryExecutorImpl;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

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

        return out.toString();
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

    static Number toNumber(Object value) {
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

    static String stringify(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    static boolean toBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        var text = String.valueOf(value).toLowerCase(Locale.ROOT);
        return "true".equals(text) || "t".equals(text) || "1".equals(text);
    }

    static BigDecimal toBigDecimal(Object value) {
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

    static byte[] toBytes(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return Arrays.copyOf(bytes, bytes.length);
        }
        return String.valueOf(value).getBytes(StandardCharsets.UTF_8);
    }

    static Object coerce(Object value, Class<?> targetType) {
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
        if (targetType == java.sql.Timestamp.class) {
            var text = String.valueOf(value).replace('T', ' ');
            return java.sql.Timestamp.valueOf(LocalDateTime.parse(text.replace(' ', 'T')));
        }
        return value;
    }

    static int oidToJdbcType(int oid) {
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
