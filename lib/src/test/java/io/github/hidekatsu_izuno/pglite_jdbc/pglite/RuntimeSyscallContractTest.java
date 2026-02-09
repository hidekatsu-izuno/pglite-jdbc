package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.pglite;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeSyscallContractTest {
    @Test
    void shouldReturnErrnoForRenameatWithInvalidArity() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var result = invokeLong(runtime, "syscallRenameAt", new long[] { 1, 2, 3 });
        assertEquals(-28L, result);
    }

    @Test
    void shouldReturnEbafdForUnknownFdSyncDescriptors() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var fdSyncResult = invokeLong(runtime, "wasiFdSync", new long[] { 9999 });
        var fdatasyncResult = invokeLong(runtime, "syscallFdatasync", new long[] { 9999 });
        assertEquals(8L, fdSyncResult);
        assertEquals(-8L, fdatasyncResult);
    }

    @Test
    void shouldAcceptFadviseForValidDescriptor() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var result = invokeLong(runtime, "syscallFadvise64", new long[] { 1, 0, 0, 0, 0, 0 });
        assertEquals(0L, result);
    }

    @Test
    void shouldRejectFallocateForUnknownDescriptor() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var result = invokeLong(runtime, "syscallFallocate", new long[] { 9999, 0, 0, 0, 0, 0 });
        assertEquals(-8L, result);
    }

    @Test
    void shouldReturnZeroForNewselectCompatibility() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var result = invokeLong(runtime, "syscallNewselect", new long[] { 0, 0, 0, 0, 0 });
        assertEquals(0L, result);
    }

    @Test
    void shouldHandleIoctlTiocgptpeerLikeEmscriptenContract() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var ttyResult = invokeLong(runtime, "syscallIoctl", new long[] { 1, 21531, 0 });
        var nonTtyResult = invokeLong(runtime, "syscallIoctl", new long[] { 9999, 21531, 0 });
        assertEquals(0L, ttyResult);
        assertEquals(-8L, nonTtyResult);
    }

    @Test
    void shouldKeepEnosysForEmscriptenSystemCommandExecution() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var result = invokeLong(runtime, "emscriptenSystem", new long[] { 1024 });
        assertEquals(-52L, result);
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
