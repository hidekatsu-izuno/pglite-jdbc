package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import com.dylibso.chicory.runtime.Instance;
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
        var instance = extractInstance(runtime);
        var sockaddrPtr = 0x3000;
        writeIpv4Sockaddr(instance, sockaddrPtr, 5432);

        var fd = invokeLong(runtime, "syscallSocket", new long[] { 2, 1, 0 });
        assertTrue(fd >= 3);
        assertEquals(0L, invokeLong(runtime, "syscallBind", new long[] { fd, sockaddrPtr, 16 }));
        assertEquals(
            4L,
            invokeLong(runtime, "syscallSendto", new long[] { fd, 0, 4, 0, sockaddrPtr, 16 })
        );
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

    @Test
    void shouldValidateSockaddrFamilyAndLengthForBindAndSendto() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var instance = extractInstance(runtime);
        var fd = invokeLong(runtime, "syscallSocket", new long[] { 2, 1, 0 });
        assertTrue(fd >= 3);

        var unsupportedFamily = 0x3200;
        instance.memory().writeShort(unsupportedFamily, (short) 7);
        assertEquals(-5L, invokeLong(runtime, "syscallBind", new long[] { fd, unsupportedFamily, 16 }));

        var invalidIpv4Len = 0x3210;
        writeIpv4Sockaddr(instance, invalidIpv4Len, 5432);
        assertEquals(-28L, invokeLong(runtime, "syscallBind", new long[] { fd, invalidIpv4Len, 12 }));

        var invalidIpv6Len = 0x3220;
        instance.memory().writeShort(invalidIpv6Len, (short) 10);
        assertEquals(-28L, invokeLong(runtime, "syscallSendto", new long[] { fd, 0, 1, 0, invalidIpv6Len, 16 }));

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

    private static void writeIpv4Sockaddr(Instance instance, int ptr, int port) {
        instance.memory().writeShort(ptr, (short) 2);
        instance.memory().writeShort(ptr + 2, (short) port);
        instance.memory().writeI32(ptr + 4, 0x0100007f);
    }
}
