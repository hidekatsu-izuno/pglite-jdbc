package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;

public class ReadyForQueryMessage implements BackendMessage {
    public final String name = "readyForQuery";
    public final int length;
    public final String status;

    public ReadyForQueryMessage(int length, String status) {
        this.length = length;
        this.status = status;
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
