package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.wasm.types.ExternalType;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.pglite;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeEnvImportParityTest {
    @Test
    void shouldSetAndGetTempRet0ViaEnvImports() throws Exception {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        invokeEnv(mod, "setTempRet0", new long[] { 123L }, 0);
        var ret = invokeEnv(mod, "getTempRet0", new long[] {}, 1);
        assertEquals(123L, ret[0]);
    }

    @Test
    void shouldDispatchCallSighandlerViaTable() throws Exception {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var instance = extractInstance(runtime);

        var setIndex = findExportFunctionIndex(instance, "_emscripten_tempret_set");
        var ensureSlot = runtime.getClass()
            .getDeclaredMethod(
                "ensureFunctionTableSlot",
                Instance.class,
                int.class,
                int.class,
                int.class
            );
        ensureSlot.setAccessible(true);
        var tableSize = instance.table(0).size();
        var slot = (int) ensureSlot.invoke(runtime, instance, setIndex, 0, tableSize);

        invokeEnv(mod, "__call_sighandler", new long[] { slot, 77L }, 0);
        var value = instance.export("_emscripten_tempret_get").apply();
        assertEquals(77L, value[0]);
    }

    @Test
    void shouldReturnZeroForAsmConstIntByDefault() throws Exception {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var ret = invokeEnv(mod, "emscripten_asm_const_int", new long[] { 9L, 0L, 0L }, 1);
        assertEquals(0L, ret[0]);
    }

    @Test
    void shouldFailAsmConstIntInStrictMode() {
        var key = "pglite.strict_asm_const";
        var previous = System.getProperty(key);
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        try {
            System.setProperty(key, "true");
            var error = assertThrows(
                RuntimeException.class,
                () -> invokeEnvUnchecked(
                    mod,
                    "emscripten_asm_const_int",
                    new long[] { 10L, 0L, 0L },
                    1
                )
            );
            assertTrue(containsInCauseChain(error, "emscripten_asm_const_int"));
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }

    @Test
    void shouldFailUnknownEnvImportInsteadOfSilentSuccess() throws Exception {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var error = assertThrows(
            RuntimeException.class,
            () -> invokeEnvUnchecked(mod, "unknown_env_import", new long[] { 1L, 2L }, 1)
        );
        assertTrue(containsInCauseChain(error, "unknown_env_import"));
    }

    @Test
    void shouldDispatchInvokeViaTableOwnerWhenCallbackMapEntryIsMissing() throws Exception {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var slot = mod.addFunction((ptr, len) -> ptr + len, "iii");
        try {
            var runtimeMod = extractRuntimeMod(mod.runtime());
            var callbacksField = runtimeMod.getClass().getDeclaredField("callbacks");
            callbacksField.setAccessible(true);
            var callbacks = (Map<Integer, ?>) callbacksField.get(runtimeMod);
            callbacks.remove(slot);

            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                var ret = invokeCallback(mod, "invoke_ii", new long[] { slot, 5L, 7L });
                assertEquals(12L, ret[0]);
            });
        } finally {
            mod.removeFunction(slot);
        }
    }

    private static long[] invokeEnv(
        postgresMod.PostgresMod mod,
        String name,
        long[] args,
        int returnCount
    ) throws Exception {
        var dispatch = findDispatch(mod.getClass());
        try {
            return (long[]) dispatch.invoke(null, mod, name, args, returnCount);
        } catch (InvocationTargetException e) {
            var cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException(cause);
        }
    }

    private static long[] invokeEnvUnchecked(
        postgresMod.PostgresMod mod,
        String name,
        long[] args,
        int returnCount
    ) {
        try {
            return invokeEnv(mod, name, args, returnCount);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static long[] invokeCallback(
        postgresMod.PostgresMod mod,
        String name,
        long[] args
    ) throws Exception {
        var runtimeMod = extractRuntimeMod(mod.runtime());
        var callback = findInvokeCallback(runtimeMod.getClass());
        try {
            return (long[]) callback.invoke(null, runtimeMod, name, args);
        } catch (InvocationTargetException e) {
            var cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException(cause);
        }
    }

    private static Method findDispatch(Class<?> runtimeModClass) throws Exception {
        for (var method : pglite.class.getDeclaredMethods()) {
            if (
                "handleEnvFunction".equals(method.getName()) &&
                method.getParameterCount() == 4
            ) {
                var params = method.getParameterTypes();
                if (
                    params[0].isAssignableFrom(runtimeModClass) &&
                    params[1] == String.class &&
                    params[2] == long[].class &&
                    params[3] == int.class
                ) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        throw new NoSuchMethodException("handleEnvFunction");
    }

    private static Method findInvokeCallback(Class<?> runtimeModClass) throws Exception {
        for (var method : pglite.class.getDeclaredMethods()) {
            if (
                "invokeCallback".equals(method.getName()) &&
                method.getParameterCount() == 3
            ) {
                var params = method.getParameterTypes();
                if (
                    params[0].isAssignableFrom(runtimeModClass) &&
                    params[1] == String.class &&
                    params[2] == long[].class
                ) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        throw new NoSuchMethodException("invokeCallback");
    }

    private static Instance extractInstance(Object runtime) {
        try {
            var modField = runtime.getClass().getDeclaredField("mod");
            modField.setAccessible(true);
            var runtimeMod = modField.get(runtime);
            var instanceField = runtimeMod.getClass().getDeclaredField("instance");
            instanceField.setAccessible(true);
            return (Instance) instanceField.get(runtimeMod);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to extract runtime instance", e);
        }
    }

    private static Object extractRuntimeMod(Object runtime) {
        try {
            var modField = runtime.getClass().getDeclaredField("mod");
            modField.setAccessible(true);
            return modField.get(runtime);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to extract runtime module", e);
        }
    }

    private static int findExportFunctionIndex(Instance instance, String exportName) {
        var section = instance.module().exportSection();
        for (var i = 0; i < section.exportCount(); i++) {
            var export = section.getExport(i);
            if (
                export.exportType() == ExternalType.FUNCTION &&
                exportName.equals(export.name())
            ) {
                return export.index();
            }
        }
        throw new IllegalStateException("Function export not found: " + exportName);
    }

    private static boolean containsInCauseChain(Throwable error, String token) {
        var current = error;
        while (current != null) {
            var message = current.getMessage();
            if (message != null && message.contains(token)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
