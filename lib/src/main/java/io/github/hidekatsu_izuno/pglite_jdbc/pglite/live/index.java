package io.github.hidekatsu_izuno.pglite_jdbc.pglite.live;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Extension;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.ExtensionSetupResult;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.PGliteInterface;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Transaction;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.utils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

public final class index {
    private static final int MAX_RETRIES = 5;

    private static CompletableFuture<ExtensionSetupResult<interface_.LiveNamespace>> setup(
        PGliteInterface pg,
        Object _emscriptenOpts
    ) {
        // The notify triggers are only ever added and never removed
        // Keep track of which triggers have been added to avoid adding them multiple times
        var tableNotifyTriggersAdded = new HashSet<String>();

        var namespaceObj = new interface_.LiveNamespace() {
            @Override
            public <T> CompletableFuture<interface_.LiveQuery<T>> query(
                String query,
                Object[] params,
                interface_.LiveQueryCallback<T> callback
            ) {
                return queryInternal(pg, tableNotifyTriggersAdded, query, params, callback);
            }

            @Override
            public <T> CompletableFuture<interface_.LiveQuery<T>> query(
                interface_.LiveQueryOptions<T> options
            ) {
                return queryInternal(
                    pg,
                    tableNotifyTriggersAdded,
                    options.query,
                    options.params,
                    options.callback,
                    options.signal,
                    options.offset,
                    options.limit
                );
            }

            @Override
            public <T> CompletableFuture<interface_.LiveChanges<T>> changes(
                String query,
                Object[] params,
                String key,
                interface_.LiveChangesCallback<T> callback
            ) {
                return changesInternal(
                    pg,
                    tableNotifyTriggersAdded,
                    query,
                    params,
                    key,
                    callback,
                    null
                );
            }

            @Override
            public <T> CompletableFuture<interface_.LiveChanges<T>> changes(
                interface_.LiveChangesOptions<T> options
            ) {
                return changesInternal(
                    pg,
                    tableNotifyTriggersAdded,
                    options.query,
                    options.params,
                    options.key,
                    options.callback,
                    options.signal
                );
            }

            @Override
            public <T> CompletableFuture<interface_.LiveQuery<T>> incrementalQuery(
                String query,
                Object[] params,
                String key,
                interface_.LiveQueryCallback<T> callback
            ) {
                return incrementalQueryInternal(
                    this,
                    query,
                    params,
                    key,
                    callback,
                    null
                );
            }

            @Override
            public <T> CompletableFuture<interface_.LiveQuery<T>> incrementalQuery(
                interface_.LiveIncrementalQueryOptions<T> options
            ) {
                return incrementalQueryInternal(
                    this,
                    options.query,
                    options.params,
                    options.key,
                    options.callback,
                    options.signal
                );
            }
        };

        var result = new ExtensionSetupResult<interface_.LiveNamespace>();
        result.namespaceObj = namespaceObj;
        return CompletableFuture.completedFuture(result);
    }

    private static <T> CompletableFuture<interface_.LiveQuery<T>> queryInternal(
        PGliteInterface pg,
        Set<String> tableNotifyTriggersAdded,
        String query,
        Object[] params,
        interface_.LiveQueryCallback<T> callback
    ) {
        return queryInternal(
            pg,
            tableNotifyTriggersAdded,
            query,
            params,
            callback,
            null,
            null,
            null
        );
    }

