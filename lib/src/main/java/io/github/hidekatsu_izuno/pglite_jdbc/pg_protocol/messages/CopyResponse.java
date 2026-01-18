package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;

public class CopyResponse implements BackendMessage {
    public final int length;
    public final String name;
    public final boolean binary;
    public final int[] columnTypes;

    public CopyResponse(int length, String name, boolean binary, int columnCount) {
        this.length = length;
        this.name = name;
        this.binary = binary;
        this.columnTypes = new int[columnCount];
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
