package io.github.hidekatsu_izuno.pglite_jdbc.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSetMetaData;
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
    public Object invoke(Object proxy, Method method, Object[] args) {
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
            case "getColumnLabel", "getColumnName" -> columns.get(((Integer) args[0]) - 1).label();
            case "getColumnType" -> JdbcCompat.oidToJdbcType(columns.get(((Integer) args[0]) - 1).oid());
            case "getColumnTypeName" -> "oid:" + columns.get(((Integer) args[0]) - 1).oid();
            case "isNullable" -> ResultSetMetaData.columnNullableUnknown;
            case "isAutoIncrement" -> false;
            case "isCaseSensitive" -> true;
            case "isSearchable" -> true;
            case "isCurrency" -> false;
            case "isSigned" -> true;
            case "getColumnDisplaySize", "getPrecision", "getScale" -> 0;
            case "getSchemaName", "getTableName", "getCatalogName" -> "";
            case "isReadOnly" -> true;
            case "isWritable", "isDefinitelyWritable" -> false;
            case "getColumnClassName" -> Object.class.getName();
            case "getBaseColumnName" -> columns.get(((Integer) args[0]) - 1).label();
            case "getBaseTableName", "getBaseSchemaName" -> "";
            case "getFormat" -> 0;
            case "unwrap" -> {
                var iface = (Class<?>) args[0];
                if (iface.isInstance(proxy)) {
                    yield proxy;
                }
                throw new IllegalArgumentException("Not a wrapper for " + iface.getName());
            }
            case "isWrapperFor" -> ((Class<?>) args[0]).isInstance(proxy);
            default -> JdbcCompat.defaultReturn(method.getReturnType());
        };
    }
}
