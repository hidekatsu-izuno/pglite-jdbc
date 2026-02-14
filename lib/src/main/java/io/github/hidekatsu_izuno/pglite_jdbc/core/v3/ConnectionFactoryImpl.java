package io.github.hidekatsu_izuno.pglite_jdbc.core.v3;

import io.github.hidekatsu_izuno.pglite_jdbc.core.ConnectionFactory;
import io.github.hidekatsu_izuno.pglite_jdbc.core.QueryExecutor;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.pglite;
import java.sql.SQLException;
import java.util.Properties;

public final class ConnectionFactoryImpl extends ConnectionFactory {
    private static final String PROP_DATA_DIR = "dataDir";
    private static final String PROP_USER = "user";
    private static final String PROP_DATABASE = "database";
    private static final String PROP_DEBUG = "debug";
    private static final String PROP_RELAXED_DURABILITY = "relaxedDurability";

    @Override
    public QueryExecutor openConnectionImpl(String url, Properties info) throws SQLException {
        var options = new pglite.PGliteOptions();
        options.dataDir = getOrDefault(info, PROP_DATA_DIR, null);
        options.username = getOrDefault(info, PROP_USER, "postgres");
        options.database = getOrDefault(info, PROP_DATABASE, "template1");
        options.debug = parseInteger(getOrDefault(info, PROP_DEBUG, null));
        options.relaxedDurability = parseBoolean(getOrDefault(info, PROP_RELAXED_DURABILITY, null));

        var db = new pglite(options);
        try {
            db.waitReady().join();
        } catch (Throwable error) {
            try {
                db.close().join();
            } catch (Throwable ignored) {
                // Keep original startup failure.
            }
            throw QueryExecutorImpl.toSqlException(error);
        }
        return new QueryExecutorImpl(db);
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
}
