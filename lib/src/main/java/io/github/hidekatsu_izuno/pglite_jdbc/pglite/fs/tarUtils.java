package io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Blob;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.File;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

public final class tarUtils {
    public static final class DumpTarCompressionOptions {
        public static final String none = "none";
        public static final String gzip = "gzip";
        public static final String auto = "auto";

        private DumpTarCompressionOptions() {
        }
    }

    public interface TarBlob extends Blob {
        byte[] arrayBuffer();
        String type();
        String name();
    }

    public static final class FileBlob implements File, TarBlob {
        private final byte[] data;
        private final String filename;
        private final String mimeType;

        public FileBlob(byte[] data, String filename, String mimeType) {
            this.data = data;
            this.filename = filename;
            this.mimeType = mimeType;
        }

        @Override
        public byte[] arrayBuffer() {
            return this.data;
        }

        @Override
        public String type() {
            return this.mimeType;
        }

        @Override
        public String name() {
            return this.filename;
        }
    }

    public static final int REGTYPE = 0;
    public static final int DIRTYPE = 5;

    public static CompletableFuture<Blob> dumpTar(
        base.FS FS,
        String pgDataDir,
        String dbname,
        String compression
    ) {
        var tarball = createTarball(FS, pgDataDir);
        var result = maybeZip(tarball, compression);
        var compressed = result.data;
        var zipped = result.zipped;
        var filename = dbname + (zipped ? ".tar.gz" : ".tar");
        var type = zipped ? "application/x-gzip" : "application/x-tar";
        return CompletableFuture.completedFuture(
            new FileBlob(compressed.toByteArray(), filename, type)
        );
    }

    private static final String[] compressedMimeTypes = {
        "application/x-gtar",
        "application/x-tar+gzip",
        "application/x-gzip",
        "application/gzip",
    };

    public static CompletableFuture<Void> loadTar(
        base.FS FS,
        TarBlob file,
        String pgDataDir
    ) {
        return CompletableFuture.runAsync(
            () -> {
                var tarball = new Uint8Array(file.arrayBuffer());
                var filename = file.name();
                var compressed = isCompressed(file.type(), filename);
                if (compressed) {
                    tarball = unzip(tarball);
                }

                List<TarFile> files;
                try {
                    files = untar(tarball);
                } catch (RuntimeException e) {
                    var message = e.getMessage();
                    if (message != null && message.contains("File is corrupted")) {
                        // The file may be compressed, but had the wrong mime type, try unzipping it
                        tarball = unzip(tarball);
                        files = untar(tarball);
                    } else {
                        throw e;
                    }
                }

                for (var fileEntry : files) {
                    var filePath = pgDataDir + fileEntry.name;

                    // Ensure the directory structure exists
                    var dirPath = filePath.split("/");
                    for (var i = 1; i < dirPath.length; i++) {
                        var dir = String.join("/", slice(dirPath, 0, i));
                        if (!FS.analyzePath(dir).exists) {
                            FS.mkdir(dir);
                        }
                    }

                    // Write the file or directory
                    if (fileEntry.type == REGTYPE) {
                        FS.writeFile(filePath, fileEntry.data);
                        FS.utime(
                            filePath,
                            dateToUnixTimestamp(fileEntry.modifyTime),
                            dateToUnixTimestamp(fileEntry.modifyTime)
                        );
                    } else if (fileEntry.type == DIRTYPE) {
                        FS.mkdir(filePath);
                    }
                }
            }
        );
    }

    private static String[] slice(String[] input, int start, int end) {
        var size = Math.max(end - start, 0);
        var result = new String[size];
        for (var i = 0; i < size; i++) {
            result[i] = input[start + i];
        }
        return result;
    }

    private static boolean isCompressed(String type, String filename) {
        if (type != null) {
            for (var mime : compressedMimeTypes) {
                if (mime.equals(type)) {
                    return true;
                }
            }
        }
        if (filename != null) {
            return filename.endsWith(".tgz") || filename.endsWith(".tar.gz");
        }
        return false;
    }

    private static List<TarFile> readDirectory(base.FS FS, String path) {
        var files = new ArrayList<TarFile>();

        var traverseDirectory = new Object() {
            void apply(String currentPath) {
                var entries = FS.readdir(currentPath);
                for (var entry : entries) {
                    if (".".equals(entry) || "..".equals(entry)) {
                        continue;
                    }
                    var fullPath = currentPath + "/" + entry;
                    var stats = FS.stat(fullPath);
                    var data = new Uint8Array(0);
                    if (FS.isFile(stats.mode)) {
                        var options = new base.FS.ReadFileOptions();
                        options.encoding = "binary";
                        data = FS.readFile(fullPath, options);
                        if (data == null) {
                            data = new Uint8Array(0);
                        }
                    }
                    var tarFile = new TarFile();
                    tarFile.name = fullPath.substring(path.length());
                    tarFile.mode = stats.mode;
                    tarFile.size = stats.size;
                    tarFile.type = FS.isFile(stats.mode) ? REGTYPE : DIRTYPE;
                    tarFile.modifyTime = stats.mtime;
                    tarFile.data = data;
                    files.add(tarFile);
                    if (FS.isDir(stats.mode)) {
                        this.apply(fullPath);
                    }
                }
            }
        };

        traverseDirectory.apply(path);
        return files;
    }

