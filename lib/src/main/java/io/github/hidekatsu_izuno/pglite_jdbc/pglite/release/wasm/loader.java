package io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.wasm;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.core.runtimeTypes;
import java.util.Map;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;

public class loader {
    private loader() {}

    public static Promise<runtimeTypes.RuntimeModule> loadWasmModule(
        runtimeTypes.WasmLoader factory,
        Map<String, Object> moduleOverrides
    ) {
        return factory.apply(moduleOverrides, null);
    }
}
