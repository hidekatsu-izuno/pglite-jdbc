package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array;

public class AuthenticationMD5Password implements AuthenticationMessage {
    public final String name = "authenticationMD5Password";
    public final int length;
    public final Uint8Array salt;

    public AuthenticationMD5Password(int length, Uint8Array salt) {
        this.length = length;
        this.salt = salt;
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
