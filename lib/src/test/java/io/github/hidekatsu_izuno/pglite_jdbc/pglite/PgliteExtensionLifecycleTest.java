package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Extension;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.ExtensionSetupResult;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.PGliteOptions;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PgliteExtensionLifecycleTest {
    private static final class ExtensionsMap
        extends HashMap<String, Object>
        implements interface_.Extensions {
    }

    @Test
    void shouldInvokeExtensionInitAndCloseExactlyOnce() {
        var initCalls = new AtomicInteger(0);
        var closeCalls = new AtomicInteger(0);
        var extension = new Extension<Object>();
        extension.name = "lifecycle";
        extension.setup = (pg, emscriptenOpts, clientOnly) -> {
            var result = new ExtensionSetupResult<Object>();
            result.init = () -> {
                initCalls.incrementAndGet();
                return CompletableFuture.completedFuture(null);
            };
            result.close = () -> {
                closeCalls.incrementAndGet();
                return CompletableFuture.completedFuture(null);
            };
            return CompletableFuture.completedFuture(result);
        };

        var options = new PGliteOptions<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Extensions>();
        options.dataDir = "memory://";
        var extensions = new ExtensionsMap();
        extensions.put("lifecycle", extension);
        options.extensions = extensions;

        var pg = pglite.create(options).join();
        pg.close().join();

        assertEquals(1, initCalls.get());
        assertEquals(1, closeCalls.get());
    }
}
