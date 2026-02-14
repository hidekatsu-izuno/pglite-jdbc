package io.github.hidekatsu_izuno.pglite_jdbc.polyfills;

public class LockManager {
    public Promise<Runnable> acquire(String name) {
        return Promise.reject(new UnsupportedOperationException("LockManager is disabled in JVM-only mode"));
    }
}
