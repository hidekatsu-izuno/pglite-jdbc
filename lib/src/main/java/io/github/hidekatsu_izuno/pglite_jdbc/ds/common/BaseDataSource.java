package io.github.hidekatsu_izuno.pglite_jdbc.ds.common;

import io.github.hidekatsu_izuno.pglite_jdbc.Driver;
import io.github.hidekatsu_izuno.pglite_jdbc.PGProperty;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;
import javax.sql.CommonDataSource;

public abstract class BaseDataSource implements CommonDataSource {
    private static final Logger LOGGER = Logger.getLogger(
        "io.github.hidekatsu_izuno.pglite_jdbc.ds"
    );

    private String url = Driver.URL_PREFIX;
    private String user;
    private String password;
    private PrintWriter logWriter;
    private int loginTimeout;
    private final Properties properties = new Properties();

    static {
        try {
            Class.forName("io.github.hidekatsu_izuno.pglite_jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                "Unable to load io.github.hidekatsu_izuno.pglite_jdbc.Driver",
                e
            );
        }
    }

    public abstract String getDescription();

    public Connection getConnection() throws SQLException {
        return getConnection(user, password);
    }

    public Connection getConnection(String user, String password) throws SQLException {
        var connectionProperties = buildConnectionProperties(user, password);
        return DriverManager.getConnection(url, connectionProperties);
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url != null && !url.isBlank() ? url : Driver.URL_PREFIX;
    }

    public String getURL() {
        return getUrl();
    }

    public void setURL(String url) {
        setUrl(url);
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDatabaseName() {
        return PGProperty.DATABASE.getOrDefault(properties);
    }

    public void setDatabaseName(String databaseName) {
        PGProperty.DATABASE.set(properties, databaseName);
    }

    public String getDataDir() {
        return PGProperty.DATA_DIR.getOrDefault(properties);
    }

    public void setDataDir(String dataDir) {
        PGProperty.DATA_DIR.set(properties, dataDir);
    }

    public Integer getDebug() {
        return PGProperty.DEBUG.getInt(properties);
    }

    public void setDebug(Integer debug) {
        PGProperty.DEBUG.set(properties, debug != null ? String.valueOf(debug) : null);
    }

    public Boolean getRelaxedDurability() {
        return PGProperty.RELAXED_DURABILITY.getBooleanObject(properties);
    }

    public void setRelaxedDurability(Boolean relaxedDurability) {
        PGProperty.RELAXED_DURABILITY.set(
            properties,
            relaxedDurability != null ? String.valueOf(relaxedDurability) : null
        );
    }

    public String getProperty(String name) {
        return properties.getProperty(name);
    }

    public void setProperty(String name, String value) {
        if (name == null || name.isBlank()) {
            return;
        }
        if (value == null) {
            properties.remove(name);
            return;
        }
        properties.setProperty(name, value);
    }

    @Override
    public PrintWriter getLogWriter() {
        return logWriter;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        logWriter = out;
    }

    @Override
    public void setLoginTimeout(int seconds) {
        loginTimeout = seconds;
    }

    @Override
    public int getLoginTimeout() {
        return loginTimeout;
    }

    @Override
    public Logger getParentLogger() {
        return LOGGER;
    }

    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("Not a wrapper for " + iface.getName());
    }

    public boolean isWrapperFor(Class<?> iface) {
        return iface.isInstance(this);
    }

    protected Properties buildConnectionProperties(String requestedUser, String requestedPassword) {
        var out = new Properties();
        for (var name : properties.stringPropertyNames()) {
            out.setProperty(name, properties.getProperty(name));
        }

        if (requestedUser != null) {
            PGProperty.USER.set(out, requestedUser);
        } else if (user != null) {
            PGProperty.USER.set(out, user);
        }
        if (requestedPassword != null) {
            PGProperty.PASSWORD.set(out, requestedPassword);
        } else if (password != null) {
            PGProperty.PASSWORD.set(out, password);
        }
        return out;
    }
}
