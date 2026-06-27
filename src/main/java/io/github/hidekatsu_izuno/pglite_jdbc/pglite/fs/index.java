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
        if (dataDir == null || dataDir.startsWith("memory://")) {
            return new ParseDataDirResult(dataDir, FsType.memoryfs);
        }
        return new ParseDataDirResult(dataDir, FsType.nodefs);
    }

    public static Filesystem loadFs(String dataDir, FsType fsType) {
        if (dataDir != null && fsType == FsType.nodefs) {
            return new nodefs.NodeFS(dataDir);
        }
        return new memoryfs.MemoryFS();
    }
}
