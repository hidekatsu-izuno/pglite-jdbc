package io.github.hidekatsu_izuno.pglite_jdbc.pglite.contrib;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Extension;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.ExtensionSetupResult;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

public final class tcn {
    public static final Extension<Object> tcn = new Extension<>();

    static {
        tcn.name = "tcn";
        tcn.setup = (_pg, emscriptenOpts, clientOnly) -> {
            var result = new ExtensionSetupResult<Object>();

            result.bundlePath = resource("tcn.tar.gz");
            return CompletableFuture.completedFuture(result);
        };
    }

    private static URL resource(String name) {
        var url = tcn.class.getClassLoader().getResource(name);
        if (url == null) {
            throw new RuntimeException("Extension bundle not found on classpath: " + name);
        }
        return url;
    }

    private tcn() {
    }
}
