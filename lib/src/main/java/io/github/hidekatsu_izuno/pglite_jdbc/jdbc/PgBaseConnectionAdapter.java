package io.github.hidekatsu_izuno.pglite_jdbc.jdbc;

import java.lang.reflect.Proxy;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.postgresql.fastpath.Fastpath;

final class PgBaseConnectionAdapter {
    private PgBaseConnectionAdapter() {
    }

    static org.postgresql.core.BaseConnection create(
        PgConnection connection,
        AtomicReference<Fastpath> fastpathRef
    ) {
        return (org.postgresql.core.BaseConnection) Proxy.newProxyInstance(
            PgBaseConnectionAdapter.class.getClassLoader(),
            new Class<?>[] { org.postgresql.core.BaseConnection.class },
            (proxy, method, args) -> {
                var name = method.getName();
                if (method.getDeclaringClass() == Object.class) {
                    return switch (name) {
                        case "toString" -> "PgBaseConnectionAdapter";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> null;
                    };
                }

                return switch (name) {
                    case "getMetaData" -> connection.proxy().getMetaData();
                    case "createStatement" -> connection.proxy().createStatement();
                    case "getAutoCommit" -> connection.proxy().getAutoCommit();
                    case "commit" -> {
                        connection.proxy().commit();
                        yield null;
                    }
                    case "rollback" -> {
                        connection.proxy().rollback();
                        yield null;
                    }
                    case "getFastpathAPI" -> fastpathRef.get();
                    case "getLogger" -> Logger.getLogger("io.github.hidekatsu_izuno.pglite_jdbc");
                    case "unwrap" -> {
                        var iface = (Class<?>) args[0];
                        if (iface.isInstance(proxy)) {
                            yield proxy;
                        }
                        throw new java.sql.SQLException("Not a wrapper for " + iface.getName());
                    }
                    case "isWrapperFor" -> ((Class<?>) args[0]).isInstance(proxy);
                    default -> {
                        if (method.getReturnType() == void.class) {
                            throw new SQLFeatureNotSupportedException(name + " is not supported");
                        }
                        yield JdbcCompat.defaultReturn(method.getReturnType());
                    }
                };
            }
        );
    }
}
