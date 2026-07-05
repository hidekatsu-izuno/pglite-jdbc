package io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.definitions.tinytar;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.extensionUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class tarUtils {
    public enum DumpTarCompressionOptions {
        none,
        gzip,
        auto,
    }

    private static final int TAR_BLOCK_SIZE = 512;
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
            for (var file : files) {
                var name = file.name();
                if (file.type() == tinytar.DIRTYPE && !name.endsWith("/")) {
                    name = name + "/";
                }
                out.write(tarHeader(name, file.mode(), file.type(), file.modifyTimeMs(), file.data().length));
                if (file.type() == tinytar.REGTYPE && file.data().length > 0) {
                    out.write(file.data());
                    writePadding(out, file.data().length);
                }
            }
            out.write(new byte[TAR_BLOCK_SIZE * 2]);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] tarHeader(String name, int mode, int type, long modifyTimeMs, int size) {
        var header = new byte[TAR_BLOCK_SIZE];
        var normalized = normalizeTarName(name);
        var split = splitTarName(normalized);
        writeString(header, 0, 100, split.name());
        writeOctal(header, 100, 8, mode);
        writeOctal(header, 108, 8, 0);
        writeOctal(header, 116, 8, 0);
        writeOctal(header, 124, 12, type == tinytar.REGTYPE ? size : 0);
        writeOctal(header, 136, 12, modifyTimeMs / 1000);
        for (var i = 148; i < 156; i++) {
            header[i] = (byte) ' ';
        }
        header[156] = (byte) (type == tinytar.DIRTYPE ? '5' : '0');
        writeString(header, 257, 6, "ustar");
        writeString(header, 263, 2, "00");
        writeString(header, 345, 155, split.prefix());
        writeChecksum(header);
        return header;
    }

    private record TarName(String name, String prefix) {}

    private static TarName splitTarName(String name) {
        var bytes = name.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= 100) {
            return new TarName(name, "");
        }
        var slash = name.length();
        while ((slash = name.lastIndexOf('/', slash - 1)) >= 0) {
            var prefix = name.substring(0, slash);
            var suffix = name.substring(slash + 1);
            if (
                prefix.getBytes(StandardCharsets.UTF_8).length <= 155
                    && suffix.getBytes(StandardCharsets.UTF_8).length <= 100
            ) {
                return new TarName(suffix, prefix);
            }
        }
        throw new IllegalArgumentException("Tar entry name is too long: " + name);
    }

    private static String normalizeTarName(String name) {
        var normalized = name.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static void writeString(byte[] header, int offset, int length, String value) {
        var bytes = value.getBytes(StandardCharsets.UTF_8);
        var count = Math.min(bytes.length, length);
        System.arraycopy(bytes, 0, header, offset, count);
    }

    private static void writeOctal(byte[] header, int offset, int length, long value) {
        var text = Long.toOctalString(Math.max(0, value));
        var start = offset + length - text.length() - 1;
        for (var i = offset; i < offset + length; i++) {
            header[i] = 0;
        }
        for (var i = offset; i < start; i++) {
            header[i] = (byte) '0';
        }
        writeString(header, start, text.length(), text);
        header[offset + length - 1] = 0;
    }

    private static void writeChecksum(byte[] header) {
        var sum = 0;
        for (var value : header) {
            sum += value & 0xff;
        }
        var text = String.format("%06o", sum);
        writeString(header, 148, 6, text);
        header[154] = 0;
        header[155] = (byte) ' ';
    }

    private static void writePadding(ByteArrayOutputStream out, int size) {
        var remainder = size % TAR_BLOCK_SIZE;
        if (remainder > 0) {
            out.writeBytes(new byte[TAR_BLOCK_SIZE - remainder]);
        }
    }

    private static List<TarEntry> untar(byte[] tarball) {
        var files = new ArrayList<TarEntry>();
        var offset = 0;
        try {
            while (offset + TAR_BLOCK_SIZE <= tarball.length) {
                var header = java.util.Arrays.copyOfRange(tarball, offset, offset + TAR_BLOCK_SIZE);
                offset += TAR_BLOCK_SIZE;
                if (isZeroBlock(header)) {
                    break;
                }
                var name = readString(header, 0, 100);
                var prefix = readString(header, 345, 155);
                if (!prefix.isEmpty()) {
                    name = prefix + "/" + name;
                }
                var mode = (int) readOctal(header, 100, 8);
                var size = (int) readOctal(header, 124, 12);
                var modifyTimeMs = readOctal(header, 136, 12) * 1000;
                var typeflag = header[156];
                var type = typeflag == '5' ? tinytar.DIRTYPE : tinytar.REGTYPE;
                if (offset + size > tarball.length) {
                    throw new IllegalArgumentException("File is corrupted");
                }
                var data = type == tinytar.REGTYPE
                    ? java.util.Arrays.copyOfRange(tarball, offset, offset + size)
                    : new byte[0];
                offset += size + padding(size);
                files.add(new TarEntry(name, mode, size, type, modifyTimeMs, data));
            }
            return files;
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("File is corrupted")) {
                throw e;
            }
            throw new RuntimeException("File is corrupted", e);
        }
    }

    private static boolean isZeroBlock(byte[] block) {
        for (var value : block) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }

    private static int padding(int size) {
        var remainder = size % TAR_BLOCK_SIZE;
        return remainder == 0 ? 0 : TAR_BLOCK_SIZE - remainder;
    }

    private static String readString(byte[] header, int offset, int length) {
        var end = offset;
        var max = offset + length;
        while (end < max && header[end] != 0) {
            end++;
        }
        return new String(header, offset, end - offset, StandardCharsets.UTF_8).trim();
    }

    private static long readOctal(byte[] header, int offset, int length) {
        var text = readString(header, offset, length).trim();
        if (text.isEmpty()) {
            return 0;
        }
        return Long.parseLong(text, 8);
    }

    private static long dateToUnixTimestamp(Long dateMs) {
        if (dateMs == null) {
            return System.currentTimeMillis() / 1000;
        }
        return dateMs / 1000;
    }
}
