package io.github.hidekatsu_izuno.pglite_jdbc.core.v3;

import io.github.hidekatsu_izuno.pglite_jdbc.core.QueryExecutor;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.pglite;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;

public final class QueryExecutorImpl implements QueryExecutor {
    private final pglite db;
    private boolean closed;

    public QueryExecutorImpl(pglite db) {
        this.db = db;
    }

    @Override
    public pglite getDatabase() {
        return db;
    }

    @Override
    @SuppressWarnings("unchecked")
    public interface_.Results<Map<String, Object>> query(String sql, Object[] params) throws SQLException {
        return query(sql, params, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public interface_.Results<Map<String, Object>> query(
        String sql,
        Object[] params,
        Consumer<messages.NoticeMessage> onNotice
    ) throws SQLException {
        ensureOpen();
        try {
            var options = onNotice == null
                ? null
                : new interface_.QueryOptions(null, null, null, null, onNotice, null);
            return (interface_.Results<Map<String, Object>>) (interface_.Results<?>) db.query(sql, params, options).join();
        } catch (Throwable error) {
            throw toSqlException(error);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public interface_.Results<List<Object>> queryArray(String sql, Object[] params) throws SQLException {
        return queryArray(sql, params, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public interface_.Results<List<Object>> queryArray(
        String sql,
        Object[] params,
        Consumer<messages.NoticeMessage> onNotice
    ) throws SQLException {
        ensureOpen();
        try {
            var options = new interface_.QueryOptions(
                interface_.RowMode.array,
                null,
                null,
                null,
                onNotice,
                null
            );
            return (interface_.Results<List<Object>>) (interface_.Results<?>) db.query(sql, params, options).join();
        } catch (Throwable error) {
            throw toSqlException(error);
        }
    }

    @Override
    public List<interface_.Results<Map<String, Object>>> exec(String sql) throws SQLException {
        return exec(sql, null);
    }

    @Override
    public List<interface_.Results<Map<String, Object>>> exec(
        String sql,
        Consumer<messages.NoticeMessage> onNotice
    ) throws SQLException {
        ensureOpen();
        try {
            var options = onNotice == null
                ? null
                : new interface_.QueryOptions(null, null, null, null, onNotice, null);
            return db.exec(sql, options).join();
        } catch (Throwable error) {
            throw toSqlException(error);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<interface_.Results<List<Object>>> execArray(String sql) throws SQLException {
        return execArray(sql, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<interface_.Results<List<Object>>> execArray(
        String sql,
        Consumer<messages.NoticeMessage> onNotice
    ) throws SQLException {
        ensureOpen();
        try {
            var options = new interface_.QueryOptions(
                interface_.RowMode.array,
                null,
                null,
                null,
                onNotice,
                null
            );
            return (List<interface_.Results<List<Object>>>) (List<?>) db.exec(sql, options).join();
        } catch (Throwable error) {
            throw toSqlException(error);
        }
    }

    @Override
    public interface_.DescribeQueryResult describe(String sql) throws SQLException {
        ensureOpen();
        try {
            return db.describeQuery(sql).join();
        } catch (Throwable error) {
            throw toSqlException(error);
        }
    }

    @Override
    public void close() throws SQLException {
        if (closed) {
            return;
        }
        try {
            db.close().join();
        } catch (Throwable error) {
            throw toSqlException(error);
        } finally {
            closed = true;
        }
    }

    private void ensureOpen() throws SQLException {
        if (closed) {
            throw new SQLException("Connection is closed");
        }
    }

    public static SQLException toSqlException(Throwable error) {
        var cause = unwrap(error);
        if (cause instanceof SQLException sqlException) {
            return sqlException;
        }
        if (cause instanceof messages.DatabaseError databaseError) {
            var severity = databaseError.severity();
            var prefix = severity == null || severity.isBlank() ? "" : (severity + ": ");
            var message = prefix + databaseError.message();
            return new PSQLException(
                message,
                resolveState(databaseError.code()),
                databaseError
            );
        }
        var message = cause.getMessage() != null ? cause.getMessage() : cause.toString();
        return new PSQLException(message, PSQLState.UNEXPECTED_ERROR, cause);
    }

    private static PSQLState resolveState(String stateCode) {
        if (stateCode == null || stateCode.isBlank()) {
            return PSQLState.UNEXPECTED_ERROR;
        }
        for (var candidate : PSQLState.values()) {
            if (stateCode.equals(candidate.getState())) {
                return candidate;
            }
        }
        return PSQLState.UNEXPECTED_ERROR;
    }

    private static Throwable unwrap(Throwable error) {
        var current = error;
        while (current instanceof RuntimeException runtime && runtime.getCause() != null) {
            if (runtime == runtime.getCause()) {
                break;
            }
            current = runtime.getCause();
        }
        return current;
    }
}
