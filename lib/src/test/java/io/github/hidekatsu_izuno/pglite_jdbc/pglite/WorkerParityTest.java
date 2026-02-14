package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.worker.index;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

public class WorkerParityTest {
    @Test
    void shouldCreateSingleProcessWorkerRuntimeAndServeRpc() {
        var runtime = index
            .worker(
                new index.WorkerOptions(options -> Promise.resolve(new index.PGliteWorker(new StubPGlite()))),
                Map.of("id", "runtime-1")
            )
            .join();

        var debug = runtime.rpc("tab-a", "getDebugLevel").join();

        assertEquals("runtime-1", runtime.id());
        assertEquals(7, debug);
        assertTrue(runtime.hasTab("tab-a"));
    }

    @Test
    void shouldRejectUnknownWorkerRpcMethod() {
        var runtime = index
            .worker(
                new index.WorkerOptions(options -> Promise.resolve(new index.PGliteWorker(new StubPGlite()))),
                Map.of()
            )
            .join();

        assertThrows(RuntimeException.class, () -> runtime.rpc("tab-a", "unknownMethod").join());
        assertTrue(runtime.warnings().stream().anyMatch(message -> message.contains("Unknown worker RPC method")));
    }

    private static class StubPGlite extends pglite {
        @Override
        public Promise<Void> waitReady() {
            return Promise.resolve(null);
        }

        @Override
        public int debug() {
            return 7;
        }

        @Override
        public boolean ready() {
            return true;
        }

        @Override
        public boolean closed() {
            return false;
        }

        @Override
        public Promise<Void> close() {
            return Promise.resolve(null);
        }

        @Override
        public <T> Promise<interface_.Results<T>> query(String query, Object[] params, interface_.QueryOptions options) {
            return Promise.resolve(new interface_.Results<>(List.of(), 0, List.of(), null));
        }

        @Override
        public Promise<List<interface_.Results<Map<String, Object>>>> exec(String query, interface_.QueryOptions options) {
            return Promise.resolve(List.of());
        }

        @Override
        public <T> Promise<interface_.Results<T>> sql(List<String> strings, Object... params) {
            return Promise.resolve(new interface_.Results<>(List.of(), 0, List.of(), null));
        }

        @Override
        public Promise<interface_.DescribeQueryResult> describeQuery(String query) {
            return Promise.resolve(new interface_.DescribeQueryResult(List.of(), List.of()));
        }

        @Override
        public <T> Promise<T> transaction(Function<interface_.Transaction, Promise<T>> callback) {
            return Promise.reject(new UnsupportedOperationException("not used"));
        }

        @Override
        public Promise<interface_.ExecProtocolResult> execProtocol(byte[] message, interface_.ExecProtocolOptions options) {
            return Promise.resolve(new interface_.ExecProtocolResult(List.<messages.BackendMessage>of(), new byte[0]));
        }

        @Override
        public Promise<byte[]> execProtocolRaw(byte[] message, interface_.ExecProtocolOptions options) {
            return Promise.resolve(new byte[0]);
        }

        @Override
        public <T> Promise<T> runExclusive(Supplier<Promise<T>> fn) {
            return fn.get();
        }

        @Override
        public Promise<Function<interface_.Transaction, Promise<Void>>> listen(String channel, Consumer<String> callback, interface_.Transaction tx) {
            return Promise.resolve(ignored -> Promise.resolve(null));
        }

        @Override
        public Promise<Void> unlisten(String channel, Consumer<String> callback, interface_.Transaction tx) {
            return Promise.resolve(null);
        }

        @Override
        public Supplier<Void> onNotification(BiConsumer<String, String> callback) {
            return () -> null;
        }

        @Override
        public void offNotification(BiConsumer<String, String> callback) {}

        @Override
        public Promise<byte[]> dumpDataDir(String compression) {
            return Promise.resolve(new byte[0]);
        }

        @Override
        public Promise<Void> syncToFs() {
            return Promise.resolve(null);
        }
    }
}
