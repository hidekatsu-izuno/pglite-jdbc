package io.github.hidekatsu_izuno.pglite_jdbc.polyfills;

import java.util.function.Consumer;

public class BroadcastChannel {
    /*
     * Removed browser BroadcastChannel polyfill from:
     * pglite/src/pglite/worker/index.ts
     *
     * const broadcastChannel = new BroadcastChannel(broadcastChannelId);
     * broadcastChannel.postMessage({ type: "leader-here", id });
     */

    public BroadcastChannel(String name) {
        throw new UnsupportedOperationException("BroadcastChannel is disabled in JVM-only mode");
    }

    public void addEventListener(String type, Consumer<MessageEvent<Object>> listener) {
        throw new UnsupportedOperationException("BroadcastChannel is disabled in JVM-only mode");
    }

    public void removeEventListener(String type, Consumer<MessageEvent<Object>> listener) {
        throw new UnsupportedOperationException("BroadcastChannel is disabled in JVM-only mode");
    }

    public void postMessage(Object data) {
        throw new UnsupportedOperationException("BroadcastChannel is disabled in JVM-only mode");
    }

    public void close() {}

    public Consumer<MessageEvent<Object>> onmessage() {
        return null;
    }

    public void setOnmessage(Consumer<MessageEvent<Object>> listener) {
        throw new UnsupportedOperationException("BroadcastChannel is disabled in JVM-only mode");
    }
}
