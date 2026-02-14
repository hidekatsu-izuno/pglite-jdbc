package io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;

public class memoryfs {
    public static class MemoryFS extends base.EmscriptenBuiltinFilesystem {
        public MemoryFS() {
            super("memory://");
        }

        @Override
        public Promise<Void> closeFs() {
            return Promise.resolve(null);
        }
    }
}
