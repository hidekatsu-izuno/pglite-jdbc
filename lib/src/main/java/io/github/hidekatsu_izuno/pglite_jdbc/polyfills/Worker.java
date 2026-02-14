package io.github.hidekatsu_izuno.pglite_jdbc.polyfills;

import java.util.concurrent.CopyOnWriteArraySet;
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

    private final CopyOnWriteArraySet<Consumer<MessageEvent<Object>>> listeners = new CopyOnWriteArraySet<>();
    private volatile Consumer<MessageEvent<Object>> onmessage;
    private volatile Worker peer;
    private volatile boolean terminated;

    public static WorkerPair createLinkedPair() {
        var mainSide = new Worker();
        var workerSide = new Worker();
        mainSide.peer = workerSide;
        workerSide.peer = mainSide;
        return new WorkerPair(mainSide, workerSide);
    }

    public void addEventListener(String type, Consumer<MessageEvent<Object>> listener) {
        if (!"message".equals(type) || listener == null || terminated) {
            return;
        }
        listeners.add(listener);
    }

    public void removeEventListener(String type, Consumer<MessageEvent<Object>> listener) {
        if (!"message".equals(type) || listener == null) {
            return;
        }
        listeners.remove(listener);
    }

    public void postMessage(Object data) {
        if (terminated || peer == null) {
            return;
        }
        peer.dispatchMessage(data);
    }

    public void terminate() {
        terminated = true;
        listeners.clear();
        onmessage = null;
    }

    public boolean terminated() {
        return terminated;
    }

    public Consumer<MessageEvent<Object>> onmessage() {
        return onmessage;
    }

    public void setOnmessage(Consumer<MessageEvent<Object>> listener) {
        this.onmessage = listener;
    }

    private void dispatchMessage(Object data) {
        if (terminated) {
            return;
        }
        var event = new MessageEvent<>("message", data, this);
        var handler = onmessage;
        if (handler != null) {
            handler.accept(event);
        }
        for (var listener : listeners) {
            listener.accept(event);
        }
    }
}
