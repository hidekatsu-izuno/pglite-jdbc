package io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Blob;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;

public final class base {
    public static final String WASM_PREFIX = "/tmp/pglite";
    public static final String PGDATA = WASM_PREFIX + "/" + "base";

    public static final class FsType {
        public static final String nodefs = "nodefs";
        public static final String idbfs = "idbfs";
        public static final String memoryfs = "memoryfs";
        public static final String opfs_ahp = "opfs-ahp";

        private FsType() {
        }
    }

    /**
     * Filesystem interface.
     * All virtual filesystems that are compatible with PGlite must implement
     * this interface.
     */
    public interface Filesystem {
        /**
         * Initiate the filesystem and return the options to pass to the emscripten module.
         */
        CompletableFuture<InitResult> init(PGlite pg, PostgresMod emscriptenOptions);

        /**
         * Sync the filesystem to any underlying storage.
         */
        CompletableFuture<Void> syncToFs(Boolean relaxedDurability);

        /**
         * Sync the filesystem from any underlying storage.
         */
        CompletableFuture<Void> initialSyncFs();

        /**
         * Dump the PGDATA dir from the filesystem to a gzipped tarball.
         */
        CompletableFuture<Blob> dumpTar(
            String dbname,
            String compression
        );

        /**
         * Close the filesystem.
         */
        CompletableFuture<Void> closeFs();
    }

    public static final class InitResult {
        public PostgresMod emscriptenOpts;
    }

    public static class PGlite {
        public PostgresMod Module;
    }

    public static class PostgresMod {
        public List<Consumer<PostgresMod>> preRun;
        public FS FS;
        public Uint8Array HEAP8;

        public int mmapAlloc(int length) {
            throw new UnsupportedOperationException("mmapAlloc not implemented");
        }
    }

    /**
     * Base class for all emscripten built-in filesystems.
     */
    public static class EmscriptenBuiltinFilesystem implements Filesystem {
        protected String dataDir;
        protected PGlite pg;

        public EmscriptenBuiltinFilesystem(String dataDir) {
            this.dataDir = dataDir;
        }

        @Override
        public CompletableFuture<InitResult> init(
            PGlite pg,
            PostgresMod emscriptenOptions
        ) {
            this.pg = pg;
            var result = new InitResult();
            result.emscriptenOpts = emscriptenOptions;
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public CompletableFuture<Void> syncToFs(Boolean _relaxedDurability) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> initialSyncFs() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> closeFs() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Blob> dumpTar(
            String dbname,
            String compression
        ) {
            return tarUtils.dumpTar(
                this.pg.Module.FS,
                PGDATA,
                dbname,
                compression
            );
        }
    }

    /**
     * Abstract base class for all custom virtual filesystems.
     * Each custom filesystem needs to implement an interface similar to the NodeJS FS API.
     */
    public abstract static class BaseFilesystem implements Filesystem {
        protected String dataDir;
        protected PGlite pg;
        public final boolean debug;

        public BaseFilesystem(String dataDir, BaseFilesystemOptions options) {
            this.dataDir = dataDir;
            var resolvedOptions = options != null ? options : new BaseFilesystemOptions();
            this.debug = resolvedOptions.debug;
        }

        @Override
        public CompletableFuture<Void> syncToFs(Boolean _relaxedDurability) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> initialSyncFs() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> closeFs() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Blob> dumpTar(
            String dbname,
            String compression
        ) {
            return tarUtils.dumpTar(this.pg.Module.FS, PGDATA, dbname, compression);
        }

        @Override
        public CompletableFuture<InitResult> init(
            PGlite pg,
            PostgresMod emscriptenOptions
        ) {
            this.pg = pg;
            var options = emscriptenOptions != null
                ? emscriptenOptions
                : new PostgresMod();
            List<Consumer<PostgresMod>> preRun = options.preRun != null
                ? new ArrayList<>(options.preRun)
                : new ArrayList<>();
            preRun.add(
                mod -> {
                    var emfs = createEmscriptenFS(mod, this);
                    mod.FS.mkdir(PGDATA);
                    mod.FS.mount(emfs, new HashMap<>(), PGDATA);
                }
            );
            options.preRun = preRun;
            var result = new InitResult();
            result.emscriptenOpts = options;
            return CompletableFuture.completedFuture(result);
        }

        // Filesystem API

