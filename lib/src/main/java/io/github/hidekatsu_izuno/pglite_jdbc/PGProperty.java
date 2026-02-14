package io.github.hidekatsu_izuno.pglite_jdbc;

import java.sql.DriverPropertyInfo;
import java.util.Properties;

public enum PGProperty {
    USER("user", "postgres", "Database user"),
    PASSWORD("password", null, "Database password"),
    DATABASE("database", "template1", "Database name"),
    DATA_DIR("dataDir", null, "pglite data directory (e.g. memory://, file:///tmp/db)"),
    DEBUG("debug", null, "Debug level"),
    RELAXED_DURABILITY("relaxedDurability", null, "Enable relaxed durability mode"),
    DEFAULT_ROW_FETCH_SIZE("defaultRowFetchSize", "0", "Default row fetch size"),
    DEFAULT_FETCH_SIZE("defaultFetchSize", "0", "Default row fetch size (canonical)"),
    QUERY_TIMEOUT("queryTimeout", "0", "Statement query timeout in seconds"),
    AUTOSAVE("autosave", "never", "Autosave mode: never/always/conservative"),
    PREFER_QUERY_MODE("preferQueryMode", "extended", "Preferred query mode"),
    CURRENT_SCHEMA("currentSchema", null, "Current schema(search_path)"),
    APPLICATION_NAME("ApplicationName", null, "Application name");

    private final String name;
    private final String defaultValue;
    private final String description;

    PGProperty(String name, String defaultValue, String description) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getOrDefault(Properties properties) {
        if (properties == null) {
            return defaultValue;
        }
        var value = properties.getProperty(name);
        return value != null ? value : defaultValue;
    }

    public Integer getInt(Properties properties) {
        var value = getOrDefault(properties);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Boolean getBooleanObject(Properties properties) {
        var value = getOrDefault(properties);
        if (value == null || value.isBlank()) {
            return null;
        }
        return Boolean.valueOf(value.trim());
    }

    public boolean getBoolean(Properties properties) {
        return Boolean.TRUE.equals(getBooleanObject(properties));
    }

    public void set(Properties properties, String value) {
        if (properties == null) {
            return;
        }
        if (value == null) {
            properties.remove(name);
            return;
        }
        properties.setProperty(name, value);
    }

    public DriverPropertyInfo toDriverPropertyInfo(Properties properties) {
        var info = new DriverPropertyInfo(name, getOrDefault(properties));
        info.description = description;
        return info;
    }
}
