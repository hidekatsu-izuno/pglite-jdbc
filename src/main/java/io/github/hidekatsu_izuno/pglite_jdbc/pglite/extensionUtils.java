package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.ArrayBuffer;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

public class extensionUtils {
    private extensionUtils() {}

    public interface ExtensionBlob {
        ArrayBuffer arrayBuffer();

        default byte[] toByteArray() {
            return new Uint8Array(arrayBuffer()).toByteArray();
        }
    }

    @FunctionalInterface
    public interface Log {
        void apply(Object... args);
    }

    @FunctionalInterface
    public interface SyncfsCallback {
        void apply(Exception err);
    }

    public static final class AnalyzePathResult {
        public boolean exists;

        public AnalyzePathResult() {}

        public AnalyzePathResult(boolean exists) {
            this.exists = exists;
        }

        public boolean exists() {
            return exists;
        }
    }

    public static final class FsStat {
        public long size;
        public long mtimeMs;
        public boolean directory;
        public int mode;
    }

    public interface EmscriptenFS extends utils.MinimalFS {
        void createPath(String parent, String path, boolean canRead, boolean canWrite);

        void createDataFile(
            String path,
            String name,
            Object data,
            boolean canRead,
            boolean canWrite,
            boolean canOwn
        );

        void createPreloadedFile(
            String parent,
            String name,
            Object data,
            boolean canRead,
            boolean canWrite,
            Log onload,
            Log onerror,
            boolean dontCreateFile
        );

        AnalyzePathResult analyzePath(String path);

        void mkdirTree(String path);

        void writeFile(String path, byte[] data);

        byte[] readFile(String path);

        void unlink(String path);

        void createLazyFile(
            String parent,
            String name,
            Object data,
            boolean canRead,
            boolean canWrite
        );

        void createDevice(String parent, String name, Object input, Object output);

        void mount(Object type, Object opts, String mountpoint);

        void unmount(String mountpoint);

        void symlink(String target, String path);

        FsStat stat(String path);

        String[] readdir(String path);

        void syncfs(boolean populate, SyncfsCallback done);

        void registerDevice(int devId, Object ops);

        int makedev(int major, int minor);

        void mkdev(String path, int dev);

        void rmdir(String path);

        default void mkdir(String path) {
            mkdirTree(path);
        }

        default void chmod(String path, int mode) {}

        default void utime(String path, long atime, long mtime) {}

        default boolean isDir(int mode) {
            return (mode & 0xF000) == 0x4000;
        }

        default boolean isFile(int mode) {
            return (mode & 0xF000) == 0x8000;
        }

        default io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.base.FSNode createNode(
            io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.base.FSNode parent,
            String name,
            int mode,
            Object dev
        ) {
            var node = new io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.base.FSNode();
            node.parent = parent;
            node.name = name;
            node.mode = mode;
            if (dev instanceof Number number) {
                node.rdev = number.intValue();
            }
            return node;
        }

        default io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.base.ErrnoError errnoError(int code) {
            return new io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.base.ErrnoError(code);
        }

        default Object NODEFS() {
            return extensionUtils.NODEFS_MARKER;
        }

        default void quit() {}

        default String __root() {
            return null;
        }
    }

    public static final Object NODEFS_MARKER = new Object();

    private static final class ByteArrayBlob implements ExtensionBlob {
        private final ArrayBuffer buffer;

        private ByteArrayBlob(byte[] data) {
            this.buffer = new Uint8Array(data).buffer;
        }

        @Override
        public ArrayBuffer arrayBuffer() {
            return this.buffer;
        }
    }

    public static ExtensionBlob toExtensionBlob(byte[] data) {
        return new ByteArrayBlob(data != null ? data : new byte[0]);
    }

