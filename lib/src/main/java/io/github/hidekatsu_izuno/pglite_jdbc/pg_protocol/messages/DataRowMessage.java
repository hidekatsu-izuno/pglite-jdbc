package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;

public class DataRowMessage implements BackendMessage {
    public final int length;
    public final String[] fields;
    public final int fieldCount;
    public final String name = "dataRow";

    public DataRowMessage(int length, String[] fields) {
        this.length = length;
        this.fields = fields;
        this.fieldCount = fields.length;
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
