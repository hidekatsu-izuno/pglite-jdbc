package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.ArrayBuffer;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.DataView;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.TextDecoder;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array;

public final class buffer_reader {
    public static class BufferReader {
        private static ArrayBuffer emptyBuffer = new ArrayBuffer(0);

        private DataView bufferView = new DataView(emptyBuffer);
        private int offset;

        // TODO(bmc): support non-utf8 encoding?
        private String encoding = "utf-8";
        private TextDecoder decoder = new TextDecoder(encoding);
        private boolean littleEndian = false;

        public BufferReader(int offset) {
            this.offset = offset;
        }

        public void setBuffer(int offset, ArrayBuffer buffer) {
            this.offset = offset;
            this.bufferView = new DataView(buffer);
        }

        public short int16() {
            // const result = this.buffer.readInt16BE(this.#offset)
            var result = this.bufferView.getInt16(this.offset, this.littleEndian);
            this.offset += 2;
            return result;
        }

        public byte byte_() {
            // const result = this.bufferView[this.#offset]
            var result = this.bufferView.getUint8(this.offset);
            this.offset++;
            return result;
        }

        public int int32() {
            // const result = this.buffer.readInt32BE(this.#offset)
            var result = this.bufferView.getInt32(this.offset, this.littleEndian);
            this.offset += 4;
            return result;
        }

        public String string(int length) {
            // const result = this.#bufferView.toString(
            //   this.#encoding,
            //   this.#offset,
            //   this.#offset + length,
            // )
            // this.#offset += length

            var result = this.decoder.decode(this.bytes(length));
            return result;
        }

        public String cstring() {
            // const start = this.#offset
            // let end = start
            // while (this.#bufferView[end++] !== 0) {}

            var start = this.offset;
            var end = start;
            while (this.bufferView.getUint8(end++) != 0) {
                // no-op - increment until terminator reached
            }
            var result = this.string(end - start - 1);
            this.offset = end;
            return result;
        }

        public Uint8Array bytes(int length) {
            // const result = this.buffer.slice(this.#offset, this.#offset + length)
            var result = this.bufferView.buffer.slice(
                this.offset,
                this.offset + length
            );
            this.offset += length;
            return new Uint8Array(result);
        }
    }

    private buffer_reader() {
    }
}
