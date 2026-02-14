package io.github.hidekatsu_izuno.pglite_jdbc.jdbc;

import java.sql.SQLException;

final class PgLargeObjectManagerAdapter extends org.postgresql.largeobject.LargeObjectManager {
    private PgLargeObjectManagerAdapter(org.postgresql.core.BaseConnection connection)
        throws SQLException {
        super(connection);
    }

    static org.postgresql.largeobject.LargeObjectManager create(PgConnection connection)
        throws SQLException {
        return new PgLargeObjectManagerAdapter((org.postgresql.core.BaseConnection)connection.proxy());
    }
}
