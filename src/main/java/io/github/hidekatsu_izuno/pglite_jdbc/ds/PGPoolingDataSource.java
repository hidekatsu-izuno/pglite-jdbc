package io.github.hidekatsu_izuno.pglite_jdbc.ds;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

public class PGPoolingDataSource extends PGConnectionPoolDataSource implements DataSource {
    private String dataSourceName;
    private int initialConnections;
    private int maxConnections;

    public String getDataSourceName() {
        return dataSourceName;
    }

    public void setDataSourceName(String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }

    public int getInitialConnections() {
        return initialConnections;
    }

    public void setInitialConnections(int initialConnections) {
        this.initialConnections = initialConnections;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return getPooledConnection().getConnection();
    }

    @Override
    public Connection getConnection(String user, String password) throws SQLException {
        return getPooledConnection(user, password).getConnection();
    }

    @Override
    public String getDescription() {
        return "Pooling DataSource from pglite-jdbc";
    }
}
