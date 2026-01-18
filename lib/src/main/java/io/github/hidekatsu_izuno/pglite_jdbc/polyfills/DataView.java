package io.github.hidekatsu_izuno.pglite_jdbc.polyfills;

public class DataView {
    public ArrayBuffer buffer;

    public DataView(ArrayBuffer buffer) {
        this.buffer = buffer;
    }

    public short getInt16(int byteOffset, boolean littleEndian) {
        checkRange(byteOffset, 2);
        int b0 = buffer.bytes[byteOffset] & 0xFF;
        int b1 = buffer.bytes[byteOffset + 1] & 0xFF;
        int value = littleEndian ? (b0 | (b1 << 8)) : ((b0 << 8) | b1);
        return (short) value;
    }

    public byte getUint8(int byteOffset) {
        checkRange(byteOffset, 1);
        return buffer.bytes[byteOffset];
    }

    public int getInt32(int byteOffset, boolean littleEndian) {
        checkRange(byteOffset, 4);
        int b0 = buffer.bytes[byteOffset] & 0xFF;
        int b1 = buffer.bytes[byteOffset + 1] & 0xFF;
        int b2 = buffer.bytes[byteOffset + 2] & 0xFF;
        int b3 = buffer.bytes[byteOffset + 3] & 0xFF;
        if (littleEndian) {
            return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
        }
        return (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
    }

    private void checkRange(int byteOffset, int size) {
        if (byteOffset < 0 || byteOffset + size > buffer.byteLength) {
            throw new IndexOutOfBoundsException("byteOffset out of range");
        }
    }
}
