package io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;

public class idbfs {
    public static class IdbFs extends base.EmscriptenBuiltinFilesystem {
        private final Path idbRoot;
        private final Path mountPath;
        private final Path snapshotPath;

        public IdbFs(String dataDir) {
            super(dataDir);
            this.idbRoot = Path.of("tmp/idbfs").toAbsolutePath().normalize();
            var dirName = dataDir != null && !dataDir.isBlank() ? dataDir : "default";
            this.mountPath = idbRoot.resolve(dirName).resolve("data");
            this.snapshotPath = idbRoot.resolve(dirName).resolve("snapshot.tar.gz");
        }

        @Override
        public Promise<base.InitResult> init(
            io.github.hidekatsu_izuno.pglite_jdbc.pglite.pglite pg,
            Map<String, Object> opts
        ) {
            this.pg = pg;
            try {
                Files.createDirectories(mountPath);
                return Promise.resolve(new base.InitResult(opts));
            } catch (Exception e) {
                return Promise.reject(e);
            }
        }

        @Override
        public Promise<Void> initialSyncFs() {
            try {
                Files.createDirectories(mountPath);
                if (Files.exists(snapshotPath)) {
                    var bytes = Files.readAllBytes(snapshotPath);
                    tarUtils.loadTar(bytes, mountPath, true);
                }
                return Promise.resolve(null);
            } catch (Exception e) {
                return Promise.reject(e);
            }
        }

        @Override
        public Promise<Void> syncToFs(Boolean relaxedDurability) {
            try {
                Files.createDirectories(snapshotPath.getParent());
                var bytes = tarUtils.createTarball(mountPath, tarUtils.DumpTarCompressionOptions.gzip);
                Files.write(snapshotPath, bytes);
                return Promise.resolve(null);
            } catch (Exception e) {
                return Promise.reject(e);
            }
        }

        @Override
        public Promise<Void> closeFs() {
            return syncToFs(false);
        }
    }
}