    public static Uint8Array createTarball(base.FS FS, String directoryPath) {
        var files = readDirectory(FS, directoryPath);
        return tar(files);
    }

    public static CompressionResult maybeZip(
        Uint8Array file,
        String compression
    ) {
        if (DumpTarCompressionOptions.none.equals(compression)) {
            return new CompressionResult(file, false);
        } else if (DumpTarCompressionOptions.auto.equals(compression)) {
            return new CompressionResult(zipNode(file), true);
        } else if (DumpTarCompressionOptions.gzip.equals(compression)) {
            return new CompressionResult(zipNode(file), true);
        } else {
            throw new RuntimeException("Compression not supported in this environment");
        }
    }

    public static Uint8Array zipBrowser(Uint8Array file) {
        return zipNode(file);
    }

    public static Uint8Array zipNode(Uint8Array file) {
        try {
            var output = new ByteArrayOutputStream();
            try (var gzip = new GZIPOutputStream(output)) {
                gzip.write(file.toByteArray());
            }
            return new Uint8Array(output.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Uint8Array unzip(Uint8Array file) {
        return unzipNode(file);
    }

    public static Uint8Array unzipBrowser(Uint8Array file) {
        return unzipNode(file);
    }

    public static Uint8Array unzipNode(Uint8Array file) {
        try {
            var input = new ByteArrayInputStream(file.toByteArray());
            var output = new ByteArrayOutputStream();
            try (var gzip = new GZIPInputStream(input)) {
                gzip.transferTo(output);
            }
            return new Uint8Array(output.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static int dateToUnixTimestamp(Object date) {
        if (date == null) {
            return (int) (System.currentTimeMillis() / 1000);
        } else if (date instanceof Number) {
            return ((Number) date).intValue();
        } else if (date instanceof Date) {
            return (int) (((Date) date).getTime() / 1000);
        } else {
            return (int) (System.currentTimeMillis() / 1000);
        }
    }

    private static Uint8Array tar(List<TarFile> files) {
        try {
            var output = new ByteArrayOutputStream();
            try (var tar = new TarArchiveOutputStream(output)) {
                for (var file : files) {
                    var entryName = file.name;
                    if (file.type == DIRTYPE && !entryName.endsWith("/")) {
                        entryName = entryName + "/";
                    }
                    var entry = new TarArchiveEntry(entryName);
                    entry.setMode(file.mode);
                    entry.setSize(file.type == DIRTYPE ? 0 : file.size);
                    entry.setModTime(new Date(file.modifyTime));
                    tar.putArchiveEntry(entry);
                    if (file.type == REGTYPE) {
                        tar.write(file.data.toByteArray());
                    }
                    tar.closeArchiveEntry();
                }
                tar.finish();
            }
            return new Uint8Array(output.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<TarFile> untar(Uint8Array tarball) {
        try {
            var files = new ArrayList<TarFile>();
            InputStream input = new ByteArrayInputStream(tarball.toByteArray());
            try (var tar = new TarArchiveInputStream(input)) {
                TarArchiveEntry entry;
                while ((entry = tar.getNextEntry()) != null) {
                    var file = new TarFile();
                    file.name = entry.getName();
                    file.mode = entry.getMode();
                    file.size = (int) entry.getSize();
                    file.type = entry.isDirectory() ? DIRTYPE : REGTYPE;
                    file.modifyTime = entry.getModTime().getTime();
                    if (!entry.isDirectory()) {
                        var data = tar.readNBytes((int) entry.getSize());
                        file.data = new Uint8Array(data);
                    } else {
                        file.data = new Uint8Array(0);
                    }
                    files.add(file);
                }
            }
            return files;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static final class CompressionResult {
        public final Uint8Array data;
        public final boolean zipped;

        public CompressionResult(Uint8Array data, boolean zipped) {
            this.data = data;
            this.zipped = zipped;
        }
    }

    public static final class TarFile {
        public String name;
        public int mode;
        public int size;
        public int type;
        public long modifyTime;
        public Uint8Array data;
    }

    private tarUtils() {
    }
}
