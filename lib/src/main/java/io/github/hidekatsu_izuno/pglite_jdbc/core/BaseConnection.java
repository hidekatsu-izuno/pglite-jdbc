package io.github.hidekatsu_izuno.pglite_jdbc.core;

import io.github.hidekatsu_izuno.pglite_jdbc.PGConnection;
import java.sql.Connection;

public interface BaseConnection extends Connection, PGConnection {
    QueryExecutor getQueryExecutor();
}
