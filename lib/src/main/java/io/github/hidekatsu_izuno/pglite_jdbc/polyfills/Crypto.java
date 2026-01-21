package io.github.hidekatsu_izuno.pglite_jdbc.polyfills;

import java.security.SecureRandom;

public class Crypto {
    private static final int MAX_RANDOM_BYTES = 65536;
    private static final ThreadLocal<SecureRandom> SECURE_RANDOM = ThreadLocal.withInitial(
        SecureRandom::new
    );

    public static TypedArray getRandomValues(TypedArray typedArray) {
        if (typedArray == null) {
            throw new IllegalArgumentException("typedArray is null");
        }
        var byteLength = typedArray.getByteLength();
        if (byteLength > MAX_RANDOM_BYTES) {
            throw new IllegalArgumentException("typedArray byteLength exceeds 65536");
        }

        if (byteLength == 0) {
            return typedArray;
        }
        var bytes = new byte[byteLength];
        var random = SECURE_RANDOM.get();
        random.nextBytes(bytes);
        for (var i = 0; i < bytes.length; i++) {
            typedArray.set(i, bytes[i] & 0xFF);
        }
        return typedArray;
    }
}
