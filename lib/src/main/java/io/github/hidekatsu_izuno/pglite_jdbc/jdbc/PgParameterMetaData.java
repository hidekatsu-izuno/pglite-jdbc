package io.github.hidekatsu_izuno.pglite_jdbc.jdbc;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ParameterMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

final class PgParameterMetaData implements InvocationHandler {
    private final int[] parameterTypes;

    private PgParameterMetaData(int[] parameterTypes) {
        this.parameterTypes = parameterTypes;
    }

    static ParameterMetaData create(List<interface_.Field> params) {
        var types = new int[params != null ? params.size() : 0];
        for (var i = 0; i < types.length; i++) {
            types[i] = params.get(i).dataTypeID();
        }
        return (ParameterMetaData) Proxy.newProxyInstance(
            PgParameterMetaData.class.getClassLoader(),
            new Class<?>[] { ParameterMetaData.class },
            new PgParameterMetaData(types)
        );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        var name = method.getName();
        if (method.getDeclaringClass() == Object.class) {
            return switch (name) {
                case "toString" -> "PgParameterMetaData";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> null;
            };
        }

        return switch (name) {
            case "getParameterCount" -> parameterTypes.length;
            case "isNullable" -> ParameterMetaData.parameterNullableUnknown;
            case "isSigned" -> true;
            case "getPrecision", "getScale" -> 0;
            case "getParameterType" -> jdbcType(index(args));
            case "getParameterTypeName" -> "oid:" + oid(index(args));
            case "getParameterClassName" -> className(index(args));
            case "getParameterMode" -> ParameterMetaData.parameterModeIn;
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

    private int index(Object[] args) throws SQLException {
        var index = (Integer) args[0];
        if (index < 1 || index > parameterTypes.length) {
            throw new SQLException("Parameter index out of bounds: " + index);
        }
        return index - 1;
    }

    private int oid(int index) {
        return parameterTypes[index];
    }

    private int jdbcType(int index) {
        return JdbcCompat.oidToJdbcType(oid(index));
    }

    private String className(int index) {
        return switch (jdbcType(index)) {
            case Types.BOOLEAN -> Boolean.class.getName();
            case Types.SMALLINT -> Short.class.getName();
            case Types.INTEGER -> Integer.class.getName();
            case Types.BIGINT -> Long.class.getName();
            case Types.REAL -> Float.class.getName();
            case Types.DOUBLE -> Double.class.getName();
            case Types.NUMERIC -> java.math.BigDecimal.class.getName();
            case Types.DATE -> java.sql.Date.class.getName();
            case Types.TIME -> java.sql.Time.class.getName();
            case Types.TIMESTAMP -> java.sql.Timestamp.class.getName();
            case Types.BINARY -> byte[].class.getName();
            default -> String.class.getName();
        };
    }
}
