package io.github.hidekatsu_izuno.pglite_jdbc.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class PgResultSetMetaData implements InvocationHandler {
    private final PgConnection connection;
    private final List<Column> columns;
    private final Map<ColumnKey, FieldInfo> fieldInfo = new HashMap<>();

    private PgResultSetMetaData(PgConnection connection, List<Column> columns) {
        this.connection = connection;
        this.columns = columns;
    }

    static ResultSetMetaData create(List<Column> columns) {
        return create(null, columns);
    }

    static ResultSetMetaData create(PgConnection connection, List<Column> columns) {
        return (ResultSetMetaData) Proxy.newProxyInstance(
            PgResultSetMetaData.class.getClassLoader(),
            new Class<?>[] {
                ResultSetMetaData.class,
                org.postgresql.PGResultSetMetaData.class,
            },
            new PgResultSetMetaData(connection, columns)
        );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        var name = method.getName();
        if (method.getDeclaringClass() == Object.class) {
            return switch (name) {
                case "toString" -> "PgResultSetMetaData";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> null;
            };
        }

        return switch (name) {
            case "getColumnCount" -> columns.size();
            case "getColumnLabel", "getColumnName" -> column((Integer) args[0]).label();
            case "getColumnType" -> JdbcCompat.oidToJdbcType(column((Integer) args[0]).oid());
            case "getColumnTypeName" -> columnTypeName(column((Integer) args[0]));
            case "isNullable" -> nullable(column((Integer) args[0]));
            case "isAutoIncrement" -> autoIncrement(column((Integer) args[0]));
            case "isCaseSensitive" -> isCaseSensitive(column((Integer) args[0]).oid());
            case "isSearchable" -> {
                column((Integer) args[0]);
                yield true;
            }
            case "isSigned" -> isSigned(column((Integer) args[0]).oid());
            case "isCurrency" -> {
                yield column((Integer) args[0]).oid() == 790;
            }
            case "getColumnDisplaySize" -> displaySize(column((Integer) args[0]));
            case "getPrecision" -> precision(column((Integer) args[0]));
            case "getScale" -> scale(column((Integer) args[0]));
            case "getSchemaName", "getTableName", "getCatalogName" -> {
                column((Integer) args[0]);
                yield "";
            }
            case "isReadOnly" -> {
                column((Integer) args[0]);
                yield false;
            }
            case "isWritable" -> {
                column((Integer) args[0]);
                yield true;
            }
            case "isDefinitelyWritable" -> {
                column((Integer) args[0]);
                yield false;
            }
            case "getColumnClassName" -> columnClassName(column((Integer) args[0]).oid());
            case "getBaseColumnName" -> baseColumnName(column((Integer) args[0]));
            case "getBaseTableName" -> baseTableName(column((Integer) args[0]));
            case "getBaseSchemaName" -> baseSchemaName(column((Integer) args[0]));
            case "getFormat" -> {
                column((Integer) args[0]);
                yield 0;
            }
            case "unwrap" -> {
                var iface = (Class<?>) args[0];
                if (iface.isInstance(proxy)) {
                    yield proxy;
                }
                throw new SQLException("Not a wrapper for " + iface.getName());
            }
            case "isWrapperFor" -> ((Class<?>) args[0]).isInstance(proxy);
            default -> JdbcCompat.defaultReturn(method.getReturnType());
        };
    }

    private Column column(int index) throws SQLException {
        if (index < 1 || index > columns.size()) {
            throw new SQLException("Column index out of bounds: " + index);
        }
        return columns.get(index - 1);
    }

    private String columnClassName(int oid) {
        if (oid == 16 || oid == 1560) {
            return Boolean.class.getName();
        }
        return switch (JdbcCompat.oidToJdbcType(oid)) {
            case java.sql.Types.BOOLEAN -> Boolean.class.getName();
            case java.sql.Types.SMALLINT -> Short.class.getName();
            case java.sql.Types.INTEGER -> Integer.class.getName();
            case java.sql.Types.BIGINT -> Long.class.getName();
            case java.sql.Types.REAL -> Float.class.getName();
            case java.sql.Types.DOUBLE, java.sql.Types.FLOAT -> Double.class.getName();
            case java.sql.Types.NUMERIC, java.sql.Types.DECIMAL -> java.math.BigDecimal.class.getName();
            case java.sql.Types.BINARY, java.sql.Types.VARBINARY, java.sql.Types.LONGVARBINARY ->
                byte[].class.getName();
            case java.sql.Types.DATE -> java.sql.Date.class.getName();
            case java.sql.Types.TIME, java.sql.Types.TIME_WITH_TIMEZONE -> java.sql.Time.class.getName();
            case java.sql.Types.TIMESTAMP, java.sql.Types.TIMESTAMP_WITH_TIMEZONE ->
                java.sql.Timestamp.class.getName();
            case java.sql.Types.ARRAY -> java.sql.Array.class.getName();
            default -> String.class.getName();
        };
    }

    private boolean isSigned(int oid) {
        return switch (oid) {
            case 20, 21, 23, 700, 701, 1700 -> true;
            default -> false;
        };
    }

    private boolean isCaseSensitive(int oid) {
        return switch (oid) {
            case 16, 20, 21, 23, 26, 700, 701, 1082, 1083, 1114, 1184, 1186, 1266, 1560, 1562, 1700 ->
                false;
            default -> true;
        };
    }

    private int precision(Column column) {
        var oid = column.oid();
        var typmod = column.typmod();
        return switch (oid) {
            case 16, 18 -> 1;
            case 21 -> 5;
            case 23, 26 -> 10;
            case 20 -> 19;
            case 700 -> 8;
            case 701, 790 -> 17;
            case 1042, 1043 -> typmod == -1 ? 0 : typmod - 4;
            case 1082, 1083, 1114, 1184, 1186, 1266 -> displaySize(column);
            case 1560 -> typmod;
            case 1562 -> typmod == -1 ? 0 : typmod;
            case 1700 -> typmod == -1 ? 0 : ((typmod - 4) & 0xffff0000) >> 16;
            default -> 0;
        };
    }

    private int scale(Column column) {
        var oid = column.oid();
        var typmod = column.typmod();
        return switch (oid) {
            case 700 -> 8;
            case 701, 790 -> 17;
            case 1083, 1114, 1184, 1266 -> typmod == -1 ? 6 : typmod;
            case 1186 -> typmod == -1 ? 6 : typmod & 0xffff;
            case 1700 -> typmod == -1 ? 0 : (typmod - 4) & 0xffff;
            default -> 0;
        };
    }

    private int displaySize(Column column) {
        var oid = column.oid();
        var typmod = column.typmod();
        return switch (oid) {
            case 16, 18 -> 1;
            case 21 -> 6;
            case 23 -> 11;
            case 26 -> 10;
            case 20 -> 20;
            case 700 -> 15;
            case 701, 790 -> 25;
            case 1082 -> 13;
            case 1083 -> 8 + timeSecondSize(typmod);
            case 1266 -> 8 + timeSecondSize(typmod) + 6;
            case 1114 -> 22 + timeSecondSize(typmod);
            case 1184 -> 22 + timeSecondSize(typmod) + 6;
            case 1186 -> 49;
            case 1042, 1043 -> typmod == -1 ? 0 : typmod - 4;
            case 1560 -> typmod;
            case 1562 -> typmod == -1 ? 0 : typmod;
            case 1700 -> numericDisplaySize(typmod);
            default -> 0;
        };
    }

    private int timeSecondSize(int typmod) {
        return switch (typmod) {
            case -1 -> 7;
            case 0 -> 0;
            case 1 -> 3;
            default -> typmod + 1;
        };
    }

    private int numericDisplaySize(int typmod) {
        if (typmod == -1) {
            return 131089;
        }
        var precision = ((typmod - 4) >> 16) & 0xffff;
        var scale = (typmod - 4) & 0xffff;
        return 1 + precision + (scale == 0 ? 0 : 1);
    }

    private String baseColumnName(Column column) throws SQLException {
        var info = fieldInfo(column);
        return info == null ? "" : info.columnName();
    }

    private String columnTypeName(Column column) throws SQLException {
        var type = JdbcCompat.oidToPgType(column.oid());
        if (!autoIncrement(column)) {
            return type;
        }
        return switch (type) {
            case "int4" -> "serial";
            case "int8" -> "bigserial";
            case "int2" -> "smallserial";
            default -> type;
        };
    }

    private int nullable(Column column) throws SQLException {
        var info = fieldInfo(column);
        return info == null ? ResultSetMetaData.columnNullable : info.nullable();
    }

    private boolean autoIncrement(Column column) throws SQLException {
        var info = fieldInfo(column);
        return info != null && info.autoIncrement();
    }

    private String baseTableName(Column column) throws SQLException {
        var info = fieldInfo(column);
        return info == null ? "" : info.tableName();
    }

    private String baseSchemaName(Column column) throws SQLException {
        var info = fieldInfo(column);
        return info == null ? "" : info.schemaName();
    }

    private FieldInfo fieldInfo(Column column) throws SQLException {
        if (connection == null || column.tableOid() == 0 || column.positionInTable() == 0) {
            return null;
        }
        var key = new ColumnKey(column.tableOid(), column.positionInTable());
        if (fieldInfo.containsKey(key)) {
            return fieldInfo.get(key);
        }
        var result = connection.query(
            """
            SELECT n.nspname AS schema_name,
                   c.relname AS table_name,
                   a.attname AS column_name,
                   a.attnotnull OR (t.typtype = 'd' AND t.typnotnull) AS not_null,
                   a.attidentity != ''
                     OR pg_catalog.pg_get_expr(d.adbin, d.adrelid) LIKE '%nextval(%' AS auto_increment
            FROM pg_catalog.pg_attribute a
            JOIN pg_catalog.pg_class c ON c.oid = a.attrelid
            JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
            JOIN pg_catalog.pg_type t ON t.oid = a.atttypid
            LEFT JOIN pg_catalog.pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
            WHERE a.attrelid = $1::oid
              AND a.attnum = $2::int2
              AND NOT a.attisdropped
            """,
            new Object[] { column.tableOid(), column.positionInTable() },
            notice -> {}
        );
        var rows = result.rows();
        var info = rows.isEmpty()
            ? null
            : new FieldInfo(
                string(rows.get(0), "SCHEMA_NAME"),
                string(rows.get(0), "TABLE_NAME"),
                string(rows.get(0), "COLUMN_NAME"),
                bool(rows.get(0), "NOT_NULL")
                    ? ResultSetMetaData.columnNoNulls
                    : ResultSetMetaData.columnNullable,
                bool(rows.get(0), "AUTO_INCREMENT")
            );
        fieldInfo.put(key, info);
        return info;
    }

    private String string(Map<String, Object> row, String key) {
        var value = row.get(key);
        if (value == null) {
            value = row.get(key.toLowerCase(java.util.Locale.ROOT));
        }
        return value == null ? "" : String.valueOf(value);
    }

    private boolean bool(Map<String, Object> row, String key) {
        var value = row.get(key);
        if (value == null) {
            value = row.get(key.toLowerCase(java.util.Locale.ROOT));
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private record ColumnKey(int tableOid, int positionInTable) {}

    private record FieldInfo(
        String schemaName,
        String tableName,
        String columnName,
        int nullable,
        boolean autoIncrement
    ) {}
}
