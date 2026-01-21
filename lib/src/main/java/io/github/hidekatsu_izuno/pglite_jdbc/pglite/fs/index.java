package io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs;

import java.util.concurrent.CompletableFuture;

public final class index {
    public static final class ParseDataDirResult {
        public String dataDir;
        public String fsType;
    }

    public static ParseDataDirResult parseDataDir(String dataDir) {
        String fsType;
        if (dataDir != null && dataDir.startsWith("file://")) {
            // Remove the file:// prefix, and use node filesystem
            dataDir = dataDir.substring(7);
            if (dataDir.isEmpty()) {
                throw new RuntimeException("Invalid dataDir, must be a valid path");
            }
            fsType = base.FsType.nodefs;
        } else if (dataDir != null && dataDir.startsWith("idb://")) {
            // Remove the idb:// prefix, and use indexeddb filesystem
            dataDir = dataDir.substring(6);
            fsType = base.FsType.idbfs;
        } else if (dataDir != null && dataDir.startsWith("opfs-ahp://")) {
            // Remove the opfsahp:// prefix, and use opfs access handle pool filesystem
            dataDir = dataDir.substring(11);
            fsType = base.FsType.opfs_ahp;
        } else if (dataDir == null || dataDir.startsWith("memory://")) {
            // Use in-memory filesystem
            fsType = base.FsType.memoryfs;
        } else {
            // No prefix, use node filesystem
            fsType = base.FsType.nodefs;
        }
        var result = new ParseDataDirResult();
        result.dataDir = dataDir;
        result.fsType = fsType;
        return result;
    }

    public static CompletableFuture<base.Filesystem> loadFs(
        String dataDir,
        String fsType
    ) {
        base.Filesystem fs;
        if (dataDir != null && base.FsType.nodefs.equals(fsType)) {
            fs = new nodefs.NodeFS(dataDir);
        } else if (dataDir != null && base.FsType.idbfs.equals(fsType)) {
            fs = new idbfs.IdbFs(dataDir);
        } else if (dataDir != null && base.FsType.opfs_ahp.equals(fsType)) {
            fs = new opfs_ahp.OpfsAhpFS(dataDir, null);
        } else {
            fs = new memoryfs.MemoryFS();
        }
        return CompletableFuture.completedFuture(fs);
    }

    private index() {
    }
}