    private static <T> CompletableFuture<interface_.LiveQuery<T>> queryInternal(
        PGliteInterface pg,
        Set<String> tableNotifyTriggersAdded,
        String query,
        Object[] params,
        interface_.LiveQueryCallback<T> callback,
        interface_.AbortSignal signal,
        Integer offset,
        Integer limit
    ) {
        // Offset and limit must be provided together
        if ((offset == null) != (limit == null)) {
            throw new RuntimeException("offset and limit must be provided together");
        }

        var isWindowed = offset != null && limit != null;
        var offsetRef = new Integer[] { offset };
        var limitRef = new Integer[] { limit };
        var totalCountRef = new Integer[1];

        if (
            isWindowed
            && (offset == null || limit == null)
        ) {
            throw new RuntimeException("offset and limit must be numbers");
        }

        var callbacks = new ArrayList<interface_.LiveQueryCallback<T>>();
        if (callback != null) {
            callbacks.add(callback);
        }
        var id = utils.uuid().replace("-", "");
        var dead = new boolean[] { false };

        var resultsHolder = new interface_.LiveQueryResults<T>[1];

        var unsubList = new ArrayList<Function<Transaction, CompletableFuture<Void>>>();

        var refreshRef = new utils.AsyncFunction<Void>[1];

        var init = (Runnable) () -> {
            pg.transaction(
                tx -> {
                    // Create a temporary view with the query
                    var formattedFuture = CompletableFuture.completedFuture(query);
                    if (params != null && params.length > 0) {
                        formattedFuture = formatQuery(pg, query, params, tx);
                    }
                    return formattedFuture.thenCompose(
                        formattedQuery -> {
                            return tx.exec(
                                "CREATE OR REPLACE TEMP VIEW live_query_"
                                    + id
                                    + "_view AS "
                                    + formattedQuery,
                                null
                            ).thenCompose(
                                ignored -> getTablesForView(
                                    tx,
                                    "live_query_" + id + "_view"
                                )
                            ).thenCompose(
                                tables -> addNotifyTriggersToTables(
                                    tx,
                                    tables,
                                    tableNotifyTriggersAdded
                                ).thenCompose(
                                    ignored -> {
                                        if (isWindowed) {
                                            return tx.exec(
                                                "\n              PREPARE live_query_"
                                                    + id
                                                    + "_get(int, int) AS\n              SELECT * FROM live_query_"
                                                    + id
                                                    + "_view\n              LIMIT $1 OFFSET $2;\n            ",
                                                null
                                            ).thenCompose(
                                                ignoredPrepare -> tx.exec(
                                                    "\n              PREPARE live_query_"
                                                        + id
                                                        + "_get_total_count AS\n              SELECT COUNT(*) FROM live_query_"
                                                        + id
                                                        + "_view;\n            ",
                                                    null
                                                )
                                            ).thenCompose(
                                                ignoredPrepare -> tx.query(
                                                    "EXECUTE live_query_"
                                                        + id
                                                        + "_get_total_count;",
                                                    null,
                                                    null
                                                )
                                            ).thenCompose(
                                                countResult -> {
                                                    totalCountRef[0] = extractCount(countResult);
                                                    return tx.query(
                                                        "EXECUTE live_query_"
                                                            + id
                                                            + "_get("
                                                            + limitRef[0]
                                                            + ", "
                                                            + offsetRef[0]
                                                            + ");",
                                                        null,
                                                        null
                                                    ).thenApply(
                                                        queryResult -> {
                                                            var liveResults = new interface_.LiveQueryResults<T>();
                                                            liveResults.rows = queryResult.rows;
                                                            liveResults.fields = queryResult.fields;
                                                            liveResults.affectedRows = queryResult.affectedRows;
                                                            liveResults.blob = queryResult.blob;
                                                            liveResults.offset = offsetRef[0];
                                                            liveResults.limit = limitRef[0];
                                                            liveResults.totalCount = totalCountRef[0];
                                                            resultsHolder[0] = liveResults;
                                                            return liveResults;
                                                        }
                                                    );
                                                }
                                            );
                                        }
                                        return tx.exec(
                                            "\n              PREPARE live_query_"
                                                + id
                                                + "_get AS\n              SELECT * FROM live_query_"
                                                + id
                                                + "_view;\n            ",
                                            null
                                        ).thenCompose(
                                            ignoredPrepare -> tx.query(
                                                "EXECUTE live_query_"
                                                    + id
                                                    + "_get;",
                                                null,
                                                null
                                            ).thenApply(
                                                queryResult -> {
                                                    var liveResults = new interface_.LiveQueryResults<T>();
                                                    liveResults.rows = queryResult.rows;
                                                    liveResults.fields = queryResult.fields;
                                                    liveResults.affectedRows = queryResult.affectedRows;
                                                    liveResults.blob = queryResult.blob;
                                                    resultsHolder[0] = liveResults;
                                                    return liveResults;
                                                }
                                            )
                                        );
                                    }
                                ).thenCompose(
                                    liveResults -> getTablesForView(
                                        tx,
                                        "live_query_" + id + "_view"
                                    ).thenCompose(
                                        tables -> {
                                            var subs = new ArrayList<CompletableFuture<Function<Transaction, CompletableFuture<Void>>>>();
                                            for (var table : tables) {
                                                subs.add(
                                                    tx.listen(
                                                        "\"table_change__"
                                                            + table.schema_oid
                                                            + "__"
                                                            + table.table_oid
                                                            + "\"",
                                                        payload -> {
                                                            refreshRef[0].apply(new interface_.RefreshOptions());
                                                        }
                                                    )
                                                );
                                            }
                                            return CompletableFuture.allOf(
                                                subs.toArray(new CompletableFuture[0])
                                            ).thenApply(
                                                ignoredSubs -> {
                                                    unsubList.clear();
                                                    for (var sub : subs) {
                                                        unsubList.add(sub.join());
                                                    }
                                                    return liveResults;
                                                }
                                            );
                                        }
                                    )
                                )
                            );
                        }
                    );
                }
            ).join();
        };

        var refresh = utils.debounceMutex(
            (Object... args) -> {
                var options = args.length > 0
                    ? (interface_.RefreshOptions) args[0]
                    : null;
                var newOffset = options != null ? options.offset : null;
                var newLimit = options != null ? options.limit : null;
                // We can optionally provide new offset and limit values to refresh with
                if (!isWindowed && (newOffset != null || newLimit != null)) {
                    throw new RuntimeException(
                        "offset and limit cannot be provided for non-windowed queries"
                    );
                }
                if (
                    (newOffset != null) || (newLimit != null)
                ) {
                    offsetRef[0] = newOffset != null ? newOffset : offsetRef[0];
                    limitRef[0] = newLimit != null ? newLimit : limitRef[0];
                }

                return runQueryRefresh(
                    pg,
                    id,
                    isWindowed,
                    callbacks,
                    resultsHolder,
                    offsetRef[0],
                    limitRef[0],
                    totalCountRef,
                    init
                );
            }
        );
        refreshRef[0] = refresh;

        init.run();

        // Function to subscribe to the query
        interface_.LiveQuerySubscribe<T> subscribe = cb -> {
            if (dead[0]) {
                throw new RuntimeException(
                    "Live query is no longer active and cannot be subscribed to"
                );
            }
            callbacks.add(cb);
        };

        // Function to unsubscribe from the query
        // If no function is provided, unsubscribe all callbacks
        // If there are no callbacks, unsubscribe from the notify triggers
        interface_.LiveQueryUnsubscribe<T> unsubscribe = cb -> {
            if (cb != null) {
                var filtered = filterCallbacks(callbacks);
                callbacks.clear();
                callbacks.addAll(filtered);
            } else {
                callbacks.clear();
            }
            if (callbacks.size() == 0 && !dead[0]) {
                dead[0] = true;
                return pg.transaction(
                    tx -> {
                        var unsubFutures = new ArrayList<CompletableFuture<Void>>();
                        for (var unsub : unsubList) {
                            unsubFutures.add(unsub.apply(tx));
                        }
                        return CompletableFuture.allOf(
                            unsubFutures.toArray(new CompletableFuture[0])
                        ).thenCompose(
                            ignored -> tx.exec(
                                "\n              DROP VIEW IF EXISTS live_query_"
                                    + id
                                    + "_view;\n              DEALLOCATE live_query_"
                                    + id
                                    + "_get;\n            ",
                                null
                            )
                        );
                    }
                );
            }
            return CompletableFuture.completedFuture(null);
        };

        // If the signal has already been aborted, unsubscribe
        if (signal != null && signal.aborted()) {
            unsubscribe.apply(null);
        } else if (signal != null) {
            // Add an event listener to unsubscribe if the signal is aborted
            var options = new interface_.AddEventListenerOptions();
            options.once = true;
            signal.addEventListener(
                "abort",
                () -> {
                    unsubscribe.apply(null);
                },
                options
            );
        }

        // Run the callback with the initial results
        runResultCallbacks(callbacks, resultsHolder[0]);

        // Return the initial results
        var liveQuery = new interface_.LiveQuery<T>();
        liveQuery.initialResults = resultsHolder[0];
        liveQuery.subscribe = subscribe;
        liveQuery.unsubscribe = unsubscribe;
        liveQuery.refresh = opts -> refresh.apply(opts);
        return CompletableFuture.completedFuture(liveQuery);
    }

