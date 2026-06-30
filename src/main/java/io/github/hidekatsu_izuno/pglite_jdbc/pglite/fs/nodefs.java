package io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.pglite;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class nodefs {
    public static class NodeFS extends base.EmscriptenBuiltinFilesystem {
        protected final String rootDir;

        public NodeFS(String dataDir) {
            super(dataDir);
            this.rootDir = Path.of(dataDir).toAbsolutePath().normalize().toString();
            try {
                Files.createDirectories(Path.of(this.rootDir));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Promise<base.InitResult> init(pglite pg, Map<String, Object> opts) {
            this.pg = pg;
            var options = base.copyEmscriptenOptions(opts);
            options.put("__wasiDataRoot", this.rootDir);
            var preRun = base.getPreRunHooks(options);
            preRun.add(mod -> {
                var nodefsType = mod.FS().NODEFS();
                mod.FS().mkdir(base.PGDATA);
                mod.FS().mount(nodefsType, Map.of("root", this.rootDir), base.PGDATA);
            });
            options.put("preRun", preRun);
            return Promise.resolve(new base.InitResult(options));
        }

        @Override
        public Promise<Void> closeFs() {
            if (pg != null && pg.Module() != null) {
                pg.Module().FS().quit();
            }
            return Promise.resolve(null);
        }
    }
}
