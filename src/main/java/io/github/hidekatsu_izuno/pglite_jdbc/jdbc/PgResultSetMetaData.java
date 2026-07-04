package io.github.hidekatsu_izuno.pglite_jdbc.jdbc;

import java.sql.ResultSetMetaData;
import java.util.List;

final class PgResultSetMetaData {
    private PgResultSetMetaData() {
    }

    static ResultSetMetaData create(List<Column> columns) {
        return create(null, columns);
    }

    static ResultSetMetaData create(PgConnectionHandler connection, List<Column> columns) {
        return new org.postgresql.jdbc.PgResultSetMetaData(
            connection == null ? null : connection.baseConnection(),
            fields(columns)
        );
    }

    private static org.postgresql.core.Field[] fields(List<Column> columns) {
        if (columns == null || columns.isEmpty()) {
            return new org.postgresql.core.Field[0];
        }
        var fields = new org.postgresql.core.Field[columns.size()];
        for (var i = 0; i < columns.size(); i++) {
            var column = columns.get(i);
            fields[i] = new org.postgresql.core.Field(
                column.label(),
                column.oid(),
                0,
                column.typmod(),
                column.tableOid(),
                column.positionInTable()
            );
        }
        return fields;
    }
}
