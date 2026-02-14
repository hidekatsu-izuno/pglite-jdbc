package io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.preload;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.core.runtimeTypes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class fsBootstrapPaths_generated {
    private fsBootstrapPaths_generated() {}

    public static final List<runtimeTypes.FsPathEntry> fsBootstrapPaths = generate();

    private static List<runtimeTypes.FsPathEntry> generate() {
        var paths = new LinkedHashSet<String>();
        paths.add("/home");
        paths.add("/home/web_user");
        paths.add("/tmp");
        paths.add("/tmp/pglite");

        for (var file : dataManifest_generated.dataManifest.files()) {
            var current = normalize(file.filename());
            while (current.lastIndexOf('/') > 0) {
                current = current.substring(0, current.lastIndexOf('/'));
                if (!current.isEmpty()) {
                    paths.add(current);
                }
            }
        }

        var out = new ArrayList<runtimeTypes.FsPathEntry>();
        for (var path : paths) {
            var idx = path.lastIndexOf('/');
            var parent = idx <= 0 ? "/" : path.substring(0, idx);
            var name = path.substring(idx + 1);
            if (!name.isEmpty()) {
                out.add(new runtimeTypes.FsPathEntry(parent, name, true, true));
            }
        }
        return out;
    }

    private static String normalize(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        var normalized = path.replace('\\', '/');
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
