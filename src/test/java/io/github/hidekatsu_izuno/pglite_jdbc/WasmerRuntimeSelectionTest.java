package io.github.hidekatsu_izuno.pglite_jdbc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.extensionCatalog;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.index;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.pglite;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.WasmRuntimeFactory;
import io.github.hidekatsu_izuno.pglite_jdbc.wasmer.WasmerNativeLoader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class WasmerRuntimeSelectionTest {
    @Test
    void bundledPgliteWasmRequiresExceptionHandling() throws Exception {
        var url = getClass().getClassLoader().getResource(
            extensionCatalog.RELEASE_RESOURCE_ROOT + "pglite.wasm"
        );
        assertNotNull(url);
        try (var input = url.openStream()) {
            assertTrue(WasmRuntimeFactory.hasExceptionHandling(input.readAllBytes()));
        }
    }

    @Test
    void wasmerNativeLoaderReportsCurrentPlatform() {
        var platform = WasmerNativeLoader.platformKey();
        if (platform != null) {
            assertTrue(
                WasmerNativeLoader.isAvailable() || WasmerNativeLoader.loadError() != null,
                "loader should either succeed or record a load error"
            );
        } else {
            assertFalse(WasmerNativeLoader.isAvailable());
        }
    }

    @Test
    void missingLibrarySelectsEndiveAndForcedWasmerFails() throws Exception {
        runSelectionProbe("missing");
    }

    @Test
    void conflictingRuntimeFlagsFailBeforeLoading() throws Exception {
        runSelectionProbe("conflict");
    }

    private void runSelectionProbe(String mode) throws Exception {
        var output = java.nio.file.Files.createTempFile("pglite-selection-", ".log");
        var executable = java.nio.file.Path.of(System.getProperty("java.home"), "bin",
            System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        var process = new ProcessBuilder(executable.toString(), "-Xmx1536m", "-XX:ActiveProcessorCount=1",
            "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
            SelectionProbe.class.getName(), mode).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(90, java.util.concurrent.TimeUnit.SECONDS), "selection probe timed out");
            assertEquals(0, process.exitValue(), java.nio.file.Files.readString(output));
        } finally {
            process.destroyForcibly();
            java.nio.file.Files.deleteIfExists(output);
        }
    }

    public static class SelectionProbe {
        public static void main(String[] args) {
            System.setProperty("pglite.wasmer.library", "/nonexistent/pglite/wasmer.dll");
            if ("conflict".equals(args[0])) {
                System.setProperty("pglite.force_wasmer", "true");
                System.setProperty("pglite.force_endive", "true");
                assertThrows(IllegalStateException.class, () -> WasmRuntimeFactory.create(null, null));
                return;
            }
            assertFalse(WasmerNativeLoader.isAvailable());
            assertNotNull(WasmerNativeLoader.loadError());
            System.setProperty("pglite.force_wasmer", "true");
            assertThrows(IllegalStateException.class, () -> WasmRuntimeFactory.create(null, null));
            System.clearProperty("pglite.force_wasmer");
            var url = SelectionProbe.class.getClassLoader().getResource(extensionCatalog.RELEASE_RESOURCE_ROOT + "pglite.wasm");
            var mod = WasmRuntimeFactory.create(null, url);
            assertTrue(mod instanceof io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.EndivePostgresMod);
        }
    }

    @Test
    void wasmerLoadsAndCallsABundledDynamicExtension() {
        Assumptions.assumeTrue(WasmerNativeLoader.isAvailable());
        var options = new pglite.PGliteOptions();
        options.extensions = java.util.Map.of("hstore", index.extension("hstore"));
        var db = new pglite(options);
        try {
            db.waitReady().join();
            db.execSync("CREATE EXTENSION hstore;", null);
            var result = db.<java.util.Map<String, Object>>querySync(
                "SELECT 'a=>1'::hstore -> 'a' AS value;", null, null
            );
            assertTrue(result.rows().size() == 1);
            assertEquals("1", result.rows().getFirst().get("value"));
            assertThrows(RuntimeException.class, () -> db.execSync("SELECT 1 / 0", null));
            assertEquals(42.0, db.<java.util.Map<String, Object>>querySync(
                "SELECT 42::int AS value", null, null).rows().getFirst().get("value"));
        } finally {
            db.close().join();
        }
    }
}
