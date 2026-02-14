package io.github.hidekatsu_izuno.pglite_jdbc.polyfills;

import java.util.function.Consumer;

public class EventTarget {
    /*
     * Removed browser EventTarget polyfill from:
     * pglite/src/pglite/worker/index.ts
     *
     * this.#eventTarget.addEventListener("leader-change", callback);
     * this.#eventTarget.dispatchEvent(new Event("connected"));
     */

    public void addEventListener(String type, Consumer<Event> listener) {
        throw new UnsupportedOperationException("EventTarget is disabled in JVM-only mode");
    }

    public void removeEventListener(String type, Consumer<Event> listener) {
        throw new UnsupportedOperationException("EventTarget is disabled in JVM-only mode");
    }

    public void dispatchEvent(Event event) {
        throw new UnsupportedOperationException("EventTarget is disabled in JVM-only mode");
    }
}
