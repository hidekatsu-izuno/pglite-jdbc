package io.github.hidekatsu_izuno.pglite_jdbc.polyfills;

import java.nio.ByteOrder;

public class Float64Array implements TypedArray {
    private static final int BYTES_PER_ELEMENT = 8;
    private static final boolean LITTLE_ENDIAN =
        ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

    public final ArrayBuffer buffer;
    public final int byteOffset;
    public final int byteLength;
    public final int length;

    public Float64Array(int length) {
        this(new ArrayBuffer(length * BYTES_PER_ELEMENT));
    }

    public Float64Array(double[] array) {
        this(new ArrayBuffer(array.length * BYTES_PER_ELEMENT));
        this.set(array);
    }

    public Float64Array(ArrayBuffer array) {
        this(array, 0, array.byteLength / BYTES_PER_ELEMENT);
        if ((array.byteLength % BYTES_PER_ELEMENT) != 0) {
            throw new IllegalArgumentException("byteLength must be a multiple of 8");
        }
    }

    public Float64Array(ArrayBuffer array, int byteOffset, int length) {
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
        return (byte) getDouble(index);
    }

    @Override
    public void set(int index, int value) {
        setDouble(index, value);
    }

    public double getDouble(int index) {
        if (index < 0 || index >= this.length) {
            throw new IndexOutOfBoundsException("index out of range");
        }
        var base = this.byteOffset + (index * BYTES_PER_ELEMENT);
        var bytes = this.buffer.bytes;
        long bits;
        if (LITTLE_ENDIAN) {
            bits = ((long) bytes[base] & 0xFF)
                | (((long) bytes[base + 1] & 0xFF) << 8)
                | (((long) bytes[base + 2] & 0xFF) << 16)
                | (((long) bytes[base + 3] & 0xFF) << 24)
                | (((long) bytes[base + 4] & 0xFF) << 32)
                | (((long) bytes[base + 5] & 0xFF) << 40)
                | (((long) bytes[base + 6] & 0xFF) << 48)
                | (((long) bytes[base + 7] & 0xFF) << 56);
        } else {
            bits = ((long) bytes[base + 7] & 0xFF)
                | (((long) bytes[base + 6] & 0xFF) << 8)
                | (((long) bytes[base + 5] & 0xFF) << 16)
                | (((long) bytes[base + 4] & 0xFF) << 24)
                | (((long) bytes[base + 3] & 0xFF) << 32)
                | (((long) bytes[base + 2] & 0xFF) << 40)
                | (((long) bytes[base + 1] & 0xFF) << 48)
                | (((long) bytes[base] & 0xFF) << 56);
        }
        return Double.longBitsToDouble(bits);
    }

    public void setDouble(int index, double value) {
        if (index < 0 || index >= this.length) {
            throw new IndexOutOfBoundsException("index out of range");
        }
        var base = this.byteOffset + (index * BYTES_PER_ELEMENT);
        var bytes = this.buffer.bytes;
        var bits = Double.doubleToLongBits(value);
        if (LITTLE_ENDIAN) {
            bytes[base] = (byte) bits;
            bytes[base + 1] = (byte) (bits >> 8);
            bytes[base + 2] = (byte) (bits >> 16);
            bytes[base + 3] = (byte) (bits >> 24);
            bytes[base + 4] = (byte) (bits >> 32);
            bytes[base + 5] = (byte) (bits >> 40);
            bytes[base + 6] = (byte) (bits >> 48);
            bytes[base + 7] = (byte) (bits >> 56);
        } else {
            bytes[base + 7] = (byte) bits;
            bytes[base + 6] = (byte) (bits >> 8);
            bytes[base + 5] = (byte) (bits >> 16);
            bytes[base + 4] = (byte) (bits >> 24);
            bytes[base + 3] = (byte) (bits >> 32);
            bytes[base + 2] = (byte) (bits >> 40);
            bytes[base + 1] = (byte) (bits >> 48);
            bytes[base] = (byte) (bits >> 56);
        }
    }

    public void set(Float64Array source) {
        this.set(source, 0);
    }

    public void set(Float64Array source, int offset) {
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

    public void set(double[] source) {
        this.set(source, 0);
    }

    public void set(double[] source, int offset) {
        if (offset < 0 || offset > this.length) {
            throw new IndexOutOfBoundsException("offset out of range");
        }
        if (source.length + offset > this.length) {
            throw new IndexOutOfBoundsException("source length out of range");
        }
        for (var i = 0; i < source.length; i++) {
            setDouble(offset + i, source[i]);
        }
    }

    public double[] toDoubleArray() {
        var copy = new double[this.length];
        for (var i = 0; i < this.length; i++) {
            copy[i] = getDouble(i);
        }
        return copy;
    }
}
