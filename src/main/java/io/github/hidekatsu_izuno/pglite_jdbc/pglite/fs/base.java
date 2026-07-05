package io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.extensionUtils;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.initdb;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.pglite;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.postgresMod;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class base {
    public static final String WASM_PREFIX = io.github.hidekatsu_izuno.pglite_jdbc.pglite.utils.WASM_PREFIX;
    public static final String PGDATA = initdb.PGDATA;

    public enum FsType {
        nodefs,
        memoryfs,
    }

    public record InitResult(Map<String, Object> emscriptenOpts) {}

    public interface Filesystem {
        Promise<InitResult> init(pglite pg, Map<String, Object> emscriptenOptions);

        Promise<Void> syncToFs(Boolean relaxedDurability);

        Promise<Void> initialSyncFs();

        Promise<Void> closeFs();
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

    public static class EmscriptenBuiltinFilesystem implements Filesystem {
        protected String dataDir;
        protected pglite pg;

        public EmscriptenBuiltinFilesystem(String dataDir) {
            this.dataDir = dataDir;
        }

        @Override
        public Promise<InitResult> init(pglite pg, Map<String, Object> emscriptenOptions) {
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
        public Promise<Void> closeFs() {
            return Promise.resolve(null);
        }
    }

    public static abstract class BaseFilesystem extends EmscriptenBuiltinFilesystem {
        protected final boolean debug;

        protected BaseFilesystem(String dataDir) {
            this(dataDir, false);
        }

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

        @Override
        public Promise<InitResult> init(pglite pg, Map<String, Object> emscriptenOptions) {
            this.pg = pg;
            var options = copyEmscriptenOptions(emscriptenOptions);
            var preRun = getPreRunHooks(options);
            preRun.add(mod -> {
                var emfs = createEmscriptenFS(mod, this);
                mod.FS().mkdir(PGDATA);
                mod.FS().mount(emfs, Map.of(), PGDATA);
            });
            options.put("preRun", preRun);
            return Promise.resolve(new InitResult(options));
        }

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

    public static class FsOperationException extends RuntimeException {
        public final Integer code;

        public FsOperationException(Integer code) {
            super("FS operation failed with code " + code);
            this.code = code;
        }

        public FsOperationException(String code) {
            super("FS operation failed with code " + code);
            Integer parsed;
            if ("UNKNOWN".equals(code)) {
                parsed = ERRNO_CODES.get("EINVAL");
            } else {
                try {
                    parsed = Integer.parseInt(code);
                } catch (NumberFormatException e) {
                    parsed = null;
                }
            }
            this.code = parsed;
        }
    }

    public static final class ErrnoError extends RuntimeException {
        public final int errno;

        public ErrnoError(int errno) {
            super("ErrnoError: " + errno);
            this.errno = errno;
        }
    }

    public interface EmscriptenFsBacking {
        boolean isDir(int mode);

        boolean isFile(int mode);

        FSNode createNode(FSNode parent, String name, int mode, Object dev);

        ErrnoError errnoError(int code);
    }

    public static final class FSNode {
        public FSNode parent;
        public String name;
        public int mode;
        public int rdev;
        public int id;
        public Object node_ops;
        public Object stream_ops;
        public FSMount mount;
    }

    public static final class FSMount {
        public MountOpts opts;

        public FSMount(MountOpts opts) {
            this.opts = opts;
        }
    }

    public record MountOpts(String root) {}

    public static final class FSStream {
        public FSNode node;
        public final SharedState shared = new SharedState();
        public int nfd;
        public int position;

        public static final class SharedState {
            public int refcount;
        }
    }

    public record GetAttrResult(
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
        Date atime,
        Date mtime,
        Date ctime
    ) {}

    public record SetAttr(
        Integer mode,
        Long size,
        Long timestamp
    ) {}

    public record MmapResult(int ptr, boolean allocated) {}

    public static Map<String, Object> copyEmscriptenOptions(Map<String, Object> emscriptenOptions) {
        var options = new HashMap<String, Object>();
        if (emscriptenOptions != null) {
            options.putAll(emscriptenOptions);
        }
        return options;
    }

    @SuppressWarnings("unchecked")
    public static List<Consumer<postgresMod.PostgresMod>> getPreRunHooks(Map<String, Object> options) {
        var preRun = new ArrayList<Consumer<postgresMod.PostgresMod>>();
        if (options.get("preRun") instanceof List<?> existing) {
            for (var item : existing) {
                if (item instanceof Consumer<?> consumer) {
                    preRun.add((Consumer<postgresMod.PostgresMod>) consumer);
                }
            }
        }
        return preRun;
    }

    @SuppressWarnings("unchecked")
    public static void mergeIntoPartialPostgresMod(
        postgresMod.PartialPostgresMod overrides,
        Map<String, Object> emscriptenOpts
    ) {
        if (emscriptenOpts == null) {
            return;
        }
        if (emscriptenOpts.get("WASM_PREFIX") instanceof String prefix) {
            overrides.WASM_PREFIX = prefix;
        }
        if (emscriptenOpts.get("INITIAL_MEMORY") instanceof Number memory) {
            overrides.INITIAL_MEMORY = memory.intValue();
        }
        if (emscriptenOpts.get("__wasiDataRoot") instanceof String dataRoot) {
            overrides.__wasiDataRoot = dataRoot;
        }
        if (emscriptenOpts.get("__wasiRoot") instanceof String wasiRoot) {
            overrides.__wasiRoot = wasiRoot;
        }
        if (emscriptenOpts.get("preRun") instanceof List<?> preRun) {
            var merged = overrides.preRun != null
                ? new ArrayList<>(overrides.preRun)
                : new ArrayList<Consumer<postgresMod.PostgresMod>>();
            for (var item : preRun) {
                if (item instanceof Consumer<?> consumer) {
                    merged.add((Consumer<postgresMod.PostgresMod>) consumer);
                }
            }
            overrides.preRun = merged;
        }
    }

    public static Object createEmscriptenFS(postgresMod.PostgresMod module, BaseFilesystem baseFS) {
        var fs = toEmscriptenFsBacking(module.FS());
        var log = baseFS.debug ? (Consumer<Object[]>) args -> System.out.println(args) : null;
        return new EmscriptenMountableFilesystem(module, fs, baseFS, log);
    }

    private static EmscriptenFsBacking toEmscriptenFsBacking(extensionUtils.EmscriptenFS fs) {
        if (fs instanceof EmscriptenFsBacking backing) {
            return backing;
        }
        return new EmscriptenFsBacking() {
            @Override
            public boolean isDir(int mode) {
                return fs.isDir(mode);
            }

            @Override
            public boolean isFile(int mode) {
                return fs.isFile(mode);
            }

            @Override
            public FSNode createNode(FSNode parent, String name, int mode, Object dev) {
                return fs.createNode(parent, name, mode, dev);
            }

            @Override
            public ErrnoError errnoError(int code) {
                return fs.errnoError(code);
            }
        };
    }

    static <T> T tryFSOperation(EmscriptenFsBacking fs, Supplier<T> operation) {
        try {
            return operation.get();
        } catch (FsOperationException e) {
            if (e.code == null) {
                throw e;
            }
            throw fs.errnoError(e.code);
        } catch (RuntimeException e) {
            if (e instanceof ErrnoError) {
                throw e;
            }
            throw e;
        }
    }

    static void tryFSOperationVoid(EmscriptenFsBacking fs, Runnable operation) {
        tryFSOperation(fs, () -> {
            operation.run();
            return null;
        });
    }

    public static final class EmscriptenMountableFilesystem {
        private final postgresMod.PostgresMod module;
        private final EmscriptenFsBacking fs;
        private final BaseFilesystem baseFS;
        private final Consumer<Object[]> log;
        public final NodeOps node_ops = new NodeOps();
        public final StreamOps stream_ops = new StreamOps();

        public EmscriptenMountableFilesystem(
            postgresMod.PostgresMod module,
            EmscriptenFsBacking fs,
            BaseFilesystem baseFS,
            Consumer<Object[]> log
        ) {
            this.module = module;
            this.fs = fs;
            this.baseFS = baseFS;
            this.log = log;
        }

        public FSNode mount(FSMount mount) {
            return createNode(null, "/", 16384 | 511, 0);
        }

        public void syncfs(Object mount, Object populate, Consumer<Integer> done) {
            if (done != null) {
                done.accept(null);
            }
        }

        public FSNode createNode(FSNode parent, String name, int mode, Object dev) {
            if (!fs.isDir(mode) && !fs.isFile(mode)) {
                throw fs.errnoError(ERRNO_CODES.get("EINVAL"));
            }
            var node = fs.createNode(parent, name, mode, dev);
            node.node_ops = node_ops;
            node.stream_ops = stream_ops;
            return node;
        }

        public int getMode(String path) {
            if (log != null) {
                log.accept(new Object[] { "getMode", path });
            }
            return tryFSOperation(fs, () -> baseFS.lstat(path).mode());
        }

        public String realPath(FSNode node) {
            var parts = new ArrayList<String>();
            var current = node;
            while (current.parent != current) {
                parts.add(current.name);
                current = current.parent;
            }
            parts.add(current.mount.opts.root());
            var reversed = new ArrayList<String>();
            for (var i = parts.size() - 1; i >= 0; i--) {
                reversed.add(parts.get(i));
            }
            return String.join("/", reversed);
        }

        public final class NodeOps {
            public GetAttrResult getattr(FSNode node) {
                if (log != null) {
                    log.accept(new Object[] { "getattr", realPath(node) });
                }
                var path = realPath(node);
                return tryFSOperation(fs, () -> {
                    var stats = baseFS.lstat(path);
                    return new GetAttrResult(
                        0,
                        node.id,
                        stats.mode(),
                        1,
                        stats.uid(),
                        stats.gid(),
                        node.rdev,
                        stats.size(),
                        stats.blksize(),
                        stats.blocks(),
                        new Date(stats.atime()),
                        new Date(stats.mtime()),
                        new Date(stats.ctime())
                    );
                });
            }

            public void setattr(FSNode node, SetAttr attr) {
                if (log != null) {
                    log.accept(new Object[] { "setattr", realPath(node), attr });
                }
                var path = realPath(node);
                tryFSOperationVoid(fs, () -> {
                    if (attr.mode() != null) {
                        baseFS.chmod(path, attr.mode());
                    }
                    if (attr.size() != null) {
                        baseFS.truncate(path, attr.size().intValue());
                    }
                    if (attr.timestamp() != null) {
                        baseFS.utimes(path, attr.timestamp(), attr.timestamp());
                    }
                    if (attr.size() != null) {
                        baseFS.truncate(path, attr.size().intValue());
                    }
                });
            }

            public FSNode lookup(FSNode parent, String name) {
                if (log != null) {
                    log.accept(new Object[] { "lookup", realPath(parent), name });
                }
                var path = realPath(parent) + "/" + name;
                var mode = getMode(path);
                return createNode(parent, name, mode, null);
            }

            public FSNode mknod(FSNode parent, String name, int mode, Object dev) {
                if (log != null) {
                    log.accept(new Object[] { "mknod", realPath(parent), name, mode, dev });
                }
                var node = createNode(parent, name, mode, dev);
                var path = realPath(node);
                return tryFSOperation(fs, () -> {
                    if (fs.isDir(node.mode)) {
                        baseFS.mkdir(path, false, mode);
                    } else {
                        baseFS.writeFile(path, new byte[0], null, mode, null);
                    }
                    return node;
                });
            }

            public void rename(FSNode oldNode, FSNode newDir, String newName) {
                if (log != null) {
                    log.accept(new Object[] { "rename", realPath(oldNode), realPath(newDir), newName });
                }
                var oldPath = realPath(oldNode);
                var newPath = realPath(newDir) + "/" + newName;
                tryFSOperationVoid(fs, () -> baseFS.rename(oldPath, newPath));
                oldNode.name = newName;
            }

            public void unlink(FSNode parent, String name) {
                if (log != null) {
                    log.accept(new Object[] { "unlink", realPath(parent), name });
                }
                var path = realPath(parent) + "/" + name;
                try {
                    baseFS.unlink(path);
                } catch (RuntimeException ignored) {
                    // no-op
                }
            }

            public void rmdir(FSNode parent, String name) {
                if (log != null) {
                    log.accept(new Object[] { "rmdir", realPath(parent), name });
                }
                var path = realPath(parent) + "/" + name;
                tryFSOperationVoid(fs, () -> baseFS.rmdir(path));
            }

            public String[] readdir(FSNode node) {
                if (log != null) {
                    log.accept(new Object[] { "readdir", realPath(node) });
                }
                var path = realPath(node);
                return tryFSOperation(fs, () -> baseFS.readdir(path));
            }

            public void symlink(FSNode parent, String newName, String oldPath) {
                if (log != null) {
                    log.accept(new Object[] { "symlink", realPath(parent), newName, oldPath });
                }
                throw fs.errnoError(63);
            }

            public String readlink(FSNode node) {
                if (log != null) {
                    log.accept(new Object[] { "readlink", realPath(node) });
                }
                throw fs.errnoError(63);
            }
        }

        public final class StreamOps {
            public void open(FSStream stream) {
                if (log != null) {
                    log.accept(new Object[] { "open stream", realPath(stream.node) });
                }
                var path = realPath(stream.node);
                tryFSOperationVoid(fs, () -> {
                    if (fs.isFile(stream.node.mode)) {
                        stream.shared.refcount = 1;
                        stream.nfd = baseFS.open(path, null, null);
                    }
                });
            }

            public void close(FSStream stream) {
                if (log != null) {
                    log.accept(new Object[] { "close stream", realPath(stream.node) });
                }
                tryFSOperationVoid(fs, () -> {
                    if (fs.isFile(stream.node.mode) && stream.nfd != 0 && --stream.shared.refcount == 0) {
                        baseFS.close(stream.nfd);
                    }
                });
            }

            public void dup(FSStream stream) {
                if (log != null) {
                    log.accept(new Object[] { "dup stream", realPath(stream.node) });
                }
                stream.shared.refcount++;
            }

            public int read(
                FSStream stream,
                byte[] buffer,
                int offset,
                int length,
                int position
            ) {
                if (log != null) {
                    log.accept(
                        new Object[] { "read stream", realPath(stream.node), offset, length, position }
                    );
                }
                if (length == 0) {
                    return 0;
                }
                return tryFSOperation(
                    fs,
                    () -> baseFS.read(stream.nfd, buffer, offset, length, position)
                );
            }

            public int write(
                FSStream stream,
                byte[] buffer,
                int offset,
                int length,
                int position
            ) {
                if (log != null) {
                    log.accept(
                        new Object[] { "write stream", realPath(stream.node), offset, length, position }
                    );
                }
                return tryFSOperation(
                    fs,
                    () -> baseFS.write(stream.nfd, buffer, offset, length, position)
                );
            }

            public int llseek(FSStream stream, int offset, int whence) {
                if (log != null) {
                    log.accept(new Object[] { "llseek stream", realPath(stream.node), offset, whence });
                }
                var position = offset;
                if (whence == 1) {
                    position += stream.position;
                } else if (whence == 2) {
                    if (fs.isFile(stream.node.mode)) {
                        position += baseFS.fstat(stream.nfd).size();
                    }
                }
                if (position < 0) {
                    throw fs.errnoError(ERRNO_CODES.get("EINVAL"));
                }
                return position;
            }

            public MmapResult mmap(
                FSStream stream,
                int length,
                int position,
                Object prot,
                Object flags
            ) {
                if (log != null) {
                    log.accept(
                        new Object[] { "mmap stream", realPath(stream.node), length, position, prot, flags }
                    );
                }
                if (!fs.isFile(stream.node.mode)) {
                    throw fs.errnoError(ERRNO_CODES.get("ENODEV"));
                }
                var ptr = module.mmapAlloc(length);
                var temp = new byte[length];
                stream_ops.read(stream, temp, 0, length, position);
                module.copyToHeap(ptr, temp, 0, length);
                return new MmapResult(ptr, true);
            }

            public int msync(
                FSStream stream,
                byte[] buffer,
                int offset,
                int length,
                Object mmapFlags
            ) {
                if (log != null) {
                    log.accept(
                        new Object[] { "msync stream", realPath(stream.node), offset, length, mmapFlags }
                    );
                }
                stream_ops.write(stream, buffer, 0, length, offset);
                return 0;
            }
        }
    }
}
