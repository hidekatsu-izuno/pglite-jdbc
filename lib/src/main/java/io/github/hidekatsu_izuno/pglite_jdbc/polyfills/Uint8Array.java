package io.github.hidekatsu_izuno.pglite_jdbc.polyfills;

public class Uint8Array implements TypedArray {
    public final ArrayBuffer buffer;
    public final int byteOffset;
    public final int byteLength;
    public final int length;

    public Uint8Array(int length) {
        this(new ArrayBuffer(length));
    }

    public Uint8Array(ArrayBuffer array) {
        this(array, 0, array.byteLength);
    }

    public Uint8Array(ArrayBuffer array, int byteOffset, int length) {
        if (byteOffset < 0 || byteOffset > array.byteLength) {
            throw new IllegalArgumentException("byteOffset out of range");
        }
        if (length < 0 || byteOffset + length > array.byteLength) {
            throw new IllegalArgumentException("length out of range");
        }
        this.buffer = array;
        this.byteOffset = byteOffset;
        this.length = length;
        this.byteLength = length;
    }

    @Override
    public ArrayBuffer getBuffer() {
        return this.buffer;
    }

    @Override
    public int getByteOffset() {
        return this.byteOffset;
    }

    @Override
    public int getByteLength() {
        return this.byteLength;
    }

    @Override
    public int getLength() {
        return this.length;
    }

    @Override
    public byte get(int index) {
        if (index < 0 || index >= this.length) {
            throw new IndexOutOfBoundsException("index out of range");
        }
        return this.buffer.bytes[this.byteOffset + index];
    }

    @Override
    public void set(int index, int value) {
        if (index < 0 || index >= this.length) {
            throw new IndexOutOfBoundsException("index out of range");
        }
        this.buffer.bytes[this.byteOffset + index] = (byte) (value & 0xFF);
    }

    public byte[] toByteArray() {
        byte[] copy = new byte[this.length];
        if (this.length > 0) {
            System.arraycopy(this.buffer.bytes, this.byteOffset, copy, 0, this.length);
        }
        return copy;
    }
}
