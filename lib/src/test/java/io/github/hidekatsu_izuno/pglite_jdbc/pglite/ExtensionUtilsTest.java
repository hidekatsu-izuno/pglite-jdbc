package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.Test;

public class ExtensionUtilsTest {
    @Test
    void shouldLoadExtensionBundleFromClasspath() {
        var bytes = extensionUtils.loadExtensionBundle("amcheck.tar.gz");
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
    }

    @Test
    void shouldUntarAndLoadExtensionsIntoModuleFs() {
        var fs = new CapturingFs();
        var mod = new CapturingMod(fs, "/tmp/pglite");
        mod.extensions.put(
            "amcheck",
            CompletableFuture.completedFuture(
                extensionUtils.toExtensionBlob(
                    createGzipTar(Map.of(
                        "lib/amcheck.so",
                        "bin".getBytes(),
                        "share/extension/amcheck.control",
                        "control".getBytes()
                    ))
                )
            )
        );
        var logs = new CopyOnWriteArrayList<String>();

        extensionUtils.loadExtensions(mod, logs::add).join();

        assertEquals(1, fs.preloadedFiles.size());
        assertTrue(fs.preloadedFiles.containsKey("/tmp/pglite/lib/amcheck"));
        assertArrayEquals("bin".getBytes(), fs.preloadedFiles.get("/tmp/pglite/lib/amcheck"));
        assertArrayEquals(
            "control".getBytes(),
            fs.files.get("/tmp/pglite/share/extension/amcheck.control")
        );
        assertTrue(logs.stream().anyMatch(line -> line.contains("pgfs:ext OK")));
    }

    private static byte[] createGzipTar(Map<String, byte[]> files) {
        try {
            var tarOut = new ByteArrayOutputStream();
            try (var tar = new TarArchiveOutputStream(tarOut)) {
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
                for (var entry : files.entrySet()) {
                    var tarEntry = new TarArchiveEntry(entry.getKey());
                    tarEntry.setSize(entry.getValue().length);
                    tar.putArchiveEntry(tarEntry);
                    tar.write(entry.getValue());
                    tar.closeArchiveEntry();
                }
            }
            var gzipOut = new ByteArrayOutputStream();
            try (var gzip = new java.util.zip.GZIPOutputStream(gzipOut)) {
                gzip.write(tarOut.toByteArray());
            }
            return gzipOut.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class CapturingFs implements extensionUtils.EmscriptenFS {
        final Map<String, byte[]> files = new HashMap<>();
        final Map<String, byte[]> preloadedFiles = new HashMap<>();
        final List<String> mkdirs = new CopyOnWriteArrayList<>();

        @Override
        public void createPath(String parent, String path, boolean canRead, boolean canWrite) {}

        @Override
        public void createDataFile(
            String path,
            String name,
            Object data,
            boolean canRead,
            boolean canWrite,
            boolean canOwn
        ) {}

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
            var bytes = data instanceof byte[]
                ? (byte[]) data
                : data instanceof Uint8Array u8
                    ? u8.toByteArray()
                    : new byte[0];
            preloadedFiles.put(parent + "/" + name, bytes);
            if (onload != null) {
                onload.apply(name);
            }
        }

        @Override
        public extensionUtils.AnalyzePathResult analyzePath(String path) {
            return new extensionUtils.AnalyzePathResult(files.containsKey(path) || mkdirs.contains(path));
        }

        @Override
        public void mkdirTree(String path) {
            mkdirs.add(path);
        }

        @Override
        public void writeFile(String path, byte[] data) {
            files.put(path, data);
        }

        @Override
        public byte[] readFile(String path) {
            return files.getOrDefault(path, new byte[0]);
        }

        @Override
        public void unlink(String path) {
            files.remove(path);
        }

        @Override
        public void createLazyFile(String parent, String name, Object data, boolean canRead, boolean canWrite) {}

        @Override
        public void createDevice(String parent, String name, Object input, Object output) {}

        @Override
        public void mount(Object type, Object opts, String mountpoint) {}

        @Override
        public void unmount(String mountpoint) {}

        @Override
        public void symlink(String target, String path) {}

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
            if (done != null) {
                done.apply(null);
            }
        }

        @Override
        public void registerDevice(int devId, Object ops) {}

        @Override
        public int makedev(int major, int minor) {
            return 0;
        }

        @Override
        public void mkdev(String path, int dev) {}
    }

    private static class CapturingMod implements postgresMod.PostgresMod {
        final CapturingFs fs;
        final String prefix;
        final Map<String, CompletableFuture<extensionUtils.ExtensionBlob>> extensions = new HashMap<>();
        final AtomicInteger functionId = new AtomicInteger(1);
        final Map<Integer, postgresMod.ReadWriteCallback> callbacks = new HashMap<>();

        CapturingMod(CapturingFs fs, String prefix) {
            this.fs = fs;
            this.prefix = prefix;
        }

        @Override
        public String WASM_PREFIX() {
            return prefix;
        }

        @Override
        public Integer INITIAL_MEMORY() {
            return 0;
        }

        @Override
        public Integer FD_BUFFER_MAX() {
            return 0;
        }

        @Override
        public void setFD_BUFFER_MAX(Integer value) {}

        @Override
        public Uint8Array HEAP8() {
            return new Uint8Array(0);
        }

        @Override
        public Uint8Array HEAPU8() {
            return new Uint8Array(0);
        }

        @Override
        public extensionUtils.EmscriptenFS FS() {
            return fs;
        }

        @Override
        public Map<String, CompletableFuture<extensionUtils.ExtensionBlob>> pg_extensions() {
            return extensions;
        }

        @Override
        public int addFunction(postgresMod.ReadWriteCallback cb, String signature) {
            var id = functionId.getAndIncrement();
            callbacks.put(id, cb);
            return id;
        }

        @Override
        public void removeFunction(int f) {
            callbacks.remove(f);
        }

        @Override
        public void _set_read_write_cbs(int readCb, int writeCb) {}

        @Override
        public void _interactive_write(int msgLength) {}

        @Override
        public void _interactive_one(int length, int peek) {}

        @Override
        public int _pgl_initdb() {
            return 0;
        }

        @Override
        public void _pgl_backend() {}

        @Override
        public void _pgl_shutdown() {}

        @Override
        public void copyFromHeap(int ptr, byte[] dest, int destOffset, int length) {}

        @Override
        public void copyToHeap(int ptr, byte[] src, int srcOffset, int length) {}

        @Override
        public postgresMod.EmscriptenRuntime runtime() {
            return new postgresMod.EmscriptenRuntime() {
                @Override
                public extensionUtils.EmscriptenFS FS() {
                    return fs;
                }

                @Override
                public byte[] getPreloadedPackage(String name, int size) {
                    return new byte[0];
                }

                @Override
                public void addRunDependency(String key) {}

                @Override
                public void removeRunDependency(String key) {}

                @Override
                public void preRun() {}

                @Override
                public void postRun() {}

                @Override
                public int makedev(int major, int minor) {
                    return 0;
                }

                @Override
                public void registerDevice(int devId, postgresMod.DeviceOps ops) {}

                @Override
                public void mkdev(String path, int devId) {}
            };
        }
    }
}
