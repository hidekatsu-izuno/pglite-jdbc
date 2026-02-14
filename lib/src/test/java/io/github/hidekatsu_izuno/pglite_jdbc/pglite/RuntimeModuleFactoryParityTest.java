package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.core.moduleFactory;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.core.runtimeTypes;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class RuntimeModuleFactoryParityTest {
    @Test
    void shouldAttachHandlersAndBindState() {
        var capturedState = new AtomicReference<runtimeTypes.RuntimeState>();
        var expectedModule = new runtimeTypes.MutableRuntimeModule();
        var loader = moduleFactory.createModuleFactory(new moduleFactory.CustomModuleFactoryOptions() {
            @Override
            public runtimeTypes.WasmLoader baseFactory() {
                return (moduleArg, ignored) -> Promise.resolve(expectedModule);
            }

            @Override
            public List<String> sourceTextsToValidate() {
                return List.of("const x = 1;");
            }

            @Override
            public java.util.function.Consumer<runtimeTypes.RuntimeState> onStateCreated() {
                return capturedState::set;
            }
        });

        var actual = loader.apply(new HashMap<>(), null).join();

        assertSame(expectedModule, actual);
        assertNotNull(capturedState.get());
        assertSame(expectedModule, capturedState.get().module());
        assertTrue(capturedState.get().asmHandlers().containsKey(2537480));
    }

    @Test
    void shouldRejectDynamicEvalSourceText() {
        var loader = moduleFactory.createModuleFactory(new moduleFactory.CustomModuleFactoryOptions() {
            @Override
            public List<String> sourceTextsToValidate() {
                return List.of("eval('1+1')");
            }
        });

        assertThrows(IllegalArgumentException.class, () -> loader.apply(new HashMap<>(), null));
    }
}