    private static <T> CompletableFuture<interface_.LiveChanges<T>> changesInternal(
        PGliteInterface pg,
        Set<String> tableNotifyTriggersAdded,
        String query,
        Object[] params,
        String key,
        interface_.LiveChangesCallback<T> callback,
        interface_.AbortSignal signal
    ) {
        if (key == null || key.isEmpty()) {
            throw new RuntimeException("key is required for changes queries");
        }
        var callbacks = new ArrayList<interface_.LiveChangesCallback<T>>();
        if (callback != null) {
            callbacks.add(callback);
        }
        var id = utils.uuid().replace("-", "");
        var dead = new boolean[] { false };

        var stateSwitch = new int[] { 1 };
        var changesHolder = new Results[1];

        var unsubList = new ArrayList<Function<Transaction, CompletableFuture<Void>>>();
        var refreshRefChanges = new interface_.LiveChangesRefresh[1];

        var init = (Runnable) () -> {
            pg.transaction(
                tx -> {
                    // Create a temporary view with the query
                    return formatQuery(pg, query, params, tx).thenCompose(
                        formattedQuery -> tx.query(
                            "CREATE OR REPLACE TEMP VIEW live_query_"
                                + id
                                + "_view AS "
                                + formattedQuery,
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
                                "\n                SELECT column_name, data_type, udt_name\n                FROM information_schema.columns \n                WHERE table_name = 'live_query_"
                                    + id
                                    + "_view'\n              ",
                                null,
                                null
                            ).thenApply(
                                columnsResult -> {
                                    var columns = new ArrayList<Map<String, Object>>();
                                    for (var row : columnsResult.rows) {
                                        columns.add((Map<String, Object>) row);
                                    }
                                    var extra = new HashMap<String, Object>();
                                    extra.put("column_name", "__after__");
                                    extra.put("data_type", "integer");
                                    columns.add(extra);
                                    return columns;
                                }
                            ).thenCompose(
                                columns -> tx.exec(
                                    "\n            CREATE TEMP TABLE live_query_"
                                        + id
                                        + "_state1 (LIKE live_query_"
                                        + id
                                        + "_view INCLUDING ALL);\n            CREATE TEMP TABLE live_query_"
                                        + id
                                        + "_state2 (LIKE live_query_"
                                        + id
                                        + "_view INCLUDING ALL);\n          ",
                                    null
                                ).thenCompose(
                                    ignoredTables -> {
                                        var ops = new ArrayList<CompletableFuture<Void>>();
                                        for (var curr : new int[] { 1, 2 }) {
                                            var prev = curr == 1 ? 2 : 1;
                                            ops.add(
                                                tx.exec(
                                                    "\n              PREPARE live_query_"
                                                        + id
                                                        + "_diff"
                                                        + curr
                                                        + " AS\n              WITH\n                prev AS (SELECT LAG(\""
                                                        + key
                                                        + "\") OVER () as __after__, * FROM live_query_"
                                                        + id
                                                        + "_state"
                                                        + prev
                                                        + "),\n                curr AS (SELECT LAG(\""
                                                        + key
                                                        + "\") OVER () as __after__, * FROM live_query_"
                                                        + id
                                                        + "_state"
                                                        + curr
                                                        + "),\n                data_diff AS (\n                  -- INSERT operations: Include all columns\n                  SELECT \n                    'INSERT' AS __op__,\n                    "
                                                        + joinColumns(columns, "curr")
                                                        + ",\n                    ARRAY[]::text[] AS __changed_columns__\n                  FROM curr\n                  LEFT JOIN prev ON curr."
                                                        + key
                                                        + " = prev."
                                                        + key
                                                        + "\n                  WHERE prev."
                                                        + key
                                                        + " IS NULL\n                UNION ALL\n                  -- DELETE operations: Include only the primary key\n                  SELECT \n                    'DELETE' AS __op__,\n                    "
                                                        + joinDeleteColumns(columns, key)
                                                        + ",\n                      ARRAY[]::text[] AS __changed_columns__\n                  FROM prev\n                  LEFT JOIN curr ON prev."
                                                        + key
                                                        + " = curr."
                                                        + key
                                                        + "\n                  WHERE curr."
                                                        + key
                                                        + " IS NULL\n                UNION ALL\n                  -- UPDATE operations: Include only changed columns\n                  SELECT \n                    'UPDATE' AS __op__,\n                    "
                                                        + joinUpdateColumns(columns, key)
                                                        + ",\n                      ARRAY(SELECT unnest FROM unnest(ARRAY["
                                                        + joinChangedColumns(columns, key)
                                                        + "]) WHERE unnest IS NOT NULL) AS __changed_columns__\n                  FROM curr\n                  INNER JOIN prev ON curr."
                                                        + key
                                                        + " = prev."
                                                        + key
                                                        + "\n                  WHERE NOT (curr IS NOT DISTINCT FROM prev)\n                )\n              SELECT * FROM data_diff;\n            ",
                                                    null
                                                )
                                            );
                                        }
                                        return CompletableFuture.allOf(
                                            ops.toArray(new CompletableFuture[0])
                                        );
                                    }
                                )
                            )
                        )
                    ).thenCompose(
                        ignored -> getTablesForView(
                            tx,
                            "live_query_" + id + "_view"
                        ).thenCompose(
                            tables -> {
                                var subs = new ArrayList<CompletableFuture<Function<Transaction, CompletableFuture<Void>>>>();
                                for (var table : tables) {
                                    subs.add(
                                        tx.listen(
                                            "\"table_change__"
                                                + table.schema_oid
                                                + "__"
                                                + table.table_oid
                                                + "\"",
                                            payload -> {
                                                refreshRefChanges[0].apply();
                                            }
                                        )
                                    );
                                }
                                return CompletableFuture.allOf(
                                    subs.toArray(new CompletableFuture[0])
                                ).thenApply(
                                    ignoredSubs -> {
                                        unsubList.clear();
                                        for (var sub : subs) {
                                            unsubList.add(sub.join());
                                        }
                                        return null;
                                    }
                                );
                            }
                        )
                    );
                }
            ).join();
        };