    public static byte[] loadExtensionBundle(String bundlePath) {
        var path = Path.of(bundlePath);
        if (Files.exists(path)) {
            try {
                return maybeUnzip(Files.readAllBytes(path));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return maybeUnzip(utils.readFile(bundlePath));
    }

    public static byte[] loadExtensionBundle(URL bundlePath) {
        return maybeUnzip(utils.readFile(bundlePath));
    }

    public static Promise<Void> loadExtensions(postgresMod.PostgresMod mod, Consumer<String> log) {
        Consumer<String> logger = log != null ? log : ignored -> {};
        var preloadPromises = new ArrayList<Promise<Void>>();
        var chain = Promise.resolve((Void) null);
        for (var entry : mod.pg_extensions().entrySet()) {
            var ext = entry.getKey();
            chain = chain.then(ignored ->
                new Promise<Void>((resolve, reject) ->
                    entry.getValue().whenComplete((blob, error) -> {
                        if (error != null) {
                            System.err.println("Failed to fetch extension: " + ext + " " + error);
                            resolve.run(null);
                            return;
                        }
                        if (blob == null) {
                            System.err.println("Could not get binary data for extension: " + ext);
                            resolve.run(null);
                            return;
                        }
                        preloadPromises.addAll(loadExtension(mod, ext, blob.toByteArray(), logger));
                        resolve.run(null);
                    })
                )
            );
        }
        return chain.then(ignored -> Promise.all(preloadPromises).then(x -> null));
    }

    private static List<Promise<Void>> loadExtension(
        postgresMod.PostgresMod mod,
        String ext,
        byte[] bytes,
        Consumer<String> log
    ) {
        var soPreloadPromises = new ArrayList<Promise<Void>>();
        var entries = untarEntries(maybeUnzip(bytes));
        entries.sort((a, b) -> a.name().compareTo(b.name()));
        for (var entry : entries) {
            if (entry.name().endsWith("/")) {
                var dirPath = mod.WASM_PREFIX() + "/" + entry.name();
                if (!mod.FS().analyzePath(dirPath).exists) {
                    mod.FS().mkdirTree(dirPath);
                }
            } else if (!entry.name().startsWith(".")) {
                var filePath = mod.WASM_PREFIX() + "/" + entry.name();
                if (entry.name().endsWith(".so")) {
                    log.accept("pgfs:ext preloading " + filePath);
                    var pathName = entry.name();
                    var lastSlash = pathName.lastIndexOf('/');
                    var soName = lastSlash >= 0 ? pathName.substring(lastSlash + 1) : pathName;
                    var dirPath = dirname(filePath);
                    var soPreload = new Promise<Void>((resolve, reject) -> {
                        mod.FS().createPreloadedFile(
                            dirPath,
                            soName,
                            entry.data(),
                            true,
                            true,
                            args -> {
                                log.accept("pgfs:ext OK " + filePath);
                                resolve.run(null);
                            },
                            args -> {
                                log.accept("pgfs:ext FAIL " + filePath);
                                copyToFS(mod.FS(), filePath, entry.data(), null);
                                resolve.run(null);
                            },
                            false
                        );
                    });
                    soPreloadPromises.add(soPreload);
                } else {
                    copyToFS(mod.FS(), filePath, entry.data(), null);
                }
            }
        }
        return soPreloadPromises;
    }

    public static void copyToFS(
        extensionUtils.EmscriptenFS fs,
        String filePath,
        byte[] data,
        Integer mode
    ) {
        try {
            var dirPath = filePath.substring(0, filePath.lastIndexOf('/'));
            if (!fs.analyzePath(dirPath).exists) {
                fs.mkdirTree(dirPath);
            }
            fs.writeFile(filePath, data);
            if (mode != null) {
                fs.chmod(filePath, mode);
            }
        } catch (Throwable e) {
            System.err.println("Error writing file " + filePath + " " + e);
            throw e;
        }
    }

    private record TarEntryData(String name, byte[] data) {}

    private static List<TarEntryData> untarEntries(byte[] tarBytes) {
        var entries = new ArrayList<TarEntryData>();
        try (var tarInput = new TarArchiveInputStream(new ByteArrayInputStream(tarBytes))) {
            TarArchiveEntry file;
            while ((file = tarInput.getNextEntry()) != null) {
                var name = file.getName();
                if (file.isDirectory() && !name.endsWith("/")) {
                    name = name + "/";
                }
                entries.add(new TarEntryData(name, file.isDirectory() ? new byte[0] : tarInput.readAllBytes()));
            }
            return entries;
        } catch (IOException e) {
            throw new RuntimeException("Failed to untar extension bundle", e);
        }
    }

    private static byte[] maybeUnzip(byte[] bytes) {
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return gzip.readAllBytes();
        } catch (IOException ignored) {
            return bytes;
        }
    }

    private static String dirname(String path) {
        var last = path.lastIndexOf('/');
        if (last > 0) {
            return path.substring(0, last);
        }
        return path;
    }
}
