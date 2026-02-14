package io.github.hidekatsu_izuno.pglite_jdbc.largeobject;

import io.github.hidekatsu_izuno.pglite_jdbc.core.BaseConnection;
import java.sql.Connection;
import java.sql.SQLException;
import org.postgresql.PGConnection;

public class LargeObjectManager {
    private final BaseConnection connection;
    private final org.postgresql.largeobject.LargeObjectManager delegate;

    public LargeObjectManager(BaseConnection connection) throws SQLException {
        this.connection = connection;
        this.delegate = resolveDelegate(connection);
    }

    public BaseConnection getConnection() {
        return connection;
    }

    public long createLO() throws SQLException {
        return delegate.createLO();
    }

    public org.postgresql.largeobject.LargeObject open(long oid) throws SQLException {
        return delegate.open(oid);
    }

    public void delete(long oid) throws SQLException {
        delegate.delete(oid);
    }

    private static org.postgresql.largeobject.LargeObjectManager resolveDelegate(
        BaseConnection connection
    ) throws SQLException {
        if (!(connection instanceof Connection jdbcConnection)) {
            throw new SQLException("BaseConnection is not a JDBC Connection");
        }
        var pgConnection = jdbcConnection.unwrap(PGConnection.class);
        return pgConnection.getLargeObjectAPI();
    }
}
