package io.github.hidekatsu_izuno.pglite_jdbc.pglite.live;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Extension;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.ExtensionSetupResult;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Extensions;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.PGliteInterface;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Transaction;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.AbortSignal;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.AddEventListenerOptions;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.Change;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveChanges;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveChangesCallback;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveChangesOptions;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveIncrementalQueryOptions;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveNamespace;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveQuery;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveQueryCallback;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveQueryOptions;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveQueryResults;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.RefreshOptions;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.utils;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.utils.AsyncFunction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

public final class index {
    private static final int MAX_RETRIES = 5;

    private static CompletableFuture<ExtensionSetupResult<LiveNamespace>> setup(
        PGliteInterface<?> pg,
        Object emscriptenOpts
    ) {
        // The notify triggers are only ever added and never removed
        // Keep track of which triggers have been added to avoid adding them multiple times
        var tableNotifyTriggersAdded = new HashSet<String>();

        var namespaceObj = new LiveNamespace() {
            @Override
            public <T> CompletableFuture<LiveQuery<T>> query(
                String query,
                Object[] params,
                LiveQueryCallback<T> callback
            ) {
                var options = new LiveQueryOptions<T>();
                options.query = query;
                options.params = params;
                options.callback = callback;
                return query(options);
            }

            @Override
            public <T> CompletableFuture<LiveQuery<T>> query(
                LiveQueryOptions<T> options
            ) {
                var signal = options != null ? options.signal : null;
                var params = options != null ? options.params : null;
                var callback = options != null ? options.callback : null;
                var offset = options != null ? options.offset : null;
                var limit = options != null ? options.limit : null;
                var query = options != null ? options.query : null;

                // Offset and limit must be provided together
                if ((offset == null) != (limit == null)) {
                    throw new RuntimeException("offset and limit must be provided together");
                }

                var isWindowed = offset != null && limit != null;
                var totalCount = new AtomicReference<Integer>(null);

                if (
                    isWindowed &&
                    (offset == null || limit == null)
                ) {
                    throw new RuntimeException("offset and limit must be numbers");
                }

                var callbacks = callback != null
                    ? new ArrayList<LiveQueryCallback<T>>(List.of(callback))
                    : new ArrayList<LiveQueryCallback<T>>();
                var id = utils.uuid().replace("-", "");
                var dead = new AtomicBoolean(false);

                var results = new AtomicReference<LiveQueryResults<T>>();

                var unsubList = new AtomicReference<List<Function<Transaction, CompletableFuture<Void>>>>();
                var offsetRef = new AtomicReference<Integer>(offset);
                var limitRef = new AtomicReference<Integer>(limit);
                var refreshRef = new AtomicReference<AsyncFunction<Void>>();

                var init = (Supplier<CompletableFuture<Void>>) () ->
                    pg.transaction(
                        tx -> {
                            // Create a temporary view with the query
                            var formattedQueryFuture = (params != null && params.length > 0)
                                ? utils.formatQuery(pg, query, params, tx)
                                : CompletableFuture.completedFuture(query);

                            return formattedQueryFuture.thenCompose(
                                formattedQuery -> tx.exec(
                                    "CREATE OR REPLACE TEMP VIEW live_query_" + id + "_view AS " + formattedQuery,
                                    null
                                )
                            ).thenCompose(
                                ignored -> getTablesForView(tx, "live_query_" + id + "_view")
                            ).thenCompose(
                                tables -> addNotifyTriggersToTables(
                                    tx,
                                    tables,
                                    tableNotifyTriggersAdded
                                ).thenCompose(
                                    ignored -> {
                                        var setupQuery = (CompletableFuture<Void>) null;
                                        if (isWindowed) {
                                            setupQuery = tx.exec(
                                                """
                                                PREPARE live_query_%s_get(int, int) AS
                                                SELECT * FROM live_query_%s_view
                                                LIMIT $1 OFFSET $2;
                                                """.formatted(id, id),
                                                null
                                            ).thenCompose(
                                                ignoredExec -> tx.exec(
                                                    """
                                                    PREPARE live_query_%s_get_total_count AS
                                                    SELECT COUNT(*) FROM live_query_%s_view;
                                                    """.formatted(id, id),
                                                    null
                                                )
                                            ).thenCompose(
                                                ignoredExec -> tx.query(
                                                    "EXECUTE live_query_" + id + "_get_total_count;",
                                                    null,
                                                    null
                                                )
                                            ).thenCompose(
                                                totalCountResult -> {
                                                    var countRow = (Map<?, ?>) totalCountResult.rows.get(0);
                                                    totalCount.set(((Number) countRow.get("count")).intValue());
                                                    return tx.query(
                                                        "EXECUTE live_query_" + id + "_get("
                                                            + limitRef.get() + ", "
                                                            + offsetRef.get() + ");",
                                                        null,
                                                        null
                                                    ).thenApply(
                                                        queryResult -> {
                                                            var liveResults =
                                                                index.<T>toLiveQueryResults(queryResult);
                                                            liveResults.offset = offsetRef.get();
                                                            liveResults.limit = limitRef.get();
                                                            liveResults.totalCount = totalCount.get();
                                                            results.set(liveResults);
                                                            return null;
                                                        }
                                                    );
                                                }
                                            );
                                        } else {
                                            setupQuery = tx.exec(
                                                """
                                                PREPARE live_query_%s_get AS
                                                SELECT * FROM live_query_%s_view;
                                                """.formatted(id, id),
                                                null
                                            ).thenCompose(
                                                ignoredExec -> tx.query(
                                                    "EXECUTE live_query_" + id + "_get;",
                                                    null,
                                                    null
                                                )
                                            ).thenApply(
                                                queryResult -> {
                                                    results.set(
                                                        index.<T>toLiveQueryResults(queryResult)
                                                    );
                                                    return null;
                                                }
                                            );
                                        }

                                        return setupQuery.thenCompose(
                                            ignoredSetup -> {
                                                var listenFutures =
                                                    new ArrayList<CompletableFuture<Function<Transaction, CompletableFuture<Void>>>>();
                                                for (var table : tables) {
                                                    listenFutures.add(
                                                        tx.listen(
                                                            "\"table_change__" + table.schema_oid + "__" + table.table_oid + "\"",
                                                            ignoredListen -> {
                                                                var refreshFn = refreshRef.get();
                                                                if (refreshFn != null) {
                                                                    refreshFn.apply();
                                                                }
                                                            }
                                                        )
                                                    );
                                                }
                                                var combined = CompletableFuture.allOf(
                                                    listenFutures.toArray(new CompletableFuture[0])
                                                );
                                                return combined.thenApply(
                                                    ignoredCombined -> {
                                                        var list =
                                                            new ArrayList<Function<Transaction, CompletableFuture<Void>>>(
                                                                listenFutures.size()
                                                            );
                                                        for (var future : listenFutures) {
                                                            list.add(future.join());
                                                        }
                                                        unsubList.set(list);
                                                        return null;
                                                    }
                                                );
                                            }
                                        );
                                    }
                                )
                            );
                        }
                    );

                return init.get().thenCompose(
                    ignoredInit -> {
                        // Function to refresh the query
                        var refreshFn = utils.debounceMutex(
                            (Object... args) -> {
                                var refreshOptions =
                                    args.length > 0 ? (RefreshOptions) args[0] : null;
                                var newOffset =
                                    refreshOptions != null ? refreshOptions.offset : null;
                                var newLimit =
                                    refreshOptions != null ? refreshOptions.limit : null;

                                // We can optionally provide new offset and limit values to refresh with
                                if (
                                    !isWindowed &&
                                    (newOffset != null || newLimit != null)
                                ) {
                                    throw new RuntimeException(
                                        "offset and limit cannot be provided for non-windowed queries"
                                    );
                                }
                                if (
                                    (newOffset != null
                                        && newOffset != 0
                                        && Double.isNaN(newOffset.doubleValue()))
                                        || (newLimit != null
                                            && newLimit != 0
                                            && Double.isNaN(newLimit.doubleValue()))
                                ) {
                                    throw new RuntimeException("offset and limit must be numbers");
                                }
                                offsetRef.set(newOffset != null ? newOffset : offsetRef.get());
                                limitRef.set(newLimit != null ? newLimit : limitRef.get());

                                var run = new Function<Integer, CompletableFuture<Void>>() {
                                    @Override
                                    public CompletableFuture<Void> apply(Integer count) {
                                        if (callbacks.isEmpty()) {
                                            return CompletableFuture.completedFuture(null);
                                        }
                                        var queryFuture = isWindowed
                                            ? pg.query(
                                                "EXECUTE live_query_" + id + "_get("
                                                    + limitRef.get() + ", "
                                                    + offsetRef.get() + ");",
                                                null,
                                                null
                                            )
                                            : pg.query(
                                                "EXECUTE live_query_" + id + "_get;",
                                                null,
                                                null
                                            );

                                        return queryFuture.<CompletableFuture<Void>>handle(
                                            (queryResult, error) -> {
                                                if (error != null) {
                                                    var cause = error instanceof CompletionException
                                                        ? error.getCause()
                                                        : error;
                                                    var msg = cause.getMessage();
                                                    if (
                                                        msg != null
                                                            && msg.startsWith("prepared statement \"live_query_" + id)
                                                            && msg.endsWith("does not exist")
                                                    ) {
                                                        // If the prepared statement does not exist, reset and try again
                                                        // This can happen if using the multi-tab worker
                                                        if (count > MAX_RETRIES) {
                                                            throw new CompletionException(cause);
                                                        }
                                                        return init.get().thenCompose(
                                                            ignoredReinit -> apply(count + 1)
                                                        );
                                                    }
                                                    throw new CompletionException(cause);
                                                }
                                                var liveResults =
                                                    index.<T>toLiveQueryResults(queryResult);
                                                if (isWindowed) {
                                                    liveResults.offset = offsetRef.get();
                                                    liveResults.limit = limitRef.get();
                                                    liveResults.totalCount = totalCount.get();
                                                }
                                                results.set(liveResults);
                                                runResultCallbacks(callbacks, liveResults);
                                                if (!isWindowed) {
                                                    return CompletableFuture.completedFuture(null);
                                                }
                                                return pg.query(
                                                    "EXECUTE live_query_" + id + "_get_total_count;",
                                                    null,
                                                    null
                                                ).thenCompose(
                                                    countResult -> {
                                                        var countRow = (Map<?, ?>) countResult.rows.get(0);
                                                        var newTotalCount =
                                                            ((Number) countRow.get("count")).intValue();
                                                        if (
                                                            !Objects.equals(newTotalCount, totalCount.get())
                                                        ) {
                                                            // The total count has changed, refresh the query
                                                            totalCount.set(newTotalCount);
                                                            var currentRefresh = refreshRef.get();
                                                            return currentRefresh != null
                                                                ? currentRefresh.apply()
                                                                : CompletableFuture.completedFuture(null);
                                                        }
                                                        return CompletableFuture.completedFuture(null);
                                                    }
                                                );
                                            }
                                        ).thenCompose(handled -> handled);
                                    }
                                };
                                return run.apply(0);
                            }
                        );
                        refreshRef.set(refreshFn);

                        // Function to subscribe to the query
                        var subscribe = (interface_.LiveQuerySubscribe<T>) cb -> {
                            if (dead.get()) {
                                throw new RuntimeException(
                                    "Live query is no longer active and cannot be subscribed to"
                                );
                            }
                            callbacks.add(cb);
                        };

                        // Function to unsubscribe from the query
                        // If no function is provided, unsubscribe all callbacks
                        // If there are no callbacks, unsubscribe from the notify triggers
                        var unsubscribe = (interface_.LiveQueryUnsubscribe<T>) cb -> {
                            if (cb != null) {
                                var filtered = callbacks.stream()
                                    .filter(item -> item != cb)
                                    .toList();
                                callbacks.clear();
                                callbacks.addAll(filtered);
                            } else {
                                callbacks.clear();
                            }
                            if (callbacks.isEmpty() && !dead.get()) {
                                dead.set(true);
                                return pg.transaction(
                                    tx -> {
                                        var unsubFutures = new ArrayList<CompletableFuture<Void>>();
                                        var list = unsubList.get();
                                        if (list != null) {
                                            for (var unsub : list) {
                                                unsubFutures.add(unsub.apply(tx));
                                            }
                                        }
                                        return CompletableFuture.allOf(
                                            unsubFutures.toArray(new CompletableFuture[0])
                                        ).thenCompose(
                                            ignoredUnsub -> tx.exec(
                                                """
                                                DROP VIEW IF EXISTS live_query_%s_view;
                                                DEALLOCATE live_query_%s_get;
                                                """.formatted(id, id),
                                                null
                                            )
                                        ).thenApply(
                                            ignoredExec -> null
                                        );
                                    }
                                );
                            }
                            return CompletableFuture.completedFuture(null);
                        };

                        var abortFuture = CompletableFuture.<Void>completedFuture(null);
                        // If the signal has already been aborted, unsubscribe
                        if (signal != null && signal.aborted()) {
                            abortFuture = unsubscribe.apply(null);
                        } else if (signal != null) {
                            // Add an event listener to unsubscribe if the signal is aborted
                            var opts = new AddEventListenerOptions();
                            opts.once = true;
                            signal.addEventListener(
                                "abort",
                                () -> unsubscribe.apply(null),
                                opts
                            );
                        }

                        return abortFuture.thenApply(
                            ignoredAbort -> {
                                // Run the callback with the initial results
                                runResultCallbacks(callbacks, results.get());

                                // Return the initial results
                                var liveQuery = new LiveQuery<T>();
                                liveQuery.initialResults = results.get();
                                liveQuery.subscribe = subscribe;
                                liveQuery.unsubscribe = unsubscribe;
                                liveQuery.refresh = refreshOptions ->
                                    refreshFn.apply(refreshOptions);
                                return liveQuery;
                            }
                        );
                    }
                );
            }

            @Override
            public <T> CompletableFuture<LiveChanges<T>> changes(
                String query,
                Object[] params,
                String key,
                LiveChangesCallback<T> callback
            ) {
                var options = new LiveChangesOptions<T>();
                options.query = query;
                options.params = params;
                options.key = key;
                options.callback = callback;
                return changes(options);
            }

            @Override
            public <T> CompletableFuture<LiveChanges<T>> changes(
                LiveChangesOptions<T> options
            ) {
                var signal = options != null ? options.signal : null;
                var params = options != null ? options.params : null;
                var key = options != null ? options.key : null;
                var callback = options != null ? options.callback : null;
                var query = options != null ? options.query : null;
                if (key == null) {
                    throw new RuntimeException("key is required for changes queries");
                }
                var callbacks = callback != null
                    ? new ArrayList<LiveChangesCallback<T>>(List.of(callback))
                    : new ArrayList<LiveChangesCallback<T>>();
                var id = utils.uuid().replace("-", "");
                var dead = new AtomicBoolean(false);

                var stateSwitch = new AtomicInteger(1);
                var changes = new AtomicReference<Results>();

                var unsubList = new AtomicReference<List<Function<Transaction, CompletableFuture<Void>>>>();
                var refreshRef = new AtomicReference<AsyncFunction<Void>>();

                var init = (Supplier<CompletableFuture<Void>>) () ->
                    pg.transaction(
                        tx -> {
                            // Create a temporary view with the query
                            return utils.formatQuery(pg, query, params, tx).thenCompose(
                                formattedQuery -> tx.query(
                                    "CREATE OR REPLACE TEMP VIEW live_query_" + id + "_view AS " + formattedQuery,
                                    null,
                                    null
                                )
                            ).thenCompose(
                                ignored -> getTablesForView(tx, "live_query_" + id + "_view")
                            ).thenCompose(
                                tables -> addNotifyTriggersToTables(
                                    tx,
                                    tables,
                                    tableNotifyTriggersAdded
                                ).thenCompose(
                                    ignored -> tx.query(
                                        """
                                        SELECT column_name, data_type, udt_name
                                        FROM information_schema.columns
                                        WHERE table_name = 'live_query_%s_view'
                                        """.formatted(id),
                                        null,
                                        null
                                    ).thenCompose(
                                        columnsResult -> {
                                            var columns = new ArrayList<Map<String, Object>>();
                                            for (var rowObj : columnsResult.rows) {
                                                @SuppressWarnings("unchecked")
                                                var row = (Map<String, Object>) rowObj;
                                                columns.add(row);
                                            }
                                            var afterColumn = new HashMap<String, Object>();
                                            afterColumn.put("column_name", "__after__");
                                            afterColumn.put("data_type", "integer");
                                            columns.add(afterColumn);

                                            // Init state tables as empty temp table
                                            return tx.exec(
                                                """
                                                CREATE TEMP TABLE live_query_%s_state1 (LIKE live_query_%s_view INCLUDING ALL);
                                                CREATE TEMP TABLE live_query_%s_state2 (LIKE live_query_%s_view INCLUDING ALL);
                                                """.formatted(id, id, id, id),
                                                null
                                            ).thenCompose(
                                                ignoredExec -> {
                                                    // Create Diff views and prepared statements
                                                    var diffFutures = new ArrayList<CompletableFuture<Void>>();
                                                    for (var curr = 1; curr <= 2; curr++) {
                                                        var prev = curr == 1 ? 2 : 1;
                                                        var diffSql =
                                                            """
                                                            PREPARE live_query_%s_diff%s AS
                                                            WITH
                                                              prev AS (SELECT LAG("%s") OVER () as __after__, * FROM live_query_%s_state%s),
                                                              curr AS (SELECT LAG("%s") OVER () as __after__, * FROM live_query_%s_state%s),
                                                              data_diff AS (
                                                                -- INSERT operations: Include all columns
                                                                SELECT
                                                                  'INSERT' AS __op__,
                                                                  %s,
                                                                  ARRAY[]::text[] AS __changed_columns__
                                                                FROM curr
                                                                LEFT JOIN prev ON curr.%s = prev.%s
                                                                WHERE prev.%s IS NULL
                                                              UNION ALL
                                                                -- DELETE operations: Include only the primary key
                                                                SELECT
                                                                  'DELETE' AS __op__,
                                                                  %s,
                                                                  ARRAY[]::text[] AS __changed_columns__
                                                                FROM prev
                                                                LEFT JOIN curr ON prev.%s = curr.%s
                                                                WHERE curr.%s IS NULL
                                                              UNION ALL
                                                                -- UPDATE operations: Include only changed columns
                                                                SELECT
                                                                  'UPDATE' AS __op__,
                                                                  %s,
                                                                  ARRAY(SELECT unnest FROM unnest(ARRAY[%s]) WHERE unnest IS NOT NULL) AS __changed_columns__
                                                                FROM curr
                                                                INNER JOIN prev ON curr.%s = prev.%s
                                                                WHERE NOT (curr IS NOT DISTINCT FROM prev)
                                                              )
                                                            SELECT * FROM data_diff;
                                                            """.formatted(
                                                                id,
                                                                curr,
                                                                key,
                                                                id,
                                                                prev,
                                                                key,
                                                                id,
                                                                curr,
                                                                columnsForInsert(columns),
                                                                key,
                                                                key,
                                                                key,
                                                                columnsForDelete(columns, key),
                                                                key,
                                                                key,
                                                                key,
                                                                columnsForUpdate(columns, key),
                                                                columnsForChanged(columns, key),
                                                                key,
                                                                key
                                                            );
                                                        diffFutures.add(
                                                            tx.exec(diffSql, null).thenApply(
                                                                ignoredDiff -> null
                                                            )
                                                        );
                                                    }
                                                    var combined = CompletableFuture.allOf(
                                                        diffFutures.toArray(new CompletableFuture[0])
                                                    );
                                                    return combined.thenCompose(
                                                        ignoredDiffs -> {
                                                            var listenFutures =
                                                                new ArrayList<CompletableFuture<Function<Transaction, CompletableFuture<Void>>>>();
                                                            for (var table : tables) {
                                                                listenFutures.add(
                                                                    tx.listen(
                                                                        "\"table_change__" + table.schema_oid + "__" + table.table_oid + "\"",
                                                                        ignoredListen -> {
                                                                            var refreshFn = refreshRef.get();
                                                                            if (refreshFn != null) {
                                                                                refreshFn.apply();
                                                                            }
                                                                        }
                                                                    )
                                                                );
                                                            }
                                                            var combinedListen = CompletableFuture.allOf(
                                                                listenFutures.toArray(new CompletableFuture[0])
                                                            );
                                                            return combinedListen.thenApply(
                                                                ignoredCombined -> {
                                                                    var list =
                                                                        new ArrayList<Function<Transaction, CompletableFuture<Void>>>(
                                                                            listenFutures.size()
                                                                        );
                                                                    for (var future : listenFutures) {
                                                                        list.add(future.join());
                                                                    }
                                                                    unsubList.set(list);
                                                                    return null;
                                                                }
                                                            );
                                                        }
                                                    );
                                                }
                                            );
                                        }
                                    )
                                )
                            );
                        }
                    );

                return init.get().thenCompose(
                    ignoredInit -> {
                        var refreshFn = utils.debounceMutex(
                            (Object... args) -> {
                                if (callbacks.isEmpty() && changes.get() != null) {
                                    return CompletableFuture.<Void>completedFuture(null);
                                }
                                var reset = new AtomicBoolean(false);

                                var runAttempt = new Function<Integer, CompletableFuture<Void>>() {
                                    @Override
                                    public CompletableFuture<Void> apply(Integer attempt) {
                                        if (attempt >= 5) {
                                            return CompletableFuture.completedFuture(null);
                                        }
                                        return pg.transaction(
                                            tx -> tx.exec(
                                                """
                                                INSERT INTO live_query_%s_state%s
                                                  SELECT * FROM live_query_%s_view;
                                                """.formatted(id, stateSwitch.get(), id),
                                                null
                                            ).thenCompose(
                                                ignoredExec -> tx.query(
                                                    "EXECUTE live_query_" + id + "_diff" + stateSwitch.get() + ";",
                                                    null,
                                                    null
                                                )
                                            ).thenCompose(
                                                changesResult -> {
                                                    changes.set(changesResult);
                                                    stateSwitch.set(stateSwitch.get() == 1 ? 2 : 1);
                                                    return tx.exec(
                                                        """
                                                        TRUNCATE live_query_%s_state%s;
                                                        """.formatted(id, stateSwitch.get()),
                                                        null
                                                    );
                                                }
                                            ).thenApply(
                                                ignoredExec -> null
                                            )
                                        ).<CompletableFuture<Void>>handle(
                                            (ignored, error) -> {
                                                if (error != null) {
                                                    var cause = error instanceof CompletionException
                                                        ? error.getCause()
                                                        : error;
                                                    var msg = cause.getMessage();
                                                    if (
                                                        msg != null
                                                            && msg.equals(
                                                                "relation \"live_query_" + id + "_state"
                                                                    + stateSwitch.get() + "\" does not exist"
                                                            )
                                                    ) {
                                                        // If the state table does not exist, reset and try again
                                                        // This can happen if using the multi-tab worker
                                                        reset.set(true);
                                                        return init.get().thenCompose(
                                                            ignoredInit -> apply(attempt + 1)
                                                        );
                                                    }
                                                    throw new CompletionException(cause);
                                                }
                                                return CompletableFuture.completedFuture(null);
                                            }
                                        ).thenCompose(handled -> handled);
                                    }
                                };

                                return runAttempt.apply(0).thenApply(
                                    ignored -> {
                                        var changesList = new ArrayList<Change<T>>();
                                        if (reset.get()) {
                                            var resetChange = new Change<T>();
                                            resetChange.put("__op__", "RESET");
                                            changesList.add(resetChange);
                                        }
                                        if (changes.get() != null) {
                                            for (var rowObj : changes.get().rows) {
                                                @SuppressWarnings("unchecked")
                                                var row = (Change<T>) rowObj;
                                                changesList.add(row);
                                            }
                                        }
                                        runChangeCallbacks(callbacks, changesList);
                                        return (Void) null;
                                    }
                                );
                            }
                        );
                        refreshRef.set(refreshFn);

                        // Function to subscribe to the query
                        var subscribe = (interface_.LiveChangesSubscribe<T>) cb -> {
                            if (dead.get()) {
                                throw new RuntimeException(
                                    "Live query is no longer active and cannot be subscribed to"
                                );
                            }
                            callbacks.add(cb);
                        };

                        // Function to unsubscribe from the query
                        var unsubscribe = (interface_.LiveChangesUnsubscribe<T>) cb -> {
                            if (cb != null) {
                                var filtered = callbacks.stream()
                                    .filter(item -> item != cb)
                                    .toList();
                                callbacks.clear();
                                callbacks.addAll(filtered);
                            } else {
                                callbacks.clear();
                            }
                            if (callbacks.isEmpty() && !dead.get()) {
                                dead.set(true);
                                return pg.transaction(
                                    tx -> {
                                        var unsubFutures = new ArrayList<CompletableFuture<Void>>();
                                        var list = unsubList.get();
                                        if (list != null) {
                                            for (var unsub : list) {
                                                unsubFutures.add(unsub.apply(tx));
                                            }
                                        }
                                        return CompletableFuture.allOf(
                                            unsubFutures.toArray(new CompletableFuture[0])
                                        ).thenCompose(
                                            ignoredUnsub -> tx.exec(
                                                """
                                                DROP VIEW IF EXISTS live_query_%s_view;
                                                DROP TABLE IF EXISTS live_query_%s_state1;
                                                DROP TABLE IF EXISTS live_query_%s_state2;
                                                DEALLOCATE live_query_%s_diff1;
                                                DEALLOCATE live_query_%s_diff2;
                                                """.formatted(id, id, id, id, id),
                                                null
                                            )
                                        ).thenApply(
                                            ignoredExec -> null
                                        );
                                    }
                                );
                            }
                            return CompletableFuture.completedFuture(null);
                        };

                        var abortFuture = CompletableFuture.<Void>completedFuture(null);
                        // If the signal has already been aborted, unsubscribe
                        if (signal != null && signal.aborted()) {
                            abortFuture = unsubscribe.apply(null);
                        } else if (signal != null) {
                            // Add an event listener to unsubscribe if the signal is aborted
                            var opts = new AddEventListenerOptions();
                            opts.once = true;
                            signal.addEventListener(
                                "abort",
                                () -> unsubscribe.apply(null),
                                opts
                            );
                        }

                        // Run the callback with the initial changes
                        return abortFuture.thenCompose(
                            ignoredAbort -> refreshFn.apply()
                        ).thenApply(
                            ignored -> {
                                // Fields
                                var fields = filterChangeFields(
                                    changes.get() != null ? changes.get().fields : null
                                );

                                // Return the initial results
                                var liveChanges = new LiveChanges<T>();
                                liveChanges.fields = fields;
                                liveChanges.initialChanges =
                                    changes.get() != null
                                        ? castChanges(changes.get().rows)
                                        : new ArrayList<>();
                                liveChanges.subscribe = subscribe;
                                liveChanges.unsubscribe = unsubscribe;
                                liveChanges.refresh = () -> refreshFn.apply();
                                return liveChanges;
                            }
                        );
                    }
                );
            }

            @Override
            public <T> CompletableFuture<LiveQuery<T>> incrementalQuery(
                String query,
                Object[] params,
                String key,
                LiveQueryCallback<T> callback
            ) {
                var options = new LiveIncrementalQueryOptions<T>();
                options.query = query;
                options.params = params;
                options.key = key;
                options.callback = callback;
                return incrementalQuery(options);
            }

            @Override
            public <T> CompletableFuture<LiveQuery<T>> incrementalQuery(
                LiveIncrementalQueryOptions<T> options
            ) {
                var signal = options != null ? options.signal : null;
                var params = options != null ? options.params : null;
                var key = options != null ? options.key : null;
                var callback = options != null ? options.callback : null;
                var query = options != null ? options.query : null;
                if (key == null) {
                    throw new RuntimeException("key is required for incremental queries");
                }
                var callbacks = callback != null
                    ? new ArrayList<LiveQueryCallback<T>>(List.of(callback))
                    : new ArrayList<LiveQueryCallback<T>>();
                var rowsMap = new HashMap<Object, Map<String, Object>>();
                var afterMap = new HashMap<Object, Object>();
                var lastRows = new AtomicReference<List<T>>(new ArrayList<>());
                var firstRun = new AtomicBoolean(true);
                var fieldsRef = new AtomicReference<List<Results.Field>>();

                return changes(query, params, key, changes -> {
                    // Process the changes
                    for (var change : changes) {
                        var op = (String) change.get("__op__");
                        @SuppressWarnings("unchecked")
                        var changedColumns =
                            (List<String>) change.get("__changed_columns__");
                        var obj = new HashMap<String, Object>(change);
                        obj.remove("__op__");
                        obj.remove("__changed_columns__");
                        switch (op) {
                            case "RESET":
                                rowsMap.clear();
                                afterMap.clear();
                                break;
                            case "INSERT":
                                rowsMap.put(obj.get(key), obj);
                                afterMap.put(obj.get("__after__"), obj.get(key));
                                break;
                            case "DELETE": {
                                var oldObj = rowsMap.get(obj.get(key));
                                rowsMap.remove(obj.get(key));
                                // null is the starting point, we don't delete it as another insert
                                // may have happened thats replacing it
                                if (oldObj != null && oldObj.get("__after__") != null) {
                                    afterMap.remove(oldObj.get("__after__"));
                                }
                                break;
                            }
                            case "UPDATE": {
                                var newObj = new HashMap<String, Object>(
                                    rowsMap.getOrDefault(obj.get(key), new HashMap<>())
                                );
                                if (changedColumns != null) {
                                    for (var columnName : changedColumns) {
                                        newObj.put(columnName, obj.get(columnName));
                                        if ("__after__".equals(columnName)) {
                                            afterMap.put(obj.get("__after__"), obj.get(key));
                                        }
                                    }
                                }
                                rowsMap.put(obj.get(key), newObj);
                                break;
                            }
                            default:
                                break;
                        }
                    }

                    // Get the rows in order
                    var rows = new ArrayList<T>();
                    var lastKey = (Object) null;
                    for (var i = 0; i < rowsMap.size(); i++) {
                        var nextKey = afterMap.get(lastKey);
                        var obj = rowsMap.get(nextKey);
                        if (obj == null) {
                            break;
                        }
                        // Remove the __after__ key from the exposed row
                        var cleanObj = new HashMap<>(obj);
                        cleanObj.remove("__after__");
                        @SuppressWarnings("unchecked")
                        var typedObj = (T) cleanObj;
                        rows.add(typedObj);
                        lastKey = nextKey;
                    }
                    lastRows.set(rows);

                    // Run the callbacks
                    if (!firstRun.get()) {
                        var result = new Results();
                        result.rows = new ArrayList<>((List<Object>) (List<?>) rows);
                        result.fields = fieldsRef.get();
                        runResultCallbacks(callbacks, result);
                    }
                }).thenCompose(
                    changesResult -> {
                        fieldsRef.set(changesResult.fields);
                        var unsubscribeChanges = changesResult.unsubscribe;
                        var refresh = changesResult.refresh;

                        firstRun.set(false);
                        var initialResult = new Results();
                        initialResult.rows =
                            new ArrayList<>((List<Object>) (List<?>) lastRows.get());
                        initialResult.fields = fieldsRef.get();
                        runResultCallbacks(callbacks, initialResult);

                        var subscribe = (interface_.LiveQuerySubscribe<T>) callbacks::add;

                        var unsubscribe = (interface_.LiveQueryUnsubscribe<T>) cb -> {
                            if (cb != null) {
                                var filtered = callbacks.stream()
                                    .filter(item -> item != cb)
                                    .toList();
                                callbacks.clear();
                                callbacks.addAll(filtered);
                            } else {
                                callbacks.clear();
                            }
                            if (callbacks.isEmpty()) {
                                return unsubscribeChanges.apply(null);
                            }
                            return CompletableFuture.completedFuture(null);
                        };

                        var abortFuture = CompletableFuture.<Void>completedFuture(null);
                        if (signal != null && signal.aborted()) {
                            abortFuture = unsubscribe.apply(null);
                        } else if (signal != null) {
                            var opts = new AddEventListenerOptions();
                            opts.once = true;
                            signal.addEventListener(
                                "abort",
                                () -> unsubscribe.apply(null),
                                opts
                            );
                        }

                        return abortFuture.thenApply(
                            ignoredAbort -> {
                                var liveQuery = new LiveQuery<T>();
                                var initialResults = new LiveQueryResults<T>();
                                initialResults.rows =
                                    new ArrayList<>((List<Object>) (List<?>) lastRows.get());
                                initialResults.fields = fieldsRef.get();
                                liveQuery.initialResults = initialResults;
                                liveQuery.subscribe = subscribe;
                                liveQuery.unsubscribe = unsubscribe;
                                liveQuery.refresh = refreshOptions -> refresh.apply();
                                return liveQuery;
                            }
                        );
                    }
                );
            }
        };

        var result = new ExtensionSetupResult<LiveNamespace>();
        result.emscriptenOpts = emscriptenOpts;
        result.namespaceObj = namespaceObj;
        return CompletableFuture.completedFuture(result);
    }

