package io.github.hidekatsu_izuno.pglite_jdbc.jdbc;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;
import org.postgresql.fastpath.Fastpath;

final class PgLargeObjectManagerAdapter extends org.postgresql.largeobject.LargeObjectManager {
    private PgLargeObjectManagerAdapter(org.postgresql.core.BaseConnection connection)
        throws SQLException {
        super(connection);
    }

    static org.postgresql.largeobject.LargeObjectManager create(PgConnection connection)
        throws SQLException {
        var fastpathHolder = new AtomicReference<Fastpath>();
        var baseConnection = PgBaseConnectionAdapter.create(connection, fastpathHolder);
        fastpathHolder.set(new PgFastpathAdapter(baseConnection, connection));
        return new PgLargeObjectManagerAdapter(baseConnection);
    }
}
