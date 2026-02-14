package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.zip.GZIPOutputStream;
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
        mod.extensions.put("amcheck", Promise.resolve(createGzipTar(Map.of(
            "lib/amcheck.so",
            "bin".getBytes(),
            "share/extension/amcheck.control",
            "control".getBytes()
        ))));
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
            try (var gzip = new GZIPOutputStream(gzipOut)) {
                gzip.write(tarOut.toByteArray());
            }
            return gzipOut.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class CapturingFs implements postgresMod.FS {
        final Map<String, byte[]> files = new HashMap<>();
        final Map<String, byte[]> preloadedFiles = new HashMap<>();
        final List<String> mkdirs = new CopyOnWriteArrayList<>();

        @Override
        public void createPreloadedFile(
            String parent,
            String name,
            byte[] data,
            boolean canRead,
            boolean canWrite,
            Runnable onLoad,
            Runnable onError,
            boolean dontCreateFile
        ) {
            preloadedFiles.put(parent + "/" + name, data);
            if (onLoad != null) {
                onLoad.run();
            }
        }

        @Override
        public postgresMod.PathInfo analyzePath(String path) {
            return new postgresMod.PathInfo(files.containsKey(path) || mkdirs.contains(path));
        }

        @Override
        public void mkdirTree(String path) {
            mkdirs.add(path);
        }

        @Override
        public void writeFile(String path, byte[] data) {
            files.put(path, data);
        }
    }

    private static class CapturingMod implements postgresMod.PostgresMod {
        final CapturingFs fs;
        final String prefix;
        final Map<String, Promise<byte[]>> extensions = new HashMap<>();
        final AtomicInteger functionId = new AtomicInteger(1);
        final Map<Integer, BiConsumer<Integer, Integer>> callbacks = new HashMap<>();

        CapturingMod(CapturingFs fs, String prefix) {
            this.fs = fs;
            this.prefix = prefix;
        }

        @Override
        public String WASM_PREFIX() {
            return prefix;
        }

        @Override
        public postgresMod.FS FS() {
            return fs;
        }

        @Override
        public Map<String, Promise<byte[]>> pg_extensions() {
            return extensions;
        }

        @Override
        public List<Consumer<postgresMod.PostgresMod>> preInit() {
            return List.of();
        }

        @Override
        public List<Consumer<postgresMod.PostgresMod>> preRun() {
            return List.of();
        }

        @Override
        public List<Consumer<postgresMod.PostgresMod>> postRun() {
            return List.of();
        }

        @Override
        public int INITIAL_MEMORY() {
            return 0;
        }

        @Override
        public int FD_BUFFER_MAX() {
            return 0;
        }

        @Override
        public int addFunction(BiConsumer<Integer, Integer> cb, String signature) {
            var id = functionId.getAndIncrement();
            callbacks.put(id, cb);
            return id;
        }

        @Override
        public void removeFunction(int f) {
            callbacks.remove(f);
        }

        @Override
        public void _queue_message(byte[] message) {}

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
    }
}
