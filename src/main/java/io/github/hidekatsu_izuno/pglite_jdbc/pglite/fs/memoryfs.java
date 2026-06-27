package io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class memoryfs {
    public static class MemoryFS implements base.Filesystem {
        private final MemoryNode root;
        private int nextFd = 100;
        private int nextIno = 1;
        private final Map<Integer, OpenFile> fds = new HashMap<>();

        public MemoryFS() {
            root = createNode(NodeType.DIR, 040755);
            ensureDir(base.PGLITE_DATA_DIR, true, 0755);
        }

        @Override
        public Promise<base.WasiFilesystemMount> initWasi() {
            return Promise.resolve(new base.WasiFilesystemMount(
                base.PGLITE_DATA_DIR,
                Map.of(base.PGLITE_DATA_DIR, new base.MemoryDescriptor("pglite-data")),
                Map.of()
            ));
        }

        @Override
        public Promise<Boolean> exists(String path) {
            return Promise.resolve(lookup(path) != null);
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
            fds.clear();
            return Promise.resolve(null);
        }

        @Override
        public void chmod(String path, int mode) {
            var node = expect(path);
            node.mode = kindBits(node) | (mode & 0777);
            node.ctime = System.currentTimeMillis();
        }

        @Override
        public void close(int fd) {
            if (fds.remove(fd) == null) {
                throw error("bad fd", "EBADF");
            }
        }

        @Override
        public base.FsStats fstat(int fd) {
            return toStats(expectFd(fd).node);
        }

        @Override
        public base.FsStats lstat(String path) {
            return toStats(expect(path));
        }

        @Override
        public void mkdir(String path, boolean recursive, Integer mode) {
            ensureDir(path, recursive, mode != null ? mode : 0755);
        }

        @Override
        public int open(String path, String flags, Integer mode) {
            var actualFlags = flags != null ? flags : "r";
            var node = lookup(path);
            if (node == null && (actualFlags.contains("w") || actualFlags.contains("a"))) {
                var parent = parent(path);
                node = createNode(NodeType.FILE, 0100000 | (mode != null ? mode : 0666));
                node.data = new byte[0];
                parent.parent.entries.put(parent.name, node);
            }
            if (node == null) {
                throw error("not found", "ENOENT");
            }
            if (node.type != NodeType.FILE) {
                throw error("is dir", "EISDIR");
            }
            if (actualFlags.contains("w")) {
                node.data = new byte[0];
                node.mtime = node.ctime = System.currentTimeMillis();
            }
            var fd = nextFd++;
            fds.put(fd, new OpenFile(node, actualFlags.contains("a") ? node.data.length : 0));
            return fd;
        }

        @Override
        public String[] readdir(String path) {
            var node = expect(path);
            if (node.type != NodeType.DIR) {
                throw error("not dir", "ENOTDIR");
            }
            var out = new String[node.entries.size() + 2];
            out[0] = ".";
            out[1] = "..";
            var i = 2;
            for (var name : node.entries.keySet()) {
                out[i++] = name;
            }
            return out;
        }

        @Override
        public int read(int fd, byte[] buffer, int offset, int length, int position) {
            var file = expectFd(fd);
            var start = position >= 0 ? position : file.position;
            var amount = Math.max(0, Math.min(length, file.node.data.length - start));
            System.arraycopy(file.node.data, start, buffer, offset, amount);
            file.position = start + amount;
            file.node.atime = System.currentTimeMillis();
            return amount;
        }

        @Override
        public void rename(String oldPath, String newPath) {
            var oldParent = parent(oldPath);
            var node = oldParent.parent.entries.get(oldParent.name);
            if (node == null) {
                throw error("not found", "ENOENT");
            }
            var newParent = parent(newPath);
            oldParent.parent.entries.remove(oldParent.name);
            newParent.parent.entries.put(newParent.name, node);
            node.ctime = System.currentTimeMillis();
        }

        @Override
        public void rmdir(String path) {
            var parent = parent(path);
            var node = parent.parent.entries.get(parent.name);
            if (node == null) {
                throw error("not found", "ENOENT");
            }
            if (node.type != NodeType.DIR) {
                throw error("not dir", "ENOTDIR");
            }
            if (!node.entries.isEmpty()) {
                throw error("not empty", "ENOTEMPTY");
            }
            parent.parent.entries.remove(parent.name);
        }

        @Override
        public void truncate(String path, int len) {
            var node = expect(path);
            if (node.type != NodeType.FILE) {
                throw error("is dir", "EISDIR");
            }
            node.data = Arrays.copyOf(node.data, len);
            node.mtime = node.ctime = System.currentTimeMillis();
        }

        @Override
        public void unlink(String path) {
            var parent = parent(path);
            var node = parent.parent.entries.get(parent.name);
            if (node == null) {
                throw error("not found", "ENOENT");
            }
            if (node.type == NodeType.DIR) {
                throw error("is dir", "EISDIR");
            }
            parent.parent.entries.remove(parent.name);
        }

        @Override
        public void utimes(String path, long atime, long mtime) {
            var node = expect(path);
            node.atime = atime;
            node.mtime = mtime;
            node.ctime = System.currentTimeMillis();
        }

        @Override
        public void writeFile(String path, byte[] data, String encoding, Integer mode, String flag) {
            var bytes = data != null ? data : new byte[0];
            if (encoding != null) {
                bytes = new String(bytes, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
            }
            var fd = open(path, flag != null ? flag : "w", mode);
            try {
                write(fd, bytes, 0, bytes.length, 0);
            } finally {
                close(fd);
            }
        }

        @Override
        public int write(int fd, byte[] buffer, int offset, int length, int position) {
            var file = expectFd(fd);
            var start = position < 0 ? file.position : position;
            var required = start + length;
            if (required > file.node.data.length) {
                file.node.data = Arrays.copyOf(file.node.data, required);
            }
            System.arraycopy(buffer, offset, file.node.data, start, length);
            file.position = start + length;
            file.node.mtime = file.node.ctime = System.currentTimeMillis();
            return length;
        }

        private MemoryNode createNode(NodeType type, int mode) {
            var now = System.currentTimeMillis();
            var node = new MemoryNode();
            node.type = type;
            node.mode = mode;
            node.data = type == NodeType.FILE ? new byte[0] : null;
            node.entries = type == NodeType.DIR ? new LinkedHashMap<>() : null;
            node.atime = now;
            node.mtime = now;
            node.ctime = now;
            node.ino = nextIno++;
            return node;
        }

        private MemoryNode ensureDir(String path, boolean recursive, int mode) {
            var parts = parts(path);
            var current = root;
            for (var i = 0; i < parts.length; i++) {
                var part = parts[i];
                var next = current.entries.get(part);
                if (next == null) {
                    if (!recursive && i != parts.length - 1) {
                        throw error("not found", "ENOENT");
                    }
                    next = createNode(NodeType.DIR, 040000 | mode);
                    current.entries.put(part, next);
                }
                if (next.type != NodeType.DIR) {
                    throw error("not dir", "ENOTDIR");
                }
                current = next;
            }
            return current;
        }

        private MemoryNode expect(String path) {
            var node = lookup(path);
            if (node == null) {
                throw error("not found", "ENOENT");
            }
            return node;
        }

        private OpenFile expectFd(int fd) {
            var file = fds.get(fd);
            if (file == null) {
                throw error("bad fd", "EBADF");
            }
            return file;
        }

        private MemoryNode lookup(String path) {
            var current = root;
            for (var part : parts(path)) {
                if (current == null || current.type != NodeType.DIR) {
                    return null;
                }
                current = current.entries.get(part);
            }
            return current;
        }

        private Parent parent(String path) {
            var parts = parts(path);
            if (parts.length == 0) {
                throw error("invalid path", "EINVAL");
            }
            var name = parts[parts.length - 1];
            var parentPath = "/" + String.join("/", Arrays.copyOf(parts, parts.length - 1));
            return new Parent(ensureDir(parentPath, true, 0755), name);
        }

        private String[] parts(String path) {
            return Arrays.stream(path.split("/")).filter(part -> !part.isEmpty()).toArray(String[]::new);
        }

        private int kindBits(MemoryNode node) {
            return node.type == NodeType.DIR ? 040000 : 0100000;
        }

        private base.FsStats toStats(MemoryNode node) {
            var size = node.type == NodeType.FILE ? node.data.length : 0;
            return new base.FsStats(0, node.ino, node.mode, 1, 0, 0, 0, size, 4096, Math.ceilDiv(size, 512), node.atime, node.mtime, node.ctime);
        }

        private RuntimeException error(String message, String code) {
            return new RuntimeException(code + ": " + message);
        }

        private enum NodeType {
            FILE,
            DIR,
        }

        private static final class MemoryNode {
            NodeType type;
            int mode;
            byte[] data;
            Map<String, MemoryNode> entries;
            long atime;
            long mtime;
            long ctime;
            int ino;
        }

        private static final class OpenFile {
            final MemoryNode node;
            int position;

            OpenFile(MemoryNode node, int position) {
                this.node = node;
                this.position = position;
            }
        }

        private record Parent(MemoryNode parent, String name) {}
    }
}
