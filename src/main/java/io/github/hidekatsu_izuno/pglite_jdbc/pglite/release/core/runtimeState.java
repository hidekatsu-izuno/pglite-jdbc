package io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class runtimeState {
    private runtimeState() {}

    public static runtimeTypes.RuntimeState createRuntimeState(
        Map<String, Object> moduleArg,
        runtimeTypes.DataManifest manifest
    ) {
        return new runtimeTypes.RuntimeState(
            moduleArg != null ? moduleArg : new HashMap<>(),
            null,
            new HashMap<>(),
            new HashMap<>(),
            manifest != null ? manifest : runtimeTypes.DataManifest.empty(),
            new ArrayList<>()
        );
    }

    public static runtimeTypes.RuntimeModule bindRuntimeModule(
        runtimeTypes.RuntimeState state,
        runtimeTypes.RuntimeModule module
    ) {
        state.setModule(module);
        return module;
    }
}
