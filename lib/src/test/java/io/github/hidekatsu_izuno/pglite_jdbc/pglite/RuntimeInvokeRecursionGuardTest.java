package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import com.dylibso.chicory.runtime.Instance;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.pglite;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeInvokeRecursionGuardTest {
    @Test
    void shouldShortCircuitWhenTablePointsToInvokeImport() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtimeMod = extractRuntimeMod(mod.runtime());
        var instance = extractInstance(runtimeMod);
        var invokeFunction = findInvokeImportFunction(instance);
        assertNotNull(invokeFunction, "expected env.invoke_* import function in module");
        var table = instance.table(0);
        var slot = table.grow(1, -1, null);
        assertTrue(slot >= 0);
        table.setRef(slot, invokeFunction.index(), instance);

        var result = invokeCallback(runtimeMod, invokeFunction.name(), new long[] { slot, 0 });
        var invokeName = invokeFunction.name();
        if ("invoke_v".equals(invokeName) || "invoke_vi".equals(invokeName) || "invoke_vii".equals(invokeName)) {
            assertEquals(0, result.length);
        } else {
            assertEquals(1, result.length);
            assertEquals(0L, result[0]);
        }
    }

    private static Object extractRuntimeMod(Object runtime) {
        try {
            var modField = runtime.getClass().getDeclaredField("mod");
            modField.setAccessible(true);
            return modField.get(runtime);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to extract runtime mod", e);
        }
    }

    private static Instance extractInstance(Object runtimeMod) {
        try {
            var instanceField = runtimeMod.getClass().getDeclaredField("instance");
            instanceField.setAccessible(true);
            return (Instance) instanceField.get(runtimeMod);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to extract runtime instance", e);
        }
    }

    private static InvokeImport findInvokeImportFunction(Instance instance) {
        var importSection = instance.module().importSection();
        var importedFunctionIndex = 0;
        for (var i = 0; i < importSection.importCount(); i++) {
            var importDecl = importSection.getImport(i);
            if (importDecl.importType() != com.dylibso.chicory.wasm.types.ExternalType.FUNCTION) {
                continue;
            }
            if (
                "env".equals(importDecl.module()) &&
                importDecl.name().startsWith("invoke_")
            ) {
                return new InvokeImport(importDecl.name(), importedFunctionIndex);
            }
            importedFunctionIndex++;
        }
        return null;
    }

    private static long[] invokeCallback(Object runtimeMod, String name, long[] args) {
        try {
            var method = pglite.class.getDeclaredMethod(
                "invokeCallback",
                runtimeMod.getClass(),
                String.class,
                long[].class
            );
            method.setAccessible(true);
            return (long[]) method.invoke(null, runtimeMod, name, (Object) args);
        } catch (InvocationTargetException e) {
            var cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke invokeCallback", e);
        }
    }

    private static final class InvokeImport {
        private final String name;
        private final int index;

        private InvokeImport(String name, int index) {
            this.name = name;
            this.index = index;
        }

        private String name() {
            return this.name;
        }

        private int index() {
            return this.index;
        }
    }
}
