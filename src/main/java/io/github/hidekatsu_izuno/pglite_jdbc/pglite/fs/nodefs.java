package io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class nodefs {
    private static final String PGLITE_ROOT_DIR = "/pglite";
    private static final String PGLITE_TMP_DIR = "/tmp";

    public static class NodeFS implements base.Filesystem {
        protected final Path rootDir;
        protected final Path dataDir;
        protected final Path tmpDir;
        private final Map<Integer, RandomAccessFile> fds = new HashMap<>();
        private int nextFd = 100;

        public NodeFS(String dataDir) {
            try {
                this.rootDir = Path.of(dataDir).toAbsolutePath().normalize();
                this.dataDir = this.rootDir.resolve("data").normalize();
                this.tmpDir = this.rootDir.resolve("tmp").normalize();
                Files.createDirectories(this.rootDir);
                Files.createDirectories(this.dataDir);
                Files.createDirectories(this.tmpDir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Promise<base.WasiFilesystemMount> initWasi() {
            return Promise.resolve(new base.WasiFilesystemMount(
                base.PGLITE_DATA_DIR,
                Map.of(PGLITE_ROOT_DIR, new base.HostDescriptor(rootDir.toString())),
                Map.of(PGLITE_ROOT_DIR, rootDir.toString())
            ));
        }

        @Override
        public Promise<Boolean> exists(String virtualPath) {
            return Promise.resolve(Files.exists(resolveVirtualPath(virtualPath)));
        }

        @Override
        public Promise<Void> syncToFs(Boolean relaxedDurability) {
            return Promise.resolve(null);
        }

        @Override
        public Promise<Void> initialSyncFs() {
            return Promise.resolve(null);
        }

        @Override
        public Promise<Void> closeFs() {
            for (var fd : fds.keySet().toArray(Integer[]::new)) {
                close(fd);
            }
            return Promise.resolve(null);
        }

        @Override
        public void chmod(String virtualPath, int mode) {
            resolveVirtualPath(virtualPath).toFile().setWritable((mode & 0200) != 0, false);
            resolveVirtualPath(virtualPath).toFile().setReadable((mode & 0400) != 0, false);
            resolveVirtualPath(virtualPath).toFile().setExecutable((mode & 0100) != 0, false);
        }

        @Override
        public void close(int fd) {
            var file = fds.remove(fd);
            if (file == null) {
                throw new RuntimeException("bad fd");
            }
            try {
                file.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public base.FsStats fstat(int fd) {
            var file = expectFd(fd);
            try {
                return toFsStats(Files.readAttributes(Path.of(file.getFD().toString()), BasicFileAttributes.class));
            } catch (Exception ignored) {
                return new base.FsStats(0, 0, 0100000, 1, 0, 0, 0, length(file), 4096, Math.ceilDiv(length(file), 512), 0, 0, 0);
            }
        }

        @Override
        public base.FsStats lstat(String virtualPath) {
            try {
                return toFsStats(Files.readAttributes(resolveVirtualPath(virtualPath), BasicFileAttributes.class));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void mkdir(String virtualPath, boolean recursive, Integer mode) {
            try {
                if (recursive) {
                    Files.createDirectories(resolveVirtualPath(virtualPath));
                } else {
                    Files.createDirectory(resolveVirtualPath(virtualPath));
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public int open(String virtualPath, String flags, Integer mode) {
            try {
                var path = resolveVirtualPath(virtualPath);
                var parent = path.getParent();
                if (parent != null && flags != null && (flags.contains("w") || flags.contains("a"))) {
                    Files.createDirectories(parent);
                }
                var file = new RandomAccessFile(path.toFile(), modeFor(flags));
                if (flags != null && flags.contains("w")) {
                    file.setLength(0);
                }
                if (flags != null && flags.contains("a")) {
                    file.seek(file.length());
                }
                var fd = nextFd++;
                fds.put(fd, file);
                return fd;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String[] readdir(String virtualPath) {
            try (var stream = Files.list(resolveVirtualPath(virtualPath))) {
                var entries = stream.map(path -> path.getFileName().toString()).toArray(String[]::new);
                var result = Arrays.copyOf(entries, entries.length + 2);
                System.arraycopy(result, 0, result, 2, entries.length);
                result[0] = ".";
                result[1] = "..";
                return result;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public int read(int fd, byte[] buffer, int offset, int length, int position) {
            var file = expectFd(fd);
            try {
                if (position >= 0) {
                    file.seek(position);
                }
                var read = file.read(buffer, offset, length);
                return Math.max(read, 0);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void rename(String oldPath, String newPath) {
            try {
                Files.move(resolveVirtualPath(oldPath), resolveVirtualPath(newPath));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void rmdir(String virtualPath) {
            try {
                Files.delete(resolveVirtualPath(virtualPath));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void truncate(String virtualPath, int len) {
            try (var file = new RandomAccessFile(resolveVirtualPath(virtualPath).toFile(), "rw")) {
                file.setLength(len);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void unlink(String virtualPath) {
            try {
                Files.delete(resolveVirtualPath(virtualPath));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void utimes(String virtualPath, long atime, long mtime) {
            try {
                Files.setLastModifiedTime(resolveVirtualPath(virtualPath), FileTime.fromMillis(mtime));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void writeFile(String virtualPath, byte[] data, String encoding, Integer mode, String flag) {
            var fd = open(virtualPath, flag != null ? flag : "w", mode);
            try {
                write(fd, data, 0, data.length, 0);
            } finally {
                close(fd);
            }
        }

        @Override
        public int write(int fd, byte[] buffer, int offset, int length, int position) {
            var file = expectFd(fd);
            try {
                if (position >= 0) {
                    file.seek(position);
                }
                file.write(buffer, offset, length);
                return length;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private RandomAccessFile expectFd(int fd) {
            var file = fds.get(fd);
            if (file == null) {
                throw new RuntimeException("bad fd");
            }
            return file;
        }

        private Path resolveVirtualPath(String virtualPath) {
            if (virtualPath.equals(PGLITE_ROOT_DIR)) {
                return rootDir;
            }
            if (virtualPath.equals(PGLITE_TMP_DIR)) {
                return tmpDir;
            }
            if (virtualPath.startsWith(PGLITE_ROOT_DIR + "/")) {
                return resolveInsideMount(rootDir, PGLITE_ROOT_DIR, virtualPath);
            }
            if (virtualPath.startsWith(PGLITE_TMP_DIR + "/")) {
                return resolveInsideMount(tmpDir, PGLITE_TMP_DIR, virtualPath);
            }
            throw new IllegalArgumentException("Path is outside the NodeFS mount: " + virtualPath);
        }

        private Path resolveInsideMount(Path hostRoot, String virtualRoot, String virtualPath) {
            var resolved = hostRoot.resolve(virtualPath.substring(virtualRoot.length() + 1)).normalize();
            if (resolved.equals(hostRoot) || resolved.startsWith(hostRoot)) {
                return resolved;
            }
            throw new IllegalArgumentException("Path is outside the NodeFS mount: " + virtualPath);
        }

        private String modeFor(String flags) {
            if (flags == null || flags.equals("r")) {
                return "r";
            }
            return "rw";
        }

        private long length(RandomAccessFile file) {
            try {
                return file.length();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private base.FsStats toFsStats(BasicFileAttributes stats) {
            var size = stats.size();
            var mode = stats.isDirectory() ? 040000 : 0100000;
            return new base.FsStats(
                0,
                0,
                mode,
                1,
                0,
                0,
                0,
                size,
                4096,
                Math.ceilDiv(size, 512),
                stats.lastAccessTime().toMillis(),
                stats.lastModifiedTime().toMillis(),
                stats.creationTime().toMillis()
            );
        }

    }
}
