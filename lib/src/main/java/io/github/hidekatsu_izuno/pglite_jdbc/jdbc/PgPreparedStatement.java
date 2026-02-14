package io.github.hidekatsu_izuno.pglite_jdbc.jdbc;

import java.sql.PreparedStatement;

public final class PgPreparedStatement {
    private PgPreparedStatement() {
    }

    public static PreparedStatement create(PgConnection connection, String sql) {
        return (PreparedStatement) PgStatement.create(connection, sql);
    }
}
