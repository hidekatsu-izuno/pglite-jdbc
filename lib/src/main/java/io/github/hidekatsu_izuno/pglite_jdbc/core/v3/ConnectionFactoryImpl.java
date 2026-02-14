package io.github.hidekatsu_izuno.pglite_jdbc.core.v3;

import io.github.hidekatsu_izuno.pglite_jdbc.PGProperty;
import io.github.hidekatsu_izuno.pglite_jdbc.core.ConnectionFactory;
import io.github.hidekatsu_izuno.pglite_jdbc.core.QueryExecutor;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.pglite;
import java.sql.SQLException;
import java.util.Properties;

public final class ConnectionFactoryImpl extends ConnectionFactory {
    @Override
    public QueryExecutor openConnectionImpl(String url, Properties info) throws SQLException {
        var options = new pglite.PGliteOptions();
        options.dataDir = PGProperty.DATA_DIR.getOrDefault(info);
        options.username = PGProperty.USER.getOrDefault(info);
        options.database = PGProperty.DATABASE.getOrDefault(info);
        options.debug = PGProperty.DEBUG.getInt(info);
        options.relaxedDurability = PGProperty.RELAXED_DURABILITY.getBooleanObject(info);

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
}
