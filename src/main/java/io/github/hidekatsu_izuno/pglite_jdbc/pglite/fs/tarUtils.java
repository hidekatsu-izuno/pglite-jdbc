package io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.definitions.tinytar;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.extensionUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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

    private static final List<String> COMPRESSED_MIME_TYPES = List.of(
        "application/x-gtar",
        "application/x-tar+gzip",
        "application/x-gzip",
        "application/gzip"
    );

    private tarUtils() {}

    public static byte[] dumpTar(
        extensionUtils.EmscriptenFS fs,
        String pgDataDir,
        String dbname,
        DumpTarCompressionOptions compression
    ) {
        var tarball = createTarball(fs, pgDataDir);
        var zipped = maybeZip(tarball, compression);
        var filename = dbname + (zipped.zipped() ? ".tar.gz" : ".tar");
        return zipped.data();
    }

    public static void loadTar(
        extensionUtils.EmscriptenFS fs,
        byte[] file,
        String pgDataDir,
        String filename,
        String mimeType
    ) {
        var tarball = file;
        var compressed =
            (mimeType != null && COMPRESSED_MIME_TYPES.contains(mimeType))
                || (filename != null && (filename.endsWith(".tgz") || filename.endsWith(".tar.gz")));
        if (compressed) {
            tarball = unzip(tarball);
        }

        List<TarEntry> files;
        try {
            files = untar(tarball);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("File is corrupted")) {
                tarball = unzip(tarball);
                files = untar(tarball);
            } else {
                throw e;
            }
        }

        for (var entry : files) {
            var filePath = pgDataDir + "/" + entry.name();

            var dirPath = filePath.split("/");
            for (var i = 1; i <= dirPath.length - 1; i++) {
                var dir = String.join("/", java.util.Arrays.copyOfRange(dirPath, 0, i));
                if (!fs.analyzePath(dir).exists) {
                    fs.mkdir(dir);
                }
            }

            if (entry.type() == tinytar.REGTYPE) {
                fs.writeFile(filePath, entry.data());
                fs.utime(
                    filePath,
                    dateToUnixTimestamp(entry.modifyTimeMs()),
                    dateToUnixTimestamp(entry.modifyTimeMs())
                );
            } else if (entry.type() == tinytar.DIRTYPE) {
                if (!fs.analyzePath(filePath).exists) {
                    fs.mkdir(filePath);
                }
            }
        }
    }

    public static void loadTar(extensionUtils.EmscriptenFS fs, byte[] file, String pgDataDir) {
        loadTar(fs, file, pgDataDir, null, null);
    }

    public static byte[] createTarball(extensionUtils.EmscriptenFS fs, String directoryPath) {
        var files = readDirectory(fs, directoryPath);
        return tar(files);
    }

    public record ZipResult(byte[] data, boolean zipped) {}

    public static ZipResult maybeZip(byte[] file, DumpTarCompressionOptions compression) {
        if (compression == DumpTarCompressionOptions.none) {
            return new ZipResult(file, false);
        }
        if (compression == DumpTarCompressionOptions.gzip) {
            return new ZipResult(zip(file), true);
        }
        return new ZipResult(zip(file), true);
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

    private record TarEntry(
        String name,
        int mode,
        int size,
        int type,
        long modifyTimeMs,
        byte[] data
    ) {}

    private static List<TarEntry> readDirectory(extensionUtils.EmscriptenFS fs, String path) {
        var files = new ArrayList<TarEntry>();
        traverseDirectory(fs, path, path, files);
        return files;
    }

    private static void traverseDirectory(
        extensionUtils.EmscriptenFS fs,
        String rootPath,
        String currentPath,
        List<TarEntry> files
    ) {
        for (var entry : fs.readdir(currentPath)) {
            if (".".equals(entry) || "..".equals(entry)) {
                continue;
            }
            var fullPath = currentPath + "/" + entry;
            var stats = fs.stat(fullPath);
            var isFile = fs.isFile(stats.mode);
            var data = isFile ? toTarData(fs.readFile(fullPath)) : new byte[0];
            files.add(
                new TarEntry(
                    fullPath.substring(rootPath.length()),
                    stats.mode,
                    data.length,
                    isFile ? tinytar.REGTYPE : tinytar.DIRTYPE,
                    stats.mtimeMs,
                    data
                )
            );
            if (fs.isDir(stats.mode)) {
                traverseDirectory(fs, rootPath, fullPath, files);
            }
        }
    }

    private static byte[] toTarData(byte[] data) {
        return data != null ? data : new byte[0];
    }

    private static byte[] tar(List<TarEntry> files) {
        try {
            var out = new ByteArrayOutputStream();
            try (var tar = new TarArchiveOutputStream(out)) {
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
                for (var file : files) {
                    var entry = new TarArchiveEntry(file.name());
                    entry.setMode(file.mode());
                    entry.setSize(file.size());
                    entry.setModTime(file.modifyTimeMs());
                    if (file.type() == tinytar.DIRTYPE) {
                        entry.setMode(file.mode() | 0x4000);
                    }
                    tar.putArchiveEntry(entry);
                    if (file.type() == tinytar.REGTYPE && file.data().length > 0) {
                        tar.write(file.data());
                    }
                    tar.closeArchiveEntry();
                }
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<TarEntry> untar(byte[] tarball) {
        var files = new ArrayList<TarEntry>();
        try (var in = new TarArchiveInputStream(new ByteArrayInputStream(tarball))) {
            TarArchiveEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                var data = entry.isDirectory() ? new byte[0] : in.readAllBytes();
                files.add(
                    new TarEntry(
                        entry.getName(),
                        entry.getMode(),
                        (int) entry.getSize(),
                        entry.isDirectory() ? tinytar.DIRTYPE : tinytar.REGTYPE,
                        entry.getModTime().getTime(),
                        data
                    )
                );
            }
            return files;
        } catch (IOException e) {
            throw new RuntimeException("File is corrupted", e);
        }
    }

    private static long dateToUnixTimestamp(Long dateMs) {
        if (dateMs == null) {
            return System.currentTimeMillis() / 1000;
        }
        return dateMs / 1000;
    }
}
