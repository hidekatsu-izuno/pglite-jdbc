package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import com.dylibso.chicory.runtime.Instance;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.pglite;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeGetdentsParityTest {
    private static final int AT_FDCWD = -100;
    private static final int DENT_SIZE = 280;

    @Test
    void shouldKeepDotInodeStableAcrossDirectoryReopen() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var instance = extractInstance(runtime);
        runtime.FS().mkdirTree("/tmp/getdents-stable");
        runtime.FS().writeFile("/tmp/getdents-stable/file.txt", "ok".getBytes(StandardCharsets.UTF_8));

        var pathPtr = writeCString(instance, 0x6100, "/tmp/getdents-stable");
        var fd1 = invokeLong(
            runtime,
            "syscallOpenAt",
            new long[] { AT_FDCWD, pathPtr, 0, 0 }
        );
        var firstCount = invokeLong(runtime, "syscallGetdents64", new long[] { fd1, 0x6200, DENT_SIZE * 16 });
        assertTrue(firstCount > 0);
        var firstDotIno = parseDirents(instance, 0x6200, (int) firstCount).stream()
            .filter(entry -> ".".equals(entry.name))
            .findFirst()
            .orElseThrow()
            .inode;
        assertEquals(0L, invokeLong(runtime, "syscallClose", new long[] { fd1 }));

        var fd2 = invokeLong(
            runtime,
            "syscallOpenAt",
            new long[] { AT_FDCWD, pathPtr, 0, 0 }
        );
        var secondCount = invokeLong(
            runtime,
            "syscallGetdents64",
            new long[] { fd2, 0x6400, DENT_SIZE * 16 }
        );
        var secondDotIno = parseDirents(instance, 0x6400, (int) secondCount).stream()
            .filter(entry -> ".".equals(entry.name))
            .findFirst()
            .orElseThrow()
            .inode;
        assertEquals(firstDotIno, secondDotIno);
        assertEquals(0L, invokeLong(runtime, "syscallClose", new long[] { fd2 }));
    }

    @Test
    void shouldSkipDeletedEntriesThatRemainInCachedDirectoryListing() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var instance = extractInstance(runtime);
        runtime.FS().mkdirTree("/tmp/getdents-skip");
        runtime.FS().writeFile("/tmp/getdents-skip/keep.txt", "k".getBytes(StandardCharsets.UTF_8));
        runtime.FS().writeFile("/tmp/getdents-skip/ephemeral.txt", "e".getBytes(StandardCharsets.UTF_8));

        var pathPtr = writeCString(instance, 0x6500, "/tmp/getdents-skip");
        var fd = invokeLong(runtime, "syscallOpenAt", new long[] { AT_FDCWD, pathPtr, 0, 0 });
        runtime.FS().unlink("/tmp/getdents-skip/ephemeral.txt");

        var count = invokeLong(runtime, "syscallGetdents64", new long[] { fd, 0x6600, DENT_SIZE * 16 });
        var names = parseDirents(instance, 0x6600, (int) count).stream().map(entry -> entry.name).toList();
        assertTrue(names.contains("keep.txt"));
        assertFalse(names.contains("ephemeral.txt"));
        assertEquals(0L, invokeLong(runtime, "syscallClose", new long[] { fd }));
    }

    private static ArrayList<Dirent> parseDirents(Instance instance, int ptr, int bytes) {
        var out = new ArrayList<Dirent>();
        for (var offset = 0; offset + DENT_SIZE <= bytes; offset += DENT_SIZE) {
            var entryPtr = ptr + offset;
            var reclen = Short.toUnsignedInt(instance.memory().readShort(entryPtr + 16));
            if (reclen != DENT_SIZE) {
                break;
            }
            var inode = instance.memory().readLong(entryPtr);
            var name = readCString(instance, entryPtr + 19, 256);
            if (!name.isEmpty()) {
                out.add(new Dirent(inode, name));
            }
        }
        return out;
    }

    private static String readCString(Instance instance, int ptr, int maxBytes) {
        var out = new StringBuilder();
        for (var i = 0; i < maxBytes; i++) {
            var value = instance.memory().read(ptr + i) & 0xFF;
            if (value == 0) {
                break;
            }
            out.append((char) value);
        }
        return out.toString();
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

    private static final class Dirent {
        private final long inode;
        private final String name;

        private Dirent(long inode, String name) {
            this.inode = inode;
            this.name = name;
        }
    }
}
