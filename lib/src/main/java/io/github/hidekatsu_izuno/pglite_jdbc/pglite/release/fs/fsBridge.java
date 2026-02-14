package io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.fs;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.core.runtimeTypes;
import java.util.List;

public class fsBridge {
    private fsBridge() {}

    public static void applyFsBootstrapPaths(
        runtimeTypes.RuntimeModule module,
        List<runtimeTypes.FsPathEntry> paths
    ) {
        if (module == null) {
            throw new IllegalArgumentException("module is null");
        }
        for (var entry : paths) {
            module.FS_createPath(entry.parent(), entry.name(), entry.canRead(), entry.canWrite());
        }
    }
}
