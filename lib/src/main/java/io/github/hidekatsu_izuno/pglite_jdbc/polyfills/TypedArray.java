package io.github.hidekatsu_izuno.pglite_jdbc.polyfills;

public interface TypedArray {
    ArrayBuffer getBuffer();
    int getByteOffset();
    int getByteLength();
    int getLength();
    byte get(int index);
    void set(int index, int value);
}
