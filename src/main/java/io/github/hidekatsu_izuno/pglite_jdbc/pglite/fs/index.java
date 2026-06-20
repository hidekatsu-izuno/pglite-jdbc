package io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.base.Filesystem;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.base.FsType;

public class index {
    public record ParseDataDirResult(String dataDir, FsType fsType) {}

    private index() {}

    public static ParseDataDirResult parseDataDir(String dataDir) {
        if (dataDir != null && dataDir.startsWith("file://")) {
            var trimmed = dataDir.substring(7);
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("Invalid dataDir, must be a valid path");
            }
            return new ParseDataDirResult(trimmed, FsType.nodefs);
        }
        if (dataDir != null && dataDir.startsWith("idb://")) {
            return new ParseDataDirResult(dataDir.substring(6), FsType.idbfs);
        }
        if (dataDir != null && dataDir.startsWith("opfs-ahp://")) {
            return new ParseDataDirResult(dataDir.substring(11), FsType.opfs_ahp);
        }
        if (dataDir == null || dataDir.startsWith("memory://")) {
            return new ParseDataDirResult(dataDir, FsType.memoryfs);
        }
        return new ParseDataDirResult(dataDir, FsType.nodefs);
    }

    public static Filesystem loadFs(String dataDir, FsType fsType) {
        if (dataDir != null && fsType == FsType.nodefs) {
            return new nodefs.NodeFS(dataDir);
        }
        if (dataDir != null && fsType == FsType.idbfs) {
            return new idbfs.IdbFs(dataDir);
        }
        if (dataDir != null && fsType == FsType.opfs_ahp) {
            return new opfs_ahp.OpfsAhpFS(dataDir);
        }
        return new memoryfs.MemoryFS();
    }
}
