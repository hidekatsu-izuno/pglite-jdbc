package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol;

import java.nio.charset.StandardCharsets;

public final class string_utils {
    public static int byteLengthUtf8(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
    
    private string_utils() {
    }
}
