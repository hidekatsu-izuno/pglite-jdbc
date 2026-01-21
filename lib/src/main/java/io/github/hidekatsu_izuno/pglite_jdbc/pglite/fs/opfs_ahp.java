package io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.JSON;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class opfs_ahp {
    public static final class OpfsAhpOptions {
        public Integer initialPoolSize;
        public Integer maintainedPoolSize;
        public Boolean debug;
    }

    // TypeScript doesn't have a built-in type for FileSystemSyncAccessHandle
    public interface FileSystemSyncAccessHandle {
        void close();
        void flush();
        int getSize();
        int read(byte[] buffer, int offset, int length, int at);
        void truncate(int newSize);
        int write(byte[] buffer, int offset, int length, int at);
    }

    public interface FileSystemFileHandle {
        FileSystemSyncAccessHandle createSyncAccessHandle();
        String name();
    }

    public interface FileSystemDirectoryHandle {
        CompletableFuture<FileSystemDirectoryHandle> getDirectoryHandle(
            String name,
            HandleOptions options
        );
        CompletableFuture<FileSystemFileHandle> getFileHandle(
            String name,
            HandleOptions options
        );
        CompletableFuture<Void> removeEntry(String name);
    }

    public interface Storage {
        CompletableFuture<FileSystemDirectoryHandle> getDirectory();
    }

    public static final class navigator {
        public static Storage storage;

        private navigator() {
        }
    }

    public static final class HandleOptions {
        public boolean create;
    }

    // State

    private static final String STATE_FILE = "state.txt";
    private static final String DATA_DIR = "data";
    private static final class INITIAL_MODE {
        private static final int DIR = 16384;
        private static final int FILE = 32768;

        private INITIAL_MODE() {
        }
    }

    public static final class State {
        public DirectoryNode root;
        public List<String> pool;
    }

    public static final class PoolFilenames extends ArrayList<String> {
    }

    // WAL

    public static final class WALEntry {
        public String opp;
        public Object[] args;
    }

    // Node tree

    public static final class NodeType {
        public static final String file = "file";
        public static final String directory = "directory";

        private NodeType() {
        }
    }

    private interface BaseNode {
        String type();
        long lastModified();
        void setLastModified(long value);
        int mode();
        void setMode(int value);
    }

    public static final class FileNode implements BaseNode {
        public String type;
        public long lastModified;
        public int mode;
        public String backingFilename;

        @Override
        public String type() {
            return this.type;
        }

        @Override
        public long lastModified() {
            return this.lastModified;
        }

        @Override
        public void setLastModified(long value) {
            this.lastModified = value;
        }

        @Override
        public int mode() {
            return this.mode;
        }

        @Override
        public void setMode(int value) {
            this.mode = value;
        }
    }

    public static final class DirectoryNode implements BaseNode {
        public String type;
        public long lastModified;
        public int mode;
        public Map<String, BaseNode> children;

        @Override
        public String type() {
            return this.type;
        }

        @Override
        public long lastModified() {
            return this.lastModified;
        }

        @Override
        public void setLastModified(long value) {
            this.lastModified = value;
        }

        @Override
        public int mode() {
            return this.mode;
        }

        @Override
        public void setMode(int value) {
            this.mode = value;
        }
    }

    public static class OpfsAhpFS extends base.BaseFilesystem {
        public final int initialPoolSize;
        public final int maintainedPoolSize;

        private FileSystemDirectoryHandle opfsRootAh;
        private FileSystemDirectoryHandle rootAh;
        private FileSystemDirectoryHandle dataDirAh;

        private FileSystemFileHandle stateFH;
        private FileSystemSyncAccessHandle stateSH;

        private Map<String, FileSystemFileHandle> fh = new HashMap<>();
        private Map<String, FileSystemSyncAccessHandle> sh = new HashMap<>();

        private int handleIdCounter = 0;
        private Map<Integer, String> openHandlePaths = new HashMap<>();
        private Map<String, Integer> openHandleIds = new HashMap<>();

        public State state;
        public long lastCheckpoint = 0;
        public long checkpointInterval = 1000L * 60L; // 1 minute
        public int poolCounter = 0;

        private Set<FileSystemSyncAccessHandle> unsyncedSH = new HashSet<>();

        public OpfsAhpFS(String dataDir, OpfsAhpOptions options) {
            super(dataDir, opfs_ahp.toBaseOptions(options));
            var resolvedOptions = options != null ? options : new OpfsAhpOptions();
            this.initialPoolSize = resolvedOptions.initialPoolSize != null
                ? resolvedOptions.initialPoolSize
                : 1000;
            this.maintainedPoolSize = resolvedOptions.maintainedPoolSize != null
                ? resolvedOptions.maintainedPoolSize
                : 100;
        }

        @Override
        public CompletableFuture<base.InitResult> init(
            base.PGlite pg,
            base.PostgresMod opts
        ) {
            return this.initInternal().thenCompose(
                ignored -> super.init(pg, opts)
            );
        }

        @Override
        public CompletableFuture<Void> syncToFs(Boolean relaxedDurability) {
            return CompletableFuture.runAsync(
                () -> {
                    this.maybeCheckpointState().join();
                    this.maintainPool(null).join();
                    if (relaxedDurability == null || !relaxedDurability) {
                        this.flush();
                    }
                }
            );
        }

        @Override
        public CompletableFuture<Void> closeFs() {
            return CompletableFuture.runAsync(
                () -> {
                    for (var sh : this.sh.values()) {
                        sh.close();
                    }
                    this.stateSH.flush();
                    this.stateSH.close();
                    this.pg.Module.FS.quit();
                }
            );
        }

        private CompletableFuture<Void> initInternal() {
            return CompletableFuture.runAsync(
                () -> {
                    this.opfsRootAh = navigator.storage.getDirectory().join();
                    this.rootAh = this.resolveOpfsDirectory(
                        this.dataDir,
                        new ResolveDirectoryOptions(null, true)
                    ).join();
                    this.dataDirAh = this.resolveOpfsDirectory(
                        DATA_DIR,
                        new ResolveDirectoryOptions(this.rootAh, true)
                    ).join();

                    this.stateFH = this.rootAh.getFileHandle(
                        STATE_FILE,
                        opfs_ahp.handleOptions(true)
                    ).join();
                    this.stateSH = this.stateFH.createSyncAccessHandle();

                    var stateAB = new byte[this.stateSH.getSize()];
                    this.stateSH.read(stateAB, 0, stateAB.length, 0);
                    var stateLines = new String(stateAB, StandardCharsets.UTF_8).split("\n");
                    // Line 1 is a base state object.
                    // Lines 1+n are WAL entries.

                    var isNewState = false;
                    State state;
                    try {
                        var parsed = JSON.parse(stateLines[0]);
                        state = parseState(parsed);
                    } catch (RuntimeException e) {
                        state = new State();
                        var root = new DirectoryNode();
                        root.type = NodeType.directory;
                        root.lastModified = System.currentTimeMillis();
                        root.mode = INITIAL_MODE.DIR;
                        root.children = new HashMap<>();
                        state.root = root;
                        state.pool = new ArrayList<>();
                        // write new state to file
                        this.stateSH.truncate(0);
                        var stateJson = JSON.stringify(stateToMap(state));
                        var stateBytes = stateJson.getBytes(StandardCharsets.UTF_8);
                        this.stateSH.write(stateBytes, 0, stateBytes.length, 0);
                        isNewState = true;
                    }
                    this.state = state;

                    // Apply WAL entries
                    var wal = new ArrayList<WALEntry>();
                    for (var i = 1; i < stateLines.length; i++) {
                        var line = stateLines[i];
                        if (line == null || line.isEmpty()) {
                            continue;
                        }
                        var entryMap = JSON.parse(line);
                        wal.add(parseWalEntry(entryMap));
                    }
                    for (var entry : wal) {
                        try {
                            applyWalEntry(entry);
                        } catch (RuntimeException e) {
                            System.out.println(
                                "Error applying OPFS AHP WAL entry " + entry.opp + " " + e
                            );
                        }
                    }

                    // Open all file handles for dir tree
                    var walk = new Object() {
                        void apply(BaseNode node) {
                            if (NodeType.file.equals(node.type())) {
                                var fileNode = (FileNode) node;
                                try {
                                    var fh = dataDirAh.getFileHandle(
                                        fileNode.backingFilename,
                                        opfs_ahp.handleOptions(false)
                                    ).join();
                                    var sh = fh.createSyncAccessHandle();
                                    OpfsAhpFS.this.fh.put(fileNode.backingFilename, fh);
                                    OpfsAhpFS.this.sh.put(fileNode.backingFilename, sh);
                                } catch (RuntimeException e) {
                                    System.out.println(
                                        "Error opening file handle for node " + fileNode + " " + e
                                    );
                                }
                            } else {
                                var dirNode = (DirectoryNode) node;
                                for (var child : dirNode.children.values()) {
                                    this.apply(child);
                                }
                            }
                        }
                    };
                    walk.apply(this.state.root);

                    // Open all pool file handles
                    for (var filename : this.state.pool) {
                        if (this.fh.containsKey(filename)) {
                            System.out.println("File handle already exists for pool file " + filename);
                        }
                        var fh = this.dataDirAh.getFileHandle(
                            filename,
                            opfs_ahp.handleOptions(false)
                        ).join();
                        var sh = fh.createSyncAccessHandle();
                        this.fh.put(filename, fh);
                        this.sh.put(filename, sh);
                    }

                    this.maintainPool(
                        isNewState ? this.initialPoolSize : this.maintainedPoolSize
                    ).join();
                }
            );
        }

        public CompletableFuture<Void> maintainPool(Integer size) {
            return CompletableFuture.runAsync(
                () -> {
                    var resolvedSize = size != null ? size : this.maintainedPoolSize;
                    var change = resolvedSize - this.state.pool.size();
                    for (var i = 0; i < change; i++) {
                        ++this.poolCounter;
                        var timestamp = System.currentTimeMillis() - 1704063600L;
                        var filename = padHex(timestamp, 8)
                            + "-"
                            + padHex(this.poolCounter, 8);
                        var fh = this.dataDirAh.getFileHandle(
                            filename,
                            opfs_ahp.handleOptions(true)
                        ).join();
                        var sh = fh.createSyncAccessHandle();
                        this.fh.put(filename, fh);
                        this.sh.put(filename, sh);
                        this.logWAL(new WalRecord("createPoolFile", new Object[] { filename }));
                        this.state.pool.add(filename);
                    }
                    for (var i = 0; i > change; i--) {
                        var filename = this.state.pool.remove(this.state.pool.size() - 1);
                        this.logWAL(new WalRecord("deletePoolFile", new Object[] { filename }));
                        var fh = this.fh.get(filename);
                        var sh = this.sh.get(filename);
                        if (sh != null) {
                            sh.close();
                        }
                        this.dataDirAh.removeEntry(fh.name()).join();
                        this.fh.remove(filename);
                        this.sh.remove(filename);
                    }
                }
            );
        }

        public void _createPoolFileState(String filename) {
            this.state.pool.add(filename);
        }

        public void _deletePoolFileState(String filename) {
            var index = this.state.pool.indexOf(filename);
            if (index > -1) {
                this.state.pool.remove(index);
            }
        }

        public CompletableFuture<Void> maybeCheckpointState() {
            if (System.currentTimeMillis() - this.lastCheckpoint > this.checkpointInterval) {
                return this.checkpointState();
            }
            return CompletableFuture.completedFuture(null);
        }

        public CompletableFuture<Void> checkpointState() {
            return CompletableFuture.runAsync(
                () -> {
                    var stateJson = JSON.stringify(stateToMap(this.state));
                    var stateAB = stateJson.getBytes(StandardCharsets.UTF_8);
                    this.stateSH.truncate(0);
                    this.stateSH.write(stateAB, 0, stateAB.length, 0);
                    this.stateSH.flush();
                    this.lastCheckpoint = System.currentTimeMillis();
                }
            );
        }

        public void flush() {
            for (var sh : this.unsyncedSH) {
                try {
                    sh.flush();
                } catch (RuntimeException e) {
                    // The file may have been closed if it was deleted
                }
            }
            this.unsyncedSH.clear();
        }

        // Filesystem API:

        @Override
        public void chmod(String path, int mode) {
            this.tryWithWAL(new WalRecord("chmod", new Object[] { path, mode }), () -> {
                this._chmodState(path, mode);
            });
        }

        public void _chmodState(String path, int mode) {
            var node = this.resolvePath(path, null);
            node.setMode(mode);
        }

        @Override
        public void close(int fd) {
            var path = this.getPathFromFd(fd);
            this.openHandlePaths.remove(fd);
            this.openHandleIds.remove(path);
        }

        @Override
        public base.FsStats fstat(int fd) {
            var path = this.getPathFromFd(fd);
            return this.lstat(path);
        }

        @Override
        public base.FsStats lstat(String path) {
            var node = this.resolvePath(path, null);
            var size = NodeType.file.equals(node.type())
                ? this.sh.get(((FileNode) node).backingFilename).getSize()
                : 0;
            var blksize = 4096;
            var stats = new base.FsStats();
            stats.dev = 0;
            stats.ino = 0;
            stats.mode = node.mode();
            stats.nlink = 1;
            stats.uid = 0;
            stats.gid = 0;
            stats.rdev = 0;
            stats.size = size;
            stats.blksize = blksize;
            stats.blocks = (int) Math.ceil((double) size / blksize);
            stats.atime = node.lastModified();
            stats.mtime = node.lastModified();
            stats.ctime = node.lastModified();
            return stats;
        }

        @Override
        public void mkdir(String path, base.MkdirOptions options) {
            this.tryWithWAL(new WalRecord("mkdir", new Object[] { path, options }), () -> {
                this._mkdirState(path, options);
            });
        }

        public void _mkdirState(String path, base.MkdirOptions options) {
            var parts = this.pathParts(path);
            var newDirName = parts.remove(parts.size() - 1);
            var currentPath = new ArrayList<String>();
            var node = this.state.root;
            for (var part : parts) {
                currentPath.add(path);
                if (!node.children.containsKey(part)) {
                    if (options != null && Boolean.TRUE.equals(options.recursive)) {
                        this.mkdir(String.join("/", currentPath), null);
                    } else {
                        throw new FsError("ENOENT", "No such file or directory");
                    }
                }
                if (!NodeType.directory.equals(node.children.get(part).type())) {
                    throw new FsError("ENOTDIR", "Not a directory");
                }
                node = (DirectoryNode) node.children.get(part);
            }
            if (node.children.containsKey(newDirName)) {
                throw new FsError("EEXIST", "File exists");
            }
            var newDir = new DirectoryNode();
            newDir.type = NodeType.directory;
            newDir.lastModified = System.currentTimeMillis();
            newDir.mode = options != null && options.mode != null
                ? options.mode
                : INITIAL_MODE.DIR;
            newDir.children = new HashMap<>();
            node.children.put(newDirName, newDir);
        }

        @Override
        public int open(String path, String _flags, Integer _mode) {
            var node = this.resolvePath(path, null);
            if (!NodeType.file.equals(node.type())) {
                throw new FsError("EISDIR", "Is a directory");
            }
            var handleId = this.nextHandleId();
            this.openHandlePaths.put(handleId, path);
            this.openHandleIds.put(path, handleId);
            return handleId;
        }

        @Override
        public String[] readdir(String path) {
            var node = this.resolvePath(path, null);
            if (!NodeType.directory.equals(node.type())) {
                throw new FsError("ENOTDIR", "Not a directory");
            }
            var dirNode = (DirectoryNode) node;
            return dirNode.children.keySet().toArray(new String[0]);
        }

        @Override
        public int read(
            int fd,
            Uint8Array buffer,
            int offset,
            int length,
            int position
        ) {
            var path = this.getPathFromFd(fd);
            var node = this.resolvePath(path, null);
            if (!NodeType.file.equals(node.type())) {
                throw new FsError("EISDIR", "Is a directory");
            }
            var sh = this.sh.get(((FileNode) node).backingFilename);
            var temp = new byte[length];
            var read = sh.read(temp, 0, length, position);
            if (read > 0) {
                if (read == temp.length) {
                    buffer.set(temp, offset);
                } else {
                    var slice = new byte[read];
                    System.arraycopy(temp, 0, slice, 0, read);
                    buffer.set(slice, offset);
                }
            }
            return read;
        }

        @Override
        public void rename(String oldPath, String newPath) {
            this.tryWithWAL(new WalRecord("rename", new Object[] { oldPath, newPath }), () -> {
                this._renameState(oldPath, newPath, true);
            });
        }

        public void _renameState(String oldPath, String newPath, boolean doFileOps) {
            var oldPathParts = this.pathParts(oldPath);
            var oldFilename = oldPathParts.remove(oldPathParts.size() - 1);
            var oldParent = (DirectoryNode) this.resolvePath(
                String.join("/", oldPathParts),
                null
            );
            if (!oldParent.children.containsKey(oldFilename)) {
                throw new FsError("ENOENT", "No such file or directory");
            }
            var newPathParts = this.pathParts(newPath);
            var newFilename = newPathParts.remove(newPathParts.size() - 1);
            var newParent = (DirectoryNode) this.resolvePath(
                String.join("/", newPathParts),
                null
            );
            if (doFileOps && newParent.children.containsKey(newFilename)) {
                // Overwrite, so return the underlying file to the pool
                var node = (FileNode) newParent.children.get(newFilename);
                var sh = this.sh.get(node.backingFilename);
                sh.truncate(0);
                this.state.pool.add(node.backingFilename);
            }
            newParent.children.put(newFilename, oldParent.children.get(oldFilename));
            oldParent.children.remove(oldFilename);
        }

        @Override
        public void rmdir(String path) {
            this.tryWithWAL(new WalRecord("rmdir", new Object[] { path }), () -> {
                this._rmdirState(path);
            });
        }

        public void _rmdirState(String path) {
            var pathParts = this.pathParts(path);
            var dirName = pathParts.remove(pathParts.size() - 1);
            var parent = (DirectoryNode) this.resolvePath(
                String.join("/", pathParts),
                null
            );
            if (!parent.children.containsKey(dirName)) {
                throw new FsError("ENOENT", "No such file or directory");
            }
            var node = parent.children.get(dirName);
            if (!NodeType.directory.equals(node.type())) {
                throw new FsError("ENOTDIR", "Not a directory");
            }
            var dirNode = (DirectoryNode) node;
            if (!dirNode.children.isEmpty()) {
                throw new FsError("ENOTEMPTY", "Directory not empty");
            }
            parent.children.remove(dirName);
        }

        @Override
        public void truncate(String path, int len) {
            var node = this.resolvePath(path, null);
            if (!NodeType.file.equals(node.type())) {
                throw new FsError("EISDIR", "Is a directory");
            }
            var sh = this.sh.get(((FileNode) node).backingFilename);
            if (sh == null) {
                throw new FsError("ENOENT", "No such file or directory");
            }
            sh.truncate(len);
            this.unsyncedSH.add(sh);
        }

        @Override
        public void unlink(String path) {
            this.tryWithWAL(new WalRecord("unlink", new Object[] { path }), () -> {
                this._unlinkState(path, true);
            });
        }

        public void _unlinkState(String path, boolean doFileOps) {
            var pathParts = this.pathParts(path);
            var filename = pathParts.remove(pathParts.size() - 1);
            var dir = (DirectoryNode) this.resolvePath(
                String.join("/", pathParts),
                null
            );
            if (!dir.children.containsKey(filename)) {
                throw new FsError("ENOENT", "No such file or directory");
            }
            var node = dir.children.get(filename);
            if (!NodeType.file.equals(node.type())) {
                throw new FsError("EISDIR", "Is a directory");
            }
            dir.children.remove(filename);
            if (doFileOps) {
                var fileNode = (FileNode) node;
                var sh = this.sh.get(fileNode.backingFilename);
                // We don't delete the file, it's truncated and returned to the pool
                if (sh != null) {
                    sh.truncate(0);
                    this.unsyncedSH.add(sh);
                }
                if (this.openHandleIds.containsKey(path)) {
                    var handleId = this.openHandleIds.get(path);
                    this.openHandlePaths.remove(handleId);
                    this.openHandleIds.remove(path);
                }
            }
            this.state.pool.add(((FileNode) node).backingFilename);
        }

        @Override
        public void utimes(String path, long atime, long mtime) {
            this.tryWithWAL(new WalRecord("utimes", new Object[] { path, atime, mtime }), () -> {
                this._utimesState(path, atime, mtime);
            });
        }

        public void _utimesState(String path, long _atime, long mtime) {
            var node = this.resolvePath(path, null);
            node.setLastModified(mtime);
        }

        @Override
        public void writeFile(String path, Object data, base.WriteFileOptions options) {
            var pathParts = this.pathParts(path);
            var filename = pathParts.remove(pathParts.size() - 1);
            var parent = (DirectoryNode) this.resolvePath(
                String.join("/", pathParts),
                null
            );

            if (!parent.children.containsKey(filename)) {
                if (this.state.pool.isEmpty()) {
                    throw new RuntimeException("No more file handles available in the pool");
                }
                var node = new FileNode();
                node.type = NodeType.file;
                node.lastModified = System.currentTimeMillis();
                node.mode = options != null && options.mode != null
                    ? options.mode
                    : INITIAL_MODE.FILE;
                node.backingFilename = this.state.pool.remove(this.state.pool.size() - 1);
                parent.children.put(filename, node);
                this.logWAL(
                    new WalRecord("createFileNode", new Object[] { path, node })
                );
            } else {
                var node = (FileNode) parent.children.get(filename);
                node.lastModified = System.currentTimeMillis();
                this.logWAL(
                    new WalRecord(
                        "setLastModified",
                        new Object[] { path, node.lastModified }
                    )
                );
            }
            var node = (FileNode) parent.children.get(filename);
            var sh = this.sh.get(node.backingFilename);
            // Files in pool are empty, only write if data is provided
            if (getDataLength(data) > 0) {
                var bytes = toBytes(data);
                sh.write(bytes, 0, bytes.length, 0);
                if (path.startsWith("/pg_wal")) {
                    this.unsyncedSH.add(sh);
                }
            }
        }

        public FileNode _createFileNodeState(String path, FileNode node) {
            var pathParts = this.pathParts(path);
            var filename = pathParts.remove(pathParts.size() - 1);
            var parent = (DirectoryNode) this.resolvePath(
                String.join("/", pathParts),
                null
            );
            parent.children.put(filename, node);
            // remove backingFilename from pool
            var index = this.state.pool.indexOf(node.backingFilename);
            if (index > -1) {
                this.state.pool.remove(index);
            }
            return node;
        }

        public void _setLastModifiedState(String path, long lastModified) {
            var node = this.resolvePath(path, null);
            node.setLastModified(lastModified);
        }

        @Override
        public int write(
            int fd,
            Uint8Array buffer,
            int offset,
            int length,
            int position
        ) {
            var path = this.getPathFromFd(fd);
            var node = this.resolvePath(path, null);
            if (!NodeType.file.equals(node.type())) {
                throw new FsError("EISDIR", "Is a directory");
            }
            var sh = this.sh.get(((FileNode) node).backingFilename);
            if (sh == null) {
                throw new FsError("EBADF", "Bad file descriptor");
            }
            var temp = new byte[length];
            for (var i = 0; i < length; i++) {
                temp[i] = buffer.get(offset + i);
            }
            var ret = sh.write(temp, 0, length, position);
            if (path.startsWith("/pg_wal")) {
                this.unsyncedSH.add(sh);
            }
            return ret;
        }

        // Internal methods:

        private void tryWithWAL(WalRecord entry, Runnable fn) {
            var offset = this.logWAL(entry);
            try {
                fn.run();
            } catch (RuntimeException e) {
                // Rollback WAL entry
                this.stateSH.truncate(offset);
                throw e;
            }
        }

        private int logWAL(WalRecord entry) {
            var entryJSON = JSON.stringify(entryToMap(entry));
            var stateAB = ("\n" + entryJSON).getBytes(StandardCharsets.UTF_8);
            var offset = this.stateSH.getSize();
            this.stateSH.write(stateAB, 0, stateAB.length, offset);
            this.unsyncedSH.add(this.stateSH);
            return offset;
        }

        private List<String> pathParts(String path) {
            var parts = path.split("/");
            var result = new ArrayList<String>();
            for (var part : parts) {
                if (!part.isEmpty()) {
                    result.add(part);
                }
            }
            return result;
        }

        private BaseNode resolvePath(String path, DirectoryNode from) {
            var parts = this.pathParts(path);
            BaseNode node = from != null ? from : this.state.root;
            for (var part : parts) {
                if (!NodeType.directory.equals(node.type())) {
                    throw new FsError("ENOTDIR", "Not a directory");
                }
                var dirNode = (DirectoryNode) node;
                if (!dirNode.children.containsKey(part)) {
                    throw new FsError("ENOENT", "No such file or directory");
                }
                node = dirNode.children.get(part);
            }
            return node;
        }

        private String getPathFromFd(int fd) {
            var path = this.openHandlePaths.get(fd);
            if (path == null) {
                throw new FsError("EBADF", "Bad file descriptor");
            }
            return path;
        }

        private int nextHandleId() {
            var id = ++this.handleIdCounter;
            while (this.openHandlePaths.containsKey(id)) {
                this.handleIdCounter++;
            }
            return id;
        }

        private CompletableFuture<FileSystemDirectoryHandle> resolveOpfsDirectory(
            String path,
            ResolveDirectoryOptions options
        ) {
            return CompletableFuture.supplyAsync(
                () -> {
                    var parts = this.pathParts(path);
                    var ah = options != null && options.from != null
                        ? options.from
                        : this.opfsRootAh;
                    for (var part : parts) {
                        ah = ah.getDirectoryHandle(
                            part,
                            opfs_ahp.handleOptions(options != null && options.create)
                        ).join();
                    }
                    return ah;
                }
            );
        }

        private void applyWalEntry(WALEntry entry) {
            var opp = entry.opp;
            var args = entry.args != null ? entry.args : new Object[0];
            switch (opp) {
                case "createPoolFile":
                    this._createPoolFileState((String) args[0]);
                    break;
                case "deletePoolFile":
                    this._deletePoolFileState((String) args[0]);
                    break;
                case "chmod":
                    this._chmodState((String) args[0], toInt(args[1]));
                    break;
                case "mkdir":
                    this._mkdirState((String) args[0], toMkdirOptions(args[1]));
                    break;
                case "rename":
                    this._renameState((String) args[0], (String) args[1], false);
                    break;
                case "rmdir":
                    this._rmdirState((String) args[0]);
                    break;
                case "unlink":
                    this._unlinkState((String) args[0], false);
                    break;
                case "utimes":
                    this._utimesState(
                        (String) args[0],
                        toLong(args[1]),
                        toLong(args[2])
                    );
                    break;
                case "createFileNode":
                    this._createFileNodeState(
                        (String) args[0],
                        (FileNode) parseNode(args[1])
                    );
                    break;
                case "setLastModified":
                    this._setLastModifiedState((String) args[0], toLong(args[1]));
                    break;
                default:
                    break;
            }
        }

        private State parseState(Object value) {
            if (!(value instanceof Map)) {
                throw new RuntimeException("Invalid state");
            }
            var map = (Map<?, ?>) value;
            var state = new State();
            state.root = (DirectoryNode) parseNode(map.get("root"));
            var poolObj = map.get("pool");
            var pool = new ArrayList<String>();
            if (poolObj instanceof List) {
                for (var item : (List<?>) poolObj) {
                    pool.add(String.valueOf(item));
                }
            }
            state.pool = pool;
            return state;
        }

        private BaseNode parseNode(Object value) {
            if (!(value instanceof Map)) {
                throw new RuntimeException("Invalid node");
            }
            var map = (Map<?, ?>) value;
            var type = String.valueOf(map.get("type"));
            if (NodeType.file.equals(type)) {
                var node = new FileNode();
                node.type = NodeType.file;
                node.lastModified = toLong(map.get("lastModified"));
                node.mode = toInt(map.get("mode"));
                node.backingFilename = String.valueOf(map.get("backingFilename"));
                return node;
            }
            var node = new DirectoryNode();
            node.type = NodeType.directory;
            node.lastModified = toLong(map.get("lastModified"));
            node.mode = toInt(map.get("mode"));
            node.children = new HashMap<>();
            var childrenObj = map.get("children");
            if (childrenObj instanceof Map) {
                var childrenMap = (Map<?, ?>) childrenObj;
                for (var entry : childrenMap.entrySet()) {
                    node.children.put(
                        String.valueOf(entry.getKey()),
                        parseNode(entry.getValue())
                    );
                }
            }
            return node;
        }

        private WALEntry parseWalEntry(Object value) {
            if (!(value instanceof Map)) {
                throw new RuntimeException("Invalid WAL entry");
            }
            var map = (Map<?, ?>) value;
            var entry = new WALEntry();
            entry.opp = String.valueOf(map.get("opp"));
            var argsObj = map.get("args");
            if (argsObj instanceof List) {
                var list = (List<?>) argsObj;
                entry.args = list.toArray(new Object[0]);
            } else {
                entry.args = new Object[0];
            }
            return entry;
        }

        private Map<String, Object> stateToMap(State state) {
            var map = new HashMap<String, Object>();
            map.put("root", nodeToMap(state.root));
            map.put("pool", new ArrayList<>(state.pool));
            return map;
        }

        private Map<String, Object> nodeToMap(BaseNode node) {
            var map = new HashMap<String, Object>();
            map.put("type", node.type());
            map.put("lastModified", node.lastModified());
            map.put("mode", node.mode());
            if (NodeType.file.equals(node.type())) {
                map.put("backingFilename", ((FileNode) node).backingFilename);
            } else {
                var dirNode = (DirectoryNode) node;
                var children = new HashMap<String, Object>();
                for (var entry : dirNode.children.entrySet()) {
                    children.put(entry.getKey(), nodeToMap(entry.getValue()));
                }
                map.put("children", children);
            }
            return map;
        }

        private Map<String, Object> entryToMap(WalRecord entry) {
            var map = new HashMap<String, Object>();
            map.put("opp", entry.opp);
            var args = new ArrayList<Object>();
            for (var arg : entry.args) {
                args.add(serializeArg(arg));
            }
            map.put("args", args);
            return map;
        }

        private Object serializeArg(Object arg) {
            if (arg instanceof FileNode) {
                return nodeToMap((FileNode) arg);
            }
            if (arg instanceof DirectoryNode) {
                return nodeToMap((DirectoryNode) arg);
            }
            if (arg instanceof base.MkdirOptions) {
                var options = (base.MkdirOptions) arg;
                var map = new HashMap<String, Object>();
                map.put("recursive", options.recursive);
                map.put("mode", options.mode);
                return map;
            }
            return arg;
        }

        private base.MkdirOptions toMkdirOptions(Object value) {
            if (!(value instanceof Map)) {
                return null;
            }
            var map = (Map<?, ?>) value;
            var options = new base.MkdirOptions();
            if (map.containsKey("recursive")) {
                var recursive = map.get("recursive");
                options.recursive = recursive instanceof Boolean
                    ? (Boolean) recursive
                    : null;
            }
            if (map.containsKey("mode")) {
                var mode = map.get("mode");
                options.mode = mode instanceof Number ? ((Number) mode).intValue() : null;
            }
            return options;
        }

        private int toInt(Object value) {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return 0;
        }

        private long toLong(Object value) {
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return 0;
        }

        private int getDataLength(Object data) {
            if (data instanceof String) {
                return ((String) data).length();
            }
            if (data instanceof Uint8Array) {
                return ((Uint8Array) data).length;
            }
            return 0;
        }

        private byte[] toBytes(Object data) {
            if (data instanceof String) {
                return ((String) data).getBytes(StandardCharsets.UTF_8);
            }
            if (data instanceof Uint8Array) {
                return ((Uint8Array) data).toByteArray();
            }
            return new byte[0];
        }

        private String padHex(long value, int length) {
            var hex = Long.toHexString(value);
            if (hex.length() >= length) {
                return hex;
            }
            var builder = new StringBuilder();
            for (var i = hex.length(); i < length; i++) {
                builder.append("0");
            }
            builder.append(hex);
            return builder.toString();
        }
    }

    private static final class WalRecord {
        public final String opp;
        public final Object[] args;

        private WalRecord(String opp, Object[] args) {
            this.opp = opp;
            this.args = args;
        }
    }

    private static final class ResolveDirectoryOptions {
        public final FileSystemDirectoryHandle from;
        public final boolean create;

        private ResolveDirectoryOptions(
            FileSystemDirectoryHandle from,
            boolean create
        ) {
            this.from = from;
            this.create = create;
        }
    }

    private static base.BaseFilesystemOptions toBaseOptions(OpfsAhpOptions options) {
        var baseOptions = new base.BaseFilesystemOptions();
        if (options != null && options.debug != null) {
            baseOptions.debug = options.debug;
        }
        return baseOptions;
    }

    private static HandleOptions handleOptions(boolean create) {
        var options = new HandleOptions();
        options.create = create;
        return options;
    }

    private static class FsError extends RuntimeException implements base.ErrnoErrorCarrier {
        private final Integer code;

        public FsError(String code, String message) {
            super(message);
            this.code = base.ERRNO_CODES.get(code);
        }

        public FsError(Integer code, String message) {
            super(message);
            this.code = code;
        }

        @Override
        public Integer code() {
            return this.code;
        }
    }

    private opfs_ahp() {
    }
}
