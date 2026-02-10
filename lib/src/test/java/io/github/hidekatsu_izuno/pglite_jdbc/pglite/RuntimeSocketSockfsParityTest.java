package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import com.dylibso.chicory.runtime.Instance;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.pglite;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeSocketSockfsParityTest {
    @Test
    void shouldRequireConnectionForStreamSendtoEvenWithExplicitDestination() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var instance = extractInstance(runtime);
        var sourceFd = invokeLong(runtime, "syscallSocket", new long[] { 2, 1, 0 });
        var targetFd = invokeLong(runtime, "syscallSocket", new long[] { 2, 1, 0 });
        assertTrue(sourceFd >= 3);
        assertTrue(targetFd >= 3);

        var targetAddrPtr = writeSockaddr(instance, 0x4050, 6101);
        assertEquals(0L, invokeLong(runtime, "syscallBind", new long[] { targetFd, targetAddrPtr, 16 }));

        var sendPtr = writePayload(instance, 0x4060, new byte[] { 9, 8, 7 });
        assertEquals(
            -53L,
            invokeLong(
                runtime,
                "syscallSendto",
                new long[] { sourceFd, sendPtr, 3, 0, targetAddrPtr, 16 }
            )
        );

        assertEquals(0L, invokeLong(runtime, "syscallClose", new long[] { sourceFd }));
        assertEquals(0L, invokeLong(runtime, "syscallClose", new long[] { targetFd }));
    }

    @Test
    void shouldReturnEfaultForInvalidSendtoAndRecvfromPointers() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var instance = extractInstance(runtime);
        var sourceFd = invokeLong(runtime, "syscallSocket", new long[] { 2, 2, 0 });
        var targetFd = invokeLong(runtime, "syscallSocket", new long[] { 2, 2, 0 });
        assertTrue(sourceFd >= 3);
        assertTrue(targetFd >= 3);

        var targetAddrPtr = writeSockaddr(instance, 0x4090, 6102);
        assertEquals(0L, invokeLong(runtime, "syscallBind", new long[] { targetFd, targetAddrPtr, 16 }));
        assertEquals(
            -21L,
            invokeLong(
                runtime,
                "syscallSendto",
                new long[] { sourceFd, 0x7FFF_FFF0L, 8, 0, targetAddrPtr, 16 }
            )
        );

        var sendPtr = writePayload(instance, 0x40A0, new byte[] { 1, 2, 3 });
        assertEquals(
            3L,
            invokeLong(
                runtime,
                "syscallSendto",
                new long[] { sourceFd, sendPtr, 3, 0, targetAddrPtr, 16 }
            )
        );
        assertEquals(
            -21L,
            invokeLong(
                runtime,
                "syscallRecvfrom",
                new long[] { targetFd, 0x7FFF_FFF0L, 3, 0, 0, 0 }
            )
        );
        assertEquals(0L, invokeLong(runtime, "syscallClose", new long[] { sourceFd }));
        assertEquals(0L, invokeLong(runtime, "syscallClose", new long[] { targetFd }));
    }

    @Test
    void shouldReturnZeroWhenRecvfromQueueIsEmpty() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var instance = extractInstance(runtime);
        var fd = invokeLong(runtime, "syscallSocket", new long[] { 2, 2, 0 });
        assertTrue(fd >= 3);

        var recvPtr = 0x4500;
        instance.memory().writeByte(recvPtr, (byte) 0x7F);
        assertEquals(
            -6L,
            invokeLong(runtime, "syscallRecvfrom", new long[] { fd, recvPtr, 16, 0, 0, 0 })
        );
        assertEquals(0x7F, instance.memory().read(recvPtr) & 0xFF);
        assertEquals(0L, invokeLong(runtime, "syscallClose", new long[] { fd }));
    }

    @Test
    void shouldReturnEprotonosupportForInvalidStreamProtocol() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var result = invokeLong(runtime, "syscallSocket", new long[] { 2, 1, 17 });
        assertEquals(-66L, result);
    }

    @Test
    void shouldUseConnectedStreamDestinationAndPreserveUnreadTail() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var instance = extractInstance(runtime);
        var sourceFd = invokeLong(runtime, "syscallSocket", new long[] { 2, 1, 0 });
        var targetFd = invokeLong(runtime, "syscallSocket", new long[] { 2, 1, 0 });
        assertTrue(sourceFd >= 3);
        assertTrue(targetFd >= 3);

        var connectedAddrPtr = writeSockaddr(instance, 0x4100, 6001);
        var alternateAddrPtr = writeSockaddr(instance, 0x4200, 6002);
        assertEquals(0L, invokeLong(runtime, "syscallBind", new long[] { targetFd, connectedAddrPtr, 16 }));
        setConnectedPeer(runtime, sourceFd, "127.0.0.1", 6001, 2);

        var sendPtr = writePayload(instance, 0x4300, new byte[] { 1, 2, 3, 4 });
        assertEquals(
            4L,
            invokeLong(
                runtime,
                "syscallSendto",
                new long[] { sourceFd, sendPtr, 4, 0, 0, 0 }
            )
        );

        var recvPtr = 0x4400;
        assertEquals(
            2L,
            invokeLong(runtime, "syscallRecvfrom", new long[] { targetFd, recvPtr, 2, 0, 0, 0 })
        );
        assertEquals(1, instance.memory().read(recvPtr) & 0xFF);
        assertEquals(2, instance.memory().read(recvPtr + 1) & 0xFF);

        assertEquals(
            2L,
            invokeLong(runtime, "syscallRecvfrom", new long[] { targetFd, recvPtr, 8, 0, 0, 0 })
        );
        assertEquals(3, instance.memory().read(recvPtr) & 0xFF);
        assertEquals(4, instance.memory().read(recvPtr + 1) & 0xFF);

        assertEquals(0L, invokeLong(runtime, "syscallClose", new long[] { sourceFd }));
        assertEquals(0L, invokeLong(runtime, "syscallClose", new long[] { targetFd }));
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

    @SuppressWarnings("unchecked")
    private static void setConnectedPeer(
        Object runtime,
        long fd,
        String address,
        int port,
        int family
    ) {
        try {
            var tableField = runtime.getClass().getDeclaredField("socketStateTable");
            tableField.setAccessible(true);
            var states = (Map<Integer, Object>) tableField.get(runtime);
            var state = states.get((int) fd);
            if (state == null) {
                throw new IllegalStateException("socket state not found for fd=" + fd);
            }
            setIntField(state, "connectedFamily", family);
            setObjectField(state, "connectedAddress", address);
            setIntField(state, "connectedPort", port);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to configure connected peer", e);
        }
    }

    private static void setIntField(Object target, String fieldName, int value)
        throws ReflectiveOperationException {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static void setObjectField(Object target, String fieldName, Object value)
        throws ReflectiveOperationException {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static int writeSockaddr(Instance instance, int ptr, int port) {
        instance.memory().writeShort(ptr, (short) 2);
        instance.memory().writeShort(ptr + 2, Short.reverseBytes((short) port));
        instance.memory().writeI32(ptr + 4, 0x0100007f);
        return ptr;
    }

    private static int writePayload(Instance instance, int ptr, byte[] data) {
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
