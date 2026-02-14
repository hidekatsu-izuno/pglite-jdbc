package io.github.hidekatsu_izuno.pglite_jdbc;

import java.sql.SQLException;

public interface PGStatement extends org.postgresql.PGStatement {
    long DATE_POSITIVE_INFINITY = 9223372036825200000L;
    long DATE_NEGATIVE_INFINITY = -9223372036832400000L;

    long getLastOID() throws SQLException;

    void setPrepareThreshold(int threshold) throws SQLException;

    int getPrepareThreshold();

    void setAdaptiveFetch(boolean adaptiveFetch);

    boolean getAdaptiveFetch();
}
