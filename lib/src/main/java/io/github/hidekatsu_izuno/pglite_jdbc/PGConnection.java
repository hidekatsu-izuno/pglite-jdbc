package io.github.hidekatsu_izuno.pglite_jdbc;

import java.sql.SQLException;

public interface PGConnection extends org.postgresql.PGConnection {
    java.sql.Array createArrayOf(String typeName, Object elements) throws SQLException;

    PGNotification[] getNotifications() throws SQLException;

    PGNotification[] getNotifications(int timeoutMillis) throws SQLException;

    org.postgresql.copy.CopyManager getCopyAPI() throws SQLException;

    org.postgresql.largeobject.LargeObjectManager getLargeObjectAPI() throws SQLException;

    org.postgresql.fastpath.Fastpath getFastpathAPI() throws SQLException;

    int getBackendPID();

    void cancelQuery() throws SQLException;

    void setPrepareThreshold(int threshold);

    int getPrepareThreshold();

    void setDefaultFetchSize(int fetchSize) throws SQLException;

    int getDefaultFetchSize();

    void setQueryTimeout(int seconds) throws SQLException;

    int getQueryTimeout();

    org.postgresql.jdbc.PreferQueryMode getPreferQueryMode();

    org.postgresql.jdbc.AutoSave getAutosave();

    void setAutosave(org.postgresql.jdbc.AutoSave autoSave);

    String escapeIdentifier(String identifier) throws SQLException;

    String escapeLiteral(String literal) throws SQLException;
}
