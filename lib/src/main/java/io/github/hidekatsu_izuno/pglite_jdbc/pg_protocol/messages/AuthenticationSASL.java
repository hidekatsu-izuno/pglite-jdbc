package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;

public class AuthenticationSASL implements AuthenticationMessage {
    public final String name = "authenticationSASL";
    public final int length;
    public final String[] mechanisms;

    public AuthenticationSASL(int length, String[] mechanisms) {
        this.length = length;
        this.mechanisms = mechanisms;
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
