package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.parser;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.ArrayBuffer;

public class Packet {
    public final int code;
    public final ArrayBuffer packet;

    public Packet(int code, ArrayBuffer packet) {
        this.code = code;
        this.packet = packet;
    }
}