    public static final Extension<LiveNamespace> live = new Extension<>();

    static {
        live.name = "Live Queries";
        live.setup = (pg, emscriptenOpts, clientOnly) -> setup(pg, emscriptenOpts);
    }

    public interface PGliteWithLive extends PGliteInterface<Extensions> {
        LiveNamespace live();
    }

    /**
     * Get a list of all the tables used in a view, recursively
     * @param tx a transaction or PGlite instance
     * @param viewName the name of the view
     * @returns list of tables used in the view
     */
    private static CompletableFuture<List<TableInfo>> getTablesForView(
        Transaction tx,
        String viewName
    ) {
        return tx.query(
            """
            WITH RECURSIVE view_dependencies AS (
              -- Base case: Get the initial view's dependencies
              SELECT DISTINCT
                cl.relname AS dependent_name,
                n.nspname AS schema_name,
                cl.oid AS dependent_oid,
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

              -- Recursive case: Traverse dependencies for views
              SELECT DISTINCT
                cl.relname AS dependent_name,
                n.nspname AS schema_name,
                cl.oid AS dependent_oid,
                n.oid AS schema_oid,
                cl.relkind = 'v' AS is_view
              FROM view_dependencies vd
              JOIN pg_rewrite r ON vd.dependent_name = (
                SELECT relname FROM pg_class WHERE oid = r.ev_class AND relkind = 'v'
              )
              JOIN pg_depend d ON r.oid = d.objid
              JOIN pg_class cl ON d.refobjid = cl.oid
              JOIN pg_namespace n ON cl.relnamespace = n.oid
              WHERE d.deptype = 'n'
            )
            SELECT DISTINCT
              dependent_name AS table_name,
              schema_name,
              dependent_oid AS table_oid,
              schema_oid
            FROM view_dependencies
            WHERE NOT is_view; -- Exclude intermediate views
            """,
            new Object[] { viewName },
            null
        ).thenApply(
            result -> {
                var tables = new ArrayList<TableInfo>();
                for (var rowObj : result.rows) {
                    var row = (Map<?, ?>) rowObj;
                    var table = new TableInfo();
                    table.table_name = (String) row.get("table_name");
                    table.schema_name = (String) row.get("schema_name");
                    table.table_oid = ((Number) row.get("table_oid")).intValue();
                    table.schema_oid = ((Number) row.get("schema_oid")).intValue();
                    tables.add(table);
                }
                return tables;
            }
        );
    }

