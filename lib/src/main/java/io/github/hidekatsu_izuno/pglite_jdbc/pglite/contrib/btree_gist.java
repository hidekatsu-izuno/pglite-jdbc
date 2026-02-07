package io.github.hidekatsu_izuno.pglite_jdbc.pglite.contrib;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Extension;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.ExtensionSetupResult;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

public final class btree_gist {
    public static final Extension<Object> btree_gist = new Extension<>();

    static {
        btree_gist.name = "btree_gist";
        btree_gist.setup = (_pg, emscriptenOpts, clientOnly) -> {
            var result = new ExtensionSetupResult<Object>();

            result.bundlePath = resource("btree_gist.tar.gz");
            return CompletableFuture.completedFuture(result);
        };
    }

    private static URL resource(String name) {
        var url = btree_gist.class.getClassLoader().getResource(name);
        if (url == null) {
            throw new RuntimeException("Extension bundle not found on classpath: " + name);
        }
        return url;
    }

    private btree_gist() {
    }
}
