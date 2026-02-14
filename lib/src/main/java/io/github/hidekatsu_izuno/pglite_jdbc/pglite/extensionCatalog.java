package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import java.util.Map;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;

public class extensionCatalog {
    private extensionCatalog() {}

    public static interface_.Extension create(String extensionName, String bundleFilename) {
        return new interface_.Extension() {
            @Override
            public String name() {
                return extensionName;
            }

            @Override
            public interface_.ExtensionSetup setup() {
                return (pg, emscriptenOpts, clientOnly) -> Promise.resolve(
                    new interface_.ExtensionSetupResult(
                        emscriptenOpts,
                        Map.of(),
                        bundleFilename,
                        null,
                        null
                    )
                );
            }
        };
    }
}
