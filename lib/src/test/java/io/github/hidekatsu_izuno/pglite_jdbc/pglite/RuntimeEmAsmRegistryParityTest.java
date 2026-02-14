package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.core.runtimeState;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.core.runtimeTypes;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.em.emAsmRegistry_generated;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.em.emResolver;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

public class RuntimeEmAsmRegistryParityTest {
    @Test
    void shouldApplyGeneratedEmAsmHandlers() {
        var state = runtimeState.createRuntimeState(
            new HashMap<>(),
            runtimeTypes.DataManifest.empty()
        );
        emResolver.attachEmAsmHandlers(state, emAsmRegistry_generated.emAsmRegistry);
        var context = new runtimeTypes.EmResolverContext(
            state,
            new runtimeTypes.MutableRuntimeModule()
        );

        var initHandler = state.asmHandlers().get(2537480);
        assertNotNull(initHandler);
        initHandler.apply(context, 4096);
        assertEquals(4096, state.moduleArg().get("FD_BUFFER_MAX"));
        assertEquals(false, state.moduleArg().get("is_worker"));

        var setCustomMessageHandler = state.asmHandlers().get(2537652);
        assertNotNull(setCustomMessageHandler);
        setCustomMessageHandler.apply(context);
        @SuppressWarnings("unchecked")
        var postMessage = (Consumer<Object>) state.moduleArg().get("postMessage");
        assertNotNull(postMessage);
        postMessage.accept("hello");
        assertTrue(state.diagnostics().stream().anyMatch(line -> line.contains("onCustomMessage")));

        var installPostMessageHandler = state.asmHandlers().get(2537781);
        assertNotNull(installPostMessageHandler);
        installPostMessageHandler.apply(context);
        @SuppressWarnings("unchecked")
        var customPostMessage = (Consumer<Object>) state.moduleArg().get("postMessage");
        customPostMessage.accept(Map.of("type", "stdin", "data", "abc"));
        customPostMessage.accept(Map.of("type", "raw", "data", "ignore"));
        customPostMessage.accept(Map.of("type", "other", "data", "warn"));
        assertTrue(state.diagnostics().stream().anyMatch(line -> line.contains("stdin:abc:4096")));
        assertTrue(state.diagnostics().stream().anyMatch(line -> line.contains("custom_postMessage?")));
        assertFalse(state.diagnostics().stream().anyMatch(line -> line.contains("raw")));
    }
}
