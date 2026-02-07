package io.github.hidekatsu_izuno.pglite_jdbc.polyfills;

import java.nio.ByteOrder;

public class BigUint64Array implements TypedArray {
    private static final int BYTES_PER_ELEMENT = 8;
    private static final boolean LITTLE_ENDIAN =
        ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

    public final ArrayBuffer buffer;
    public final int byteOffset;
    public final int byteLength;
    public final int length;

    public BigUint64Array(int length) {
        this(new ArrayBuffer(length * BYTES_PER_ELEMENT));
    }

    public BigUint64Array(long[] array) {
        this(new ArrayBuffer(array.length * BYTES_PER_ELEMENT));
        this.set(array);
    }

    public BigUint64Array(ArrayBuffer array) {
        this(array, 0, array.byteLength / BYTES_PER_ELEMENT);
        if ((array.byteLength % BYTES_PER_ELEMENT) != 0) {
            throw new IllegalArgumentException("byteLength must be a multiple of 8");
        }
    }

    public BigUint64Array(ArrayBuffer array, int byteOffset, int length) {
        if ((byteOffset & (BYTES_PER_ELEMENT - 1)) != 0) {
            throw new IllegalArgumentException("byteOffset must be a multiple of 8");
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
        return (byte) getLong(index);
    }

    @Override
    public void set(int index, int value) {
        setLong(index, value);
    }

    public long getLong(int index) {
        if (index < 0 || index >= this.length) {
            throw new IndexOutOfBoundsException("index out of range");
        }
        var base = this.byteOffset + (index * BYTES_PER_ELEMENT);
        var bytes = this.buffer.bytes;
        if (LITTLE_ENDIAN) {
            return ((long) bytes[base] & 0xFF)
                | (((long) bytes[base + 1] & 0xFF) << 8)
                | (((long) bytes[base + 2] & 0xFF) << 16)
                | (((long) bytes[base + 3] & 0xFF) << 24)
                | (((long) bytes[base + 4] & 0xFF) << 32)
                | (((long) bytes[base + 5] & 0xFF) << 40)
                | (((long) bytes[base + 6] & 0xFF) << 48)
                | (((long) bytes[base + 7] & 0xFF) << 56);
        }
        return ((long) bytes[base + 7] & 0xFF)
            | (((long) bytes[base + 6] & 0xFF) << 8)
            | (((long) bytes[base + 5] & 0xFF) << 16)
            | (((long) bytes[base + 4] & 0xFF) << 24)
            | (((long) bytes[base + 3] & 0xFF) << 32)
            | (((long) bytes[base + 2] & 0xFF) << 40)
            | (((long) bytes[base + 1] & 0xFF) << 48)
            | (((long) bytes[base] & 0xFF) << 56);
    }

    public void setLong(int index, long value) {
        if (index < 0 || index >= this.length) {
            throw new IndexOutOfBoundsException("index out of range");
        }
        var base = this.byteOffset + (index * BYTES_PER_ELEMENT);
        var bytes = this.buffer.bytes;
        if (LITTLE_ENDIAN) {
            bytes[base] = (byte) value;
            bytes[base + 1] = (byte) (value >> 8);
            bytes[base + 2] = (byte) (value >> 16);
            bytes[base + 3] = (byte) (value >> 24);
            bytes[base + 4] = (byte) (value >> 32);
            bytes[base + 5] = (byte) (value >> 40);
            bytes[base + 6] = (byte) (value >> 48);
            bytes[base + 7] = (byte) (value >> 56);
        } else {
            bytes[base + 7] = (byte) value;
            bytes[base + 6] = (byte) (value >> 8);
            bytes[base + 5] = (byte) (value >> 16);
            bytes[base + 4] = (byte) (value >> 24);
            bytes[base + 3] = (byte) (value >> 32);
            bytes[base + 2] = (byte) (value >> 40);
            bytes[base + 1] = (byte) (value >> 48);
            bytes[base] = (byte) (value >> 56);
        }
    }

    public void set(BigUint64Array source) {
        this.set(source, 0);
    }

    public void set(BigUint64Array source, int offset) {
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

    public void set(long[] source) {
        this.set(source, 0);
    }

    public void set(long[] source, int offset) {
        if (offset < 0 || offset > this.length) {
            throw new IndexOutOfBoundsException("offset out of range");
        }
        if (source.length + offset > this.length) {
            throw new IndexOutOfBoundsException("source length out of range");
        }
        for (var i = 0; i < source.length; i++) {
            setLong(offset + i, source[i]);
        }
    }

    public long[] toLongArray() {
        var copy = new long[this.length];
        for (var i = 0; i < this.length; i++) {
            copy[i] = getLong(i);
        }
        return copy;
    }
}
