package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.ArrayBuffer;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Blob;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

public final class extensionUtils {

    public static interface ExtensionBlob extends Blob {
        ArrayBuffer arrayBuffer();
    }

    public static interface PostgresMod {
        Map<String, CompletableFuture<ExtensionBlob>> pg_extensions();
        String WASM_PREFIX();
        EmscriptenFS FS();
    }

    @FunctionalInterface
    public interface Log {
        void apply(Object... args);
    }

    public static interface EmscriptenFS {
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
    }

    public static final class AnalyzePathResult {
        public boolean exists;
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

    private static final class TarFile {
        public final String name;
        public final byte[] data;

        private TarFile(String name, byte[] data) {
            this.name = name;
            this.data = data;
        }
    }

    public static ExtensionBlob loadExtensionBundle(String bundlePath) {
        return new ByteArrayBlob(utils.readFile(bundlePath));
    }

    public static ExtensionBlob loadExtensionBundle(URL bundlePath) {
        return new ByteArrayBlob(utils.readFile(bundlePath));
    }

    public static void loadExtensions(
        PostgresMod mod,
        Log log
    ) {
        for (var ext : mod.pg_extensions().keySet()) {
            ExtensionBlob blob;
            try {
                blob = mod.pg_extensions().get(ext).join();
            } catch (RuntimeException err) {
                System.err.println(
                    "Failed to fetch extension: " + ext + " " + err
                );
                continue;
            }
            if (blob != null) {
                var bytes = new Uint8Array(blob.arrayBuffer());
                loadExtension(mod, ext, bytes, log);
            } else {
                System.err.println(
                    "Could not get binary data for extension: " + ext
                );
            }
        }
    }

    private static void loadExtension(
        PostgresMod mod,
        String _ext,
        Uint8Array bytes,
        Log log
    ) {
        var data = untar(bytes);
        data.forEach(
            file -> {
                if (!file.name.startsWith(".")) {
                    var filePath = mod.WASM_PREFIX() + "/" + file.name;
                    if (file.name.endsWith(".so")) {
                        Log extOk = args -> {
                            log.apply(
                                "pgfs:ext OK",
                                filePath,
                                (Object) args
                            );
                        };
                        Log extFail = args -> {
                            log.apply(
                                "pgfs:ext FAIL",
                                filePath,
                                (Object) args
                            );
                        };
                        mod.FS()
                            .createPreloadedFile(
                                dirname(filePath),
                                file.name.substring(
                                    file.name.lastIndexOf("/") + 1,
                                    file.name.length() - 3
                                ),
                                file.data, // There is a type error in Emscripten's FS.createPreloadedFile, this excepts a Uint8Array, but the type is defined as any
                                true,
                                true,
                                extOk,
                                extFail,
                                false
                            );
                    } else {
                        try {
                            var dirIndex = filePath.lastIndexOf("/");
                            var dirPath = dirIndex >= 0
                                ? filePath.substring(0, dirIndex)
                                : "";
                            if (mod.FS().analyzePath(dirPath).exists == false) {
                                mod.FS().mkdirTree(dirPath);
                            }
                            mod.FS().writeFile(filePath, file.data);
                        } catch (RuntimeException e) {
                            System.err.println(
                                "Error writing file " + filePath + " " + e
                            );
                        }
                    }
                }
            }
        );
    }

    private static String dirname(String path) {
        var last = path.lastIndexOf("/");
        if (last > 0) {
            return path.substring(0, last);
        } else {
            return path;
        }
    }

    private static List<TarFile> untar(Uint8Array bytes) {
        var entries = new ArrayList<TarFile>();
        try (
            var input = new ByteArrayInputStream(bytes.toByteArray());
            var tar = new TarArchiveInputStream(input)
        ) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                var size = entry.getSize();
                var data = readTarEntryBytes(tar, size);
                entries.add(new TarFile(entry.getName(), data));
            }
        } catch (IOException e) {
            throw new CompletionException(e);
        }
        return entries;
    }

    private static byte[] readTarEntryBytes(InputStream input, long size)
        throws IOException {
        if (size <= 0) {
            return new byte[0];
        }
        if (size > Integer.MAX_VALUE) {
            throw new IOException("Tar entry too large: " + size);
        }
        var data = new byte[(int) size];
        var offset = 0;
        var read = 0;
        while (offset < data.length) {
            read = input.read(data, offset, data.length - offset);
            if (read == -1) {
                break;
            }
            offset += read;
        }
        if (offset < data.length) {
            var trimmed = new byte[offset];
            System.arraycopy(data, 0, trimmed, 0, offset);
            return trimmed;
        }
        return data;
    }

    private extensionUtils() {
    }
}