        public abstract void chmod(String path, int mode);
        public abstract void close(int fd);
        public abstract FsStats fstat(int fd);
        public abstract FsStats lstat(String path);
        public abstract void mkdir(String path, MkdirOptions options);
        public abstract int open(String path, String flags, Integer mode);
        public abstract String[] readdir(String path);
        public abstract int read(
            int fd,
            Uint8Array buffer, // Buffer to read into
            int offset, // Offset in buffer to start writing to
            int length, // Number of bytes to read
            int position // Position in file to read from
        );
        public abstract void rename(String oldPath, String newPath);
        public abstract void rmdir(String path);
        public abstract void truncate(
            String path,
            int len // Length to truncate to - defaults to 0
        );
        public abstract void unlink(String path);
        public abstract void utimes(String path, long atime, long mtime);
        public abstract void writeFile(
            String path,
            Object data,
            WriteFileOptions options
        );
        public abstract int write(
            int fd,
            Uint8Array buffer, // Buffer to read from
            int offset, // Offset in buffer to start reading from
            int length, // Number of bytes to write
            int position // Position in file to write to
        );
    }

    public static final class BaseFilesystemOptions {
        public boolean debug;

        public BaseFilesystemOptions() {
            this.debug = false;
        }
    }

    public static final class MkdirOptions {
        public Boolean recursive;
        public Integer mode;
    }

    public static final class WriteFileOptions {
        public String encoding;
        public Integer mode;
        public String flag;
    }

    public static final class FsStats {
        public int dev;
        public int ino;
        public int mode;
        public int nlink;
        public int uid;
        public int gid;
        public int rdev;
        public int size;
        public int blksize;
        public int blocks;
        public long atime;
        public long mtime;
        public long ctime;
    }

    public static class FS {
        public Filesystems filesystems;

        public static class ErrnoError extends RuntimeException {
            public final int errno;

            public ErrnoError(int errno) {
                this.errno = errno;
            }
        }

        public static class Filesystems {
            public FileSystemType MEMFS;
            public FileSystemType NODEFS;
            public IDBFS IDBFS;
        }

        public static class FileSystemType {
        }

        public static class IDBFS extends FileSystemType {
            public Map<String, IDBDatabase> dbs;
        }

        public interface IDBDatabase {
            void close();
        }

        public static final class AnalyzePathResult {
            public boolean exists;
        }

        public static final class ReadFileOptions {
            public String encoding;
        }

        public static final class Stats {
            public int dev;
            public int ino;
            public Integer mode;
            public int nlink;
            public int rdev;
            public Integer size;
            public Date atime;
            public Date mtime;
            public Date ctime;
            public Long timestamp;
        }

        public interface NodeOps {
            Stats getattr(FSNode node);
            void setattr(FSNode node, Stats attr);
            FSNode lookup(FSNode parent, String name);
            FSNode mknod(FSNode parent, String name, int mode, Object dev);
            void rename(FSNode oldNode, FSNode newDir, String newName);
            void unlink(FSNode parent, String name);
            void rmdir(FSNode parent, String name);
            String[] readdir(FSNode node);
            void symlink(FSNode parent, String newName, String oldPath);
            String readlink(FSNode node);
        }

        public interface StreamOps {
            void open(FSStream stream);
            void close(FSStream stream);
            void dup(FSStream stream);
            int read(
                FSStream stream,
                Uint8Array buffer,
                int offset,
                int length,
                int position
            );
            int write(
                FSStream stream,
                Uint8Array buffer,
                int offset,
                int length,
                int position
            );
            int llseek(FSStream stream, int offset, int whence);
            MMapResult mmap(
                FSStream stream,
                int length,
                int position,
                Object prot,
                Object flags
            );
            int msync(
                FSStream stream,
                Uint8Array buffer,
                int offset,
                int length,
                Object mmapFlags
            );
        }

        public static final class MMapResult {
            public int ptr;
            public boolean allocated;
        }

        public static class FSNode {
            public FSNode parent;
            public String name;
            public int mode;
            public int id;
            public int rdev;
            public FSMount mount;
            public NodeOps node_ops;
            public StreamOps stream_ops;
        }

        public static class FSStream {
            public FSNode node;
            public Shared shared = new Shared();
            public Integer nfd;
            public int position;
        }

        public static class Shared {
            public int refcount;
        }

        public static class FSMount {
            public MountOptions opts;
        }

        public static class MountOptions {
            public String root;
        }

        public boolean isDir(int mode) {
            throw new UnsupportedOperationException("isDir not implemented");
        }

        public boolean isFile(int mode) {
            throw new UnsupportedOperationException("isFile not implemented");
        }

        public FSNode createNode(FSNode parent, String name, int mode) {
            throw new UnsupportedOperationException("createNode not implemented");
        }

