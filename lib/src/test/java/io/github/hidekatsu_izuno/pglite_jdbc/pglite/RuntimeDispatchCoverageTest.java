package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.wasm.types.ExternalType;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.pglite;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeDispatchCoverageTest {
    @Test
    void shouldDispatchSocketSyscallsViaEmscriptenSwitch() throws Exception {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var dispatch = findDispatch(mod.getClass());
        var instance = extractInstance(mod.runtime());
        var sockaddrPtr = 0x3300;
        instance.memory().writeShort(sockaddrPtr, (short) 2);
        instance.memory().writeShort(sockaddrPtr + 2, (short) 5432);
        instance.memory().writeI32(sockaddrPtr + 4, 0x0100007f);
        var socketRet = (long[]) dispatch.invoke(
            null,
            mod,
            "__syscall_socket",
            new long[] { 2, 1, 0 },
            1
        );
        assertTrue(socketRet[0] >= 3);

        var bindRet = (long[]) dispatch.invoke(
            null,
            mod,
            "__syscall_bind",
            new long[] { socketRet[0], sockaddrPtr, 16 },
            1
        );
        assertEquals(0L, bindRet[0]);
    }

    @Test
    void shouldKeepEnosysForUnknownSyscallName() throws Exception {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var dispatch = findDispatch(mod.getClass());
        var ret = (long[]) dispatch.invoke(
            null,
            mod,
            "__syscall_nonexistent",
            new long[] {},
            1
        );
        assertEquals(-52L, ret[0]);
    }

    @Test
    void shouldCoverWasmFunctionImportsByRuntimeDispatchers() throws Exception {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var envDispatch = findDispatch(mod.getClass());
        var wasiDispatch = findWasiDispatch(mod.getClass());
        var instance = extractInstance(mod.runtime());
        var section = instance.module().importSection();
        for (var i = 0; i < section.importCount(); i++) {
            var importDecl = section.getImport(i);
            if (importDecl.importType() != ExternalType.FUNCTION) {
                continue;
            }
            if ("env".equals(importDecl.module())) {
                var name = importDecl.name();
                if (name.startsWith("invoke_")) {
                    continue;
                }
                try {
                    envDispatch.invoke(null, mod, name, new long[] {}, 1);
                } catch (InvocationTargetException e) {
                    var cause = e.getCause();
                    if (
                        cause != null &&
                        cause.getMessage() != null &&
                        cause.getMessage().contains("Unknown env import")
                    ) {
                        throw new AssertionError("Unhandled env import: " + name, cause);
                    }
                }
                continue;
            }
            if ("wasi_snapshot_preview1".equals(importDecl.module())) {
                var name = importDecl.name();
                if (
                    "environ_get".equals(name) ||
                    "environ_sizes_get".equals(name) ||
                    "clock_time_get".equals(name) ||
                    "proc_exit".equals(name)
                ) {
                    continue;
                }
                var ret = (long[]) wasiDispatch.invoke(null, mod, name, new long[] {});
                assertEquals(1, ret.length);
            }
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

    private static Method findWasiDispatch(Class<?> runtimeModClass) throws Exception {
        var method = pglite.class.getDeclaredMethod(
            "handleWasiFunction",
            runtimeModClass,
            String.class,
            long[].class
        );
        method.setAccessible(true);
        return method;
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
}
