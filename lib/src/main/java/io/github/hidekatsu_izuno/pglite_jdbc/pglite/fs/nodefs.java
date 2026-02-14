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
            if (pg == null) {
                return Promise.resolve(null);
            }
            try {
                var modField = io.github.hidekatsu_izuno.pglite_jdbc.pglite.pglite.class.getDeclaredField("mod");
                modField.setAccessible(true);
                var mod = modField.get(pg);
                if (mod == null) {
                    return Promise.resolve(null);
                }
                var fsMethod = mod.getClass().getMethod("FS");
                var fs = fsMethod.invoke(mod);
                if (fs == null) {
                    return Promise.resolve(null);
                }
                try {
                    var quitMethod = fs.getClass().getMethod("quit");
                    quitMethod.invoke(fs);
                } catch (NoSuchMethodException ignored) {
                    // Runtime FS may not expose quit in JVM mode.
                }
                return Promise.resolve(null);
            } catch (Throwable e) {
                return Promise.reject(e instanceof Exception ex ? ex : new RuntimeException(e));
            }
        }
    }
}
