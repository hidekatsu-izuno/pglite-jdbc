package io.github.hidekatsu_izuno.pglite_jdbc.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;

final class PgResultSetMetaData implements InvocationHandler {
    private final List<Column> columns;

    private PgResultSetMetaData(List<Column> columns) {
        this.columns = columns;
    }

    static ResultSetMetaData create(List<Column> columns) {
        return (ResultSetMetaData) Proxy.newProxyInstance(
            PgResultSetMetaData.class.getClassLoader(),
            new Class<?>[] {
                ResultSetMetaData.class,
                org.postgresql.PGResultSetMetaData.class,
            },
            new PgResultSetMetaData(columns)
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
            case "getColumnTypeName" -> JdbcCompat.oidToPgType(column((Integer) args[0]).oid());
            case "isNullable" -> {
                column((Integer) args[0]);
                yield ResultSetMetaData.columnNullableUnknown;
            }
            case "isAutoIncrement" -> {
                column((Integer) args[0]);
                yield false;
            }
            case "isCaseSensitive", "isSearchable" -> {
                column((Integer) args[0]);
                yield true;
            }
            case "isSigned" -> isSigned(column((Integer) args[0]).oid());
            case "isCurrency" -> {
                column((Integer) args[0]);
                yield false;
            }
            case "getColumnDisplaySize", "getPrecision", "getScale" -> {
                column((Integer) args[0]);
                yield 0;
            }
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
            case "getBaseColumnName" -> column((Integer) args[0]).label();
            case "getBaseTableName", "getBaseSchemaName" -> {
                column((Integer) args[0]);
                yield "";
            }
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
        return switch (JdbcCompat.oidToJdbcType(oid)) {
            case java.sql.Types.SMALLINT, java.sql.Types.INTEGER, java.sql.Types.BIGINT,
                java.sql.Types.REAL, java.sql.Types.DOUBLE, java.sql.Types.FLOAT,
                java.sql.Types.NUMERIC, java.sql.Types.DECIMAL -> true;
            default -> false;
        };
    }
}
