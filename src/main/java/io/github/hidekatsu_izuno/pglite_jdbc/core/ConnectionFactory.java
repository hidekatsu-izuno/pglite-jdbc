package io.github.hidekatsu_izuno.pglite_jdbc.core;

import io.github.hidekatsu_izuno.pglite_jdbc.core.v3.ConnectionFactoryImpl;
import java.sql.SQLException;
import java.util.Properties;

public abstract class ConnectionFactory {
    public static QueryExecutor openConnection(String url, Properties info) throws SQLException {
        return new ConnectionFactoryImpl().openConnectionImpl(url, info);
    }

    public abstract QueryExecutor openConnectionImpl(String url, Properties info) throws SQLException;
}
