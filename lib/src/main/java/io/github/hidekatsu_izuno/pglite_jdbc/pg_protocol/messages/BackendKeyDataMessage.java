package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;

public class BackendKeyDataMessage implements BackendMessage {
    public final String name = "backendKeyData";
    public final int length;
    public final int processID;
    public final int secretKey;

    public BackendKeyDataMessage(int length, int processID, int secretKey) {
        this.length = length;
        this.processID = processID;
        this.secretKey = secretKey;
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