    /**
     * Add triggers to tables to notify when they change
     * @param tx a transaction or PGlite instance
     * @param tables list of tables to add triggers to
     */
    private static CompletableFuture<Void> addNotifyTriggersToTables(
        Transaction tx,
        List<TableInfo> tables,
        Set<String> tableNotifyTriggersAdded
    ) {
        var triggers = new StringBuilder();
        for (var table : tables) {
            var key = table.schema_oid + "_" + table.table_oid;
            if (tableNotifyTriggersAdded.contains(key)) {
                continue;
            }
            triggers.append(
                """
                CREATE OR REPLACE FUNCTION "_notify_%s_%s"() RETURNS TRIGGER AS $$
                BEGIN
                  PERFORM pg_notify('table_change__%s__%s', '');
                  RETURN NULL;
                END;
                $$ LANGUAGE plpgsql;
                CREATE OR REPLACE TRIGGER "_notify_trigger_%s_%s"
                AFTER INSERT OR UPDATE OR DELETE ON "%s"."%s"
                FOR EACH STATEMENT EXECUTE FUNCTION "_notify_%s_%s"();
                """
                    .formatted(
                        table.schema_oid,
                        table.table_oid,
                        table.schema_oid,
                        table.table_oid,
                        table.schema_oid,
                        table.table_oid,
                        table.schema_name,
                        table.table_name,
                        table.schema_oid,
                        table.table_oid
                    )
            );
        }
        for (var table : tables) {
            tableNotifyTriggersAdded.add(table.schema_oid + "_" + table.table_oid);
        }
        if (!triggers.toString().trim().isEmpty()) {
            return tx.exec(triggers.toString(), null).thenApply(ignored -> null);
        }
        return CompletableFuture.completedFuture(null);
    }

