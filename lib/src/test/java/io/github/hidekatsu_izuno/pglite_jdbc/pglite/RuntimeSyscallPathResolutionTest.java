package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import com.dylibso.chicory.runtime.Instance;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.pglite;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeSyscallPathResolutionTest {
    private static final int AT_FDCWD = -100;
    private static final int AT_EMPTY_PATH = 0x1000;

    @Test
    void shouldResolveAtFdcwdForFaccessat() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        runtime.FS().mkdirTree("/tmp");
        runtime.FS().writeFile("/tmp/faccess.txt", "ok".getBytes(StandardCharsets.UTF_8));
        var instance = extractInstance(runtime);
        var pathPtr = writeCString(instance, 0x2000, "tmp/faccess.txt");

        var result = invokeLong(
            runtime,
            "syscallFaccessAt",
            new long[] { AT_FDCWD, pathPtr, 0, 0 }
        );
        assertEquals(0L, result);
    }

    @Test
    void shouldAllowEmptyPathWhenAtEmptyPathFlagIsSet() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var instance = extractInstance(runtime);
        var pathPtr = writeCString(instance, 0x2100, "");

        var result = invokeLong(
            runtime,
            "syscallFaccessAt",
            new long[] { AT_FDCWD, pathPtr, 0, AT_EMPTY_PATH }
        );
        assertEquals(0L, result);
    }

    @Test
    void shouldRejectUnsupportedFaccessatFlags() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var instance = extractInstance(runtime);
        var pathPtr = writeCString(instance, 0x2200, "tmp/missing.txt");

        var result = invokeLong(
            runtime,
            "syscallFaccessAt",
            new long[] { AT_FDCWD, pathPtr, 0, 0x80 }
        );
        assertEquals(-28L, result);
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

    private static int writeCString(Instance instance, int ptr, String value) {
        var data = (value + "\0").getBytes(StandardCharsets.UTF_8);
        instance.memory().write(ptr, data, 0, data.length);
        return ptr;
    }

    private static long invokeLong(Object target, String methodName, long[] args) {
        try {
            var method = target.getClass().getDeclaredMethod(methodName, long[].class);
            method.setAccessible(true);
            return (long) method.invoke(target, (Object) args);
        } catch (InvocationTargetException e) {
            var cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke runtime method: " + methodName, e);
        }
    }
}
