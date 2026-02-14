package io.github.hidekatsu_izuno.pglite_jdbc.fastpath;

import io.github.hidekatsu_izuno.pglite_jdbc.core.BaseConnection;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import org.postgresql.PGConnection;
import org.postgresql.fastpath.FastpathArg;

public class Fastpath {
    private final org.postgresql.fastpath.Fastpath delegate;

    public Fastpath(BaseConnection connection) throws SQLException {
        if (!(connection instanceof Connection jdbcConnection)) {
            throw new SQLException("BaseConnection is not a JDBC Connection");
        }
        var pgConnection = jdbcConnection.unwrap(PGConnection.class);
        this.delegate = pgConnection.getFastpathAPI();
    }

    public Object fastpath(int functionId, boolean resultType, byte[][] args)
        throws SQLException {
        throw new SQLFeatureNotSupportedException(
            "Fastpath direct function-id API is not supported"
        );
    }

    public byte[] fastpath(String name, FastpathArg[] args) throws SQLException {
        return delegate.fastpath(name, args);
    }
}
