package io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs;

import java.util.concurrent.CompletableFuture;

public final class memoryfs {
    public static class MemoryFS extends base.EmscriptenBuiltinFilesystem {
        public MemoryFS() {
            super(null);
        }

        @Override
        public CompletableFuture<Void> closeFs() {
            this.pg.Module.FS.quit();
            return CompletableFuture.completedFuture(null);
        }
    }

    private memoryfs() {
    }
}
