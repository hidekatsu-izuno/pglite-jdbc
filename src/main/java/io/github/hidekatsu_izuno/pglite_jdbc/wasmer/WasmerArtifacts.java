package io.github.hidekatsu_izuno.pglite_jdbc.wasmer;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;

/** Loads trusted, build-time compiled modules matching the source, ABI and platform. */
public final class WasmerArtifacts {
    private WasmerArtifacts() { }

    public static byte[] read(byte[] wasm, String version) {
        var resource = resourceName(wasm, version);
        try (var input = WasmerArtifacts.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing Wasmer headless artifact: " + resource
                    + "; run scripts/compile-wasmer.py for this platform and Wasmer version");
            }
            try (var gzip = new GZIPInputStream(input)) {
                return gzip.readAllBytes();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read Wasmer headless artifact: " + resource, e);
        }
    }

    public static boolean isAvailable(byte[] wasm, String version) {
        return WasmerArtifacts.class.getClassLoader().getResource(resourceName(wasm, version)) != null;
    }

    private static String resourceName(byte[] wasm, String version) {
        try {
            var hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(wasm));
            return "io/github/hidekatsu_izuno/pglite_jdbc/wasmer/compiled/" + version + "/"
                + WasmerNativeLoader.platformKey() + "/" + hash + ".wasmu.gz";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
