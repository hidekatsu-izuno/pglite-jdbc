package io.github.hidekatsu_izuno.pglite_jdbc.polyfills;

import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.types.BufferParameter;

public interface TypedArray extends BufferParameter {
    ArrayBuffer getBuffer();
    int getByteOffset();
    int getByteLength();
    int getLength();
    byte get(int index);
    void set(int index, int value);
}