        public void mkdir(String path) {
            throw new UnsupportedOperationException("mkdir not implemented");
        }

        public void mount(Object type, Object opts, String mountpoint) {
            throw new UnsupportedOperationException("mount not implemented");
        }

        public void symlink(String target, String path) {
            throw new UnsupportedOperationException("symlink not implemented");
        }

        public void utime(String path, long atime, long mtime) {
            throw new UnsupportedOperationException("utime not implemented");
        }

        public AnalyzePathResult analyzePath(String path) {
            throw new UnsupportedOperationException("analyzePath not implemented");
        }

        public String[] readdir(String path) {
            throw new UnsupportedOperationException("readdir not implemented");
        }

        public FsStats stat(String path) {
            throw new UnsupportedOperationException("stat not implemented");
        }

        public Uint8Array readFile(String path, ReadFileOptions options) {
            throw new UnsupportedOperationException("readFile not implemented");
        }

        public void writeFile(String path, Object data) {
            throw new UnsupportedOperationException("writeFile not implemented");
        }

        public void quit() {
            throw new UnsupportedOperationException("quit not implemented");
        }

        public void syncfs(boolean populate, SyncfsCallback done) {
            throw new UnsupportedOperationException("syncfs not implemented");
        }
    }

    public interface SyncfsCallback {
        void apply(Exception err);
    }

    public interface ErrnoErrorCarrier {
        Integer code();
    }

    public static final Map<String, Integer> ERRNO_CODES = Map.of(
        "EBADF", 8,
        "EBADFD", 127,
        "EEXIST", 20,
        "EINVAL", 28,
        "EISDIR", 31,
        "ENODEV", 43,
        "ENOENT", 44,
        "ENOTDIR", 54,
        "ENOTEMPTY", 55
    );

