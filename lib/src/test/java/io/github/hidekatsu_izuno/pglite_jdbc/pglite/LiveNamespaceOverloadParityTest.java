package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.AbortController;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.index;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

public class LiveNamespaceOverloadParityTest {
    @Test
    void shouldSupportStringOverloads() {
        var pg = new StubPGlite();
        var setup = index.live.setup().setup(pg, Map.of(), false).join();
        @SuppressWarnings("unchecked")
        var namespace = (io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveNamespace) setup.namespaceObj().get("live");
        assertNotNull(namespace);

        var query = namespace.query("select 1", null, null).join();
        var changes = namespace.changes("select 1", null, "id", null).join();
        var incremental = namespace.incrementalQuery("select 1", null, "id", null).join();

        assertNotNull(query.initialResults());
        assertNotNull(changes.initialChanges());
        assertNotNull(incremental.initialResults());
        assertTrue(changes.fields().isEmpty());
    }

    @Test
    void shouldRejectPartialWindowOptions() {
        var pg = new StubPGlite();
        var setup = index.live.setup().setup(pg, Map.of(), false).join();
        @SuppressWarnings("unchecked")
        var namespace = (io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveNamespace) setup.namespaceObj().get("live");
        var options = new io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveQueryOptions<>(
            "select 1",
            null,
            0,
            null,
            null,
            null
        );
        var error = assertThrows(RuntimeException.class, () -> namespace.query(options).join());
        assertTrue(error.getMessage().contains("offset and limit"));
    }

    @Test
    void shouldRequireKeyForChangesAndIncrementalQuery() {
        var pg = new StubPGlite();
        var setup = index.live.setup().setup(pg, Map.of(), false).join();
        @SuppressWarnings("unchecked")
        var namespace = (io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveNamespace) setup.namespaceObj().get("live");

        var changesOptions = new io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveChangesOptions<>(
            "select 1",
            null,
            "",
            null,
            null
        );
        var incrementalOptions = new io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveIncrementalQueryOptions<>(
            "select 1",
            null,
            "",
            null,
            null
        );

        var changesError = assertThrows(RuntimeException.class, () -> namespace.changes(changesOptions).join());
        var incrementalError = assertThrows(RuntimeException.class, () -> namespace.incrementalQuery(incrementalOptions).join());
        assertTrue(changesError.getMessage().contains("key is required"));
        assertTrue(incrementalError.getMessage().contains("key is required"));
    }

    @Test
    void shouldComputeChangesInsertUpdateDelete() {
        var pg = new SequencedStubPGlite(
            List.of(Map.of("id", 1, "name", "a")),
            List.of(Map.of("id", 1, "name", "b"), Map.of("id", 2, "name", "c")),
            List.of(Map.of("id", 2, "name", "c"))
        );
        var setup = index.live.setup().setup(pg, Map.of(), false).join();
        @SuppressWarnings("unchecked")
        var namespace = (io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveNamespace) setup.namespaceObj().get("live");

        var changes = namespace.changes("select * from t", null, "id", null).join();
        assertTrue(changes.initialChanges().stream().anyMatch(c ->
            c instanceof io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.ChangeInsert<?>
        ));

        changes.refresh().join();
        var step2 = changes.initialChanges();
        assertTrue(step2.stream().anyMatch(c ->
            c instanceof io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.ChangeUpdate<?>
        ));
        assertTrue(step2.stream().anyMatch(c ->
            c instanceof io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.ChangeInsert<?>
        ));

        changes.refresh().join();
        var step3 = changes.initialChanges();
        assertTrue(step3.stream().anyMatch(c ->
            c instanceof io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.ChangeDelete<?>
        ));
    }

    @Test
    void shouldUnsubscribeWhenSignalIsAborted() {
        var pg = new SequencedStubPGlite(
            List.of(Map.of("id", 1, "name", "a")),
            List.of(Map.of("id", 1, "name", "b"))
        );
        var setup = index.live.setup().setup(pg, Map.of(), false).join();
        @SuppressWarnings("unchecked")
        var namespace = (io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveNamespace) setup.namespaceObj().get("live");
        var controller = new AbortController();
        var options = new io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveIncrementalQueryOptions<>(
            "select * from t",
            null,
            "id",
            null,
            controller.signal()
        );

        var live = namespace.incrementalQuery(options).join();
        controller.abort();
        live.refresh(null, null).join();
        assertTrue(true);
    }

