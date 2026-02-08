package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import com.dylibso.chicory.runtime.Instance;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.pglite;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeSyscallUnlinkAtTest {
    private static final int AT_FDCWD = -100;
    private static final int AT_REMOVEDIR = 0x200;

    @Test
    void shouldRejectDirectoryUnlinkWithoutRemovedirFlag() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        runtime.FS().mkdirTree("/tmp/unlink-dir");
        var instance = extractInstance(runtime);
        var pathPtr = writeCString(instance, 0x2300, "tmp/unlink-dir");

        var result = invokeLong(
            runtime,
            "syscallUnlinkAt",
            new long[] { AT_FDCWD, pathPtr, 0 }
        );
        assertEquals(-31L, result);
    }

    @Test
    void shouldRemoveDirectoryWhenRemovedirFlagIsSet() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        runtime.FS().mkdirTree("/tmp/remove-dir");
        var instance = extractInstance(runtime);
        var pathPtr = writeCString(instance, 0x2400, "tmp/remove-dir");

        var result = invokeLong(
            runtime,
            "syscallUnlinkAt",
            new long[] { AT_FDCWD, pathPtr, AT_REMOVEDIR }
        );
        assertEquals(0L, result);
        assertFalse(runtime.FS().analyzePath("/tmp/remove-dir").exists);
    }

    @Test
    void shouldSupportRmdirCompatibilityArgShape() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        runtime.FS().mkdirTree("/tmp/remove-dir-compat");
        var instance = extractInstance(runtime);
        var pathPtr = writeCString(instance, 0x2500, "tmp/remove-dir-compat");

        var result = invokeLong(runtime, "syscallUnlinkAt", new long[] { pathPtr });
        assertEquals(0L, result);
        assertFalse(runtime.FS().analyzePath("/tmp/remove-dir-compat").exists);
    }

    @Test
    void shouldCreateSymlinkViaSymlinkAt() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        runtime.FS().mkdirTree("/tmp/symlink");
        runtime.FS().writeFile("/tmp/symlink/target.txt", "ok".getBytes(StandardCharsets.UTF_8));
        var instance = extractInstance(runtime);
        var targetPtr = writeCString(instance, 0x2600, "/tmp/symlink/target.txt");
        var linkPtr = writeCString(instance, 0x2700, "tmp/symlink/link.txt");

        var result = invokeLong(
            runtime,
            "syscallSymlinkAt",
            new long[] { targetPtr, AT_FDCWD, linkPtr }
        );
        assertEquals(0L, result);
        assertTrue(runtime.FS().analyzePath("/tmp/symlink/link.txt").exists);
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
