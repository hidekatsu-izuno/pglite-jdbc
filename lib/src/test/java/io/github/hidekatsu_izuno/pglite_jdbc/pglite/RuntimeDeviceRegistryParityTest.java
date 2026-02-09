package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import com.dylibso.chicory.runtime.Instance;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.pglite;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeDeviceRegistryParityTest {
    private static final int AT_FDCWD = -100;

    @Test
    void shouldShareRuntimeDeviceRegistryWithFsAndOpenDeviceNode() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var devId = runtime.makedev(90, 1);
        runtime.registerDevice(
            devId,
            new postgresMod.DeviceOps() {
                @Override
                public int read(byte[] buffer, int offset, int length, int position) {
                    var payload = "dev".getBytes(StandardCharsets.UTF_8);
                    var size = Math.min(length, payload.length);
                    System.arraycopy(payload, 0, buffer, offset, size);
                    return size;
                }

                @Override
                public int write(byte[] buffer, int offset, int length, int position) {
                    return length;
                }

                @Override
                public int llseek(int offset, int whence, int position) {
                    return Math.max(0, offset);
                }
            }
        );
        runtime.mkdev("/dev/runtime-device", devId);
        assertTrue(runtime.FS().analyzePath("/dev/runtime-device").exists);

        var instance = extractInstance(runtime);
        var pathPtr = writeCString(instance, 0x4100, "/dev/runtime-device");
        var fd = invokeLong(
            runtime,
            "syscallOpenAt",
            new long[] { AT_FDCWD, pathPtr, 0, 0 }
        );
        assertTrue(fd >= 0);
        var bufferPtr = 0x4200;
        var read = invokeLong(runtime, "syscallRead", new long[] { fd, bufferPtr, 3 });
        assertEquals(3L, read);
        assertEquals("dev", new String(instance.memory().readBytes(bufferPtr, 3), StandardCharsets.UTF_8));
        assertEquals(0L, invokeLong(runtime, "syscallClose", new long[] { fd }));
    }

    @Test
    void shouldRejectDuplicateRuntimeAndFsDeviceRegistration() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var devId = runtime.makedev(70, 2);
        runtime.registerDevice(
            devId,
            new postgresMod.DeviceOps() {
                @Override
                public int read(byte[] buffer, int offset, int length, int position) {
                    return 0;
                }

                @Override
                public int write(byte[] buffer, int offset, int length, int position) {
                    return 0;
                }

                @Override
                public int llseek(int offset, int whence, int position) {
                    return 0;
                }
            }
        );
        assertThrows(RuntimeException.class, () -> runtime.FS().registerDevice(devId, new Object()));

        runtime.FS().registerDevice(7722, new Object());
        runtime.FS().mkdev("/dev/fs-only", 7722);
        assertTrue(runtime.FS().analyzePath("/dev/fs-only").exists);
        assertThrows(RuntimeException.class, () -> runtime.mkdev("/dev/fs-only", devId));
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
