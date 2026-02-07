package io.github.hidekatsu_izuno.pglite_jdbc.polyfills;

import java.nio.ByteOrder;

public class Uint16Array implements TypedArray {
    private static final int BYTES_PER_ELEMENT = 2;
    private static final boolean LITTLE_ENDIAN =
        ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

    public final ArrayBuffer buffer;
    public final int byteOffset;
    public final int byteLength;
    public final int length;

    public Uint16Array(int length) {
        this(new ArrayBuffer(length * BYTES_PER_ELEMENT));
    }

    public Uint16Array(int[] array) {
        this(new ArrayBuffer(array.length * BYTES_PER_ELEMENT));
        this.set(array);
    }

    public Uint16Array(ArrayBuffer array) {
        this(array, 0, array.byteLength / BYTES_PER_ELEMENT);
        if ((array.byteLength % BYTES_PER_ELEMENT) != 0) {
            throw new IllegalArgumentException("byteLength must be a multiple of 2");
        }
    }

    public Uint16Array(ArrayBuffer array, int byteOffset, int length) {
        if ((byteOffset & (BYTES_PER_ELEMENT - 1)) != 0) {
            throw new IllegalArgumentException("byteOffset must be a multiple of 2");
        }
        if (byteOffset < 0 || byteOffset > array.byteLength) {
            throw new IllegalArgumentException("byteOffset out of range");
        }
        if (length < 0) {
            throw new IllegalArgumentException("length out of range");
        }
        var byteLength = length * BYTES_PER_ELEMENT;
        if (byteOffset + byteLength > array.byteLength) {
            throw new IllegalArgumentException("length out of range");
        }
        this.buffer = array;
        this.byteOffset = byteOffset;
        this.length = length;
        this.byteLength = byteLength;
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
        return (byte) getUint16(index);
    }

    @Override
    public void set(int index, int value) {
        setUint16(index, value);
    }

    public int getUint16(int index) {
        if (index < 0 || index >= this.length) {
            throw new IndexOutOfBoundsException("index out of range");
        }
        var base = this.byteOffset + (index * BYTES_PER_ELEMENT);
        var bytes = this.buffer.bytes;
        return LITTLE_ENDIAN
            ? (bytes[base] & 0xFF) | ((bytes[base + 1] & 0xFF) << 8)
            : (bytes[base + 1] & 0xFF) | ((bytes[base] & 0xFF) << 8);
    }

    public void setUint16(int index, int value) {
        if (index < 0 || index >= this.length) {
            throw new IndexOutOfBoundsException("index out of range");
        }
        var base = this.byteOffset + (index * BYTES_PER_ELEMENT);
        var bytes = this.buffer.bytes;
        var masked = value & 0xFFFF;
        if (LITTLE_ENDIAN) {
            bytes[base] = (byte) masked;
            bytes[base + 1] = (byte) (masked >> 8);
        } else {
            bytes[base + 1] = (byte) masked;
            bytes[base] = (byte) (masked >> 8);
        }
    }

    public void set(Uint16Array source) {
        this.set(source, 0);
    }

    public void set(Uint16Array source, int offset) {
        if (offset < 0 || offset > this.length) {
            throw new IndexOutOfBoundsException("offset out of range");
        }
        if (source.length + offset > this.length) {
            throw new IndexOutOfBoundsException("source length out of range");
        }
        if (source.byteLength > 0) {
            System.arraycopy(
                source.buffer.bytes,
                source.byteOffset,
                this.buffer.bytes,
                this.byteOffset + (offset * BYTES_PER_ELEMENT),
                source.byteLength
            );
        }
    }

    public void set(int[] source) {
        this.set(source, 0);
    }

    public void set(int[] source, int offset) {
        if (offset < 0 || offset > this.length) {
            throw new IndexOutOfBoundsException("offset out of range");
        }
        if (source.length + offset > this.length) {
            throw new IndexOutOfBoundsException("source length out of range");
        }
        for (var i = 0; i < source.length; i++) {
            setUint16(offset + i, source[i]);
        }
    }

    public int[] toIntArray() {
        var copy = new int[this.length];
        for (var i = 0; i < this.length; i++) {
            copy[i] = getUint16(i);
        }
        return copy;
    }
}
