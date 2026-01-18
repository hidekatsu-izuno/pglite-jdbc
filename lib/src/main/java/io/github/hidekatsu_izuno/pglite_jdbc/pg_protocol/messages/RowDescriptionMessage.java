package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;

public class RowDescriptionMessage implements BackendMessage {
    public final String name = "rowDescription";
    public final int length;
    public final int fieldCount;
    public final Field[] fields;

    public RowDescriptionMessage(int length, int fieldCount) {
        this.length = length;
        this.fieldCount = fieldCount;
        this.fields = new Field[this.fieldCount];
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public int getLength() {
        return this.length;
    }
}
