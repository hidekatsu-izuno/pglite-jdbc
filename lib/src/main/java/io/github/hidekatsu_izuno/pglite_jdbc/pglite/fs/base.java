package io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.tarUtils.DumpTarCompressionOptions;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.pglite;
import java.nio.file.Path;
import java.util.Map;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;

public class base {
    public static final String WASM_PREFIX = "/tmp/pglite";
    public static final String PGDATA = WASM_PREFIX + "/base";

    public enum FsType {
        nodefs,
        idbfs,
        memoryfs,
        opfs_ahp,
    }

    public record InitResult(Map<String, Object> emscriptenOpts) {}

    public interface Filesystem {
        Promise<InitResult> init(
            pglite pg,
            Map<String, Object> emscriptenOptions
        );

        Promise<Void> syncToFs(Boolean relaxedDurability);

        Promise<Void> initialSyncFs();

        Promise<byte[]> dumpTar(String dbname, DumpTarCompressionOptions compression);

        Promise<Void> closeFs();
    }

    public static class EmscriptenBuiltinFilesystem implements Filesystem {
        protected String dataDir;
        protected pglite pg;

        public EmscriptenBuiltinFilesystem(String dataDir) {
            this.dataDir = dataDir;
        }

        @Override
        public Promise<InitResult> init(
            pglite pg,
            Map<String, Object> emscriptenOptions
        ) {
            this.pg = pg;
            return Promise.resolve(new InitResult(emscriptenOptions));
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
        public Promise<byte[]> dumpTar(
            String dbname,
            DumpTarCompressionOptions compression
        ) {
            Path path;
            if (dataDir != null && !dataDir.isBlank()) {
                path = Path.of(dataDir).toAbsolutePath().normalize();
            } else {
                path = Path.of(PGDATA);
            }
            return Promise.resolve(tarUtils.createTarball(path, compression));
        }

        @Override
        public Promise<Void> closeFs() {
            return Promise.resolve(null);
        }
    }

    public static abstract class BaseFilesystem extends EmscriptenBuiltinFilesystem {
        protected final boolean debug;

        protected BaseFilesystem(String dataDir, boolean debug) {
            super(dataDir);
            this.debug = debug;
        }

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

        public abstract void chmod(String path, int mode);

        public abstract void close(int fd);

        public abstract FsStats fstat(int fd);

        public abstract FsStats lstat(String path);

        public abstract void mkdir(String path, boolean recursive, Integer mode);

        public abstract int open(String path, String flags, Integer mode);

        public abstract String[] readdir(String path);

        public abstract int read(int fd, byte[] buffer, int offset, int length, int position);

        public abstract void rename(String oldPath, String newPath);

        public abstract void rmdir(String path);

        public abstract void truncate(String path, int len);

        public abstract void unlink(String path);

        public abstract void utimes(String path, long atime, long mtime);

        public abstract void writeFile(
            String path,
            byte[] data,
            String encoding,
            Integer mode,
            String flag
        );

        public abstract int write(int fd, byte[] buffer, int offset, int length, int position);
    }
}
