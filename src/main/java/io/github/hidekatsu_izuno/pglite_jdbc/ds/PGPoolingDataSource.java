package io.github.hidekatsu_izuno.pglite_jdbc.ds;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.naming.NamingException;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.StringRefAddr;
import javax.sql.DataSource;

public class PGPoolingDataSource extends PGConnectionPoolDataSource implements DataSource {
    protected static ConcurrentMap<String, PGPoolingDataSource> dataSources =
        new ConcurrentHashMap<>();

    public static PGPoolingDataSource getDataSource(String name) {
        return dataSources.get(name);
    }

    private String dataSourceName;
    private int initialConnections;
    private int maxConnections;

    public String getDataSourceName() {
        return dataSourceName;
    }

    public void setDataSourceName(String dataSourceName) {
        if (this.dataSourceName != null && dataSourceName != null && dataSourceName.equals(this.dataSourceName)) {
            return;
        }
        var previous = dataSources.putIfAbsent(dataSourceName, this);
        if (previous != null) {
            throw new IllegalArgumentException(
                "DataSource with name '" + dataSourceName + "' already exists!"
            );
        }
        if (this.dataSourceName != null) {
            dataSources.remove(this.dataSourceName);
        }
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

    @Override
    public Reference getReference() throws NamingException {
        var reference = super.getReference();
        reference.add(new StringRefAddr("dataSourceName", dataSourceName));
        if (initialConnections > 0) {
            reference.add(new StringRefAddr("initialConnections", Integer.toString(initialConnections)));
        }
        if (maxConnections > 0) {
            reference.add(new StringRefAddr("maxConnections", Integer.toString(maxConnections)));
        }
        return reference;
    }

    @Override
    public void setFromReference(Reference reference) {
        super.setFromReference(reference);
        setProperty("dataSourceName", null);
        setProperty("initialConnections", null);
        setProperty("maxConnections", null);
        var referenceDataSourceName = getReferenceProperty(reference, "dataSourceName");
        if (referenceDataSourceName != null) {
            setDataSourceName(referenceDataSourceName);
        }
        var referenceInitialConnections = getReferenceProperty(reference, "initialConnections");
        if (referenceInitialConnections != null) {
            setInitialConnections(parseInt(referenceInitialConnections));
        }
        var referenceMaxConnections = getReferenceProperty(reference, "maxConnections");
        if (referenceMaxConnections != null) {
            setMaxConnections(parseInt(referenceMaxConnections));
        }
    }

    public void close() {
        if (dataSourceName != null) {
            dataSources.remove(dataSourceName);
        }
    }

    private static String getReferenceProperty(Reference reference, String name) {
        RefAddr address = reference.get(name);
        return address == null ? null : (String) address.getContent();
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
