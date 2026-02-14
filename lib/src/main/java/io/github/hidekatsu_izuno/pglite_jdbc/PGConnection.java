package io.github.hidekatsu_izuno.pglite_jdbc;

import java.sql.SQLException;

public interface PGConnection {
    PGNotification[] getNotifications() throws SQLException;

    PGNotification[] getNotifications(int timeoutMillis) throws SQLException;

    int getBackendPID();

    void cancelQuery() throws SQLException;

    void setPrepareThreshold(int threshold);

    int getPrepareThreshold();

    void setDefaultFetchSize(int fetchSize) throws SQLException;

    int getDefaultFetchSize();

    void setQueryTimeout(int seconds) throws SQLException;

    int getQueryTimeout();

    String escapeIdentifier(String identifier) throws SQLException;

    String escapeLiteral(String literal) throws SQLException;
}
