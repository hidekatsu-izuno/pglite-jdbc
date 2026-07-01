package io.github.hidekatsu_izuno.pglite_jdbc.jdbc;

import java.sql.SQLException;
import java.sql.Savepoint;

final class PgSavepoint implements Savepoint {
    private final int id;
    private final String name;
    private boolean released;

    private PgSavepoint(int id, String name) {
        this.id = id;
        this.name = name;
    }

    static PgSavepoint unnamed(int id) {
        return new PgSavepoint(id, null);
    }

    static PgSavepoint named(int id, String name) {
        return new PgSavepoint(id, name);
    }

    @Override
    public int getSavepointId() throws SQLException {
        if (name != null) {
            throw new SQLException("Named savepoints do not have an id");
        }
        return id;
    }

    @Override
    public String getSavepointName() throws SQLException {
        if (name == null) {
            throw new SQLException("Unnamed savepoints do not have a name");
        }
        return name;
    }

    String sqlIdentifier() {
        return name != null ? name : "JDBC_SAVEPOINT_" + id;
    }

    void markReleased() {
        released = true;
    }

    void ensureActive() throws SQLException {
        if (released) {
            throw new SQLException("Savepoint has already been released");
        }
    }
}
