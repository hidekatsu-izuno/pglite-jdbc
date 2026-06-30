package io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;

public class memoryfs {
    public static class MemoryFS extends base.EmscriptenBuiltinFilesystem {
        public MemoryFS() {
            super(null);
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
