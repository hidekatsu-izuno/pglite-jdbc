package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.preload.dataManifest_generated;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.preload.fsBootstrapPaths_generated;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.runtime.syscalls;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.wasm.dylib;
import org.junit.jupiter.api.Test;

public class RuntimeDeviceRegistryParityTest {
    @Test
    void shouldProvideDynamicLibrarySymbolTable() {
        var table = dylib.createDynamicLibrarySymbolTable();
        table.register("foo", 123);

        assertTrue(table.has("foo"));
        assertEquals(123, table.resolve("foo"));
        assertFalse(table.has("missing"));
        assertThrows(IllegalArgumentException.class, () -> table.resolve("missing"));
    }

    @Test
    void shouldRecordRecentSyscallsWithBoundedBuffer() {
        var recorder = syscalls.createSyscallRecorder(2);
        recorder.record("open", new Object[] { "a" }, 1);
        recorder.record("read", new Object[] { 1, 32 }, 16);
        recorder.record("close", new Object[] { 1 }, 0);

        var recent = recorder.recent();
        assertEquals(2, recent.size());
        assertEquals("read", recent.getFirst().name());
        assertEquals("close", recent.getLast().name());
    }

    @Test
    void shouldLoadGeneratedManifestAndBootstrapPaths() {
        var manifest = dataManifest_generated.dataManifest;
        var paths = fsBootstrapPaths_generated.fsBootstrapPaths;

        assertNotNull(manifest);
        assertNotNull(paths);
        assertTrue(paths.size() > 0);
    }
}