    private static <T> LiveQueryResults<T> toLiveQueryResults(Results results) {
        var liveResults = new LiveQueryResults<T>();
        if (results != null) {
            liveResults.rows = results.rows;
            liveResults.fields = results.fields;
            liveResults.affectedRows = results.affectedRows;
            liveResults.blob = results.blob;
        }
        return liveResults;
    }

    private static List<Results.Field> filterChangeFields(
        List<Results.Field> fields
    ) {
        var filtered = new ArrayList<Results.Field>();
        if (fields == null) {
            return filtered;
        }
        for (var field : fields) {
            if (
                !List.of("__after__", "__op__", "__changed_columns__")
                    .contains(field.name)
            ) {
                filtered.add(field);
            }
        }
        return filtered;
    }

    private static <T> List<Change<T>> castChanges(List<Object> rows) {
        var casted = new ArrayList<Change<T>>();
        if (rows == null) {
            return casted;
        }
        for (var rowObj : rows) {
            @SuppressWarnings("unchecked")
            var row = (Change<T>) rowObj;
            casted.add(row);
        }
        return casted;
    }

    private static String columnsForInsert(List<Map<String, Object>> columns) {
        var parts = new ArrayList<String>();
        for (var column : columns) {
            var columnName = (String) column.get("column_name");
            parts.add("curr.\"" + columnName + "\" AS \"" + columnName + "\"");
        }
        return String.join(",\n", parts);
    }

