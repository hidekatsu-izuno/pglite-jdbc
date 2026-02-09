package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.pglite;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeEmscriptenFsMountSyncTest {
    @Test
    void shouldTrackMountAndUnmountContracts() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var fs = mod.runtime().FS();

        fs.mount("MEMFS", Map.of(), "/mnt/runtime");
        assertTrue(fs.analyzePath("/mnt/runtime").exists);

        assertThrows(
            RuntimeException.class,
            () -> fs.mount("MEMFS", Map.of(), "/mnt/runtime")
        );
        assertThrows(RuntimeException.class, () -> fs.unmount("/mnt/missing"));

        fs.unmount("/mnt/runtime");
        fs.mount("MEMFS", Map.of(), "/mnt/runtime");
    }

    @Test
    void shouldInvokeSyncfsCallbackExactlyOnceAfterMountScan() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var fs = mod.runtime().FS();
        fs.mount("MEMFS", Map.of("name", "a"), "/mnt/a");
        fs.mount("MEMFS", Map.of("name", "b"), "/mnt/b");

        var callbackCount = new AtomicInteger(0);
        var callbackError = new AtomicReference<Exception>();
        fs.syncfs(
            true,
            err -> {
                callbackCount.incrementAndGet();
                callbackError.set(err);
            }
        );
        assertEquals(1, callbackCount.get());
        assertNull(callbackError.get());
    }

    @Test
    void shouldMapNodeFsMountToHostRootForReadAndWrite() throws Exception {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var fs = mod.runtime().FS();
        var hostRoot = Files.createTempDirectory("pglite-nodefs-mount");
        var source = hostRoot.resolve("host.txt");
        Files.writeString(source, "from-host", StandardCharsets.UTF_8);
        fs.mount("NODEFS", Map.of("root", hostRoot.toString()), "/mnt/node");

        var loaded = fs.readFile("/mnt/node/host.txt");
        assertEquals("from-host", new String(loaded, StandardCharsets.UTF_8));

        fs.writeFile("/mnt/node/new.txt", "from-runtime".getBytes(StandardCharsets.UTF_8));
        var written = Files.readString(hostRoot.resolve("new.txt"), StandardCharsets.UTF_8);
        assertEquals("from-runtime", written);
    }

    @Test
    void shouldNormalizeParentSegmentsAtRoot() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var fs = mod.runtime().FS();
        fs.writeFile("/../../outside.bin", "x".getBytes(StandardCharsets.UTF_8));
        assertTrue(fs.analyzePath("/outside.bin").exists);
        assertEquals("x", new String(fs.readFile("/outside.bin"), StandardCharsets.UTF_8));
    }
}
