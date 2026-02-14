package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        @SuppressWarnings("unchecked")
        var pg = new SequencedStubPGlite(
            List.of(Map.of("id", 1, "name", "a")),
            List.of(Map.of("id", 1, "name", "b"), Map.of("id", 2, "name", "c")),
            List.of(Map.of("id", 2, "name", "c"))
        );
        var setup = index.live.setup().setup(pg, Map.of(), false).join();
        var namespace = (io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveNamespace) setup.namespaceObj().get("live");

        var changes = namespace.changes("select * from t", null, "id", ignored -> {}).join();
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
        @SuppressWarnings("unchecked")
        var pg = new SequencedStubPGlite(
            List.of(Map.of("id", 1, "name", "a")),
            List.of(Map.of("id", 1, "name", "b"))
        );
        var setup = index.live.setup().setup(pg, Map.of(), false).join();
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
        var namespace = (io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveNamespace) setup.namespaceObj().get("live");

        var liveQuery = namespace.query("select 1", null, null).join();
        var error = assertThrows(RuntimeException.class, () -> liveQuery.refresh(0, 10).join());
        assertTrue(error.getMessage().contains("cannot be provided for non-windowed"));
    }

    @Test
    void shouldSkipRefreshWhenNoQuerySubscribers() {
        var pg = new CountingStubPGlite();
        var setup = index.live.setup().setup(pg, Map.of(), false).join();
        var namespace = (io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveNamespace) setup.namespaceObj().get("live");

        var liveQuery = namespace.query("select 1", null, null).join();
        assertEquals(1, pg.count);
        liveQuery.refresh(null, null).join();
        assertEquals(1, pg.count);
    }

    @Test
    void shouldSkipRefreshWhenNoChangesSubscribers() {
        var pg = new CountingStubPGlite();
        var setup = index.live.setup().setup(pg, Map.of(), false).join();
        var namespace = (io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveNamespace) setup.namespaceObj().get("live");

        var changes = namespace.changes("select * from t", null, "id", null).join();
        assertEquals(1, pg.count);
        changes.refresh().join();
        assertEquals(1, pg.count);
    }

    @Test
    void shouldClearAllSubscribersWhenUnsubscribeCallbackProvided() {
        var pg = new CountingStubPGlite();
        var setup = index.live.setup().setup(pg, Map.of(), false).join();
        var namespace = (io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveNamespace) setup.namespaceObj().get("live");

        var live = namespace.<Map<String, Object>>query("select 1", null, results -> {}).join();
        var sub = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Consumer<io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveQueryResults<Map<String, Object>>> callback = ignored -> sub.incrementAndGet();
        live.subscribe(callback);
        live.unsubscribe(callback).join();
        live.refresh(null, null).join();
        assertEquals(0, sub.get());
    }

    @Test
    void shouldUseWindowedSqlAndTotalCount() {
        var pg = new WindowedStubPGlite();
        var setup = index.live.setup().setup(pg, Map.of(), false).join();
        var namespace = (io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveNamespace) setup.namespaceObj().get("live");
        var options = new io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveQueryOptions<Map<String, Object>>(
            "select * from items",
            null,
            1,
            1,
            null,
            null
        );

        var liveQuery = namespace.query(options).join();
        assertEquals(3, liveQuery.initialResults().totalCount());
        assertEquals(1, liveQuery.initialResults().rows().size());
        assertTrue(pg.queries.stream().anyMatch(sql -> sql.contains("live_query_window LIMIT 1 OFFSET 1")));
        assertTrue(pg.queries.stream().anyMatch(sql -> sql.contains("live_query_total_count")));
    }

    @Test
    void shouldEmitResetWhenRetryingMissingRelationError() {
        var pg = new MissingRelationThenDataStubPGlite();
        var setup = index.live.setup().setup(pg, Map.of(), false).join();
        var namespace = (io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveNamespace) setup.namespaceObj().get("live");

        var changes = namespace.changes("select * from t", null, "id", null).join();
        assertTrue(changes.initialChanges().stream().anyMatch(c ->
            c instanceof io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.ChangeReset<?>
        ));
        assertTrue(changes.initialChanges().getFirst() instanceof io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.ChangeReset<?>);
    }

    @Test
    void shouldFilterLiveChangeMetadataFields() {
        var pg = new MetadataFieldsStubPGlite();
        var setup = index.live.setup().setup(pg, Map.of(), false).join();
        var namespace = (io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveNamespace) setup.namespaceObj().get("live");

        var changes = namespace.changes("select * from t", null, "id", null).join();
        assertEquals(List.of("id", "name"), changes.fields().stream().map(io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Field::name).toList());
    }

    @Test
    void shouldRetryWindowedLiveQueryOnMissingRelationError() {
        var pg = new WindowedRetryStubPGlite();
        var setup = index.live.setup().setup(pg, Map.of(), false).join();
        var namespace = (io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveNamespace) setup.namespaceObj().get("live");
        var options = new io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveQueryOptions<Map<String, Object>>(
            "select * from items",
            null,
            0,
            1,
            null,
            null
        );

        var liveQuery = namespace.query(options).join();
        assertEquals(2, liveQuery.initialResults().totalCount());
        assertEquals(1, liveQuery.initialResults().rows().size());
    }

    @Test
    void shouldRefreshAgainWhenWindowedTotalCountChanges() {
        var pg = new WindowedChangingCountStubPGlite();
        var setup = index.live.setup().setup(pg, Map.of(), false).join();
        var namespace = (io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveNamespace) setup.namespaceObj().get("live");
        var seenTotals = new java.util.concurrent.CopyOnWriteArrayList<Integer>();
        var query = namespace.query(
            new io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveQueryOptions<Map<String, Object>>(
                "select * from items",
                null,
                0,
                1,
                results -> seenTotals.add(results.rows().size()),
                null
            )
        ).join();
        query.subscribe(results -> seenTotals.add(results.totalCount()));

        query.refresh(null, null).join();

        assertTrue(seenTotals.stream().anyMatch(v -> v == 1));
        assertTrue(seenTotals.stream().anyMatch(v -> v == 2));
    }

    @Test
    void shouldKeepIncrementalOrderForStringKeys() {
        @SuppressWarnings("unchecked")
        var pg = new SequencedStubPGlite(
            List.of(Map.of("id", "a", "name", "A"), Map.of("id", "b", "name", "B")),
            List.of(Map.of("id", "b", "name", "B"), Map.of("id", "a", "name", "A"))
        );
        var setup = index.live.setup().setup(pg, Map.of(), false).join();
        var namespace = (io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveNamespace) setup.namespaceObj().get("live");
        var latestOrder = new java.util.concurrent.atomic.AtomicReference<List<String>>(List.of());
        var latestRows = new java.util.concurrent.atomic.AtomicReference<List<Map<String, Object>>>(List.of());
        var live = namespace.incrementalQuery(
            new io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveIncrementalQueryOptions<Map<String, Object>>(
                "select * from t",
                null,
                "id",
                results -> {
                    latestRows.set(results.rows());
                    latestOrder.set(results.rows().stream().map(row -> String.valueOf(row.get("id"))).toList());
                },
                null
            )
        ).join();
        assertEquals(List.of("a", "b"), live.initialResults().rows().stream().map(row -> String.valueOf(row.get("id"))).toList());
        live.refresh(null, null).join();
        assertEquals(List.of("b", "a"), latestOrder.get());
        assertTrue(latestRows.get().stream().noneMatch(row -> row.containsKey("__after__")));
    }

    @Test
    void shouldApplyMiddleReorderUsingAfterMarker() {
        @SuppressWarnings("unchecked")
        var pg = new SequencedStubPGlite(
            List.of(Map.of("id", "a", "name", "A"), Map.of("id", "b", "name", "B"), Map.of("id", "c", "name", "C")),
            List.of(Map.of("id", "a", "name", "A"), Map.of("id", "c", "name", "C"), Map.of("id", "b", "name", "B"))
        );
        var setup = index.live.setup().setup(pg, Map.of(), false).join();
        var namespace = (io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveNamespace) setup.namespaceObj().get("live");
        var latestOrder = new java.util.concurrent.atomic.AtomicReference<List<String>>(List.of());
        var live = namespace.incrementalQuery(
            new io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveIncrementalQueryOptions<Map<String, Object>>(
                "select * from t",
                null,
                "id",
                results -> latestOrder.set(results.rows().stream().map(row -> String.valueOf(row.get("id"))).toList()),
                null
            )
        ).join();

        assertEquals(List.of("a", "b", "c"), live.initialResults().rows().stream().map(row -> String.valueOf(row.get("id"))).toList());
        live.refresh(null, null).join();
        assertEquals(List.of("a", "c", "b"), latestOrder.get());
    }

    @Test
    void shouldAllowResubscribeForIncrementalAfterUnsubscribeAllWithoutUpdates() {
        @SuppressWarnings("unchecked")
        var pg = new SequencedStubPGlite(
            List.of(Map.of("id", 1, "name", "a")),
            List.of(Map.of("id", 1, "name", "b"))
        );
        var setup = index.live.setup().setup(pg, Map.of(), false).join();
        var namespace = (io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveNamespace) setup.namespaceObj().get("live");
        var updates = new java.util.concurrent.atomic.AtomicInteger();
        var live = namespace.incrementalQuery("select * from t", null, "id", null).join();

        live.unsubscribe(null).join();
        live.subscribe(results -> updates.incrementAndGet());
        live.refresh(null, null).join();

        assertEquals(0, updates.get());
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
            @SuppressWarnings("unchecked") List<Map<String, Object>>... rows
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

    private static class WindowedStubPGlite extends StubPGlite {
        private final List<String> queries = new ArrayList<>();
        @Override
        public <T> Promise<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<T>> query(String query, Object[] params, io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.QueryOptions options) {
            queries.add(query);
            if (query.contains("COUNT(*) AS count")) {
                @SuppressWarnings("unchecked")
                var countRows = (List<T>) List.of(Map.of("count", 3));
                return Promise.resolve(new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<>(countRows, 1, List.of(
                    new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Field("count", 0)
                ), null));
            }
            @SuppressWarnings("unchecked")
            var rows = (List<T>) List.of(Map.of("id", 2, "name", "b"));
            return Promise.resolve(new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<>(rows, rows.size(), List.of(
                new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Field("id", 0),
                new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Field("name", 0)
            ), null));
        }
    }

    private static class CountingStubPGlite extends StubPGlite {
        int count;
        @Override
        public <T> Promise<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<T>> query(String query, Object[] params, io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.QueryOptions options) {
            count++;
            @SuppressWarnings("unchecked")
            var rows = (List<T>) List.of(Map.of("id", 1));
            return Promise.resolve(new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<>(rows, rows.size(), List.of(), null));
        }
    }

    private static class MissingRelationThenDataStubPGlite extends StubPGlite {
        private int calls;
        @Override
        public <T> Promise<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<T>> query(String query, Object[] params, io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.QueryOptions options) {
            calls++;
            if (calls == 1) {
                return Promise.reject(new RuntimeException("relation \"live_query_state\" does not exist"));
            }
            @SuppressWarnings("unchecked")
            var rows = (List<T>) List.of(Map.of("id", 1, "name", "a"));
            return Promise.resolve(new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<>(rows, rows.size(), List.of(
                new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Field("id", 0),
                new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Field("name", 0)
            ), null));
        }
    }

    private static class MetadataFieldsStubPGlite extends StubPGlite {
        @Override
        public <T> Promise<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<T>> query(String query, Object[] params, io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.QueryOptions options) {
            @SuppressWarnings("unchecked")
            var rows = (List<T>) List.of(Map.of("id", 1, "name", "a"));
            return Promise.resolve(new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<>(rows, rows.size(), List.of(
                new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Field("__after__", 0),
                new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Field("__op__", 0),
                new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Field("__changed_columns__", 0),
                new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Field("id", 0),
                new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Field("name", 0)
            ), null));
        }
    }

    private static class WindowedRetryStubPGlite extends StubPGlite {
        private int calls;
        @Override
        public <T> Promise<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<T>> query(String query, Object[] params, io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.QueryOptions options) {
            calls++;
            if (calls == 1) {
                return Promise.reject(new RuntimeException("prepared statement \"live_query_x\" does not exist"));
            }
            if (query.contains("COUNT(*) AS count")) {
                @SuppressWarnings("unchecked")
                var countRows = (List<T>) List.of(Map.of("count", 2));
                return Promise.resolve(new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<>(countRows, 1, List.of(
                    new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Field("count", 0)
                ), null));
            }
            @SuppressWarnings("unchecked")
            var rows = (List<T>) List.of(Map.of("id", 1, "name", "a"));
            return Promise.resolve(new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<>(rows, rows.size(), List.of(
                new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Field("id", 0),
                new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Field("name", 0)
            ), null));
        }
    }

    private static class WindowedChangingCountStubPGlite extends StubPGlite {
        private int countReads;
        @Override
        public <T> Promise<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<T>> query(String query, Object[] params, io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.QueryOptions options) {
            if (query.contains("COUNT(*) AS count")) {
                countReads++;
                var count = countReads == 1 ? 1 : 2;
                @SuppressWarnings("unchecked")
                var countRows = (List<T>) List.of(Map.of("count", count));
                return Promise.resolve(new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<>(countRows, 1, List.of(
                    new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Field("count", 0)
                ), null));
            }
            @SuppressWarnings("unchecked")
            var rows = (List<T>) List.of(Map.of("id", 1, "name", "a"));
            return Promise.resolve(new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<>(rows, rows.size(), List.of(
                new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Field("id", 0),
                new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Field("name", 0)
            ), null));
        }
    }
}
