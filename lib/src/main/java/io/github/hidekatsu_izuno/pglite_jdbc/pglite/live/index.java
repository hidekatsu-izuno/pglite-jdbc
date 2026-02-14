package io.github.hidekatsu_izuno.pglite_jdbc.pglite.live;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.AbortSignal;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class index {
    private index() {}

    public static record TableRef(
        String table_name,
        String schema_name,
        int table_oid,
        int schema_oid
    ) {}

    public static final io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Extension live = new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Extension() {
        @Override
        public String name() {
            return "live";
        }

        @Override
        public io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.ExtensionSetup setup() {
            return (pg, emscriptenOpts, clientOnly) -> Promise.resolve(
                new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.ExtensionSetupResult(
                    emscriptenOpts,
                    java.util.Map.of("live", createNamespace(pg)),
                    null,
                    null,
                    null
                )
            );
        }
    };

    private static io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveNamespace createNamespace(
        io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.PGliteInterface pg
    ) {
        return new io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveNamespace() {
            @Override
            public <T> Promise<io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveQuery<T>> query(
                String query,
                Object[] params,
                Consumer<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<T>> callback
            ) {
                return query(
                    new io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveQueryOptions<>(
                        query,
                        params,
                        null,
                        null,
                        callback,
                        null
                    )
                );
            }

            @Override
            public <T> Promise<io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveQuery<T>> query(
                io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveQueryOptions<T> options
            ) {
                if ((options.offset() == null) != (options.limit() == null)) {
                    return Promise.reject(new IllegalArgumentException("offset and limit must be provided together"));
                }
                var subscribers = new ArrayList<Consumer<io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveQueryResults<T>>>();
                if (options.callback() != null) {
                    subscribers.add(results ->
                        options.callback().accept(
                            new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<>(
                                results.rows(),
                                results.affectedRows(),
                                results.fields(),
                                results.blob()
                            )
                        )
                    );
                }
                return pg.query(options.query(), options.params(), null).then(initialResult -> {
                    @SuppressWarnings("unchecked")
                    var initialRows = (List<T>) initialResult.rows();
                    var initial = new io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveQueryResults<>(
                        initialRows,
                        initialResult.affectedRows(),
                        initialResult.fields(),
                        initialResult.blob(),
                        initialResult.rows().size(),
                        options.offset(),
                        options.limit()
                    );
                    for (var subscriber : List.copyOf(subscribers)) {
                        subscriber.accept(initial);
                    }
                    return new io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveQuery<T>() {
                        private volatile io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveQueryResults<T> latest = initial;
                        private volatile boolean dead;

                        @Override
                        public io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveQueryResults<T> initialResults() {
                            return initial;
                        }

                        @Override
                        public void subscribe(
                            Consumer<io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveQueryResults<T>> callback
                        ) {
                            if (dead) {
                                throw new IllegalStateException("Live query is no longer active and cannot be subscribed to");
                            }
                            subscribers.add(callback);
                        }

                        @Override
                        public Promise<Void> unsubscribe(
                            Consumer<io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveQueryResults<T>> callback
                        ) {
                            if (callback == null) {
                                subscribers.clear();
                            } else {
                                subscribers.remove(callback);
                            }
                            if (subscribers.isEmpty()) {
                                dead = true;
                            }
                            return Promise.resolve(null);
                        }

                        @Override
                        public Promise<Void> refresh(Integer offset, Integer limit) {
                            if ((offset == null) != (limit == null)) {
                                return Promise.reject(new IllegalArgumentException("offset and limit must be provided together"));
                            }
                            var isWindowed = options.offset() != null && options.limit() != null;
                            if (!isWindowed && (offset != null || limit != null)) {
                                return Promise.reject(
                                    new IllegalArgumentException("offset and limit cannot be provided for non-windowed queries")
                                );
                            }
                            return pg.query(options.query(), options.params(), null).then(result -> {
                                @SuppressWarnings("unchecked")
                                var rows = (List<T>) result.rows();
                                latest = new io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveQueryResults<>(
                                    rows,
                                    result.affectedRows(),
                                    result.fields(),
                                    result.blob(),
                                    result.rows().size(),
                                    offset != null ? offset : options.offset(),
                                    limit != null ? limit : options.limit()
                                );
                                for (var subscriber : List.copyOf(subscribers)) {
                                    subscriber.accept(latest);
                                }
                                return null;
                            });
                        }
                    };
                }).then(liveQueryObj -> {
                    @SuppressWarnings("unchecked")
                    var liveQuery = (io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveQuery<T>) liveQueryObj;
                    attachAbort(options.signal(), () -> liveQuery.unsubscribe(null));
                    return liveQuery;
                });
            }

            @Override
            public <T> Promise<io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveChanges<T>> changes(
                String query,
                Object[] params,
                String key,
                Consumer<List<io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.Change<T>>> callback
            ) {
                return changes(
                    new io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveChangesOptions<>(
                        query,
                        params,
                        key,
                        callback,
                        null
                    )
                );
            }

            @Override
            public <T> Promise<io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveChanges<T>> changes(
                io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveChangesOptions<T> options
            ) {
                if (options.key() == null || options.key().isBlank()) {
                    return Promise.reject(new IllegalArgumentException("key is required for changes queries"));
                }
                var subscribers = new ArrayList<Consumer<List<io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.Change<T>>>>();
                if (options.callback() != null) {
                    subscribers.add(options.callback());
                }
                var fieldsRef = new java.util.concurrent.atomic.AtomicReference<List<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Field>>(List.of());
                var changesRef = new java.util.concurrent.atomic.AtomicReference<List<io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.Change<T>>>(List.of());
                var previousByKey = new LinkedHashMap<Object, Map<String, Object>>();
                var previousOrder = new ArrayList<Object>();

                var liveChanges = new io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveChanges<T>() {
                    private volatile boolean dead;
                    @Override
                    public List<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Field> fields() {
                        return fieldsRef.get();
                    }

                    @Override
                    public List<io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.Change<T>> initialChanges() {
                        return changesRef.get();
                    }

                    @Override
                    public void subscribe(
                        Consumer<List<io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.Change<T>>> callback
                    ) {
                        if (dead) {
                            throw new IllegalStateException("Live query is no longer active and cannot be subscribed to");
                        }
                        subscribers.add(callback);
                    }

                    @Override
                    public Promise<Void> unsubscribe(
                        Consumer<List<io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.Change<T>>> callback
                    ) {
                        if (callback == null) {
                            subscribers.clear();
                        } else {
                            subscribers.remove(callback);
                        }
                        if (subscribers.isEmpty()) {
                            dead = true;
                        }
                        return Promise.resolve(null);
                    }

                    @Override
                    public Promise<Void> refresh() {
                        return pg.query(options.query(), options.params(), null).then(result -> {
                            var fields = result.fields() != null ? result.fields() : List.<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Field>of();
                            fieldsRef.set(fields);

                            var currentRows = toRowMaps(result.rows());
                            var currentByKey = new LinkedHashMap<Object, Map<String, Object>>();
                            var currentOrder = new ArrayList<Object>();
                            for (var row : currentRows) {
                                var keyValue = row.get(options.key());
                                if (keyValue == null) {
                                    continue;
                                }
                                currentByKey.put(keyValue, row);
                                currentOrder.add(keyValue);
                            }

                            var computed = new ArrayList<io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.Change<T>>();
                            for (var prevKey : previousOrder) {
                                if (!currentByKey.containsKey(prevKey)) {
                                    computed.add(
                                        new io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.ChangeDelete<>(
                                            List.of(),
                                            castRow(previousByKey.get(prevKey))
                                        )
                                    );
                                }
                            }

                            Object previousRowKey = null;
                            for (var keyValue : currentOrder) {
                                var current = currentByKey.get(keyValue);
                                var old = previousByKey.get(keyValue);
                                var after = previousRowKey == null ? -1 : previousRowKey.hashCode();
                                if (old == null) {
                                    computed.add(
                                        new io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.ChangeInsert<>(
                                            List.of(),
                                            after,
                                            castRow(current)
                                        )
                                    );
                                } else {
                                    var changedColumns = changedColumns(old, current, options.key());
                                    if (!changedColumns.isEmpty()) {
                                        computed.add(
                                            new io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.ChangeUpdate<>(
                                                changedColumns,
                                                after,
                                                castRow(current)
                                            )
                                        );
                                    }
                                }
                                previousRowKey = keyValue;
                            }

                            previousByKey.clear();
                            previousByKey.putAll(currentByKey);
                            previousOrder.clear();
                            previousOrder.addAll(currentOrder);

                            changesRef.set(List.copyOf(computed));
                            for (var subscriber : List.copyOf(subscribers)) {
                                subscriber.accept(changesRef.get());
                            }
                            return null;
                        });
                    }
                };

                return liveChanges.refresh().then(ignored -> {
                    attachAbort(options.signal(), () -> liveChanges.unsubscribe(null));
                    return liveChanges;
                });
            }

            @Override
            public <T> Promise<io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveQuery<T>> incrementalQuery(
                String query,
                Object[] params,
                String key,
                Consumer<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<T>> callback
            ) {
                return incrementalQuery(
                    new io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveIncrementalQueryOptions<>(
                        query,
                        params,
                        key,
                        callback,
                        null
                    )
                );
            }

            @Override
            public <T> Promise<io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveQuery<T>> incrementalQuery(
                io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveIncrementalQueryOptions<T> options
            ) {
                if (options.key() == null || options.key().isBlank()) {
                    return Promise.reject(new IllegalArgumentException("key is required for incremental queries"));
                }
                var callbacks = new ArrayList<Consumer<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<T>>>();
                if (options.callback() != null) {
                    callbacks.add(options.callback());
                }
                var callbackBridges = new LinkedHashMap<
                    Consumer<io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveQueryResults<T>>,
                    Consumer<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<T>>
                >();
                var rowsMap = new LinkedHashMap<Object, Map<String, Object>>();
                var afterMap = new LinkedHashMap<Integer, Object>();
                var lastRowsRef = new java.util.concurrent.atomic.AtomicReference<List<T>>(List.of());
                var fieldsRef = new java.util.concurrent.atomic.AtomicReference<List<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Field>>(List.of());
                var firstRun = new java.util.concurrent.atomic.AtomicBoolean(true);

                return changes(
                    new io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveChangesOptions<>(
                        options.query(),
                        options.params(),
                        options.key(),
                        changes -> {
                            for (var change : changes) {
                                if (change instanceof io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.ChangeReset<?>) {
                                    rowsMap.clear();
                                    afterMap.clear();
                                    continue;
                                }
                                if (change instanceof io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.ChangeInsert<?> insert) {
                                    var obj = rowMap(insert.row());
                                    var keyValue = obj.get(options.key());
                                    if (keyValue == null) {
                                        continue;
                                    }
                                    rowsMap.put(keyValue, obj);
                                    afterMap.put(insert.after(), keyValue);
                                    continue;
                                }
                                if (change instanceof io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.ChangeDelete<?> delete) {
                                    var obj = rowMap(delete.row());
                                    var keyValue = obj.get(options.key());
                                    if (keyValue == null) {
                                        continue;
                                    }
                                    rowsMap.remove(keyValue);
                                    removeAfterEntry(afterMap, keyValue);
                                    continue;
                                }
                                if (change instanceof io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.ChangeUpdate<?> update) {
                                    var obj = rowMap(update.row());
                                    var keyValue = obj.get(options.key());
                                    if (keyValue == null) {
                                        continue;
                                    }
                                    var merged = new LinkedHashMap<String, Object>(rowsMap.getOrDefault(keyValue, Map.of()));
                                    for (var col : update.changedColumns()) {
                                        merged.put(col, obj.get(col));
                                    }
                                    merged.put(options.key(), keyValue);
                                    rowsMap.put(keyValue, merged);
                                    afterMap.put(update.after(), keyValue);
                                }
                            }

                            var orderedRows = index.<T>orderedRows(rowsMap, afterMap);
                            lastRowsRef.set(orderedRows);
                            if (!firstRun.get()) {
                                runResultCallbacks(callbacks, new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<T>(
                                    orderedRows,
                                    orderedRows.size(),
                                    fieldsRef.get(),
                                    null
                                ));
                            }
                        },
                        options.signal()
                    )
                ).then(changesObj -> {
                    var changes = (io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveChanges<T>) changesObj;
                    fieldsRef.set(changes.fields());
                    firstRun.set(false);
                    runResultCallbacks(callbacks, new io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<>(
                        lastRowsRef.get(),
                        lastRowsRef.get().size(),
                        fieldsRef.get(),
                        null
                    ));

                    var initialResults = new io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveQueryResults<>(
                        lastRowsRef.get(),
                        lastRowsRef.get().size(),
                        fieldsRef.get(),
                        null,
                        lastRowsRef.get().size(),
                        null,
                        null
                    );

                    return new io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveQuery<T>() {
                        private volatile boolean dead;
                        @Override
                        public io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveQueryResults<T> initialResults() {
                            return initialResults;
                        }

                        @Override
                        public void subscribe(
                            Consumer<io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveQueryResults<T>> callback
                        ) {
                            if (dead) {
                                throw new IllegalStateException("Live query is no longer active and cannot be subscribed to");
                            }
                            var bridge = (Consumer<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<T>>) (results ->
                                callback.accept(new io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveQueryResults<>(
                                    results.rows(),
                                    results.affectedRows(),
                                    results.fields(),
                                    results.blob(),
                                    results.rows().size(),
                                    null,
                                    null
                                ))
                            );
                            callbackBridges.put(callback, bridge);
                            callbacks.add(bridge);
                        }

                        @Override
                        public Promise<Void> unsubscribe(
                            Consumer<io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveQueryResults<T>> callback
                        ) {
                            if (callback == null) {
                                callbackBridges.clear();
                                callbacks.clear();
                                dead = true;
                                return changes.unsubscribe(null);
                            }
                            var bridge = callbackBridges.remove(callback);
                            if (bridge != null) {
                                callbacks.remove(bridge);
                            }
                            if (callbacks.isEmpty()) {
                                dead = true;
                                return changes.unsubscribe(null);
                            }
                            return Promise.resolve(null);
                        }

                        @Override
                        public Promise<Void> refresh(Integer offset, Integer limit) {
                            if (offset != null || limit != null) {
                                return Promise.reject(new IllegalArgumentException("offset/limit are not supported in incrementalQuery"));
                            }
                            return changes.refresh();
                        }
                    };
                }).then(liveQueryObj -> {
                    @SuppressWarnings("unchecked")
                    var liveQuery = (io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveQuery<T>) liveQueryObj;
                    attachAbort(options.signal(), () -> liveQuery.unsubscribe(null));
                    return liveQuery;
                });
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> T castRow(Map<String, Object> row) {
        return (T) row;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> toRowMaps(List<?> rows) {
        var out = new ArrayList<Map<String, Object>>();
        if (rows == null) {
            return out;
        }
        for (var row : rows) {
            if (row instanceof Map<?, ?> map) {
                var cast = (Map<String, Object>) map;
                out.add(cast);
            } else {
                out.add(Map.of("value", row));
            }
        }
        return out;
    }

    private static List<String> changedColumns(
        Map<String, Object> before,
        Map<String, Object> after,
        String keyColumn
    ) {
        var changed = new ArrayList<String>();
        for (var entry : after.entrySet()) {
            if (Objects.equals(entry.getKey(), keyColumn)) {
                continue;
            }
            if (!Objects.equals(before.get(entry.getKey()), entry.getValue())) {
                changed.add(entry.getKey());
            }
        }
        return changed;
    }

    @SuppressWarnings("unchecked")
    private static <T> Map<String, Object> rowMap(T row) {
        if (row instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of("value", row);
    }

    private static void removeAfterEntry(Map<Integer, Object> afterMap, Object keyValue) {
        Integer target = null;
        for (var entry : afterMap.entrySet()) {
            if (Objects.equals(entry.getValue(), keyValue)) {
                target = entry.getKey();
                break;
            }
        }
        if (target != null) {
            afterMap.remove(target);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> orderedRows(
        LinkedHashMap<Object, Map<String, Object>> rowsMap,
        LinkedHashMap<Integer, Object> afterMap
    ) {
        var ordered = new ArrayList<T>();
        var lastAfter = -1;
        for (var i = 0; i < rowsMap.size(); i++) {
            var nextKey = afterMap.get(lastAfter);
            if (nextKey == null) {
                break;
            }
            var row = rowsMap.get(nextKey);
            if (row == null) {
                break;
            }
            ordered.add(castRow(row));
            lastAfter = nextKey.hashCode();
        }
        if (ordered.size() != rowsMap.size()) {
            for (var row : rowsMap.values()) {
                var cast = (T) castRow(row);
                if (!ordered.contains(cast)) {
                    ordered.add(cast);
                }
            }
        }
        return ordered;
    }

    private static <T> void runResultCallbacks(
        List<Consumer<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<T>>> callbacks,
        io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<T> results
    ) {
        for (var callback : List.copyOf(callbacks)) {
            callback.accept(results);
        }
    }

    public static Promise<List<TableRef>> getTablesForView(
        io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.PGliteInterface tx,
        String viewName
    ) {
        var sql = """
            WITH RECURSIVE view_dependencies AS (
              SELECT DISTINCT
                cl.relname AS table_name,
                n.nspname AS schema_name,
                cl.oid AS table_oid,
                n.oid AS schema_oid,
                cl.relkind = 'v' AS is_view
              FROM pg_rewrite r
              JOIN pg_depend d ON r.oid = d.objid
              JOIN pg_class cl ON d.refobjid = cl.oid
              JOIN pg_namespace n ON cl.relnamespace = n.oid
              WHERE
                r.ev_class = (
                  SELECT oid FROM pg_class WHERE relname = $1 AND relkind = 'v'
                )
                AND d.deptype = 'n'
              UNION ALL
              SELECT DISTINCT
                cl.relname AS table_name,
                n.nspname AS schema_name,
                cl.oid AS table_oid,
                n.oid AS schema_oid,
                cl.relkind = 'v' AS is_view
              FROM view_dependencies vd
              JOIN pg_rewrite r ON vd.table_name = (
                SELECT relname FROM pg_class WHERE oid = r.ev_class AND relkind = 'v'
              )
              JOIN pg_depend d ON r.oid = d.objid
              JOIN pg_class cl ON d.refobjid = cl.oid
              JOIN pg_namespace n ON cl.relnamespace = n.oid
              WHERE d.deptype = 'n'
            )
            SELECT DISTINCT table_name, schema_name, table_oid, schema_oid
            FROM view_dependencies
            WHERE NOT is_view;
            """;
        return tx.query(sql, new Object[] { viewName }, null).then(result -> {
            var out = new ArrayList<TableRef>();
            for (var row : result.rows()) {
                @SuppressWarnings("unchecked")
                var mapRow = row instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.<String, Object>of();
                out.add(
                    new TableRef(
                        String.valueOf(mapRow.getOrDefault("table_name", "")),
                        String.valueOf(mapRow.getOrDefault("schema_name", "")),
                        asInt(mapRow.get("table_oid")),
                        asInt(mapRow.get("schema_oid"))
                    )
                );
            }
            return out;
        });
    }

    public static Promise<Void> addNotifyTriggersToTables(
        io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.PGliteInterface tx,
        List<TableRef> tables,
        Set<String> tableNotifyTriggersAdded
    ) {
        var sqlBuilder = new StringBuilder();
        for (var table : tables) {
            var key = table.schema_oid() + "_" + table.table_oid();
            if (tableNotifyTriggersAdded.contains(key)) {
                continue;
            }
            sqlBuilder.append(
                """
                CREATE OR REPLACE FUNCTION "_notify_%d_%d"() RETURNS TRIGGER AS $$
                BEGIN
                  PERFORM pg_notify('table_change__%d__%d', '');
                  RETURN NULL;
                END;
                $$ LANGUAGE plpgsql;
                CREATE OR REPLACE TRIGGER "_notify_trigger_%d_%d"
                AFTER INSERT OR UPDATE OR DELETE ON "%s"."%s"
                FOR EACH STATEMENT EXECUTE FUNCTION "_notify_%d_%d"();

                """.formatted(
                    table.schema_oid(),
                    table.table_oid(),
                    table.schema_oid(),
                    table.table_oid(),
                    table.schema_oid(),
                    table.table_oid(),
                    table.schema_name(),
                    table.table_name(),
                    table.schema_oid(),
                    table.table_oid()
                )
            );
            tableNotifyTriggersAdded.add(key);
        }
        if (sqlBuilder.isEmpty()) {
            return Promise.resolve(null);
        }
        return tx.exec(sqlBuilder.toString(), null).then(ignore -> null);
    }

    private static int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static void attachAbort(
        Object signal,
        java.util.function.Supplier<Promise<Void>> onAbort
    ) {
        if (!(signal instanceof AbortSignal abortSignal)) {
            return;
        }
        if (abortSignal.aborted()) {
            onAbort.get();
            return;
        }
        abortSignal.addEventListener("abort", ignored -> onAbort.get());
    }

}
