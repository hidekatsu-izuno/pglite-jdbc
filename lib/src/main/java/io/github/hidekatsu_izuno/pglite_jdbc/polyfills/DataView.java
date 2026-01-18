package io.github.hidekatsu_izuno.pglite_jdbc.polyfills;

public class DataView {
    public ArrayBuffer buffer;

    public DataView(ArrayBuffer buffer) {
        this.buffer = buffer;
    }

    public short getInt16(int byteOffset, boolean littleEndian) {
        checkRange(byteOffset, 2);
        int b0 = this.buffer.bytes[byteOffset] & 0xFF;
        int b1 = this.buffer.bytes[byteOffset + 1] & 0xFF;
        int value = littleEndian ? (b0 | (b1 << 8)) : ((b0 << 8) | b1);
        return (short) value;
    }

    public byte getUint8(int byteOffset) {
        checkRange(byteOffset, 1);
        return this.buffer.bytes[byteOffset];
    }

    public void setUint8(int byteOffset, int value) {
        checkRange(byteOffset, 1);
        this.buffer.bytes[byteOffset] = (byte) (value & 0xFF);
    }

    public int getInt32(int byteOffset, boolean littleEndian) {
        checkRange(byteOffset, 4);
        int b0 = this.buffer.bytes[byteOffset] & 0xFF;
        int b1 = this.buffer.bytes[byteOffset + 1] & 0xFF;
        int b2 = this.buffer.bytes[byteOffset + 2] & 0xFF;
        int b3 = this.buffer.bytes[byteOffset + 3] & 0xFF;
        if (littleEndian) {
            return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
        }
        return (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
    }

    public int getUint32(int byteOffset, boolean littleEndian) {
        checkRange(byteOffset, 4);
        var b0 = this.buffer.bytes[byteOffset] & 0xFF;
        var b1 = this.buffer.bytes[byteOffset + 1] & 0xFF;
        var b2 = this.buffer.bytes[byteOffset + 2] & 0xFF;
        var b3 = this.buffer.bytes[byteOffset + 3] & 0xFF;
        if (littleEndian) {
            return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
        }
        return (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
    }

    public void setInt16(int byteOffset, short value, boolean littleEndian) {
        checkRange(byteOffset, 2);
        if (littleEndian) {
            this.buffer.bytes[byteOffset] = (byte) (value);
            this.buffer.bytes[byteOffset + 1] = (byte) (value >> 8);
        } else {
            this.buffer.bytes[byteOffset] = (byte) (value >> 8);
            this.buffer.bytes[byteOffset + 1] = (byte) (value);
        }
    }

    public void setInt32(int byteOffset, int value, boolean littleEndian) {
        checkRange(byteOffset, 4);
        if (littleEndian) {
            this.buffer.bytes[byteOffset] = (byte) (value);
            this.buffer.bytes[byteOffset + 1] = (byte) (value >> 8);
            this.buffer.bytes[byteOffset + 2] = (byte) (value >> 16);
            this.buffer.bytes[byteOffset + 3] = (byte) (value >> 24);
        } else {
            this.buffer.bytes[byteOffset] = (byte) (value >> 24);
            this.buffer.bytes[byteOffset + 1] = (byte) (value >> 16);
            this.buffer.bytes[byteOffset + 2] = (byte) (value >> 8);
            this.buffer.bytes[byteOffset + 3] = (byte) (value);
        }
    }

    private void checkRange(int byteOffset, int size) {
        if (byteOffset < 0 || byteOffset + size > this.buffer.byteLength) {
            throw new IndexOutOfBoundsException("byteOffset out of range");
        }
    }
}
