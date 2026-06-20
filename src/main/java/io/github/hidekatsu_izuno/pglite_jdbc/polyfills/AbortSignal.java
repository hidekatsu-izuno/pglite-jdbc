package io.github.hidekatsu_izuno.pglite_jdbc.polyfills;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class AbortSignal {
    private final Set<Consumer<Event>> listeners = ConcurrentHashMap.newKeySet();
    private volatile boolean aborted;

    public boolean aborted() {
        return aborted;
    }

    public void addEventListener(String type, Consumer<Event> listener) {
        if (!"abort".equals(type) || listener == null) {
            return;
        }
        listeners.add(listener);
    }

    public void removeEventListener(String type, Consumer<Event> listener) {
        if (!"abort".equals(type) || listener == null) {
            return;
        }
        listeners.remove(listener);
    }

    void abort() {
        if (aborted) {
            return;
        }
        aborted = true;
        var event = new Event("abort");
        for (var listener : List.copyOf(listeners)) {
            listener.accept(event);
        }
    }
}
