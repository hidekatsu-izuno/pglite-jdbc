package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.pglite;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeSyscallSocketTest {
    @Test
    void shouldCreateSocketAndSupportBasicOps() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();

        var fd = invokeLong(runtime, "syscallSocket", new long[] { 2, 1, 0 });
        assertTrue(fd >= 3);
        assertEquals(0L, invokeLong(runtime, "syscallBind", new long[] { fd, 0, 0 }));
        assertEquals(4L, invokeLong(runtime, "syscallSendto", new long[] { fd, 0, 4, 0, 0, 0 }));
        assertEquals(0L, invokeLong(runtime, "syscallRecvfrom", new long[] { fd, 0, 8, 0, 0, 0 }));
        assertEquals(0L, invokeLong(runtime, "syscallClose", new long[] { fd }));
        assertEquals(-8L, invokeLong(runtime, "syscallSendto", new long[] { fd, 0, 1, 0, 0, 0 }));
    }

    @Test
    void shouldAllowNonInetFamilyLikeEmscriptenSockfs() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var fd = invokeLong(runtime, "syscallSocket", new long[] { 999, 1, 0 });
        assertTrue(fd >= 3);
        assertEquals(0L, invokeLong(runtime, "syscallClose", new long[] { fd }));
    }

    @Test
    void shouldRejectUnsupportedStreamProtocol() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        assertEquals(-66L, invokeLong(runtime, "syscallSocket", new long[] { 2, 1, 17 }));
    }

    @Test
    void shouldMaskSocketTypeFlagsBeforeValidation() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var cloexecAndNonblockStream = 0x80800 | 1;
        var fd = invokeLong(
            runtime,
            "syscallSocket",
            new long[] { 2, cloexecAndNonblockStream, 6 }
        );
        assertTrue(fd >= 3);
        assertEquals(0L, invokeLong(runtime, "syscallClose", new long[] { fd }));
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
