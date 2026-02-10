package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtensionLoadFlowTest {
    @Test
    void shouldLoadExtensionFilesIntoRuntimeFs() {
        var fs = new FakeFs();
        var mod = new FakeMod(fs);
        extensionUtils.loadExtensions(mod, args -> {
        });

        assertFalse(fs.createdPreloadedFiles.isEmpty());
        assertTrue(
            fs.createdPreloadedFiles.stream().anyMatch(name -> name.endsWith("/pgcrypto")),
            "expected .so preloaded file for pgcrypto"
        );
        assertFalse(fs.writtenFiles.isEmpty());
    }

    private static final class FakeMod implements extensionUtils.PostgresMod {
        private final FakeFs fs;

        private FakeMod(FakeFs fs) {
            this.fs = fs;
        }

        @Override
        public Map<String, CompletableFuture<extensionUtils.ExtensionBlob>> pg_extensions() {
            return Map.of(
                "pgcrypto",
                CompletableFuture.completedFuture(extensionUtils.loadExtensionBundle("pgcrypto.tar.gz"))
            );
        }

        @Override
        public String WASM_PREFIX() {
            return "/tmp";
        }

        @Override
        public extensionUtils.EmscriptenFS FS() {
            return this.fs;
        }
    }

    private static final class FakeFs implements extensionUtils.EmscriptenFS {
        private final ArrayList<String> createdPreloadedFiles = new ArrayList<>();
        private final ArrayList<String> writtenFiles = new ArrayList<>();

        @Override
        public void createPath(String parent, String path, boolean canRead, boolean canWrite) {
        }

        @Override
        public void createDataFile(
            String path,
            String name,
            Object data,
            boolean canRead,
            boolean canWrite,
            boolean canOwn
        ) {
        }

        @Override
        public void createPreloadedFile(
            String parent,
            String name,
            Object data,
            boolean canRead,
            boolean canWrite,
            extensionUtils.Log onload,
            extensionUtils.Log onerror,
            boolean dontCreateFile
        ) {
            this.createdPreloadedFiles.add(parent + "/" + name);
            assertNotNull(data);
            if (onload != null) {
                onload.apply();
            }
        }

        @Override
        public extensionUtils.AnalyzePathResult analyzePath(String path) {
            var result = new extensionUtils.AnalyzePathResult();
            result.exists = true;
            return result;
        }

        @Override
        public void mkdirTree(String path) {
        }

        @Override
        public void writeFile(String path, byte[] data) {
            this.writtenFiles.add(path);
            assertNotNull(data);
        }

        @Override
        public byte[] readFile(String path) {
            return new byte[0];
        }

        @Override
        public void unlink(String path) {
        }

        @Override
        public void createLazyFile(
            String parent,
            String name,
            Object data,
            boolean canRead,
            boolean canWrite
        ) {
        }

        @Override
        public void createDevice(String parent, String name, Object input, Object output) {
        }

        @Override
        public void mount(Object type, Object opts, String mountpoint) {
        }

        @Override
        public void unmount(String mountpoint) {
        }

        @Override
        public void symlink(String target, String path) {
        }

        @Override
        public extensionUtils.FsStat stat(String path) {
            return new extensionUtils.FsStat();
        }

        @Override
        public String[] readdir(String path) {
            return new String[0];
        }

        @Override
        public void syncfs(boolean populate, extensionUtils.SyncfsCallback done) {
            done.apply(null);
        }

        @Override
        public void registerDevice(int devId, Object ops) {
        }

        @Override
        public int makedev(int major, int minor) {
            return (major << 8) | minor;
        }

        @Override
        public void mkdev(String path, int dev) {
        }
    }
}
