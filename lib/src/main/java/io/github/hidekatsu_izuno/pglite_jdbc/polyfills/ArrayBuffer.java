package io.github.hidekatsu_izuno.pglite_jdbc.polyfills;

import java.util.Arrays;

public class ArrayBuffer {
    public final int byteLength;
    final byte[] bytes;

    public ArrayBuffer(int byteLength) {
        if (byteLength < 0) {
            throw new IllegalArgumentException("byteLength must be non-negative");
        }
        this.byteLength = byteLength;
        this.bytes = new byte[byteLength];
    }

    public ArrayBuffer slice(int begin, int end) {
        int length = this.byteLength;
        int start = begin < 0 ? Math.max(length + begin, 0) : Math.min(begin, length);
        int finish = end < 0 ? Math.max(length + end, 0) : Math.min(end, length);
        int newLength = Math.max(finish - start, 0);
        byte[] slice = new byte[newLength];
        if (newLength > 0) {
            System.arraycopy(this.bytes, start, slice, 0, newLength);
        }
        return new ArrayBuffer(slice, false);
    }

    ArrayBuffer(byte[] bytes, boolean copy) {
        this.bytes = copy ? Arrays.copyOf(bytes, bytes.length) : bytes;
        this.byteLength = this.bytes.length;
    }
}
