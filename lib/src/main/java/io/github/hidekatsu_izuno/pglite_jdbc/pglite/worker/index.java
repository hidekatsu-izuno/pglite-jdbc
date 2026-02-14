package io.github.hidekatsu_izuno.pglite_jdbc.pglite.worker;

import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Event;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.EventTarget;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class index {
    private index() {}

    /*
     * Removed browser worker runtime from:
     * pglite/src/pglite/worker/index.ts
     *
     * export async function worker({ init }: WorkerOptions) {
     *   const broadcastChannel = new BroadcastChannel(broadcastChannelId);
     *   await navigator.locks.request(mainLock, async () => { ... });
     *   broadcastChannel.postMessage({ type: "leader-here", id });
     *   postMessage({ type: "leader-now" });
     * }
     */

    @FunctionalInterface
    public interface WorkerInit {
        Promise<PGliteWorker> init(Map<String, Object> options);
    }

    public record WorkerOptions(WorkerInit init) {}

    public interface WorkerApi {
        Promise<Integer> getDebugLevel();
        Promise<Void> close();
        Promise<interface_.ExecProtocolResult> execProtocol(byte[] message);
        Promise<List<messages.BackendMessage>> execProtocolStream(byte[] message);
        Promise<byte[]> execProtocolRaw(byte[] message);
        Promise<byte[]> dumpDataDir(String compression);
        Promise<Void> syncToFs();
    }

    public static class WorkerRuntime {
        private final String id;
        private final PGliteWorker db;
        private final Set<String> connectedTabs = ConcurrentHashMap.newKeySet();
        private final List<String> warnings = new CopyOnWriteArrayList<>();

        WorkerRuntime(String id, PGliteWorker db) {
            this.id = id;
            this.db = db;
            warnings.add("Browser leader election is disabled; running in single-process worker mode");
        }

        public String id() {
            return id;
        }

        public PGliteWorker db() {
            return db;
        }

        public boolean hasTab(String tabId) {
            return connectedTabs.contains(tabId);
        }

        public Promise<Object> rpc(String tabId, String method, Object... args) {
            connectedTabs.add(tabId);
            var api = makeWorkerApi(tabId, db);
            try {
                return switch (method) {
                    case "getDebugLevel" -> api.getDebugLevel().then(value -> (Object) value);
                    case "close" -> api.close().then(value -> null);
                    case "execProtocol" -> api.execProtocol((byte[]) args[0]).then(value -> (Object) value);
                    case "execProtocolStream" -> api.execProtocolStream((byte[]) args[0]).then(value -> (Object) value);
                    case "execProtocolRaw" -> api.execProtocolRaw((byte[]) args[0]).then(value -> (Object) value);
                    case "dumpDataDir" -> api.dumpDataDir(args.length > 0 ? (String) args[0] : null).then(value -> (Object) value);
                    case "syncToFs" -> api.syncToFs().then(value -> null);
                    default -> {
                        warnings.add("Unknown worker RPC method: " + method);
                        yield Promise.reject(new IllegalArgumentException("Unknown worker RPC method: " + method));
                    }
                };
            } catch (Throwable e) {
                return Promise.reject(e instanceof Exception ex ? ex : new RuntimeException(e));
            }
        }

        public List<String> warnings() {
            return List.copyOf(warnings);
        }
    }

    public static class PGliteWorker implements interface_.PGliteInterface {
        private final io.github.hidekatsu_izuno.pglite_jdbc.pglite.pglite delegate;
        private final EventTarget eventTarget = new EventTarget();
        private final List<String> warnings = new CopyOnWriteArrayList<>();
        private volatile boolean leader = true;

        public PGliteWorker() {
            this.delegate = new io.github.hidekatsu_izuno.pglite_jdbc.pglite.pglite();
            warnings.add("Browser tab coordination is disabled; using single-process worker mode");
        }

        public PGliteWorker(io.github.hidekatsu_izuno.pglite_jdbc.pglite.pglite delegate) {
            this.delegate = delegate;
            warnings.add("Browser tab coordination is disabled; using single-process worker mode");
        }

        @Override
        public Promise<Void> waitReady() {
            return delegate.waitReady();
        }

        @Override
        public int debug() {
            return delegate.debug();
        }

        @Override
        public boolean ready() {
            return delegate.ready();
        }

        @Override
        public boolean closed() {
            return delegate.closed();
        }

        @Override
        public Promise<Void> close() {
            leader = false;
            eventTarget.dispatchEvent(new Event("leader-change"));
            return delegate.close();
        }

        @Override
        public <T> Promise<interface_.Results<T>> query(String query, Object[] params, interface_.QueryOptions options) {
            return delegate.query(query, params, options);
        }

        @Override
        public Promise<List<interface_.Results<Map<String, Object>>>> exec(String query, interface_.QueryOptions options) {
            return delegate.exec(query, options);
        }

        @Override
        public <T> Promise<interface_.Results<T>> sql(List<String> strings, Object... params) {
            return delegate.sql(strings, params);
        }

        @Override
        public Promise<interface_.DescribeQueryResult> describeQuery(String query) {
            return delegate.describeQuery(query);
        }

        @Override
        public <T> Promise<T> transaction(Function<interface_.Transaction, Promise<T>> callback) {
            return delegate.transaction(callback);
        }

        @Override
        public Promise<interface_.ExecProtocolResult> execProtocol(byte[] message, interface_.ExecProtocolOptions options) {
            return delegate.execProtocol(message, options);
        }

        @Override
        public Promise<byte[]> execProtocolRaw(byte[] message, interface_.ExecProtocolOptions options) {
            return delegate.execProtocolRaw(message, options);
        }

        @Override
        public <T> Promise<T> runExclusive(Supplier<Promise<T>> fn) {
            return delegate.runExclusive(fn);
        }

        @Override
        public Promise<Function<interface_.Transaction, Promise<Void>>> listen(
            String channel,
            Consumer<String> callback,
            interface_.Transaction tx
        ) {
            return delegate.listen(channel, callback, tx);
        }

        @Override
        public Promise<Void> unlisten(String channel, Consumer<String> callback, interface_.Transaction tx) {
            return delegate.unlisten(channel, callback, tx);
        }

        @Override
        public Supplier<Void> onNotification(BiConsumer<String, String> callback) {
            return delegate.onNotification(callback);
        }

        @Override
        public void offNotification(BiConsumer<String, String> callback) {
            delegate.offNotification(callback);
        }

        @Override
        public Promise<byte[]> dumpDataDir(String compression) {
            return delegate.dumpDataDir(compression);
        }

        public Promise<List<messages.BackendMessage>> execProtocolStream(byte[] message, interface_.ExecProtocolOptions options) {
            return delegate.execProtocol(message, options).then(result -> result.messages());
        }

        public Promise<Void> syncToFs() {
            return delegate.syncToFs();
        }

        public boolean isLeader() {
            return leader;
        }

        public Supplier<Void> onLeaderChange(Runnable callback) {
            Consumer<Event> listener = ignored -> callback.run();
            eventTarget.addEventListener("leader-change", listener);
            return () -> {
                eventTarget.removeEventListener("leader-change", listener);
                return null;
            };
        }

        public void offLeaderChange(Runnable callback) {
            warnings.add("offLeaderChange is a no-op in single-process worker mode");
        }

        public List<String> warnings() {
            return List.copyOf(warnings);
        }
    }

    public static WorkerApi makeWorkerApi(String tabId, PGliteWorker db) {
        return new WorkerApi() {
            @Override
            public Promise<Integer> getDebugLevel() {
                return Promise.resolve(db.debug());
            }

            @Override
            public Promise<Void> close() {
                return db.close();
            }

            @Override
            public Promise<interface_.ExecProtocolResult> execProtocol(byte[] message) {
                return db.execProtocol(message, null);
            }

            @Override
            public Promise<List<messages.BackendMessage>> execProtocolStream(byte[] message) {
                return db.execProtocolStream(message, null);
            }

            @Override
            public Promise<byte[]> execProtocolRaw(byte[] message) {
                return db.execProtocolRaw(message, null);
            }

            @Override
            public Promise<byte[]> dumpDataDir(String compression) {
                return db.dumpDataDir(compression);
            }

            @Override
            public Promise<Void> syncToFs() {
                return db.syncToFs();
            }
        };
    }

    public static Promise<WorkerRuntime> worker(WorkerOptions options, Map<String, Object> initOptions) {
        if (options == null || options.init() == null) {
            return Promise.reject(new IllegalArgumentException("worker init callback is required"));
        }
        var effectiveInitOptions = initOptions != null ? initOptions : Map.<String, Object>of();
        var dataDir = String.valueOf(effectiveInitOptions.getOrDefault("dataDir", ""));
        var id = String.valueOf(effectiveInitOptions.getOrDefault("id", "jvm:" + dataDir));
        return options.init().init(effectiveInitOptions).then(db -> new WorkerRuntime(id, db));
    }
}
