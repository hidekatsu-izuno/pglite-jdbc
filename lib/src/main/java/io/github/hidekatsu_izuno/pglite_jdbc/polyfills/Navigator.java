package io.github.hidekatsu_izuno.pglite_jdbc.polyfills;

public class Navigator {
    /*
     * Removed browser navigator.locks usage from:
     * pglite/src/pglite/worker/index.ts
     *
     * await navigator.locks.request(mainLock, async () => { ... });
     */

    private Navigator() {}

    public static LockManager locks() {
        throw new UnsupportedOperationException("Navigator.locks is disabled in JVM-only mode");
    }
}
