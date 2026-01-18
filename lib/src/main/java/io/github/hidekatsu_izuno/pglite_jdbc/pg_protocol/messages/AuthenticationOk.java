package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;

public class AuthenticationOk implements AuthenticationMessage, BackendMessage {
    private int length;

    public AuthenticationOk(int length) {
        this.length = length;
    }

    @Override
    public String getName() {
        return "authenticationOk";
    }

    @Override
    public int getLength() {
        return length;
    }
    
}
