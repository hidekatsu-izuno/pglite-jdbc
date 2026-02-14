package io.github.hidekatsu_izuno.pglite_jdbc.polyfills;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

public class LockManager {
    private final ConcurrentHashMap<String, Semaphore> locks = new ConcurrentHashMap<>();

    public Promise<Runnable> acquire(String name) {
        return new Promise<>((resolve, reject) -> {
            try {
                var semaphore = locks.computeIfAbsent(name, ignored -> new Semaphore(1));
                semaphore.acquire();
                var released = new AtomicBoolean(false);
                resolve.run(() -> {
                    if (released.compareAndSet(false, true)) {
                        semaphore.release();
                    }
                });
            } catch (Throwable e) {
                reject.run(e);
            }
        });
    }
}
