package io.github.hidekatsu_izuno.pglite_jdbc.pglite.contrib;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Extension;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.ExtensionSetupResult;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

public final class tsm_system_rows {
    public static final Extension<Object> tsm_system_rows = new Extension<>();

    static {
        tsm_system_rows.name = "tsm_system_rows";
        tsm_system_rows.setup = (_pg, emscriptenOpts, clientOnly) -> {
            var result = new ExtensionSetupResult<Object>();

            result.bundlePath = resource("tsm_system_rows.tar.gz");
            return CompletableFuture.completedFuture(result);
        };
    }

    private static URL resource(String name) {
        var url = tsm_system_rows.class.getClassLoader().getResource(name);
        if (url == null) {
            throw new RuntimeException("Extension bundle not found on classpath: " + name);
        }
        return url;
    }

    private tsm_system_rows() {
    }
}
