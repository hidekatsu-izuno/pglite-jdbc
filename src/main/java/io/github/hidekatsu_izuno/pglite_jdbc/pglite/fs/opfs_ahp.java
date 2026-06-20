package io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.tarUtils.DumpTarCompressionOptions;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class opfs_ahp {
    public static final class OpfsAhpFS extends base.BaseFilesystem {
        private static final String STATE_FILE = "state.txt";
        private static final String DATA_DIR = "data";

        private final int initialPoolSize;
        private final int maintainedPoolSize;
        private final AtomicInteger poolCounter = new AtomicInteger();
        private final Map<String, byte[]> fileData = new ConcurrentHashMap<>();
        private final List<String> pool = new ArrayList<>();
        private final ArrayDeque<String> wal = new ArrayDeque<>();
        private Path rootPath;
        private Path dataPath;
        private long lastCheckpoint;
        private long checkpointIntervalMillis = 60_000L;
        private final Map<Integer, String> openHandlePaths = new ConcurrentHashMap<>();
        private final Map<String, Integer> openHandleIds = new ConcurrentHashMap<>();
        private final AtomicInteger handleCounter = new AtomicInteger(100);

        public OpfsAhpFS(String dataDir) {
            this(dataDir, 1000, 100, false);
        }

        public OpfsAhpFS(
            String dataDir,
            int initialPoolSize,
            int maintainedPoolSize,
            boolean debug
        ) {
            super(dataDir, debug);
            this.initialPoolSize = initialPoolSize;
            this.maintainedPoolSize = maintainedPoolSize;
        }

        @Override
        public Promise<base.InitResult> init(
            io.github.hidekatsu_izuno.pglite_jdbc.pglite.pglite pg,
            Map<String, Object> emscriptenOptions
        ) {
            return super.init(pg, emscriptenOptions).then(resultObj -> {
                initializeStateFiles();
                maintainPool(initialPoolSize);
                return (base.InitResult) resultObj;
            });
        }

        @Override
        public Promise<Void> syncToFs(Boolean relaxedDurability) {
            return Promise.resolve(null).then(ignored -> {
                maybeCheckpointState();
                maintainPool(maintainedPoolSize);
                if (!Boolean.TRUE.equals(relaxedDurability)) {
                    flush();
                }
                return null;
            });
        }

        @Override
        public Promise<Void> closeFs() {
            return Promise.resolve(null).then(ignored -> {
                checkpointState();
                return null;
            });
        }

        @Override
        public Promise<byte[]> dumpTar(String dbname, DumpTarCompressionOptions compression) {
            return Promise.resolve(tarUtils.createTarball(dataPath, compression));
        }

        public synchronized void checkpointState() {
            var statePath = rootPath.resolve(STATE_FILE);
            var sb = new StringBuilder();
            sb.append("{\"pool\":[");
            for (var i = 0; i < pool.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append('"').append(pool.get(i)).append('"');
            }
            sb.append("]}");
            for (var entry : wal) {
                sb.append('\n').append(entry);
            }
            try {
                Files.writeString(
                    statePath,
                    sb.toString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
                );
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            wal.clear();
            lastCheckpoint = System.currentTimeMillis();
        }

        public synchronized void maybeCheckpointState() {
            if (System.currentTimeMillis() - lastCheckpoint > checkpointIntervalMillis) {
                checkpointState();
            }
        }

        public synchronized void flush() {
            for (var entry : fileData.entrySet()) {
                var path = dataPath.resolve(entry.getKey());
                try {
                    Files.createDirectories(path.getParent());
                    Files.write(
                        path,
                        entry.getValue(),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                    );
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        public synchronized void maintainPool(int size) {
            while (pool.size() < size) {
                var filename = nextPoolFilename();
                pool.add(filename);
                wal.add("{\"opp\":\"createPoolFile\",\"args\":[\"" + filename + "\"]}");
                fileData.putIfAbsent(filename, new byte[0]);
            }
            while (pool.size() > size) {
                var removed = pool.removeLast();
                wal.add("{\"opp\":\"deletePoolFile\",\"args\":[\"" + removed + "\"]}");
                fileData.remove(removed);
                try {
                    Files.deleteIfExists(dataPath.resolve(removed));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        public synchronized String allocatePoolFile() {
            if (pool.isEmpty()) {
                maintainPool(Math.max(maintainedPoolSize, 1));
            }
            var allocated = pool.removeLast();
            wal.add("{\"opp\":\"allocPoolFile\",\"args\":[\"" + allocated + "\"]}");
            return allocated;
        }

        public synchronized void releasePoolFile(String filename) {
            if (!pool.contains(filename)) {
                pool.add(filename);
                wal.add("{\"opp\":\"releasePoolFile\",\"args\":[\"" + filename + "\"]}");
            }
        }

        public synchronized void putFile(String filename, byte[] bytes) {
            fileData.put(filename, bytes != null ? bytes : new byte[0]);
            wal.add("{\"opp\":\"writeFile\",\"args\":[\"" + filename + "\"]}");
        }

        public synchronized byte[] getFile(String filename) {
            var bytes = fileData.get(filename);
            if (bytes != null) {
                return bytes;
            }
            var path = dataPath.resolve(filename);
            if (!Files.exists(path)) {
                return null;
            }
            try {
                var loaded = Files.readAllBytes(path);
                fileData.put(filename, loaded);
                return loaded;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void initializeStateFiles() {
            try {
                var root = dataDir != null && !dataDir.isBlank() ? dataDir : "tmp/opfs-ahp";
                rootPath = Path.of(root).toAbsolutePath().normalize();
                dataPath = rootPath.resolve(DATA_DIR);
                Files.createDirectories(dataPath);
                var statePath = rootPath.resolve(STATE_FILE);
                if (!Files.exists(statePath)) {
                    Files.writeString(
                        statePath,
                        "{\"pool\":[]}",
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE
                    );
                } else {
                    replayState(statePath);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void replayState(Path statePath) throws IOException {
            var lines = Files.readAllLines(statePath, StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                return;
            }
            var first = lines.getFirst();
            var start = first.indexOf('[');
            var end = first.indexOf(']');
            if (start >= 0 && end > start) {
                var body = first.substring(start + 1, end).trim();
                if (!body.isEmpty()) {
                    for (var token : body.split(",")) {
                        var name = token.trim().replace("\"", "");
                        if (!name.isBlank()) {
                            pool.add(name);
                        }
                    }
                }
            }
            for (var i = 1; i < lines.size(); i++) {
                var line = lines.get(i);
                if (line == null || line.isBlank()) {
                    continue;
                }
                wal.add(line);
            }
        }

        private String nextPoolFilename() {
            var now = System.currentTimeMillis();
            var counter = poolCounter.incrementAndGet();
            return String.format("%08x-%08x", now - 1704063600L, counter);
        }

        @Override
        public synchronized void chmod(String path, int mode) {
            // mode metadata is persisted in WAL-compatible no-op form
            wal.add("{\"opp\":\"chmod\",\"args\":[\"" + path + "\"," + mode + "]}");
        }

        @Override
        public synchronized void close(int fd) {
            var path = openHandlePaths.remove(fd);
            if (path != null) {
                openHandleIds.remove(path);
            }
        }

        @Override
        public synchronized FsStats fstat(int fd) {
            var path = openHandlePaths.get(fd);
            if (path == null) {
                throw new IllegalArgumentException("Bad file descriptor: " + fd);
            }
            return lstat(path);
        }

        @Override
        public synchronized FsStats lstat(String path) {
            var normalized = normalizePath(path);
            var filePath = dataPath.resolve(normalized);
            try {
                if (Files.isDirectory(filePath)) {
                    var now = System.currentTimeMillis();
                    return new FsStats(0, 0, 16384 | 511, 1, 0, 0, 0, 0, 4096, 0, now, now, now);
                }
                var bytes = getFile(normalized);
                var size = bytes != null ? bytes.length : 0;
                var now = System.currentTimeMillis();
                return new FsStats(0, 0, 32768 | 511, 1, 0, 0, 0, size, 4096, (size + 511) / 512, now, now, now);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public synchronized void mkdir(String path, boolean recursive, Integer mode) {
            var normalized = normalizePath(path);
            try {
                if (recursive) {
                    Files.createDirectories(dataPath.resolve(normalized));
                } else {
                    Files.createDirectory(dataPath.resolve(normalized));
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public synchronized int open(String path, String flags, Integer mode) {
            var normalized = normalizePath(path);
            if (openHandleIds.containsKey(normalized)) {
                return openHandleIds.get(normalized);
            }
            var fd = handleCounter.incrementAndGet();
            openHandleIds.put(normalized, fd);
            openHandlePaths.put(fd, normalized);
            fileData.putIfAbsent(normalized, new byte[0]);
            return fd;
        }

        @Override
        public synchronized String[] readdir(String path) {
            var normalized = normalizePath(path);
            var folder = dataPath.resolve(normalized);
            if (!Files.exists(folder)) {
                return new String[] { ".", ".." };
            }
            try (var stream = Files.list(folder)) {
                var names = new ArrayList<String>();
                names.add(".");
                names.add("..");
                stream.forEach(p -> names.add(p.getFileName().toString()));
                return names.toArray(String[]::new);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public synchronized int read(int fd, byte[] buffer, int offset, int length, int position) {
            var path = openHandlePaths.get(fd);
            if (path == null) {
                throw new IllegalArgumentException("Bad file descriptor: " + fd);
            }
            var bytes = getFile(path);
            if (bytes == null || position >= bytes.length) {
                return 0;
            }
            var readLen = Math.min(length, bytes.length - position);
            System.arraycopy(bytes, position, buffer, offset, readLen);
            return readLen;
        }

        @Override
        public synchronized void rename(String oldPath, String newPath) {
            var oldNormalized = normalizePath(oldPath);
            var newNormalized = normalizePath(newPath);
            var bytes = fileData.remove(oldNormalized);
            if (bytes != null) {
                fileData.put(newNormalized, bytes);
            }
            try {
                Files.createDirectories(dataPath.resolve(newNormalized).getParent());
                Files.move(dataPath.resolve(oldNormalized), dataPath.resolve(newNormalized));
            } catch (IOException ignored) {
            }
            wal.add("{\"opp\":\"rename\",\"args\":[\"" + oldNormalized + "\",\"" + newNormalized + "\"]}");
        }

        @Override
        public synchronized void rmdir(String path) {
            var normalized = normalizePath(path);
            try {
                Files.deleteIfExists(dataPath.resolve(normalized));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public synchronized void truncate(String path, int len) {
            var normalized = normalizePath(path);
            var bytes = getFile(normalized);
            if (bytes == null) {
                bytes = new byte[0];
            }
            var next = new byte[Math.max(0, len)];
            System.arraycopy(bytes, 0, next, 0, Math.min(bytes.length, next.length));
            putFile(normalized, next);
        }

        @Override
        public synchronized void unlink(String path) {
            var normalized = normalizePath(path);
            fileData.remove(normalized);
            try {
                Files.deleteIfExists(dataPath.resolve(normalized));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public synchronized void utimes(String path, long atime, long mtime) {
            wal.add("{\"opp\":\"utimes\",\"args\":[\"" + normalizePath(path) + "\"," + atime + "," + mtime + "]}");
        }

        @Override
        public synchronized void writeFile(
            String path,
            byte[] data,
            String encoding,
            Integer mode,
            String flag
        ) {
            putFile(normalizePath(path), data != null ? data : new byte[0]);
        }

        @Override
        public synchronized int write(int fd, byte[] buffer, int offset, int length, int position) {
            var path = openHandlePaths.get(fd);
            if (path == null) {
                throw new IllegalArgumentException("Bad file descriptor: " + fd);
            }
            var current = getFile(path);
            if (current == null) {
                current = new byte[0];
            }
            var needed = Math.max(current.length, position + length);
            var out = new byte[needed];
            System.arraycopy(current, 0, out, 0, current.length);
            System.arraycopy(buffer, offset, out, position, length);
            putFile(path, out);
            return length;
        }

        private String normalizePath(String path) {
            if (path == null || path.isBlank() || "/".equals(path)) {
                return "";
            }
            var normalized = path.replace('\\', '/');
            if (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }
            return normalized;
        }
    }
}
