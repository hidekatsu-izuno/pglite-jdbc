package io.github.hidekatsu_izuno.pglite_jdbc.ds;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.PooledConnection;
import javax.sql.StatementEventListener;

public class PGPooledConnection implements PooledConnection {
    private final Connection physicalConnection;
    private final List<ConnectionEventListener> connectionListeners = new CopyOnWriteArrayList<>();
    private final List<StatementEventListener> statementListeners = new CopyOnWriteArrayList<>();
    private volatile Connection logicalConnection;
    private volatile boolean closed;

    public PGPooledConnection(Connection physicalConnection) {
        this.physicalConnection = physicalConnection;
    }

    @Override
    public Connection getConnection() throws SQLException {
        ensureOpen();
        closeLogicalConnectionIfNeeded();
        logicalConnection = wrapLogicalConnection();
        return logicalConnection;
    }

    @Override
    public void close() throws SQLException {
        if (closed) {
            return;
        }
        closed = true;

        SQLException failure = null;
        try {
            closeLogicalConnectionIfNeeded();
        } catch (SQLException e) {
            failure = e;
        }
        try {
            physicalConnection.close();
        } catch (SQLException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public void addConnectionEventListener(ConnectionEventListener listener) {
        if (listener != null) {
            connectionListeners.add(listener);
        }
    }

    @Override
    public void removeConnectionEventListener(ConnectionEventListener listener) {
        connectionListeners.remove(listener);
    }

    @Override
    public void addStatementEventListener(StatementEventListener listener) {
        if (listener != null) {
            statementListeners.add(listener);
        }
    }

    @Override
    public void removeStatementEventListener(StatementEventListener listener) {
        statementListeners.remove(listener);
    }

    private Connection wrapLogicalConnection() {
        var handler = new LogicalConnectionHandler();

        return (Connection) Proxy.newProxyInstance(
            PGPooledConnection.class.getClassLoader(),
            new Class<?>[] { Connection.class },
            handler
        );
    }

    private void ensureOpen() throws SQLException {
        if (closed) {
            throw new SQLException("PooledConnection is closed");
        }
        if (physicalConnection.isClosed()) {
            throw new SQLException("Physical connection is closed");
        }
    }

    private void closeLogicalConnectionIfNeeded() throws SQLException {
        var existing = logicalConnection;
        if (existing != null && !existing.isClosed()) {
            existing.close();
        }
        logicalConnection = null;
    }

    private void fireConnectionClosed() {
        var event = new ConnectionEvent(this);
        for (var listener : connectionListeners) {
            listener.connectionClosed(event);
        }
    }

    private void fireConnectionError(SQLException e) {
        var event = new ConnectionEvent(this, e);
        for (var listener : connectionListeners) {
            listener.connectionErrorOccurred(event);
        }
    }

    private final class LogicalConnectionHandler implements InvocationHandler {
        private boolean logicalClosed;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            var name = method.getName();
            if (method.getDeclaringClass() == Object.class) {
                return switch (name) {
                    case "toString" -> "PGPooledConnection.LogicalConnection";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> method.invoke(this, args);
                };
            }

            if ("close".equals(name)) {
                if (!logicalClosed) {
                    logicalClosed = true;
                    logicalConnection = null;
                    fireConnectionClosed();
                }
                return null;
            }

            if (logicalClosed) {
                throw new SQLException("Connection is closed");
            }

            try {
                return method.invoke(physicalConnection, args);
            } catch (InvocationTargetException e) {
                var cause = e.getCause();
                if (cause instanceof SQLException sqlException) {
                    fireConnectionError(sqlException);
                    throw sqlException;
                }
                throw cause;
            }
        }
    }
}
