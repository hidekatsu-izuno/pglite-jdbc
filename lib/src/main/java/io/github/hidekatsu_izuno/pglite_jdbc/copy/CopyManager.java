package io.github.hidekatsu_izuno.pglite_jdbc.copy;

import io.github.hidekatsu_izuno.pglite_jdbc.core.BaseConnection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

public class CopyManager {
    private final BaseConnection connection;

    public CopyManager(BaseConnection connection) {
        this.connection = connection;
    }

    public BaseConnection getConnection() {
        return connection;
    }

    public long copyIn(String sql) throws SQLException {
        throw new SQLFeatureNotSupportedException("COPY IN is not yet supported");
    }

    public long copyOut(String sql) throws SQLException {
        throw new SQLFeatureNotSupportedException("COPY OUT is not yet supported");
    }
}
