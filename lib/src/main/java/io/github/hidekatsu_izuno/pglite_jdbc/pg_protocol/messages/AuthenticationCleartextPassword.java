package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;

public class AuthenticationCleartextPassword implements AuthenticationMessage {
    public final String name = "authenticationCleartextPassword";
    public final int length;

    public AuthenticationCleartextPassword(int length) {
        this.length = length;
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
