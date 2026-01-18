package io.github.hidekatsu_izuno.pglite_jdbc.polyfills;

import java.nio.charset.Charset;

public class TextDecoder {
    private final Charset charset;

    public TextDecoder(String encoding) {
        this.charset = Charset.forName(encoding);
    }

    public String decode(TypedArray array) {
        if (array == null) {
            throw new IllegalArgumentException("array must not be null");
        }
        int offset = array.getByteOffset();
        int length = array.getByteLength();
        if (length == 0) {
            return "";
        }
        return new String(array.getBuffer().bytes, offset, length, charset);
    }
}