        var refresh = (interface_.LiveChangesRefresh) () -> {
            if (callbacks.size() == 0 && changesHolder[0] != null) {
                return CompletableFuture.completedFuture(null);
            }
            var reset = new boolean[] { false };
            return pg.transaction(
                tx -> {
                    var changeFuture = CompletableFuture.completedFuture(null);
                    for (var i = 0; i < 5; i++) {
                        var idx = i;
                        changeFuture = changeFuture.thenCompose(
                            ignored -> {
                                return tx.exec(
                                    "\n                INSERT INTO live_query_"
                                        + id
                                        + "_state"
                                        + stateSwitch[0]
                                        + " \n                  SELECT * FROM live_query_"
                                        + id
                                        + "_view;\n              ",
                                    null
                                ).thenCompose(
                                    ignoredInsert -> tx.query(
                                        "EXECUTE live_query_"
                                            + id
                                            + "_diff"
                                            + stateSwitch[0]
                                            + ";",
                                        null,
                                        null
                                    )
                                ).thenApply(
                                    queryResult -> {
                                        changesHolder[0] = queryResult;
                                        stateSwitch[0] = stateSwitch[0] == 1 ? 2 : 1;
                                        return null;
                                    }
                                ).thenCompose(
                                    ignoredResult -> tx.exec(
                                        "\n                TRUNCATE live_query_"
                                            + id
                                            + "_state"
                                            + stateSwitch[0]
                                            + ";\n              ",
                                        null
                                    )
                                ).exceptionally(
                                    error -> {
                                        var msg = error instanceof CompletionException
                                            ? error.getCause().getMessage()
                                            : error.getMessage();
                                        if (
                                            ("relation \"live_query_" + id + "_state" + stateSwitch[0] + "\" does not exist").equals(msg)
                                        ) {
                                            // If the state table does not exist, reset and try again
                                            // This can happen if using the multi-tab worker
                                            reset[0] = true;
                                            init.run();
                                            return null;
                                        }
                                        throw new CompletionException(error);
                                    }
                                );
                            }
                        );
                        if (changesHolder[0] != null) {
                            break;
                        }
                    }
                    return changeFuture;
                }
            ).thenApply(
                ignored -> {
                    var changeRows = new ArrayList<interface_.Change<T>>();
                    if (reset[0]) {
                        var resetEntry = new interface_.Change<T>();
                        changeRows.add(resetEntry);
                    }
                    for (var row : changesHolder[0].rows) {
                        changeRows.add((interface_.Change<T>) row);
                    }
                    runChangeCallbacks(callbacks, changeRows);
                    return null;
                }
            );
        };
        refreshRefChanges[0] = refresh;

