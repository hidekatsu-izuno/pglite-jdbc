package io.github.hidekatsu_izuno.pglite_jdbc.polyfills;

public class Navigator {
    /*
     * Removed browser navigator.locks usage from:
     * pglite/src/pglite/worker/index.ts
     *
     * await navigator.locks.request(mainLock, async () => { ... });
     */

    private static final LockManager LOCK_MANAGER = new LockManager();

    private Navigator() {}

    public static LockManager locks() {
        return LOCK_MANAGER;
    }
}
