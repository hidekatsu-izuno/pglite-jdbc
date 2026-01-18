package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;

public class NotificationResponseMessage implements BackendMessage {
    public final String name = "notification";
    public final int length;
    public final int processId;
    public final String channel;
    public final String payload;

    public NotificationResponseMessage(int length, int processId, String channel, String payload) {
        this.length = length;
        this.processId = processId;
        this.channel = channel;
        this.payload = payload;
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