        init.run();

        // Function to subscribe to the query
        interface_.LiveChangesSubscribe<T> subscribe = cb -> {
            if (dead[0]) {
                throw new RuntimeException(
                    "Live query is no longer active and cannot be subscribed to"
                );
            }
            callbacks.add(cb);
        };

        // Function to unsubscribe from the query
        interface_.LiveChangesUnsubscribe<T> unsubscribe = cb -> {
            if (cb != null) {
                var filtered = filterChangeCallbacks(callbacks);
                callbacks.clear();
                callbacks.addAll(filtered);
            } else {
                callbacks.clear();
            }
            if (callbacks.size() == 0 && !dead[0]) {
                dead[0] = true;
                return pg.transaction(
                    tx -> {
                        var unsubFutures = new ArrayList<CompletableFuture<Void>>();
                        for (var unsub : unsubList) {
                            unsubFutures.add(unsub.apply(tx));
                        }
                        return CompletableFuture.allOf(
                            unsubFutures.toArray(new CompletableFuture[0])
                        ).thenCompose(
                            ignored -> tx.exec(
                                "\n              DROP VIEW IF EXISTS live_query_"
                                    + id
                                    + "_view;\n              DROP TABLE IF EXISTS live_query_"
                                    + id
                                    + "_state1;\n              DROP TABLE IF EXISTS live_query_"
                                    + id
                                    + "_state2;\n              DEALLOCATE live_query_"
                                    + id
                                    + "_diff1;\n              DEALLOCATE live_query_"
                                    + id
                                    + "_diff2;\n            ",
                                null
                            )
                        );
                    }
                );
            }
            return CompletableFuture.completedFuture(null);
        };

        // If the signal has already been aborted, unsubscribe
        if (signal != null && signal.aborted()) {
            unsubscribe.apply(null);
        } else if (signal != null) {
            // Add an event listener to unsubscribe if the signal is aborted
            var options = new interface_.AddEventListenerOptions();
            options.once = true;
            signal.addEventListener(
                "abort",
                () -> {
                    unsubscribe.apply(null);
                },
                options
            );
        }

        // Run the callback with the initial changes
        refresh.apply();

        // Fields
        var fields = new ArrayList<Results.Field>();
        for (var field : changesHolder[0].fields) {
            if (
                !"__after__".equals(field.name)
                && !"__op__".equals(field.name)
                && !"__changed_columns__".equals(field.name)
            ) {
                fields.add(field);
            }
        }

