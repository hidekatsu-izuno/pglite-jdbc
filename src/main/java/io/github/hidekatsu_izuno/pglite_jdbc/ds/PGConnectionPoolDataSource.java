package io.github.hidekatsu_izuno.pglite_jdbc.ds;

import io.github.hidekatsu_izuno.pglite_jdbc.ds.common.BaseDataSource;
import java.sql.SQLException;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.PooledConnection;

public class PGConnectionPoolDataSource extends BaseDataSource implements ConnectionPoolDataSource {
    private boolean defaultAutoCommit = true;

    @Override
    public PooledConnection getPooledConnection() throws SQLException {
        return new PGPooledConnection(getConnection(), defaultAutoCommit);
    }

    @Override
    public PooledConnection getPooledConnection(String user, String password) throws SQLException {
        return new PGPooledConnection(getConnection(user, password), defaultAutoCommit);
    }

    public boolean isDefaultAutoCommit() {
        return defaultAutoCommit;
    }

    public void setDefaultAutoCommit(boolean defaultAutoCommit) {
        this.defaultAutoCommit = defaultAutoCommit;
    }

    @Override
    public String getDescription() {
        return "ConnectionPoolDataSource from pglite-jdbc";
    }
}
