package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import com.dylibso.chicory.runtime.Instance;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.pglite;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeSyscallSelectIoctlFallocateTest {
    private static final int AT_FDCWD = -100;

    @Test
    void shouldExtendFileSizeForFallocate() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var instance = extractInstance(runtime);
        var path = "/tmp/pglite/base/fallocate.bin";
        var pathPtr = writeCString(instance, 0x3100, path);

        var fd = invokeLong(
            runtime,
            "syscallOpenAt",
            new long[] { AT_FDCWD, pathPtr, 0x40 | 0x2, 0 }
        );
        assertTrue(fd >= 3);

        var result = invokeLong(runtime, "syscallFallocate", new long[] { fd, 0, 0, 8192 });
        assertEquals(0L, result);
        assertTrue(runtime.FS().stat(path).size >= 8192L);

        assertEquals(0L, invokeLong(runtime, "syscallClose", new long[] { fd }));
    }

    @Test
    void shouldApplySelectMaskAndRejectUnknownDescriptor() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var instance = extractInstance(runtime);
        var writeSetPtr = 0x3200;
        instance.memory().writeI32(writeSetPtr, 1 << 1);
        instance.memory().writeI32(writeSetPtr + 4, 0);

        var ready = invokeLong(
            runtime,
            "syscallNewselect",
            new long[] { 2, 0, writeSetPtr, 0, 0 }
        );
        assertEquals(1L, ready);
        assertEquals(1 << 1, instance.memory().readI32(writeSetPtr));

        instance.memory().writeI32(writeSetPtr, 0);
        instance.memory().writeI32(writeSetPtr + 4, 1 << 31);
        var invalid = invokeLong(
            runtime,
            "syscallNewselect",
            new long[] { 64, 0, writeSetPtr, 0, 0 }
        );
        assertEquals(-8L, invalid);
    }

    @Test
    void shouldCountReadyDescriptorsAcrossReadAndWriteSets() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var instance = extractInstance(runtime);
        var readSetPtr = 0x3230;
        var writeSetPtr = 0x3240;
        instance.memory().writeI32(readSetPtr, 1); // fd 0
        instance.memory().writeI32(writeSetPtr, 1 << 1); // fd 1

        var ready = invokeLong(
            runtime,
            "syscallNewselect",
            new long[] { 3, readSetPtr, writeSetPtr, 0, 0 }
        );
        assertEquals(2L, ready);
        assertEquals(1, instance.memory().readI32(readSetPtr));
        assertEquals(1 << 1, instance.memory().readI32(writeSetPtr));
    }

    @Test
    void shouldHonorSelectTimeoutWithoutBlocking() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var instance = extractInstance(runtime);
        var timeoutPtr = 0x3280;
        instance.memory().writeI32(timeoutPtr, 0);
        instance.memory().writeI32(timeoutPtr + 4, 50_000);

        var start = System.nanoTime();
        var ready = invokeLong(
            runtime,
            "syscallNewselect",
            new long[] { 0, 0, 0, 0, timeoutPtr }
        );
        var elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertEquals(0L, ready);
        assertTrue(elapsedMs < 30L, "select should not block in the emscripten compatibility path");
        assertEquals(0, instance.memory().readI32(timeoutPtr));
        assertEquals(50_000, instance.memory().readI32(timeoutPtr + 4));
    }

    @Test
    void shouldRejectNegativeSelectTimeout() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var instance = extractInstance(runtime);
        var timeoutPtr = 0x3290;
        instance.memory().writeI32(timeoutPtr, -1);
        instance.memory().writeI32(timeoutPtr + 4, 0);

        var result = invokeLong(
            runtime,
            "syscallNewselect",
            new long[] { 0, 0, 0, 0, timeoutPtr }
        );
        assertEquals(-28L, result);
    }

    @Test
    void shouldRejectOutOfRangeSelectUsec() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var instance = extractInstance(runtime);
        var timeoutPtr = 0x32A0;
        instance.memory().writeI32(timeoutPtr, 0);
        instance.memory().writeI32(timeoutPtr + 4, 1_000_000);

        var result = invokeLong(
            runtime,
            "syscallNewselect",
            new long[] { 0, 0, 0, 0, timeoutPtr }
        );
        assertEquals(-28L, result);
    }

    @Test
    void shouldMapSelectInvalidSetPointerToEfault() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var result = invokeLong(
            runtime,
            "syscallNewselect",
            new long[] { 32, 0, 0x7FFF_FFF0L, 0, 0 }
        );
        assertEquals(-21L, result);
    }

    @Test
    void shouldHandleIoctlAndReadlinkContracts() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var instance = extractInstance(runtime);

        var argPtr = 0x3300;
        var varargsPtr = 0x3380;
        instance.memory().writeI32(argPtr, 1234);
        instance.memory().writeI32(varargsPtr, argPtr);
        assertEquals(0L, invokeLong(runtime, "syscallIoctl", new long[] { 1, 21519, varargsPtr }));
        assertEquals(0, instance.memory().readI32(argPtr));
        assertEquals(-8L, invokeLong(runtime, "syscallIoctl", new long[] { 9999, 21519, varargsPtr }));
        assertEquals(-28L, invokeLong(runtime, "syscallIoctl", new long[] { 1, 12345, varargsPtr }));
        assertEquals(-28L, invokeLong(runtime, "syscallIoctl", new long[] { 1, 12345, 0x7FFF_FFF0L }));
        assertEquals(-8L, invokeLong(runtime, "syscallIoctl", new long[] { 9999, 12345, varargsPtr }));

        runtime.FS().mkdirTree("/tmp/readlink");
        runtime.FS().writeFile("/tmp/readlink/target.txt", "ok".getBytes(StandardCharsets.UTF_8));
        var targetPtr = writeCString(instance, 0x3400, "target.txt");
        var linkPtr = writeCString(instance, 0x3500, "tmp/readlink/link.txt");
        assertEquals(
            0L,
            invokeLong(runtime, "syscallSymlinkAt", new long[] { targetPtr, AT_FDCWD, linkPtr })
        );

        var regularPathPtr = writeCString(instance, 0x3600, "tmp/readlink/target.txt");
        assertEquals(
            -28L,
            invokeLong(runtime, "syscallReadlinkAt", new long[] { AT_FDCWD, regularPathPtr, 0x3700, 32 })
        );
        assertEquals(
            -28L,
            invokeLong(runtime, "syscallReadlinkAt", new long[] { AT_FDCWD, linkPtr, 0x3700, 0 })
        );

        var truncated = invokeLong(
            runtime,
            "syscallReadlinkAt",
            new long[] { AT_FDCWD, linkPtr, 0x3700, 4 }
        );
        assertEquals(4L, truncated);
        instance.memory().writeByte(0x3704, (byte) 'X');
        invokeLong(runtime, "syscallReadlinkAt", new long[] { AT_FDCWD, linkPtr, 0x3700, 4 });
        var bytes = instance.memory().readBytes(0x3700, 4);
        assertEquals("targ", new String(bytes, StandardCharsets.UTF_8));
        assertEquals('X', instance.memory().read(0x3704) & 0xFF);
    }

    @Test
    void shouldReturnEnottyForTiocgptpeerOnRegularFile() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var instance = extractInstance(runtime);
        var path = "/tmp/pglite/base/ioctl-regular.txt";
        var pathPtr = writeCString(instance, 0x3900, path);
        var fd = invokeLong(
            runtime,
            "syscallOpenAt",
            new long[] { AT_FDCWD, pathPtr, 0x40 | 0x2, 0 }
        );
        assertTrue(fd >= 3);

        assertEquals(-59L, invokeLong(runtime, "syscallIoctl", new long[] { fd, 21531, 0 }));
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
