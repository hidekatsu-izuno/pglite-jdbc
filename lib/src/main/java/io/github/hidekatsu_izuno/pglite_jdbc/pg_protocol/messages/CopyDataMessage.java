package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array;

public class CopyDataMessage implements BackendMessage {
    public final String name = "copyData";
    public final int length;
    public final Uint8Array chunk;

    public CopyDataMessage(int length, Uint8Array chunk) {
        this.length = length;
        this.chunk = chunk;
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
