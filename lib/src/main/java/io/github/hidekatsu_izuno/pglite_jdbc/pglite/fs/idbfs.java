package io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

public final class idbfs {
    public static class IdbFs extends base.EmscriptenBuiltinFilesystem {
        public IdbFs(String dataDir) {
            super(dataDir);
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
                    var idbfs = mod.FS.filesystems.IDBFS;
                    // Mount the idbfs to the users dataDir then symlink the PGDATA to the
                    // idbfs mount point.
                    // We specifically use /pglite as the root directory for the idbfs
                    // as the fs will ber persisted in the indexeddb as a database with
                    // the path as the name.
                    mod.FS.mkdir("/pglite");
                    mod.FS.mkdir("/pglite/" + this.dataDir);
                    mod.FS.mount(idbfs, new Object(), "/pglite/" + this.dataDir);
                    mod.FS.symlink("/pglite/" + this.dataDir, base.PGDATA);
                }
            );
            options.preRun = preRun;
            var result = new base.InitResult();
            result.emscriptenOpts = options;
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public CompletableFuture<Void> initialSyncFs() {
            if (this.pg == null || this.pg.Module == null || this.pg.Module.FS == null) {
                return CompletableFuture.completedFuture(null);
            }
            var future = new CompletableFuture<Void>();
            this.pg.Module.FS.syncfs(
                true,
                err -> {
                    if (err != null) {
                        future.completeExceptionally(err);
                    } else {
                        future.complete(null);
                    }
                }
            );
            return future;
        }

        @Override
        public CompletableFuture<Void> syncToFs(Boolean _relaxedDurability) {
            if (this.pg == null || this.pg.Module == null || this.pg.Module.FS == null) {
                return CompletableFuture.completedFuture(null);
            }
            var future = new CompletableFuture<Void>();
            this.pg.Module.FS.syncfs(
                false,
                err -> {
                    if (err != null) {
                        future.completeExceptionally(err);
                    } else {
                        future.complete(null);
                    }
                }
            );
            return future;
        }

        @Override
        public CompletableFuture<Void> closeFs() {
            if (this.pg == null || this.pg.Module == null || this.pg.Module.FS == null) {
                return CompletableFuture.completedFuture(null);
            }
            // IDBDatabase.close() method is essentially async, but returns immediately,
            // the database will be closed when all transactions are complete.
            // This needs to be handled in application code if you want to delete the
            // database after it has been closed. If you try to delete the database
            // before it has fully closed it will throw a blocking error.
            var idbfs = this.pg.Module.FS.filesystems.IDBFS;
            if (idbfs != null && idbfs.dbs != null) {
                var indexedDb = idbfs.dbs.get(this.dataDir);
                if (indexedDb != null) {
                    indexedDb.close();
                }
            }
            this.pg.Module.FS.quit();
            return CompletableFuture.completedFuture(null);
        }
    }

    private idbfs() {
    }
}
