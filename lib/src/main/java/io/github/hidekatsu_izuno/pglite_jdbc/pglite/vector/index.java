package io.github.hidekatsu_izuno.pglite_jdbc.pglite.vector;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Extension;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.ExtensionSetupResult;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

public final class index {
    public static final Extension<Object> vector = new Extension<>();

    static {
        vector.name = "pgvector";
        vector.setup = (_pg, emscriptenOpts, clientOnly) -> {
            var result = new ExtensionSetupResult<Object>();
            result.emscriptenOpts = emscriptenOpts;
            result.bundlePath = resource("vector.tar.gz");
            return CompletableFuture.completedFuture(result);
        };
    }

    private static URL resource(String name) {
        var url = index.class.getClassLoader().getResource(name);
        if (url == null) {
            throw new RuntimeException("Extension bundle not found on classpath: " + name);
        }
        return url;
    }

    private index() {
    }
}
