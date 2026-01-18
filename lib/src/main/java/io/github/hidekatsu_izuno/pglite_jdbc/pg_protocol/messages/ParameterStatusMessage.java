package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;

public class ParameterStatusMessage implements BackendMessage {
    public final String name = "parameterStatus";
    public final int length;
    public final String parameterName;
    public final String parameterValue;

    public ParameterStatusMessage(int length, String parameterName, String parameterValue) {
        this.length = length;
        this.parameterName = parameterName;
        this.parameterValue = parameterValue;
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
