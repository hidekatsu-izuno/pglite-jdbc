package io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;

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
        public Promise<base.InitResult> init(
            io.github.hidekatsu_izuno.pglite_jdbc.pglite.pglite pg,
            Map<String, Object> opts
        ) {
            this.pg = pg;
            try {
                Files.createDirectories(Path.of(rootDir));
            } catch (Exception e) {
                return Promise.reject(e);
            }
            return Promise.resolve(new base.InitResult(opts));
        }

        @Override
        public Promise<Void> initialSyncFs() {
            try {
                Files.createDirectories(Path.of(rootDir));
                return Promise.resolve(null);
            } catch (Exception e) {
                return Promise.reject(e);
            }
        }

        @Override
        public Promise<Void> syncToFs(Boolean relaxedDurability) {
            return Promise.resolve(null);
        }

        @Override
        public Promise<Void> closeFs() {
            return Promise.resolve(null);
        }
    }
}
