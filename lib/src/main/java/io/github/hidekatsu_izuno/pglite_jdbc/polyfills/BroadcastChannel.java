package io.github.hidekatsu_izuno.pglite_jdbc.polyfills;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

public class BroadcastChannel {
    /*
     * Removed browser BroadcastChannel polyfill from:
     * pglite/src/pglite/worker/index.ts
     *
     * const broadcastChannel = new BroadcastChannel(broadcastChannelId);
     * broadcastChannel.postMessage({ type: "leader-here", id });
     */

    private static final ConcurrentHashMap<String, CopyOnWriteArraySet<BroadcastChannel>> CHANNELS =
        new ConcurrentHashMap<>();

    private final String name;
    private final CopyOnWriteArraySet<Consumer<MessageEvent<Object>>> listeners = new CopyOnWriteArraySet<>();
    private volatile Consumer<MessageEvent<Object>> onmessage;
    private volatile boolean closed;

    public BroadcastChannel(String name) {
        this.name = name;
        CHANNELS.computeIfAbsent(name, ignored -> new CopyOnWriteArraySet<>()).add(this);
    }

    public void addEventListener(String type, Consumer<MessageEvent<Object>> listener) {
        if (!"message".equals(type) || listener == null || closed) {
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
        if (closed) {
            return;
        }
        var peers = CHANNELS.get(name);
        if (peers == null) {
            return;
        }
        for (var peer : peers) {
            peer.dispatchMessage(data);
        }
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        var peers = CHANNELS.get(name);
        if (peers == null) {
            return;
        }
        peers.remove(this);
        if (peers.isEmpty()) {
            CHANNELS.remove(name, peers);
        }
    }

    public Consumer<MessageEvent<Object>> onmessage() {
        return onmessage;
    }

    public void setOnmessage(Consumer<MessageEvent<Object>> listener) {
        this.onmessage = listener;
    }

    private void dispatchMessage(Object data) {
        if (closed) {
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
