package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.index;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

public class LiveSqlHelpersTest {
    @Test
    void shouldParseTablesForViewResult() {
        var tx = new SqlCapturePGlite(
            List.of(Map.of(
                "table_name",
                "items",
                "schema_name",
                "public",
                "table_oid",
                100,
                "schema_oid",
                2200
            ))
        );

        var tables = index.getTablesForView(tx, "live_query_test_view").join();

        assertEquals(1, tables.size());
        assertEquals("items", tables.getFirst().table_name());
        assertTrue(tx.queries.getFirst().contains("WITH RECURSIVE view_dependencies"));
    }

    @Test
    void shouldGenerateNotifyTriggersOnlyForNewTables() {
        var tx = new SqlCapturePGlite(List.of());
        var tables = List.of(
            new index.TableRef("items", "public", 100, 2200),
            new index.TableRef("orders", "public", 101, 2200)
        );
        var added = new HashSet<String>();
        added.add("2200_100");

        index.addNotifyTriggersToTables(tx, tables, added).join();

        assertEquals(1, tx.execs.size());
        assertTrue(tx.execs.getFirst().contains("_notify_2200_101"));
        assertTrue(tx.execs.getFirst().contains("table_change__2200__101"));
        assertEquals(2, added.size());
    }

    private static class SqlCapturePGlite implements io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.PGliteInterface {
        final List<String> queries = new ArrayList<>();
        final List<String> execs = new ArrayList<>();
        final List<Map<String, Object>> queryRows;

        SqlCapturePGlite(List<Map<String, Object>> queryRows) {
            this.queryRows = queryRows;
        }

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
        public <T> Promise<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<T>> query(
            String query,
            Object[] params,
            io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.QueryOptions options
        ) {
            queries.add(query);
            @SuppressWarnings("unchecked")
            var rows = (List<T>) queryRows;
            return Promise.resolve(new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<>(rows, rows.size(), List.of(), null));
        }

        @Override
        public Promise<List<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<Map<String, Object>>>> exec(
            String query,
            io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.QueryOptions options
        ) {
            execs.add(query);
            return Promise.resolve(List.of());
        }

        @Override
        public <T> Promise<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<T>> sql(
            List<String> strings,
            Object... params
        ) {
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
        public Promise<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.ExecProtocolResult> execProtocol(
            byte[] message,
            io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.ExecProtocolOptions options
        ) {
            return Promise.resolve(new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.ExecProtocolResult(List.of(), new byte[0]));
        }

        @Override
        public Promise<byte[]> execProtocolRaw(
            byte[] message,
            io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.ExecProtocolOptions options
        ) {
            return Promise.resolve(new byte[0]);
        }

        @Override
        public <T> Promise<T> runExclusive(Supplier<Promise<T>> fn) {
            return fn.get();
        }

        @Override
        public Promise<Function<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Transaction, Promise<Void>>> listen(
            String channel,
            java.util.function.Consumer<String> callback,
            io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Transaction tx
        ) {
            return Promise.resolve(ignored -> Promise.resolve(null));
        }

        @Override
        public Promise<Void> unlisten(
            String channel,
            java.util.function.Consumer<String> callback,
            io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Transaction tx
        ) {
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
}
