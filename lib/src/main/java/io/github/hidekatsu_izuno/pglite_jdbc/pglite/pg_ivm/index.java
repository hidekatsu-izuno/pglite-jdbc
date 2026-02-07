package io.github.hidekatsu_izuno.pglite_jdbc.pglite.pg_ivm;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Extension;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.ExtensionSetupResult;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

public final class index {
    public static final Extension<Object> pg_ivm = new Extension<>();

    static {
        pg_ivm.name = "pg_ivm";
        pg_ivm.setup = (_pg, emscriptenOpts, clientOnly) -> {
            var result = new ExtensionSetupResult<Object>();
            result.emscriptenOpts = emscriptenOpts;
            result.bundlePath = resource("pg_ivm.tar.gz");
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
