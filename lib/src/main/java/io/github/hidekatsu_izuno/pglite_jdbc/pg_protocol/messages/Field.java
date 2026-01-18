package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;

import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.types.Mode;

public class Field {
    public final String name;
    public final int tableID;
    public final int columnID;
    public final int dataTypeID;
    public final int dataTypeSize;
    public final int dataTypeModifier;
    public final Mode format;

    public Field(
        String name,
        int tableID,
        int columnID,
        int dataTypeID,
        int dataTypeSize,
        int dataTypeModifier,
        Mode format
    ) {
        this.name = name;
        this.tableID = tableID;
        this.columnID = columnID;
        this.dataTypeID = dataTypeID;
        this.dataTypeSize = dataTypeSize;
        this.dataTypeModifier = dataTypeModifier;
        this.format = format;
    }
}
