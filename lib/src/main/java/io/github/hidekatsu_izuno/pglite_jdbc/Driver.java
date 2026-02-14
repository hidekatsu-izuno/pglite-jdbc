package io.github.hidekatsu_izuno.pglite_jdbc;

import io.github.hidekatsu_izuno.pglite_jdbc.core.ConnectionFactory;
import io.github.hidekatsu_izuno.pglite_jdbc.core.QueryExecutor;
import io.github.hidekatsu_izuno.pglite_jdbc.jdbc.PgConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;

public class Driver implements java.sql.Driver {
    public static final String URL_PREFIX = "jdbc:pglite:";
    private static final Logger PARENT_LOGGER = Logger.getLogger(
        "io.github.hidekatsu_izuno.pglite_jdbc"
    );
    private static volatile Driver registeredDriver;

    static {
        try {
            register();
        } catch (SQLException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static void register() throws SQLException {
        if (registeredDriver != null) {
            return;
        }
        synchronized (Driver.class) {
            if (registeredDriver == null) {
                var driver = new Driver();
                DriverManager.registerDriver(driver);
                registeredDriver = driver;
            }
        }
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (url == null) {
            throw new SQLException("url is null");
        }
        if (!acceptsURL(url)) {
            return null;
        }

        var properties = mergeUrlProperties(url, info);
        var user = PGProperty.USER.getOrDefault(properties);
        var database = PGProperty.DATABASE.getOrDefault(properties);
        QueryExecutor queryExecutor = ConnectionFactory.openConnection(url, properties);
        return PgConnection.create(queryExecutor, url, user, database, properties);
    }

    @Override
    public boolean acceptsURL(String url) {
        return url != null && url.startsWith(URL_PREFIX);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        var properties = info != null ? new Properties(info) : new Properties();
        if (url != null && acceptsURL(url)) {
            properties = mergeUrlProperties(url, properties);
        }
        return new DriverPropertyInfo[] {
            PGProperty.USER.toDriverPropertyInfo(properties),
            PGProperty.PASSWORD.toDriverPropertyInfo(properties),
            PGProperty.DATABASE.toDriverPropertyInfo(properties),
            PGProperty.DATA_DIR.toDriverPropertyInfo(properties),
            PGProperty.DEBUG.toDriverPropertyInfo(properties),
            PGProperty.RELAXED_DURABILITY.toDriverPropertyInfo(properties),
            PGProperty.DEFAULT_ROW_FETCH_SIZE.toDriverPropertyInfo(properties),
            PGProperty.QUERY_TIMEOUT.toDriverPropertyInfo(properties),
            PGProperty.AUTOSAVE.toDriverPropertyInfo(properties),
            PGProperty.PREFER_QUERY_MODE.toDriverPropertyInfo(properties),
            PGProperty.CURRENT_SCHEMA.toDriverPropertyInfo(properties),
            PGProperty.APPLICATION_NAME.toDriverPropertyInfo(properties),
        };
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
        return false;
    }

    @Override
    public Logger getParentLogger() {
        return PARENT_LOGGER;
    }

    static Properties mergeUrlProperties(String url, Properties info) throws SQLException {
        if (url == null || !url.startsWith(URL_PREFIX)) {
            throw new SQLException("URL is not accepted by pglite driver: " + url);
        }

        var properties = new Properties();
        if (info != null) {
            for (var name : info.stringPropertyNames()) {
                properties.setProperty(name, info.getProperty(name));
            }
        }

        var body = url.substring(URL_PREFIX.length());
        var queryIndex = body.indexOf('?');
        var rawPath = queryIndex >= 0 ? body.substring(0, queryIndex).trim() : body.trim();
        if (!rawPath.isEmpty()) {
            PGProperty.DATA_DIR.set(properties, rawPath);
        }

        if (queryIndex >= 0 && queryIndex + 1 < body.length()) {
            parseQueryString(body.substring(queryIndex + 1), properties);
        }
        normalizePropertyAliases(properties);

        applyDefaults(properties, Map.of(
            PGProperty.USER, "postgres",
            PGProperty.DATABASE, "template1"
        ));
        return properties;
    }

    private static void normalizePropertyAliases(Properties properties) throws SQLException {
        alias(properties, "defaultRowFetchSize", "defaultFetchSize");
        alias(properties, "ApplicationName", "applicationName");
        validateEnumProperty(
            properties,
            "autosave",
            new String[] { "always", "never", "conservative" }
        );
    }

    private static void alias(Properties properties, String from, String to) {
        var fromValue = properties.getProperty(from);
        if ((properties.getProperty(to) == null || properties.getProperty(to).isBlank()) &&
            fromValue != null &&
            !fromValue.isBlank()) {
            properties.setProperty(to, fromValue);
        }
    }

    private static void validateEnumProperty(
        Properties properties,
        String property,
        String[] allowedValues
    ) throws SQLException {
        var value = properties.getProperty(property);
        if (value == null || value.isBlank()) {
            return;
        }
        for (var allowed : allowedValues) {
            if (allowed.equalsIgnoreCase(value)) {
                return;
            }
        }
        throw new PSQLException(
            "Invalid value for property '" + property + "': " + value,
            PSQLState.INVALID_PARAMETER_VALUE
        );
    }

    private static void applyDefaults(
        Properties properties,
        Map<PGProperty, String> defaults
    ) {
        for (var entry : defaults.entrySet()) {
            var key = entry.getKey().getName();
            var value = properties.getProperty(key);
            if (value == null || value.isBlank()) {
                properties.setProperty(key, entry.getValue());
            }
        }
    }

    private static void parseQueryString(String queryString, Properties properties)
        throws SQLException {
        for (var pair : queryString.split("&")) {
            if (pair == null || pair.isBlank()) {
                continue;
            }
            var separator = pair.indexOf('=');
            var key = separator < 0 ? decodeUrlPart(pair) : decodeUrlPart(pair.substring(0, separator));
            if (key.isBlank()) {
                continue;
            }
            var value = separator < 0 ? "" : decodeUrlPart(pair.substring(separator + 1));
            properties.setProperty(key, value);
        }
    }

    private static String decodeUrlPart(String value) throws SQLException {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new SQLException("Invalid URL encoding in JDBC URL", e);
        }
    }
}
