package io.github.hidekatsu_izuno.pglite_jdbc.jdbc;

public record Column(String label, int oid, int typmod, int tableOid, int positionInTable) {
    public Column(String label, int oid) {
        this(label, oid, -1);
    }

    public Column(String label, int oid, int typmod) {
        this(label, oid, typmod, 0, 0);
    }
}
