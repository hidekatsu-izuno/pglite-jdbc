package io.github.hidekatsu_izuno.pglite_jdbc.core.v3;

import io.github.hidekatsu_izuno.pglite_jdbc.core.ConnectionFactory;
import io.github.hidekatsu_izuno.pglite_jdbc.core.QueryExecutor;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.pglite;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ConnectionFactoryImpl extends ConnectionFactory {
    private static final String PROP_DATA_DIR = "dataDir";
    private static final String PROP_USER = "user";
    private static final String PROP_DATABASE = "database";
    private static final String PROP_DEBUG = "debug";
    private static final String PROP_RELAXED_DURABILITY = "relaxedDurability";
    private static final String PROP_CONNECT_TIMEOUT = "connectTimeout";
    private static final String PROP_STARTUP_RETRIES = "startupRetries";
    private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 180;
    private static final int DEFAULT_STARTUP_RETRIES = 2;
    private static final long STARTUP_CLOSE_TIMEOUT_MILLIS = 5_000L;

    @Override
    public QueryExecutor openConnectionImpl(String url, Properties info) throws SQLException {
        var options = new pglite.PGliteOptions();
        options.dataDir = getOrDefault(info, PROP_DATA_DIR, null);
        options.username = getOrDefault(info, PROP_USER, "postgres");
        options.database = getOrDefault(info, PROP_DATABASE, "template1");
        options.debug = parseInteger(getOrDefault(info, PROP_DEBUG, null));
        options.relaxedDurability = parseBoolean(getOrDefault(info, PROP_RELAXED_DURABILITY, null));
        var timeoutSeconds = parsePositiveInteger(
            getOrDefault(info, PROP_CONNECT_TIMEOUT, null),
            DEFAULT_CONNECT_TIMEOUT_SECONDS
        );
        var startupRetries = parsePositiveInteger(
            getOrDefault(info, PROP_STARTUP_RETRIES, null),
            DEFAULT_STARTUP_RETRIES
        );
        var timeoutMillis = TimeUnit.SECONDS.toMillis(timeoutSeconds);

        SQLException lastError = null;
        for (var attempt = 1; attempt <= startupRetries; attempt++) {
            var db = new pglite(options);
            try {
                db.waitReady().toCompletableFuture().orTimeout(timeoutMillis, TimeUnit.MILLISECONDS).join();
                return new QueryExecutorImpl(db);
            } catch (Throwable error) {
                closeQuietly(db);
                lastError = QueryExecutorImpl.toSqlException(error);
                if (!isTimeout(error) || attempt >= startupRetries) {
                    throw lastError;
                }
            }
        }
        throw lastError != null ? lastError : new SQLException("Connection startup failed");
    }

    private static String getOrDefault(Properties properties, String key, String defaultValue) {
        if (properties == null) {
            return defaultValue;
        }
        var value = properties.getProperty(key);
        return value != null ? value : defaultValue;
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

    private static int parsePositiveInteger(String value, int defaultValue) {
        var parsed = parseInteger(value);
        if (parsed == null || parsed <= 0) {
            return defaultValue;
        }
        return parsed;
    }

    private static boolean isTimeout(Throwable error) {
        var current = error;
        while (current != null) {
            if (current instanceof TimeoutException) {
                return true;
            }
            var message = current.getMessage();
            if (message != null && message.toLowerCase().contains("timed out")) {
                return true;
            }
            if (current.getCause() == current) {
                return false;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void closeQuietly(pglite db) {
        try {
            db.close().toCompletableFuture().orTimeout(
                STARTUP_CLOSE_TIMEOUT_MILLIS,
                TimeUnit.MILLISECONDS
            ).join();
        } catch (Throwable ignored) {
            // Keep original startup failure.
        }
    }
}
