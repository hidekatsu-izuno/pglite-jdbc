package io.github.hidekatsu_izuno.pglite_jdbc.ds.common;

import io.github.hidekatsu_izuno.pglite_jdbc.Driver;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Properties;
import java.util.logging.Logger;
import javax.naming.NamingException;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.StringRefAddr;
import javax.sql.CommonDataSource;

public abstract class BaseDataSource implements CommonDataSource, Referenceable, Serializable {
    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(
        "io.github.hidekatsu_izuno.pglite_jdbc.ds"
    );

    private String url = Driver.URL_PREFIX;
    private String[] serverNames = new String[] { "localhost" };
    private int[] portNumbers = new int[] { 0 };
    private String user;
    private String password;
    private PrintWriter logWriter;
    private int loginTimeout;
    private Properties properties = new Properties();
    private static final String PROP_USER = "user";
    private static final String PROP_PASSWORD = "password";
    private static final String PROP_DATABASE = "database";
    private static final String PROP_DATA_DIR = "dataDir";
    private static final String PROP_DEBUG = "debug";
    private static final String PROP_RELAXED_DURABILITY = "relaxedDurability";
    private static final String PROP_APPLICATION_NAME = "ApplicationName";

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

    @Deprecated
    public String getServerName() {
        return serverNames[0];
    }

    @Deprecated
    public void setServerName(String serverName) {
        setServerNames(new String[] { serverName });
    }

    public String[] getServerNames() {
        return serverNames;
    }

    public void setServerNames(String[] serverNames) {
        if (serverNames == null || serverNames.length == 0) {
            this.serverNames = new String[] { "localhost" };
            return;
        }
        this.serverNames = serverNames.clone();
        for (var i = 0; i < this.serverNames.length; i++) {
            if (this.serverNames[i] == null || this.serverNames[i].isEmpty()) {
                this.serverNames[i] = "localhost";
            }
        }
    }

    @Deprecated
    public int getPortNumber() {
        return portNumbers[0];
    }

    @Deprecated
    public void setPortNumber(int portNumber) {
        setPortNumbers(new int[] { portNumber });
    }

    public int[] getPortNumbers() {
        return portNumbers;
    }

    public void setPortNumbers(int[] portNumbers) {
        if (portNumbers == null || portNumbers.length == 0) {
            this.portNumbers = new int[] { 0 };
            return;
        }
        this.portNumbers = Arrays.copyOf(portNumbers, portNumbers.length);
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

    public String getApplicationName() {
        return getOrDefault(properties, PROP_APPLICATION_NAME, "");
    }

    public void setApplicationName(String applicationName) {
        setPropertyOrRemove(properties, PROP_APPLICATION_NAME, applicationName);
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

    @Override
    public Reference getReference() throws NamingException {
        var reference = new Reference(getClass().getName());
        reference.add(new StringRefAddr("url", url));
        reference.add(new StringRefAddr("serverName", String.join(",", serverNames)));
        reference.add(new StringRefAddr("portNumber", joinPorts()));
        addReference(reference, "databaseName", getDatabaseName());
        addReference(reference, PROP_USER, user);
        addReference(reference, PROP_PASSWORD, password);
        for (var name : properties.stringPropertyNames()) {
            addReference(reference, name, properties.getProperty(name));
        }
        return reference;
    }

    public void setFromReference(Reference reference) {
        var referenceUrl = getReferenceProperty(reference, "url");
        if (referenceUrl != null) {
            setUrl(referenceUrl);
        }
        var serverName = getReferenceProperty(reference, "serverName");
        if (serverName != null) {
            setServerNames(serverName.split(","));
        }
        var portNumber = getReferenceProperty(reference, "portNumber");
        if (portNumber != null) {
            setPortNumbers(parsePorts(portNumber));
        }
        setDatabaseName(getReferenceProperty(reference, "databaseName"));
        setUser(getReferenceProperty(reference, PROP_USER));
        setPassword(getReferenceProperty(reference, PROP_PASSWORD));
        var applicationName = getReferenceProperty(reference, PROP_APPLICATION_NAME);
        if (applicationName != null) {
            setApplicationName(applicationName);
        }
        var dataDir = getReferenceProperty(reference, PROP_DATA_DIR);
        if (dataDir != null) {
            setDataDir(dataDir);
        }
        Enumeration<RefAddr> all = reference.getAll();
        while (all.hasMoreElements()) {
            var address = all.nextElement();
            var type = address.getType();
            if (isReferenceBeanProperty(type)) {
                continue;
            }
            setProperty(type, (String) address.getContent());
        }
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

    protected void writeBaseObject(ObjectOutputStream out) throws IOException {
        out.writeObject(url);
        out.writeObject(serverNames);
        out.writeObject(getDatabaseName());
        out.writeObject(user);
        out.writeObject(password);
        out.writeObject(portNumbers);
        out.writeObject(properties);
    }

    protected void readBaseObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        url = (String) in.readObject();
        serverNames = (String[]) in.readObject();
        var databaseName = (String) in.readObject();
        user = (String) in.readObject();
        password = (String) in.readObject();
        portNumbers = (int[]) in.readObject();
        properties = (Properties) in.readObject();
        if (databaseName != null && !properties.containsKey(PROP_DATABASE)) {
            setDatabaseName(databaseName);
        }
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

    private String joinPorts() {
        var out = new StringBuilder();
        for (var i = 0; i < portNumbers.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(portNumbers[i]);
        }
        return out.toString();
    }

    private static int[] parsePorts(String value) {
        var parts = value.split(",");
        var ports = new int[parts.length];
        for (var i = 0; i < parts.length; i++) {
            try {
                ports[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException ignored) {
                ports[i] = 0;
            }
        }
        return ports;
    }

    private static void addReference(Reference reference, String name, String value) {
        if (value != null) {
            reference.add(new StringRefAddr(name, value));
        }
    }

    private static String getReferenceProperty(Reference reference, String name) {
        RefAddr address = reference.get(name);
        return address == null ? null : (String) address.getContent();
    }

    private static boolean isReferenceBeanProperty(String name) {
        return switch (name) {
            case "url", "serverName", "portNumber", "databaseName", PROP_USER, PROP_PASSWORD -> true;
            default -> false;
        };
    }
}
