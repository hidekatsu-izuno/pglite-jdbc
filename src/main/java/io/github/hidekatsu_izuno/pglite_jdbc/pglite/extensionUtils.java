package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.ArrayBuffer;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
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
    }

    public interface EmscriptenFS {
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
    }

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

        try (InputStream in = extensionUtils.class.getClassLoader().getResourceAsStream(bundlePath)) {
            if (in == null) {
                throw new IllegalArgumentException("Extension bundle not found: " + bundlePath);
            }
            return maybeUnzip(in.readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] loadExtensionBundle(URL bundlePath) {
        return maybeUnzip(utils.readFile(bundlePath));
    }

    public static Promise<Void> loadExtensions(postgresMod.PostgresMod mod, Consumer<String> log) {
        Consumer<String> logger = log != null ? log : ignored -> {};
        var chain = Promise.resolve((Void) null);
        for (var entry : mod.pg_extensions().entrySet()) {
            var ext = entry.getKey();
            chain = chain.then(ignored ->
                entry.getValue().handle((blob, error) -> {
                    if (error != null) {
                        System.err.println("Failed to fetch extension: " + ext + " " + error);
                        return (Void) null;
                    }
                    if (blob == null) {
                        System.err.println("Could not get binary data for extension: " + ext);
                        return (Void) null;
                    }
                    loadExtension(mod, ext, blob.toByteArray(), logger);
                    return (Void) null;
                })
            );
        }
        return chain;
    }

    private static void loadExtension(
        postgresMod.PostgresMod mod,
        String ext,
        byte[] bytes,
        Consumer<String> log
    ) {
        var tarBytes = maybeUnzip(bytes);
        try (var tarInput = new TarArchiveInputStream(new ByteArrayInputStream(tarBytes))) {
            TarArchiveEntry file;
            while ((file = tarInput.getNextEntry()) != null) {
                if (file.getName().startsWith(".")) {
                    continue;
                }
                var filePath = mod.WASM_PREFIX() + "/" + file.getName();
                if (file.isDirectory()) {
                    mod.FS().mkdirTree(filePath);
                    continue;
                }
                var fileData = tarInput.readAllBytes();
                if (file.getName().endsWith(".so")) {
                    var pathName = file.getName();
                    var lastSlash = pathName.lastIndexOf('/');
                    var leafName = lastSlash >= 0 ? pathName.substring(lastSlash + 1) : pathName;
                    var baseName = leafName.length() > 3 ? leafName.substring(0, leafName.length() - 3) : leafName;
                    mod.FS().createPreloadedFile(
                        dirname(filePath),
                        baseName,
                        fileData,
                        true,
                        true,
                        args -> log.accept("pgfs:ext OK " + ext + " " + filePath),
                        args -> log.accept("pgfs:ext FAIL " + ext + " " + filePath),
                        false
                    );
                    continue;
                }
                try {
                    var dirPath = dirname(filePath);
                    if (!mod.FS().analyzePath(dirPath).exists) {
                        mod.FS().mkdirTree(dirPath);
                    }
                    mod.FS().writeFile(filePath, fileData);
                } catch (Throwable e) {
                    System.err.println("Error writing file " + filePath + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to untar extension " + ext, e);
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
