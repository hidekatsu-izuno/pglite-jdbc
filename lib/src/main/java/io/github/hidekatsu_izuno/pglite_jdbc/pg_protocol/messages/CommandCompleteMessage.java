package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;

public class CommandCompleteMessage implements BackendMessage {
    public final String name = "commandComplete";
    public final int length;
    public final String text;

    public CommandCompleteMessage(int length, String text) {
        this.length = length;
        this.text = text;
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
