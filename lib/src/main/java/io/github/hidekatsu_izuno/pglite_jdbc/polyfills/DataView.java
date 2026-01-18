package io.github.hidekatsu_izuno.pglite_jdbc.polyfills;

public class DataView {
    public ArrayBuffer buffer;

    public DataView(ArrayBuffer buffer) {
        this.buffer = buffer;
    }

    public short getInt16(int byteOffset, boolean littleEndian) {
        //TODO
        return 0;
    }

    public byte getUint8(int byteOffset) {
        //TODO
        return 0;
    }

    public int getInt32(int byteOffset, boolean littleEndian) {
        //TODO
        return 0;
    }
}
