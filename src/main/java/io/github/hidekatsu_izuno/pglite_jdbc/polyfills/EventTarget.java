package io.github.hidekatsu_izuno.pglite_jdbc.polyfills;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class EventTarget {
    /*
     * Removed browser EventTarget polyfill from:
     * pglite/src/pglite/worker/index.ts
     *
     * this.#eventTarget.addEventListener("leader-change", callback);
     * this.#eventTarget.dispatchEvent(new Event("connected"));
     */

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Consumer<Event>>> listeners =
        new ConcurrentHashMap<>();

    public void addEventListener(String type, Consumer<Event> listener) {
        if (type == null || listener == null) {
            return;
        }
        listeners.computeIfAbsent(type, ignored -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void removeEventListener(String type, Consumer<Event> listener) {
        if (type == null || listener == null) {
            return;
        }
        var handlers = listeners.get(type);
        if (handlers == null) {
            return;
        }
        handlers.remove(listener);
        if (handlers.isEmpty()) {
            listeners.remove(type, handlers);
        }
    }

    public void dispatchEvent(Event event) {
        if (event == null) {
            return;
        }
        var handlers = listeners.get(event.type());
        if (handlers == null) {
            return;
        }
        for (var handler : handlers) {
            handler.accept(event);
        }
    }
}
