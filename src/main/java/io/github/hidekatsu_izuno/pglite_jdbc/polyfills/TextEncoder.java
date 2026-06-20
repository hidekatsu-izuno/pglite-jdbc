package io.github.hidekatsu_izuno.pglite_jdbc.polyfills;

import java.nio.charset.StandardCharsets;

public class TextEncoder {
    public int encodeInto(String input, Uint8Array destination) {
        if (destination == null) {
            throw new IllegalArgumentException("destination must not be null");
        }
        var value = input == null ? "" : input;
        var bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > destination.length) {
            throw new IndexOutOfBoundsException("destination too small");
        }
        destination.set(bytes);
        return bytes.length;
    }
}
