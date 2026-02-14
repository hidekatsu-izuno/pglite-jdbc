package io.github.hidekatsu_izuno.pglite_jdbc.largeobject;

import java.sql.SQLFeatureNotSupportedException;

public class LargeObjectManager {
    public long createLO() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("LargeObject API is not supported");
    }
}
