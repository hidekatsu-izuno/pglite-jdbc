package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import java.util.zip.GZIPInputStream;
import java.util.function.Consumer;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

public class extensionUtils {
    private extensionUtils() {}

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

    public static Promise<Void> loadExtensions(
        postgresMod.PostgresMod mod,
        Consumer<String> log
    ) {
        Consumer<String> logger = log != null ? log : ignored -> {};
        var chain = Promise.resolve((Void) null);
        for (var entry : mod.pg_extensions().entrySet()) {
            var ext = entry.getKey();
            chain = chain.then(ignored -> entry.getValue().then(bytes -> {
                if (bytes == null || bytes.length == 0) {
                    System.err.println("Could not get binary data for extension: " + ext);
                    return (Void) null;
                }
                loadExtension(mod, ext, bytes, logger);
                return (Void) null;
            }, error -> {
                System.err.println("Failed to fetch extension: " + ext + " " + error);
                return (Void) null;
            }));
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
                        () -> log.accept("pgfs:ext OK " + ext + " " + filePath),
                        () -> log.accept("pgfs:ext FAIL " + ext + " " + filePath),
                        false
                    );
                    continue;
                }
                try {
                    var dirPath = dirname(filePath);
                    if (!mod.FS().analyzePath(dirPath).exists()) {
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
