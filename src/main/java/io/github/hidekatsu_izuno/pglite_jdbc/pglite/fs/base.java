package io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import java.util.Map;

public class base {
    public static final String PGLITE_DATA_DIR = "/pglite/data";

    public enum FsType {
        nodefs,
        memoryfs,
    }

    public sealed interface WasiFilesystemDescriptor permits HostDescriptor, MemoryDescriptor, RuntimeDescriptor {
        String kind();
    }

    public record HostDescriptor(String path) implements WasiFilesystemDescriptor {
        @Override
        public String kind() {
            return "host";
        }
    }

    public record MemoryDescriptor(String name) implements WasiFilesystemDescriptor {
        @Override
        public String kind() {
            return "memory";
        }
    }

    public record RuntimeDescriptor(String name, Map<String, Object> options) implements WasiFilesystemDescriptor {
        @Override
        public String kind() {
            return "runtime";
        }
    }

    public record WasiFilesystemMount(
        String dataDir,
        Map<String, WasiFilesystemDescriptor> mounts,
        Map<String, String> preopens
    ) {}

    public record FsStats(
        long dev,
        long ino,
        int mode,
        long nlink,
        long uid,
        long gid,
        long rdev,
        long size,
        long blksize,
        long blocks,
        long atime,
        long mtime,
        long ctime
    ) {}

    public interface Filesystem {
        Promise<WasiFilesystemMount> initWasi();

        default Promise<Boolean> exists(String path) {
            return Promise.resolve(false);
        }

        Promise<Void> syncToFs(Boolean relaxedDurability);

        Promise<Void> initialSyncFs();

        Promise<Void> closeFs();

        void chmod(String path, int mode);

        void close(int fd);

        FsStats fstat(int fd);

        FsStats lstat(String path);

        void mkdir(String path, boolean recursive, Integer mode);

        int open(String path, String flags, Integer mode);

        String[] readdir(String path);

        int read(int fd, byte[] buffer, int offset, int length, int position);

        void rename(String oldPath, String newPath);

        void rmdir(String path);

        void truncate(String path, int len);

        void unlink(String path);

        void utimes(String path, long atime, long mtime);

        void writeFile(String path, byte[] data, String encoding, Integer mode, String flag);

        int write(int fd, byte[] buffer, int offset, int length, int position);
    }
}
