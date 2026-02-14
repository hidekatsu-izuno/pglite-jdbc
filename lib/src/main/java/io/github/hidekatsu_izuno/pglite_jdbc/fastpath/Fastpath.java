package io.github.hidekatsu_izuno.pglite_jdbc.fastpath;

import java.sql.SQLFeatureNotSupportedException;

public class Fastpath {
    public Object fastpath(int functionId, boolean resultType, byte[][] args)
        throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Fastpath is not supported");
    }
}