    private static String columnsForDelete(
        List<Map<String, Object>> columns,
        String key
    ) {
        var parts = new ArrayList<String>();
        for (var column : columns) {
            var columnName = (String) column.get("column_name");
            var dataType = (String) column.get("data_type");
            var udtName = (String) column.get("udt_name");
            if (columnName.equals(key)) {
                parts.add("prev.\"" + columnName + "\" AS \"" + columnName + "\"");
            } else {
                var cast = "USER-DEFINED".equals(dataType) && udtName != null
                    ? "::" + udtName
                    : "";
                parts.add("NULL" + cast + " AS \"" + columnName + "\"");
            }
        }
        return String.join(",\n", parts);
    }

    private static String columnsForUpdate(
        List<Map<String, Object>> columns,
        String key
    ) {
        var parts = new ArrayList<String>();
        for (var column : columns) {
            var columnName = (String) column.get("column_name");
            var dataType = (String) column.get("data_type");
            var udtName = (String) column.get("udt_name");
            if (columnName.equals(key)) {
                parts.add("curr.\"" + columnName + "\" AS \"" + columnName + "\"");
            } else {
                var cast = "USER-DEFINED".equals(dataType) && udtName != null
                    ? "::" + udtName
                    : "";
                parts.add(
                    """
                    CASE
                      WHEN curr."%s" IS DISTINCT FROM prev."%s"
                      THEN curr."%s"
                      ELSE NULL%s
                      END AS "%s"
                    """.formatted(columnName, columnName, columnName, cast, columnName).trim()
                );
            }
        }
        return String.join(",\n", parts);
    }

    private static String columnsForChanged(
        List<Map<String, Object>> columns,
        String key
    ) {
        var parts = new ArrayList<String>();
        for (var column : columns) {
            var columnName = (String) column.get("column_name");
            if (columnName.equals(key)) {
                continue;
            }
            parts.add(
                """
                CASE
                  WHEN curr."%s" IS DISTINCT FROM prev."%s"
                  THEN '%s'
                  ELSE NULL
                  END
                """.formatted(columnName, columnName, columnName).trim()
            );
        }
        return String.join(", ", parts);
    }

    private static <T> void runResultCallbacks(
        List<LiveQueryCallback<T>> callbacks,
        Results results
    ) {
        for (var callback : callbacks) {
            callback.apply(results);
        }
    }

    private static <T> void runChangeCallbacks(
        List<LiveChangesCallback<T>> callbacks,
        List<Change<T>> changes
    ) {
        for (var callback : callbacks) {
            callback.apply(changes);
        }
    }

    private static final class TableInfo {
        private String table_name;
        private String schema_name;
        private int table_oid;
        private int schema_oid;
    }

    private index() {
    }
}