    @Test
    void shouldNotAllowSubscribeAfterFullyUnsubscribed() {
        var pg = new StubPGlite();
        var setup = index.live.setup().setup(pg, Map.of(), false).join();
        @SuppressWarnings("unchecked")
        var namespace = (io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveNamespace) setup.namespaceObj().get("live");

        var liveQuery = namespace.query("select 1", null, null).join();
        liveQuery.unsubscribe(null).join();
        assertThrows(IllegalStateException.class, () -> liveQuery.subscribe(results -> {}));

        var changes = namespace.changes("select 1", null, "id", null).join();
        changes.unsubscribe(null).join();
        assertThrows(IllegalStateException.class, () -> changes.subscribe(rows -> {}));
    }

    @Test
    void shouldRejectOffsetLimitOnNonWindowedRefresh() {
        var pg = new StubPGlite();
        var setup = index.live.setup().setup(pg, Map.of(), false).join();
        @SuppressWarnings("unchecked")
        var namespace = (io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveNamespace) setup.namespaceObj().get("live");

        var liveQuery = namespace.query("select 1", null, null).join();
        var error = assertThrows(RuntimeException.class, () -> liveQuery.refresh(0, 10).join());
        assertTrue(error.getMessage().contains("cannot be provided for non-windowed"));
    }

    private static class StubPGlite implements io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.PGliteInterface {
        @Override
        public Promise<Void> waitReady() {
            return Promise.resolve(null);
        }

        @Override
        public int debug() {
            return 0;
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
        public <T> Promise<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<T>> query(String query, Object[] params, io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.QueryOptions options) {
            return Promise.resolve(new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<>(List.of(), 0, List.of(), null));
        }

        @Override
        public Promise<List<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<Map<String, Object>>>> exec(String query, io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.QueryOptions options) {
            return Promise.resolve(List.of());
        }

        @Override
        public <T> Promise<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<T>> sql(List<String> strings, Object... params) {
            return Promise.resolve(new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<>(List.of(), 0, List.of(), null));
        }

        @Override
        public Promise<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.DescribeQueryResult> describeQuery(String query) {
            return Promise.resolve(new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.DescribeQueryResult(List.of(), List.of()));
        }

        @Override
        public <T> Promise<T> transaction(Function<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Transaction, Promise<T>> callback) {
            return Promise.reject(new UnsupportedOperationException("not used"));
        }

        @Override
        public Promise<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.ExecProtocolResult> execProtocol(byte[] message, io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.ExecProtocolOptions options) {
            return Promise.resolve(new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.ExecProtocolResult(List.of(), new byte[0]));
        }

        @Override
        public Promise<byte[]> execProtocolRaw(byte[] message, io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.ExecProtocolOptions options) {
            return Promise.resolve(new byte[0]);
        }

        @Override
        public <T> Promise<T> runExclusive(Supplier<Promise<T>> fn) {
            return fn.get();
        }

        @Override
        public Promise<Function<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Transaction, Promise<Void>>> listen(String channel, java.util.function.Consumer<String> callback, io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Transaction tx) {
            return Promise.resolve(ignored -> Promise.resolve(null));
        }

        @Override
        public Promise<Void> unlisten(String channel, java.util.function.Consumer<String> callback, io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Transaction tx) {
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
    }

    private static class SequencedStubPGlite extends StubPGlite {
        private final Queue<List<Map<String, Object>>> sequences = new ArrayDeque<>();

        SequencedStubPGlite(
            List<Map<String, Object>>... rows
        ) {
            for (var row : rows) {
                sequences.add(row);
            }
        }

        @Override
        public <T> Promise<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<T>> query(String query, Object[] params, io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.QueryOptions options) {
            var next = sequences.isEmpty() ? List.<Map<String, Object>>of() : sequences.remove();
            var fields = new ArrayList<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Field>();
            if (!next.isEmpty()) {
                for (var key : next.getFirst().keySet()) {
                    fields.add(new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Field(key, 0));
                }
            }
            @SuppressWarnings("unchecked")
            var castRows = (List<T>) next;
            return Promise.resolve(new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<>(castRows, castRows.size(), fields, null));
        }
    }
}
