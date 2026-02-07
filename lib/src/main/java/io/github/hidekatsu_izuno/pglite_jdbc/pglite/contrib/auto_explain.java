package io.github.hidekatsu_izuno.pglite_jdbc.pglite.contrib;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Extension;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.ExtensionSetupResult;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

public final class auto_explain {
    public static final Extension<Object> auto_explain = new Extension<>();

    static {
        auto_explain.name = "auto_explain";
        auto_explain.setup = (_pg, emscriptenOpts, clientOnly) -> {
            var result = new ExtensionSetupResult<Object>();

            result.bundlePath = resource("auto_explain.tar.gz");
            return CompletableFuture.completedFuture(result);
        };
    }

    private static URL resource(String name) {
        var url = auto_explain.class.getClassLoader().getResource(name);
        if (url == null) {
            throw new RuntimeException("Extension bundle not found on classpath: " + name);
        }
        return url;
    }

    private auto_explain() {
    }
}
