package io.github.hidekatsu_izuno.pglite_jdbc.pglite.pg_hashids;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Extension;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.ExtensionSetupResult;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

public final class index {
    public static final Extension<Object> pg_hashids = new Extension<>();

    static {
        pg_hashids.name = "pg_hashids";
        pg_hashids.setup = (_pg, emscriptenOpts, clientOnly) -> {
            var result = new ExtensionSetupResult<Object>();
            result.emscriptenOpts = emscriptenOpts;
            result.bundlePath = resource("pg_hashids.tar.gz");
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
