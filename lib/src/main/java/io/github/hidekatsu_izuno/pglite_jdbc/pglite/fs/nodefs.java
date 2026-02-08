package io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

public final class nodefs {
    public static class NodeFS extends base.EmscriptenBuiltinFilesystem {
        protected String rootDir;

        public NodeFS(String dataDir) {
            super(dataDir);
            var path = Path.of(dataDir).toAbsolutePath().normalize();
            this.rootDir = path.toString();
            if (!Files.exists(path)) {
                try {
                    Files.createDirectories(path);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        public CompletableFuture<base.InitResult> init(
            base.PGlite pg,
            base.PostgresMod opts
        ) {
            this.pg = pg;
            var options = opts != null ? opts : new base.PostgresMod();
            var preRun = new ArrayList<java.util.function.Consumer<base.PostgresMod>>();
            if (options.preRun != null) {
                preRun.addAll(options.preRun);
            }
            preRun.add(
                mod -> {
                    var nodefs = mod.FS.filesystems.NODEFS;
                    mod.FS.mkdir(base.PGDATA);
                    var mountOpts = new HashMap<String, Object>();
                    mountOpts.put("root", this.rootDir);
                    mod.FS.mount(nodefs, mountOpts, base.PGDATA);
                }
            );
            options.preRun = preRun;
            var result = new base.InitResult();
            result.emscriptenOpts = options;
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public CompletableFuture<Void> closeFs() {
            if (this.pg != null && this.pg.Module != null && this.pg.Module.FS != null) {
                this.pg.Module.FS.quit();
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    private nodefs() {
    }
}
