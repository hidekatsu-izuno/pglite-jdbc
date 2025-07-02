package io.github.hidekatsu_izuno.pglite_jdbc;

import java.sql.*;
import java.util.Properties;
import java.util.logging.Logger;

public class PGliteDriver implements Driver {
    
    public static final String URL_PREFIX = "jdbc:pglite:";
    
    // Register the driver when the class is loaded
    static {
        try {
            DriverManager.registerDriver(new PGliteDriver());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to register PGliteDriver", e);
        }
    }
    
    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return null;
        }
        
        // Parse the URL to extract database configuration
        String databasePath = url.substring(URL_PREFIX.length());
        if (databasePath.isEmpty()) {
            databasePath = ":memory:"; // Default to in-memory database
        }
        
        return new PGliteConnection(databasePath, info);
    }
    
    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return url != null && url.startsWith(URL_PREFIX);
    }
    
    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return new DriverPropertyInfo[0]; // No additional properties for now
    }
    
    @Override
    public int getMajorVersion() {
        return 0;
    }
    
    @Override
    public int getMinorVersion() {
        return 1;
    }
    
    @Override
    public boolean jdbcCompliant() {
        return false; // We're not fully JDBC compliant yet
    }
    
    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("getParentLogger not supported");
    }
}