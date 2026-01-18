package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.ArrayBuffer;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.DataView;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.TextEncoder;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array;

public class BufferWriter {
    private DataView bufferView;
    private int offset = 5;
    private final boolean littleEndian = false;
    private final int headerPosition = 0;
    private final TextEncoder encoder = new TextEncoder();
    private final int size;

    public BufferWriter() {
        this(256);
    }

    public BufferWriter(int size) {
        this.size = size;
        this.bufferView = this.allocateBuffer(size);
    }

    private DataView allocateBuffer(int size) {
        return new DataView(new ArrayBuffer(size));
    }

    private void ensure(int size) {
        int remaining = this.bufferView.buffer.byteLength - this.offset;
        if (remaining < size) {
            var oldBuffer = this.bufferView.buffer;
            // exponential growth factor of around ~ 1.5
            // https://stackoverflow.com/questions/2269063/buffer-growth-strategy
            var newSize = oldBuffer.byteLength + (oldBuffer.byteLength >> 1) + size;
            this.bufferView = this.allocateBuffer(newSize);
            new Uint8Array(this.bufferView.buffer).set(new Uint8Array(oldBuffer));
        }
    }

    public BufferWriter addInt32(int num) {
        this.ensure(4);
        this.bufferView.setInt32(this.offset, num, this.littleEndian);
        this.offset += 4;
        return this;
    }

    public BufferWriter addInt16(int num) {
        this.ensure(2);
        this.bufferView.setInt16(this.offset, (short) num, this.littleEndian);
        this.offset += 2;
        return this;
    }

    public BufferWriter addCString(String string) {
        if (string != null && !string.isEmpty()) {
            // TODO(msfstef): might be faster to extract `addString` code and
            // ensure length + 1 once rather than length and then +1?
            this.addString(string);
        }
        // set null terminator
        this.ensure(1);
        this.bufferView.setUint8(this.offset, 0);
        this.offset++;
        return this;
    }

    public BufferWriter addString() {
        return this.addString("");
    }

    public BufferWriter addString(String string) {
        var length = StringUtils.byteLengthUtf8(string);
        this.ensure(length);
        this.encoder.encodeInto(
            string,
            new Uint8Array(this.bufferView.buffer, this.offset, length)
        );
        this.offset += length;
        return this;
    }

    public BufferWriter add(ArrayBuffer otherBuffer) {
        this.ensure(otherBuffer.byteLength);
        new Uint8Array(this.bufferView.buffer).set(new Uint8Array(otherBuffer), this.offset);
        this.offset += otherBuffer.byteLength;
        return this;
    }

    private ArrayBuffer join(Byte code) {
        if (code != null) {
            this.bufferView.setUint8(this.headerPosition, code);
            // length is everything in this packet minus the code
            var length = this.offset - (this.headerPosition + 1);
            this.bufferView.setInt32(this.headerPosition + 1, length, this.littleEndian);
        }
        return this.bufferView.buffer.slice(code != null ? 0 : 5, this.offset);
    }

    public Uint8Array flush() {
        var result = this.join(null);
        this.offset = 5;
        this.bufferView = this.allocateBuffer(this.size);
        return new Uint8Array(result);
    }

    public Uint8Array flush(byte code) {
        var result = this.join(code);
        this.offset = 5;
        this.bufferView = this.allocateBuffer(this.size);
        return new Uint8Array(result);
    }

}
