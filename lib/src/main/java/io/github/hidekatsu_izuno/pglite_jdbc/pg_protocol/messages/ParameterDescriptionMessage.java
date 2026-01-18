package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;

public class ParameterDescriptionMessage implements BackendMessage {
    public final String name = "parameterDescription";
    public final int length;
    public final int parameterCount;
    public final int[] dataTypeIDs;

    public ParameterDescriptionMessage(int length, int parameterCount) {
        this.length = length;
        this.parameterCount = parameterCount;
        this.dataTypeIDs = new int[this.parameterCount];
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
