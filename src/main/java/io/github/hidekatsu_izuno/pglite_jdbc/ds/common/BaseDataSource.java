package io.github.hidekatsu_izuno.pglite_jdbc.ds.common;

import io.github.hidekatsu_izuno.pglite_jdbc.Driver;
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
    private static final String PROP_USER = "user";
    private static final String PROP_PASSWORD = "password";
    private static final String PROP_DATABASE = "database";
    private static final String PROP_DATA_DIR = "dataDir";
    private static final String PROP_DEBUG = "debug";
    private static final String PROP_RELAXED_DURABILITY = "relaxedDurability";

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
        return getOrDefault(properties, PROP_DATABASE, "template1");
    }

    public void setDatabaseName(String databaseName) {
        setPropertyOrRemove(properties, PROP_DATABASE, databaseName);
    }

    public String getDataDir() {
        return getOrDefault(properties, PROP_DATA_DIR, null);
    }

    public void setDataDir(String dataDir) {
        setPropertyOrRemove(properties, PROP_DATA_DIR, dataDir);
    }

    public Integer getDebug() {
        return parseInteger(getOrDefault(properties, PROP_DEBUG, null));
    }

    public void setDebug(Integer debug) {
        setPropertyOrRemove(properties, PROP_DEBUG, debug != null ? String.valueOf(debug) : null);
    }

    public Boolean getRelaxedDurability() {
        return parseBoolean(getOrDefault(properties, PROP_RELAXED_DURABILITY, null));
    }

    public void setRelaxedDurability(Boolean relaxedDurability) {
        setPropertyOrRemove(
            properties,
            PROP_RELAXED_DURABILITY,
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
            out.setProperty(PROP_USER, requestedUser);
        } else if (user != null) {
            out.setProperty(PROP_USER, user);
        }
        if (requestedPassword != null) {
            out.setProperty(PROP_PASSWORD, requestedPassword);
        } else if (password != null) {
            out.setProperty(PROP_PASSWORD, password);
        }
        return out;
    }

    private static String getOrDefault(Properties properties, String key, String defaultValue) {
        if (properties == null) {
            return defaultValue;
        }
        var value = properties.getProperty(key);
        return value != null ? value : defaultValue;
    }

    private static void setPropertyOrRemove(Properties properties, String key, String value) {
        if (value == null) {
            properties.remove(key);
            return;
        }
        properties.setProperty(key, value);
    }

    private static Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Boolean parseBoolean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Boolean.valueOf(value.trim());
    }
}
