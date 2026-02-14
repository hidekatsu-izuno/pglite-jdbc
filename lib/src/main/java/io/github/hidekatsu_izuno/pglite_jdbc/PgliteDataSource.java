package io.github.hidekatsu_izuno.pglite_jdbc;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;
import javax.sql.DataSource;

public class PgliteDataSource implements DataSource {
    private String url = "jdbc:pglite:";
    private String user;
    private String password;
    private PrintWriter logWriter;
    private int loginTimeout;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
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

    @Override
    public Connection getConnection() throws SQLException {
        return getConnection(user, password);
    }

    @Override
    public Connection getConnection(String username, String pwd) throws SQLException {
        var props = new Properties();
        if (username != null) {
            props.setProperty("user", username);
        }
        if (pwd != null) {
            props.setProperty("password", pwd);
        }
        var driver = new io.github.hidekatsu_izuno.pglite_jdbc.Driver();
        var connection = driver.connect(url, props);
        if (connection == null) {
            throw new SQLException("URL is not accepted by pglite driver: " + url);
        }
        return connection;
    }

    @Override
    public PrintWriter getLogWriter() {
        return logWriter;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        this.logWriter = out;
    }

    @Override
    public void setLoginTimeout(int seconds) {
        this.loginTimeout = seconds;
    }

    @Override
    public int getLoginTimeout() {
        return loginTimeout;
    }

    @Override
    public Logger getParentLogger() {
        return Logger.getLogger("io.github.hidekatsu_izuno.pglite_jdbc");
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("Not a wrapper for " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isInstance(this);
    }
}