        // Return the initial results
        var liveChanges = new interface_.LiveChanges<T>();
        liveChanges.fields = fields;
        liveChanges.initialChanges = (List) changesHolder[0].rows;
        liveChanges.subscribe = subscribe;
        liveChanges.unsubscribe = unsubscribe;
        liveChanges.refresh = refresh;
        return CompletableFuture.completedFuture(liveChanges);
    }

    private static <T> CompletableFuture<interface_.LiveQuery<T>> incrementalQueryInternal(
        interface_.LiveNamespace namespaceObj,
        String query,
        Object[] params,
        String key,
        interface_.LiveQueryCallback<T> callback,
        interface_.AbortSignal signal
    ) {
        if (key == null || key.isEmpty()) {
            throw new RuntimeException("key is required for incremental queries");
        }
        var callbacks = new ArrayList<interface_.LiveQueryCallback<T>>();
        if (callback != null) {
            callbacks.add(callback);
        }
        var rowsMap = new HashMap<Object, Map<String, Object>>();
        var afterMap = new HashMap<Object, Object>();
        var lastRows = new ArrayList<Map<String, Object>>();
        var firstRun = new boolean[] { true };

        return namespaceObj.changes(
            query,
            params,
            key,
            changes -> {
                // Process the changes
                for (var change : changes) {
                    var changeMap = (Map<String, Object>) change;
                    var op = String.valueOf(changeMap.get("__op__"));
                    var changedColumns = (List<String>) changeMap.get("__changed_columns__");
                    var obj = new HashMap<String, Object>(changeMap);
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
                            var existing = rowsMap.get(obj.get(key));
                            var newObj = existing != null
                                ? new HashMap<String, Object>(existing)
                                : new HashMap<>();
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
                var rows = new ArrayList<Map<String, Object>>();
                Object lastKey = null;
                for (var i = 0; i < rowsMap.size(); i++) {
                    var nextKey = afterMap.get(lastKey);
                    var obj = rowsMap.get(nextKey);
                    if (obj == null) {
                        break;
                    }
                    // Remove the __after__ key from the exposed row
                    var cleanObj = new HashMap<String, Object>(obj);
                    cleanObj.remove("__after__");
                    rows.add(cleanObj);
                    lastKey = nextKey;
                }
                lastRows.clear();
                lastRows.addAll(rows);

                // Run the callbacks
                if (!firstRun[0]) {
                    var results = new Results();
                    results.rows = (List) rows;
                    results.fields = new ArrayList<>();
                    runResultCallbacks(callbacks, results);
                }
            }
        ).thenApply(
            liveChanges -> {
                firstRun[0] = false;
                var initialResults = new Results();
                initialResults.rows = (List) lastRows;
                initialResults.fields = liveChanges.fields;
                runResultCallbacks(callbacks, initialResults);

                interface_.LiveQuerySubscribe<T> subscribe = cb -> callbacks.add(cb);
                interface_.LiveQueryUnsubscribe<T> unsubscribe = cb -> {
                    if (cb != null) {
                        var filtered = filterCallbacks(callbacks);
                        callbacks.clear();
                        callbacks.addAll(filtered);
                    } else {
                        callbacks.clear();
                    }
                    if (callbacks.size() == 0) {
                        return liveChanges.unsubscribe.apply(null);
                    }
                    return CompletableFuture.completedFuture(null);
                };

                if (signal != null && signal.aborted()) {
                    unsubscribe.apply(null);
                } else if (signal != null) {
                    var options = new interface_.AddEventListenerOptions();
                    options.once = true;
                    signal.addEventListener(
                        "abort",
                        () -> {
                            unsubscribe.apply(null);
                        },
                        options
                    );
                }

                var liveQuery = new interface_.LiveQuery<T>();
                var liveResults = new interface_.LiveQueryResults<T>();
                liveResults.rows = (List) lastRows;
                liveResults.fields = liveChanges.fields;
                liveQuery.initialResults = liveResults;
                liveQuery.subscribe = subscribe;
                liveQuery.unsubscribe = unsubscribe;
                liveQuery.refresh = opts -> liveChanges.refresh.apply();
                return liveQuery;
            }
        );
    }

    public static final Extension<interface_.LiveNamespace> live = new Extension<>();

    static {
        live.name = "Live Queries";
        live.setup = index::setup;
    }

    public interface PGliteWithLive extends PGliteInterface {
        interface_.LiveNamespace live();
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
            "\n      WITH RECURSIVE view_dependencies AS (\n        -- Base case: Get the initial view's dependencies\n        SELECT DISTINCT\n          cl.relname AS dependent_name,\n          n.nspname AS schema_name,\n          cl.oid AS dependent_oid,\n          n.oid AS schema_oid,\n          cl.relkind = 'v' AS is_view\n        FROM pg_rewrite r\n        JOIN pg_depend d ON r.oid = d.objid\n        JOIN pg_class cl ON d.refobjid = cl.oid\n        JOIN pg_namespace n ON cl.relnamespace = n.oid\n        WHERE\n          r.ev_class = (\n              SELECT oid FROM pg_class WHERE relname = $1 AND relkind = 'v'\n          )\n          AND d.deptype = 'n'\n\n        UNION ALL\n\n        -- Recursive case: Traverse dependencies for views\n        SELECT DISTINCT\n          cl.relname AS dependent_name,\n          n.nspname AS schema_name,\n          cl.oid AS dependent_oid,\n          n.oid AS schema_oid,\n          cl.relkind = 'v' AS is_view\n        FROM view_dependencies vd\n        JOIN pg_rewrite r ON vd.dependent_name = (\n          SELECT relname FROM pg_class WHERE oid = r.ev_class AND relkind = 'v'\n        )\n        JOIN pg_depend d ON r.oid = d.objid\n        JOIN pg_class cl ON d.refobjid = cl.oid\n        JOIN pg_namespace n ON cl.relnamespace = n.oid\n        WHERE d.deptype = 'n'\n      )\n      SELECT DISTINCT\n        dependent_name AS table_name,\n        schema_name,\n        dependent_oid AS table_oid,\n        schema_oid\n      FROM view_dependencies\n      WHERE NOT is_view; -- Exclude intermediate views\n    ",
            new Object[] { viewName },
            null
        ).thenApply(
            result -> {
                var tables = new ArrayList<TableInfo>();
                for (var rowObj : result.rows) {
                    var row = (Map<String, Object>) rowObj;
                    var table = new TableInfo();
                    table.table_name = String.valueOf(row.get("table_name"));
                    table.schema_name = String.valueOf(row.get("schema_name"));
                    table.table_oid = toInt(row.get("table_oid"));
                    table.schema_oid = toInt(row.get("schema_oid"));
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
        var builder = new StringBuilder();
        for (var table : tables) {
            var key = table.schema_oid + "_" + table.table_oid;
            if (tableNotifyTriggersAdded.contains(key)) {
                continue;
            }
            builder.append(
                "\n      CREATE OR REPLACE FUNCTION \"_notify_"
                    + table.schema_oid
                    + "_"
                    + table.table_oid
                    + "\"() RETURNS TRIGGER AS $$\n      BEGIN\n        PERFORM pg_notify('table_change__"
                    + table.schema_oid
                    + "__"
                    + table.table_oid
                    + "', '');\n        RETURN NULL;\n      END;\n      $$ LANGUAGE plpgsql;\n      CREATE OR REPLACE TRIGGER \"_notify_trigger_"
                    + table.schema_oid
                    + "_"
                    + table.table_oid
                    + "\"\n      AFTER INSERT OR UPDATE OR DELETE ON \""
                    + table.schema_name
                    + "\".\""
                    + table.table_name
                    + "\"\n      FOR EACH STATEMENT EXECUTE FUNCTION \"_notify_"
                    + table.schema_oid
                    + "_"
                    + table.table_oid
                    + "\"();\n      "
            );
            tableNotifyTriggersAdded.add(key);
        }
        var triggers = builder.toString();
        if (!triggers.trim().isEmpty()) {
            return tx.exec(triggers, null).thenApply(ignored -> null);
        }
        return CompletableFuture.completedFuture(null);
    }

    private static <T> void runResultCallbacks(
        List<interface_.LiveQueryCallback<T>> callbacks,
        Results results
    ) {
        if (callbacks == null) {
            return;
        }
        for (var callback : callbacks) {
            callback.apply(results);
        }
    }

    private static <T> void runChangeCallbacks(
        List<interface_.LiveChangesCallback<T>> callbacks,
        List<interface_.Change<T>> changes
    ) {
        if (callbacks == null) {
            return;
        }
        for (var callback : callbacks) {
            callback.apply(changes);
        }
    }

    private static int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    private static CompletableFuture<String> formatQuery(
        PGliteInterface pg,
        String query,
        Object[] params,
        Transaction tx
    ) {
        return utils.formatQuery(new FormatQueryAdapter(pg), query, params, new FormatQueryTransactionAdapter(tx));
    }

    private static String joinColumns(List<Map<String, Object>> columns, String alias) {
        var parts = new ArrayList<String>();
        for (var column : columns) {
            var columnName = String.valueOf(column.get("column_name"));
            parts.add(alias + ".\"" + columnName + "\" AS \"" + columnName + "\"");
        }
        return String.join(",\n", parts);
    }

    private static String joinDeleteColumns(List<Map<String, Object>> columns, String key) {
        var parts = new ArrayList<String>();
        for (var column : columns) {
            var columnName = String.valueOf(column.get("column_name"));
            var dataType = String.valueOf(column.get("data_type"));
            var udtName = String.valueOf(column.get("udt_name"));
            if (columnName.equals(key)) {
                parts.add("prev.\"" + columnName + "\" AS \"" + columnName + "\"");
            } else {
                var suffix = "USER-DEFINED".equals(dataType) ? "::" + udtName : "";
                parts.add("NULL" + suffix + " AS \"" + columnName + "\"");
            }
        }
        return String.join(",\n", parts);
    }

    private static String joinUpdateColumns(List<Map<String, Object>> columns, String key) {
        var parts = new ArrayList<String>();
        for (var column : columns) {
            var columnName = String.valueOf(column.get("column_name"));
            var dataType = String.valueOf(column.get("data_type"));
            var udtName = String.valueOf(column.get("udt_name"));
            if (columnName.equals(key)) {
                parts.add("curr.\"" + columnName + "\" AS \"" + columnName + "\"");
            } else {
                var suffix = "USER-DEFINED".equals(dataType) ? "::" + udtName : "";
                parts.add(
                    "CASE \n                              WHEN curr.\""
                        + columnName
                        + "\" IS DISTINCT FROM prev.\""
                        + columnName
                        + "\" \n                              THEN curr.\""
                        + columnName
                        + "\"\n                              ELSE NULL"
                        + suffix
                        + "\n                              END AS \""
                        + columnName
                        + "\""
                );
            }
        }
        return String.join(",\n", parts);
    }

    private static String joinChangedColumns(List<Map<String, Object>> columns, String key) {
        var parts = new ArrayList<String>();
        for (var column : columns) {
            var columnName = String.valueOf(column.get("column_name"));
            if (columnName.equals(key)) {
                continue;
            }
            parts.add(
                "CASE\n                              WHEN curr.\""
                    + columnName
                    + "\" IS DISTINCT FROM prev.\""
                    + columnName
                    + "\" \n                              THEN '"
                    + columnName
                    + "' \n                              ELSE NULL \n                              END"
            );
        }
        return String.join(", ", parts);
    }

    private static <T> CompletableFuture<Void> runQueryRefresh(
        PGliteInterface pg,
        String id,
        boolean isWindowed,
        List<interface_.LiveQueryCallback<T>> callbacks,
        interface_.LiveQueryResults<T>[] resultsHolder,
        Integer offset,
        Integer limit,
        Integer[] totalCountRef,
        Runnable init
    ) {
        return CompletableFuture.runAsync(
            () -> {
                var run = (Runnable) () -> {
                    if (callbacks.size() == 0) {
                        return;
                    }
                    try {
                        if (isWindowed) {
                            // For a windowed query we defer the refresh of the total count until
                            // after we have returned the results with the old total count. This
                            // is due to a count(*) being a fairly slow query and we want to update
                            // the rows on screen as quickly as possible.
                            var queryResult = pg.query(
                                "EXECUTE live_query_"
                                    + id
                                    + "_get("
                                    + limit
                                    + ", "
                                    + offset
                                    + ");",
                                null,
                                null
                            ).join();
                            var liveResults = new interface_.LiveQueryResults<T>();
                            liveResults.rows = queryResult.rows;
                            liveResults.fields = queryResult.fields;
                            liveResults.affectedRows = queryResult.affectedRows;
                            liveResults.blob = queryResult.blob;
                            liveResults.offset = offset;
                            liveResults.limit = limit;
                            liveResults.totalCount = totalCountRef[0];
                            resultsHolder[0] = liveResults;
                        } else {
                            var queryResult = pg.query(
                                "EXECUTE live_query_"
                                    + id
                                    + "_get;",
                                null,
                                null
                            ).join();
                            var liveResults = new interface_.LiveQueryResults<T>();
                            liveResults.rows = queryResult.rows;
                            liveResults.fields = queryResult.fields;
                            liveResults.affectedRows = queryResult.affectedRows;
                            liveResults.blob = queryResult.blob;
                            resultsHolder[0] = liveResults;
                        }
                    } catch (RuntimeException e) {
                        var msg = e.getMessage();
                        if (
                            msg != null
                            && msg.startsWith("prepared statement \"live_query_" + id)
                            && msg.endsWith("does not exist")
                        ) {
                            // If the prepared statement does not exist, reset and try again
                            // This can happen if using the multi-tab worker
                            init.run();
                        } else {
                            throw e;
                        }
                    }

                    runResultCallbacks(callbacks, resultsHolder[0]);

                    // Update the total count
                    // If the total count has changed, refresh the query
                    if (isWindowed) {
                        var newTotalCount = extractCount(
                            pg.query(
                                "EXECUTE live_query_"
                                    + id
                                    + "_get_total_count;",
                                null,
                                null
                            ).join()
                        );
                        if (
                            newTotalCount != null
                            && (totalCountRef[0] == null || !newTotalCount.equals(totalCountRef[0]))
                        ) {
                            // The total count has changed, refresh the query
                            totalCountRef[0] = newTotalCount;
                            run.run();
                        }
                    }
                };
                run.run();
            }
        );
    }

    private static Integer extractCount(Results result) {
        if (result == null || result.rows == null || result.rows.isEmpty()) {
            return null;
        }
        var row = result.rows.get(0);
        if (row instanceof Map) {
            var map = (Map<?, ?>) row;
            var value = map.get("count");
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        }
        return null;
    }

    private static class TableInfo {
        public String table_name;
        public String schema_name;
        public int table_oid;
        public int schema_oid;
    }

    private static <T> List<interface_.LiveQueryCallback<T>> filterCallbacks(
        List<interface_.LiveQueryCallback<T>> callbacks
    ) {
        var next = new ArrayList<interface_.LiveQueryCallback<T>>();
        for (var callback : callbacks) {
            if (callback != callback) {
                next.add(callback);
            }
        }
        return next;
    }

    private static <T> List<interface_.LiveChangesCallback<T>> filterChangeCallbacks(
        List<interface_.LiveChangesCallback<T>> callbacks
    ) {
        var next = new ArrayList<interface_.LiveChangesCallback<T>>();
        for (var callback : callbacks) {
            if (callback != callback) {
                next.add(callback);
            }
        }
        return next;
    }

    private static final class FormatQueryAdapter implements utils.PGliteInterface {
        private final PGliteInterface pg;

        private FormatQueryAdapter(PGliteInterface pg) {
            this.pg = pg;
        }

        @Override
        public CompletableFuture<utils.ExecProtocolResult> execProtocol(
            io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array message,
            utils.ExecProtocolOptions options
        ) {
            return pg.execProtocol(message, null)
                .thenApply(
                    result -> {
                        var mapped = new utils.ExecProtocolResult();
                        mapped.messages = result.messages;
                        mapped.data = result.data;
                        return mapped;
                    }
                );
        }

        @Override
        public CompletableFuture<utils.Results> query(
            String query,
            Object[] params,
            utils.QueryOptions options
        ) {
            return pg.query(query, params, null).thenApply(
                result -> {
                    var mapped = new utils.Results();
                    var rows = new ArrayList<utils.FormatQueryRow>();
                    if (result.rows != null) {
                        for (var row : result.rows) {
                            var formatRow = new utils.FormatQueryRow();
                            if (row instanceof Map) {
                                var map = (Map<?, ?>) row;
                                var value = map.get("query");
                                formatRow.query = value != null ? value.toString() : null;
                            } else {
                                formatRow.query = row != null ? row.toString() : null;
                            }
                            rows.add(formatRow);
                        }
                    }
                    mapped.rows = (List) rows;
                    return mapped;
                }
            );
        }
    }

    private static final class FormatQueryTransactionAdapter implements utils.Transaction {
        private final Transaction tx;

        private FormatQueryTransactionAdapter(Transaction tx) {
            this.tx = tx;
        }

        @Override
        public CompletableFuture<utils.Results> query(
            String query,
            Object[] params,
            utils.QueryOptions options
        ) {
            if (tx == null) {
                return CompletableFuture.completedFuture(null);
            }
            return tx.query(query, params, null).thenApply(
                result -> {
                    var mapped = new utils.Results();
                    var rows = new ArrayList<utils.FormatQueryRow>();
                    if (result.rows != null) {
                        for (var row : result.rows) {
                            var formatRow = new utils.FormatQueryRow();
                            if (row instanceof Map) {
                                var map = (Map<?, ?>) row;
                                var value = map.get("query");
                                formatRow.query = value != null ? value.toString() : null;
                            } else {
                                formatRow.query = row != null ? row.toString() : null;
                            }
                            rows.add(formatRow);
                        }
                    }
                    mapped.rows = (List) rows;
                    return mapped;
                }
            );
        }
    }

    private index() {
    }
}
