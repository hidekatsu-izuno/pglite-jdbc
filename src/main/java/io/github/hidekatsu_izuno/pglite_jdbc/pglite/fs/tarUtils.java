package io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

public class tarUtils {
    public enum DumpTarCompressionOptions {
        none,
        gzip,
        auto,
    }

    private tarUtils() {}

    public static byte[] createTarball(Path directoryPath, DumpTarCompressionOptions compression) {
        try {
            var out = new ByteArrayOutputStream();
            try (var tar = new TarArchiveOutputStream(out)) {
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
                if (Files.exists(directoryPath)) {
                    Files.walk(directoryPath).forEach(path -> addPath(tar, directoryPath, path));
                }
            }
            var tarBytes = out.toByteArray();
            if (compression == DumpTarCompressionOptions.gzip || compression == DumpTarCompressionOptions.auto) {
                return zip(tarBytes);
            }
            return tarBytes;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void loadTar(byte[] file, Path pgDataDir, boolean compressed) {
        try {
            var tarBytes = compressed ? unzip(file) : file;
            try (
                var in = new TarArchiveInputStream(new ByteArrayInputStream(tarBytes))
            ) {
                TarArchiveEntry entry;
                while ((entry = in.getNextEntry()) != null) {
                    var outPath = pgDataDir.resolve(entry.getName()).normalize();
                    if (!outPath.startsWith(pgDataDir.normalize())) {
                        throw new IllegalArgumentException("Tar path escapes base dir: " + entry.getName());
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(outPath);
                    } else {
                        Files.createDirectories(outPath.getParent());
                        Files.write(outPath, in.readAllBytes());
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] zip(byte[] file) {
        try {
            var out = new ByteArrayOutputStream();
            try (var gz = new GZIPOutputStream(out)) {
                gz.write(file);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] unzip(byte[] file) {
        try {
            var in = new ByteArrayInputStream(file);
            try (var gz = new GZIPInputStream(in)) {
                return gz.readAllBytes();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void addPath(
        TarArchiveOutputStream tar,
        Path root,
        Path current
    ) {
        try {
            var relative = root.relativize(current).toString().replace('\\', '/');
            if (relative.isEmpty()) {
                return;
            }
            var entry = new TarArchiveEntry(current.toFile(), relative);
            tar.putArchiveEntry(entry);
            if (Files.isRegularFile(current)) {
                tar.write(Files.readAllBytes(current));
            }
            tar.closeArchiveEntry();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