    /**
     * Create an emscripten filesystem that uses the BaseFilesystem.
     * @param Module The emscripten module
     * @param baseFS The BaseFilesystem implementation
     * @returns The emscripten filesystem
     */
    private static EmscriptenFileSystem createEmscriptenFS(
        PostgresMod Module,
        BaseFilesystem baseFS
    ) {
        var FS = Module.FS;
        var log = baseFS.debug ? (Log) args -> {
            var builder = new StringBuilder();
            for (var i = 0; i < args.length; i++) {
                if (i > 0) {
                    builder.append(" ");
                }
                builder.append(String.valueOf(args[i]));
            }
            System.out.println(builder);
        } : null;
        var emfs = new EmscriptenFileSystem();
        emfs.tryFSOperation = new TryFSOperation() {
            @Override
            public <T> T apply(Supplier<T> f) {
                try {
                    return f.get();
                } catch (RuntimeException e) {
                    if (e instanceof ErrnoErrorCarrier) {
                        var code = ((ErrnoErrorCarrier) e).code();
                        if (code == null) {
                            throw e;
                        }
                        var unknownCode = ERRNO_CODES.get("UNKNOWN");
                        if (unknownCode != null && code.equals(unknownCode)) {
                            throw new FS.ErrnoError(ERRNO_CODES.get("EINVAL"));
                        }
                        throw new FS.ErrnoError(code);
                    }
                    throw e;
                }
            }
        };
        emfs.mount = mount -> emfs.createNode.apply(null, "/", 16384 | 511, 0);
        emfs.syncfs = (mount, populate, done) -> {
            // noop
        };
        emfs.createNode = (parent, name, mode, dev) -> {
            if (!FS.isDir(mode) && !FS.isFile(mode)) {
                throw new FS.ErrnoError(28);
            }
            var node = FS.createNode(parent, name, mode);
            node.node_ops = emfs.node_ops;
            node.stream_ops = emfs.stream_ops;
            return node;
        };
        emfs.getMode = path -> {
            log(log, "getMode", path);
            return emfs.tryFSOperation.apply(() -> {
                var stats = baseFS.lstat(path);
                return stats.mode;
            });
        };
        emfs.realPath = node -> {
            var parts = new ArrayList<String>();
            while (node.parent != node) {
                parts.add(node.name);
                node = node.parent;
            }
            parts.add(node.mount.opts.root);
            var output = new StringBuilder();
            for (var i = parts.size() - 1; i >= 0; i--) {
                if (output.length() > 0) {
                    output.append("/");
                }
                output.append(parts.get(i));
            }
            return output.toString();
        };
        emfs.node_ops = new FS.NodeOps() {
            @Override
            public FS.Stats getattr(FS.FSNode node) {
                log(log, "getattr", emfs.realPath.apply(node));
                var path = emfs.realPath.apply(node);
                return emfs.tryFSOperation.apply(() -> {
                    var stats = baseFS.lstat(path);
                    var result = new FS.Stats();
                    result.dev = 0;
                    result.ino = node.id;
                    result.mode = stats.mode;
                    result.nlink = 1;
                    result.rdev = node.rdev;
                    result.size = stats.size;
                    result.atime = new Date(stats.atime);
                    result.mtime = new Date(stats.mtime);
                    result.ctime = new Date(stats.ctime);
                    return result;
                });
            }

            @Override
            public void setattr(FS.FSNode node, FS.Stats attr) {
                log(log, "setattr", emfs.realPath.apply(node), attr);
                var path = emfs.realPath.apply(node);
                emfs.tryFSOperation.apply(() -> {
                    if (attr.mode != null) {
                        baseFS.chmod(path, attr.mode);
                    }
                    if (attr.size != null) {
                        baseFS.truncate(path, attr.size);
                    }
                    if (attr.timestamp != null) {
                        baseFS.utimes(path, attr.timestamp, attr.timestamp);
                    }
                    if (attr.size != null) {
                        baseFS.truncate(path, attr.size);
                    }
                    return null;
                });
            }

            @Override
            public FS.FSNode lookup(FS.FSNode parent, String name) {
                log(log, "lookup", emfs.realPath.apply(parent), name);
                var path = emfs.realPath.apply(parent) + "/" + name;
                var mode = emfs.getMode.apply(path);
                return emfs.createNode.apply(parent, name, mode, null);
            }

            @Override
            public FS.FSNode mknod(FS.FSNode parent, String name, int mode, Object dev) {
                log(log, "mknod", emfs.realPath.apply(parent), name, mode, dev);
                var node = emfs.createNode.apply(parent, name, mode, dev);
                // create the backing node for this in the fs root as well
                var path = emfs.realPath.apply(node);
                return emfs.tryFSOperation.apply(() -> {
                    if (FS.isDir(node.mode)) {
                        var options = new MkdirOptions();
                        options.mode = mode;
                        baseFS.mkdir(path, options);
                    } else {
                        var options = new WriteFileOptions();
                        options.mode = mode;
                        baseFS.writeFile(path, "", options);
                    }
                    return node;
                });
            }

            @Override
            public void rename(FS.FSNode oldNode, FS.FSNode newDir, String newName) {
                log(log, "rename", emfs.realPath.apply(oldNode), emfs.realPath.apply(newDir), newName);
                var oldPath = emfs.realPath.apply(oldNode);
                var newPath = emfs.realPath.apply(newDir) + "/" + newName;
                emfs.tryFSOperation.apply(() -> {
                    baseFS.rename(oldPath, newPath);
                    return null;
                });
                oldNode.name = newName;
            }

            @Override
            public void unlink(FS.FSNode parent, String name) {
                log(log, "unlink", emfs.realPath.apply(parent), name);
                var path = emfs.realPath.apply(parent) + "/" + name;
                try {
                    baseFS.unlink(path);
                } catch (RuntimeException e) {
                    // no-op
                }
            }

            @Override
            public void rmdir(FS.FSNode parent, String name) {
                log(log, "rmdir", emfs.realPath.apply(parent), name);
                var path = emfs.realPath.apply(parent) + "/" + name;
                emfs.tryFSOperation.apply(() -> {
                    baseFS.rmdir(path);
                    return null;
                });
            }

            @Override
            public String[] readdir(FS.FSNode node) {
                log(log, "readdir", emfs.realPath.apply(node));
                var path = emfs.realPath.apply(node);
                return emfs.tryFSOperation.apply(() -> baseFS.readdir(path));
            }

            @Override
            public void symlink(FS.FSNode parent, String newName, String oldPath) {
                log(log, "symlink", emfs.realPath.apply(parent), newName, oldPath);
                // This is not supported by EMFS
                throw new FS.ErrnoError(63);
            }

            @Override
            public String readlink(FS.FSNode node) {
                log(log, "readlink", emfs.realPath.apply(node));
                // This is not supported by EMFS
                throw new FS.ErrnoError(63);
            }
        };
        emfs.stream_ops = new FS.StreamOps() {
            @Override
            public void open(FS.FSStream stream) {
                log(log, "open stream", emfs.realPath.apply(stream.node));
                var path = emfs.realPath.apply(stream.node);
                emfs.tryFSOperation.apply(() -> {
                    if (FS.isFile(stream.node.mode)) {
                        stream.shared.refcount = 1;
                        stream.nfd = baseFS.open(path, null, null);
                    }
                    return null;
                });
            }

            @Override
            public void close(FS.FSStream stream) {
                log(log, "close stream", emfs.realPath.apply(stream.node));
                emfs.tryFSOperation.apply(() -> {
                    if (
                        FS.isFile(stream.node.mode) &&
                        stream.nfd != null &&
                        --stream.shared.refcount == 0
                    ) {
                        baseFS.close(stream.nfd);
                    }
                    return null;
                });
            }

            @Override
            public void dup(FS.FSStream stream) {
                log(log, "dup stream", emfs.realPath.apply(stream.node));
                stream.shared.refcount++;
            }

            @Override
            public int read(
                FS.FSStream stream,
                Uint8Array buffer,
                int offset,
                int length,
                int position
            ) {
                log(
                    log,
                    "read stream",
                    emfs.realPath.apply(stream.node),
                    offset,
                    length,
                    position
                );
                if (length == 0) return 0;
                return emfs.tryFSOperation.apply(() ->
                    baseFS.read(
                        stream.nfd,
                        buffer,
                        offset,
                        length,
                        position
                    )
                );
            }

            @Override
            public int write(
                FS.FSStream stream,
                Uint8Array buffer,
                int offset,
                int length,
                int position
            ) {
                log(
                    log,
                    "write stream",
                    emfs.realPath.apply(stream.node),
                    offset,
                    length,
                    position
                );
                return emfs.tryFSOperation.apply(() ->
                    baseFS.write(
                        stream.nfd,
                        buffer,
                        offset,
                        length,
                        position
                    )
                );
            }

            @Override
            public int llseek(FS.FSStream stream, int offset, int whence) {
                log(log, "llseek stream", emfs.realPath.apply(stream.node), offset, whence);
                var position = offset;
                if (whence == 1) {
                    position += stream.position;
                } else if (whence == 2) {
                    if (FS.isFile(stream.node.mode)) {
                        var stat = emfs.tryFSOperation.apply(() -> baseFS.fstat(stream.nfd));
                        position += stat.size;
                    }
                }
                if (position < 0) {
                    throw new FS.ErrnoError(28);
                }
                return position;
            }

            @Override
            public FS.MMapResult mmap(
                FS.FSStream stream,
                int length,
                int position,
                Object prot,
                Object flags
            ) {
                log(
                    log,
                    "mmap stream",
                    emfs.realPath.apply(stream.node),
                    length,
                    position,
                    prot,
                    flags
                );
                if (!FS.isFile(stream.node.mode)) {
                    throw new FS.ErrnoError(ERRNO_CODES.get("ENODEV"));
                }

                var ptr = Module.mmapAlloc(length); // TODO: Fix type and check this is exported

                emfs.stream_ops.read(
                    stream,
                    Module.HEAP8,
                    ptr,
                    length,
                    position
                );
                var result = new FS.MMapResult();
                result.ptr = ptr;
                result.allocated = true;
                return result;
            }

            @Override
            public int msync(
                FS.FSStream stream,
                Uint8Array buffer,
                int offset,
                int length,
                Object mmapFlags
            ) {
                log(
                    log,
                    "msync stream",
                    emfs.realPath.apply(stream.node),
                    offset,
                    length,
                    mmapFlags
                );
                emfs.stream_ops.write(stream, buffer, 0, length, offset);
                return 0;
            }
        };
        return emfs;
    }

    private static void log(Log log, Object... args) {
        if (log != null) {
            log.apply(args);
        }
    }

    private interface Log {
        void apply(Object... args);
    }

    private static final class EmscriptenFileSystem {
        public TryFSOperation tryFSOperation;
        public Function<FS.FSMount, FS.FSNode> mount;
        public SyncfsOp syncfs;
        public CreateNodeOp createNode;
        public Function<String, Integer> getMode;
        public Function<FS.FSNode, String> realPath;
        public FS.NodeOps node_ops;
        public FS.StreamOps stream_ops;
    }

    private interface TryFSOperation {
        <T> T apply(Supplier<T> f);
    }

    private interface SyncfsOp {
        void apply(FS.FSMount mount, Object populate, DoneCallback done);
    }

    private interface DoneCallback {
        void apply(Integer err);
    }

    private interface CreateNodeOp {
        FS.FSNode apply(FS.FSNode parent, String name, int mode, Object dev);
    }

    private base() {
    }
}
