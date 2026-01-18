package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;

public class AuthenticationSASLContinue implements AuthenticationMessage {
    public final String name = "authenticationSASLContinue";
    public final int length;
    public final String data;

    public AuthenticationSASLContinue(int length, String data) {
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
