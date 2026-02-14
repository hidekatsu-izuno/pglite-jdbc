package io.github.hidekatsu_izuno.pglite_jdbc.copy;

import io.github.hidekatsu_izuno.pglite_jdbc.core.BaseConnection;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.sql.Connection;
import java.sql.SQLException;
import org.postgresql.PGConnection;

public class CopyManager {
    private final BaseConnection connection;
    private final org.postgresql.copy.CopyManager delegate;

    public CopyManager(BaseConnection connection) throws SQLException {
        this.connection = connection;
        this.delegate = resolveDelegate(connection);
    }

    public BaseConnection getConnection() {
        return connection;
    }

    public long copyIn(String sql) throws SQLException {
        var op = delegate.copyIn(sql);
        return op.endCopy();
    }

    public long copyOut(String sql) throws SQLException {
        var op = delegate.copyOut(sql);
        while (op.readFromCopy() != null) {
            // Consume all rows.
        }
        return op.getHandledRowCount();
    }

    public long copyIn(String sql, InputStream from) throws Exception {
        return delegate.copyIn(sql, from);
    }

    public long copyIn(String sql, Reader from) throws Exception {
        return delegate.copyIn(sql, from);
    }

    public long copyOut(String sql, OutputStream to) throws Exception {
        return delegate.copyOut(sql, to);
    }

    public long copyOut(String sql, Writer to) throws Exception {
        return delegate.copyOut(sql, to);
    }

    private static org.postgresql.copy.CopyManager resolveDelegate(BaseConnection connection)
        throws SQLException {
        if (!(connection instanceof Connection jdbcConnection)) {
            throw new SQLException("BaseConnection is not a JDBC Connection");
        }
        var pgConnection = jdbcConnection.unwrap(PGConnection.class);
        return pgConnection.getCopyAPI();
    }
}
