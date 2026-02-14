package io.github.hidekatsu_izuno.pglite_jdbc.pglite.worker;

import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;
import java.util.List;
import java.util.Map;
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

        WorkerRuntime(String id, PGliteWorker db) {
            this.id = id;
            this.db = db;
        }

        public String id() {
            return id;
        }

        public PGliteWorker db() {
            return db;
        }

        public boolean hasTab(String tabId) {
            return false;
        }

        public Promise<Object> rpc(String tabId, String method, Object... args) {
            return Promise.reject(new UnsupportedOperationException("Worker RPC is disabled in JVM-only mode"));
        }

        public List<String> warnings() {
            return List.of("Worker runtime is disabled in JVM-only mode");
        }
    }

    public static class PGliteWorker implements interface_.PGliteInterface {
        private final io.github.hidekatsu_izuno.pglite_jdbc.pglite.pglite delegate;

        public PGliteWorker() {
            this.delegate = new io.github.hidekatsu_izuno.pglite_jdbc.pglite.pglite();
        }

        public PGliteWorker(io.github.hidekatsu_izuno.pglite_jdbc.pglite.pglite delegate) {
            this.delegate = delegate;
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
            return Promise.reject(new UnsupportedOperationException("Worker stream API is disabled in JVM-only mode"));
        }

        public boolean isLeader() {
            return false;
        }

        public Supplier<Void> onLeaderChange(Runnable callback) {
            return () -> null;
        }

        public void offLeaderChange(Runnable callback) {}

        public List<String> warnings() {
            return List.of("Worker APIs are disabled in JVM-only mode");
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
                return Promise.reject(new UnsupportedOperationException("Worker stream API is disabled in JVM-only mode"));
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
                return Promise.reject(new UnsupportedOperationException("Worker sync API is disabled in JVM-only mode"));
            }
        };
    }

    public static Promise<WorkerRuntime> worker(WorkerOptions options, Map<String, Object> initOptions) {
        return Promise.reject(new UnsupportedOperationException("Browser Worker runtime is disabled in JVM-only mode"));
    }
}
