package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;

public class AuthenticationSASLFinal implements AuthenticationMessage {
    public final String name = "authenticationSASLFinal";
    public final int length;
    public final String data;

    public AuthenticationSASLFinal(int length, String data) {
        this.length = length;
        this.data = data;
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
