package io.github.hidekatsu_izuno.pglite_jdbc.core.v3;

import io.github.hidekatsu_izuno.pglite_jdbc.core.QueryExecutor;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.pglite;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
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
        ensureOpen();
        try {
            return (interface_.Results<Map<String, Object>>) (interface_.Results<?>) db.query(sql, params, null).join();
        } catch (Throwable error) {
            throw toSqlException(error);
        }
    }

    @Override
    public List<interface_.Results<Map<String, Object>>> exec(String sql) throws SQLException {
        ensureOpen();
        try {
            return db.exec(sql, null).join();
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
