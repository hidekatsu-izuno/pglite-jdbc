package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import com.dylibso.chicory.runtime.Instance;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.pglite;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeNumericParityTest {
    private static final int AT_FDCWD = -100;
    private static final long INT53_OVERFLOW = 9_007_199_254_740_993L;

    @Test
    void shouldReturnOverflowCodeForFallocateOutsideI53Range() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var instance = extractInstance(runtime);
        var path = "/tmp/pglite/base/overflow-fallocate.bin";
        var pathPtr = writeCString(instance, 0x7100, path);
        var fd = invokeLong(
            runtime,
            "syscallOpenAt",
            new long[] { AT_FDCWD, pathPtr, 0x40 | 0x2, 0 }
        );
        assertTrue(fd >= 3);
        assertEquals(61L, invokeLong(runtime, "syscallFallocate", new long[] { fd, 0, INT53_OVERFLOW, 1 }));
        assertEquals(0L, invokeLong(runtime, "syscallClose", new long[] { fd }));
    }

    @Test
    void shouldReturnOverflowCodeForTruncateOutsideI53Range() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var instance = extractInstance(runtime);
        runtime.FS().writeFile("/tmp/pglite/base/overflow-truncate.bin", new byte[] { 1, 2, 3 });
        var pathPtr = writeCString(instance, 0x7200, "/tmp/pglite/base/overflow-truncate.bin");
        assertEquals(61L, invokeLong(runtime, "syscallTruncate64", new long[] { pathPtr, INT53_OVERFLOW, 0 }));
    }

    @Test
    void shouldKeepErrnoForNegativeTruncateLength() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var instance = extractInstance(runtime);
        var path = "/tmp/pglite/base/negative-truncate.bin";
        var pathPtr = writeCString(instance, 0x7300, path);
        var fd = invokeLong(
            runtime,
            "syscallOpenAt",
            new long[] { AT_FDCWD, pathPtr, 0x40 | 0x2, 0 }
        );
        assertTrue(fd >= 3);
        assertEquals(-28L, invokeLong(runtime, "syscallTruncate64", new long[] { fd, -1 }));
        assertEquals(0L, invokeLong(runtime, "syscallClose", new long[] { fd }));
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
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke runtime method: " + methodName, e);
        }
    }
}
