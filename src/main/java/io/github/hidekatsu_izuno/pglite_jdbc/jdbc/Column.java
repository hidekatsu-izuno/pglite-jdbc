package io.github.hidekatsu_izuno.pglite_jdbc.jdbc;

public record Column(String label, int oid, int typmod) {
    public Column(String label, int oid) {
        this(label, oid, -1);
    }
}
