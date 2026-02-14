package io.github.hidekatsu_izuno.pglite_jdbc.polyfills;

import java.util.function.Consumer;

public class Worker {
    public record WorkerPair(Worker mainSide, Worker workerSide) {}

    /*
     * Removed browser Worker polyfill from:
     * pglite/src/pglite/worker/index.ts
     *
     * this.#workerProcess.postMessage({ type: "init", options: workerOptions });
     * this.#workerProcess.addEventListener("message", callback);
     */

    public static WorkerPair createLinkedPair() {
        throw new UnsupportedOperationException("Worker is disabled in JVM-only mode");
    }

    public void addEventListener(String type, Consumer<MessageEvent<Object>> listener) {
        throw new UnsupportedOperationException("Worker is disabled in JVM-only mode");
    }

    public void removeEventListener(String type, Consumer<MessageEvent<Object>> listener) {
        throw new UnsupportedOperationException("Worker is disabled in JVM-only mode");
    }

    public void postMessage(Object data) {
        throw new UnsupportedOperationException("Worker is disabled in JVM-only mode");
    }

    public void terminate() {}

    public boolean terminated() {
        return true;
    }

    public Consumer<MessageEvent<Object>> onmessage() {
        return null;
    }

    public void setOnmessage(Consumer<MessageEvent<Object>> listener) {
        throw new UnsupportedOperationException("Worker is disabled in JVM-only mode");
    }
}
