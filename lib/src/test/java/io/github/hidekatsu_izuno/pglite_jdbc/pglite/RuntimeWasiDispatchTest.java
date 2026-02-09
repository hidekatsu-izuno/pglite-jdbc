package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import com.dylibso.chicory.runtime.Instance;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.pglite;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeWasiDispatchTest {
    @Test
    void shouldHandleWasiEnvironFunctions() throws Exception {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var dispatch = findWasiDispatch(mod.getClass());
        var instance = extractInstance(mod.runtime());
        var countPtr = 0x1200;
        var sizePtr = 0x1204;
        var environPtr = 0x1300;
        var environBufPtr = 0x1400;

        var sizesRet = (long[]) dispatch.invoke(
            null,
            mod,
            "environ_sizes_get",
            new long[] { countPtr, sizePtr }
        );
        assertEquals(0L, sizesRet[0]);
        var count = instance.memory().readI32(countPtr);
        var totalSize = instance.memory().readI32(sizePtr);
        assertTrue(count > 0);
        assertTrue(totalSize > 0);

        var getRet = (long[]) dispatch.invoke(
            null,
            mod,
            "environ_get",
            new long[] { environPtr, environBufPtr }
        );
        assertEquals(0L, getRet[0]);
        var firstPtr = (int) instance.memory().readI32(environPtr);
        assertNotEquals(0, firstPtr);
        var firstEntry = readCString(instance, firstPtr);
        assertTrue(firstEntry.contains("="));
    }

    @Test
    void shouldHandleWasiClockTimeGet() throws Exception {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var dispatch = findWasiDispatch(mod.getClass());
        var instance = extractInstance(mod.runtime());
        var timePtr = 0x1500;

        var ret = (long[]) dispatch.invoke(
            null,
            mod,
            "clock_time_get",
            new long[] { 0, 0, timePtr }
        );
        assertEquals(0L, ret[0]);
        assertTrue(instance.memory().readLong(timePtr) > 0L);
    }

    @Test
    void shouldThrowExitStatusForWasiProcExit() throws Exception {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var dispatch = findWasiDispatch(mod.getClass());
        var error = assertThrows(
            InvocationTargetException.class,
            () -> dispatch.invoke(null, mod, "proc_exit", new long[] { 7 })
        );
        assertEquals("ExitStatusException", error.getCause().getClass().getSimpleName());
        assertTrue(error.getCause().getMessage().contains("proc_exit(7)"));
    }

    @Test
    void shouldDispatchFdDatasyncToFdSyncPath() throws Exception {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var dispatch = pglite.class.getDeclaredMethod(
            "handleWasiFunction",
            mod.getClass(),
            String.class,
            long[].class
        );
        dispatch.setAccessible(true);

        var ret = (long[]) dispatch.invoke(null, mod, "fd_datasync", new long[] { 9999 });
        // EBADF on wasi path
        assertEquals(8L, ret[0]);
    }

    @Test
    void shouldKeepEnosysForUnknownWasiFunction() throws Exception {
        var key = "pglite.wasi.lenient";
        var previous = System.getProperty(key);
        try {
            System.setProperty(key, "true");
            var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
            var dispatch = findWasiDispatch(mod.getClass());
            var ret = (long[]) dispatch.invoke(null, mod, "fd_unknown", new long[] {});
            assertEquals(52L, ret[0]);
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }

    @Test
    void shouldFailFastForUnknownWasiFunctionByDefault() throws Exception {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var dispatch = findWasiDispatch(mod.getClass());
        var error = assertThrows(
            InvocationTargetException.class,
            () -> dispatch.invoke(null, mod, "fd_unknown", new long[] {})
        );
        assertTrue(error.getCause().getMessage().contains("Unknown wasi import"));
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

    private static String readCString(Instance instance, int ptr) {
        var out = new StringBuilder();
        var offset = 0;
        while (offset < 4096) {
            var value = instance.memory().readBytes(ptr + offset, 1)[0] & 0xFF;
            if (value == 0) {
                break;
            }
            out.append((char) value);
            offset++;
        }
        return out.toString();
    }
}
