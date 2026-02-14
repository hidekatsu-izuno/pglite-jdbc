package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.serializer;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class base {
    protected final Map<Integer, types.Serializer> serializers = new HashMap<>(types.serializers);
    protected final Map<Integer, types.Parser> parsers = new HashMap<>(types.parsers);
    protected final Semaphore queryMutex = new Semaphore(1);
    protected final Semaphore transactionMutex = new Semaphore(1);
    protected volatile boolean inTransaction = false;
    protected volatile boolean arrayTypesInitialized = false;

    public abstract int debug();

    public abstract Promise<interface_.ExecProtocolResult> execProtocol(
        byte[] message,
        interface_.ExecProtocolOptions options
    );

    public abstract Promise<byte[]> execProtocolRaw(byte[] message, interface_.ExecProtocolOptions options);

    public abstract Promise<Void> syncToFs();

    public abstract Promise<Function<interface_.Transaction, Promise<Void>>> listen(
        String channel,
        Consumer<String> callback,
        interface_.Transaction tx
    );

    public abstract Promise<Void> unlisten(
        String channel,
        Consumer<String> callback,
        interface_.Transaction tx
    );

    protected Promise<Void> handleBlob(byte[] blob) {
        return Promise.resolve(null);
    }

    protected Promise<byte[]> getWrittenBlob() {
        return Promise.resolve(null);
    }

    protected Promise<Void> cleanupBlob() {
        return Promise.resolve(null);
    }

    protected Promise<Void> checkReady() {
        return Promise.resolve(null);
    }

    protected <T> Promise<T> runExclusiveQuery(ThrowingSupplier<Promise<T>> fn) {
        return runWithSemaphore(queryMutex, fn);
    }

    protected <T> Promise<T> runExclusiveTransaction(ThrowingSupplier<Promise<T>> fn) {
        return runWithSemaphore(transactionMutex, fn);
    }

    private Promise<List<messages.BackendMessage>> execProtocolNoSync(
        byte[] message,
        interface_.QueryOptions options
    ) {
        var execOptions = new interface_.ExecProtocolOptions(
            false,
            true,
            options != null ? options.onNotice() : null
        );
        return this.execProtocol(message, execOptions).then(result -> result.messages());
    }

    public Promise<Void> refreshArrayTypes() {
        if (this.arrayTypesInitialized) {
            return Promise.resolve(null);
        }
        this.arrayTypesInitialized = true;

        var sql = """
            SELECT b.oid, b.typarray
            FROM pg_catalog.pg_type a
            LEFT JOIN pg_catalog.pg_type b ON b.oid = a.typelem
            WHERE a.typcategory = 'A'
            GROUP BY b.oid, b.typarray
            ORDER BY b.oid
            """;

        return this.query(sql, null, null).then(result -> {
            for (var rowObj : result.rows()) {
                if (!(rowObj instanceof Map<?, ?> row)) {
                    continue;
                }
                var oidValue = row.get("oid");
                var arrayValue = row.get("typarray");
                if (!(oidValue instanceof Number oidNumber) || !(arrayValue instanceof Number arrayNumber)) {
                    continue;
                }
                var oid = oidNumber.intValue();
                var typarray = arrayNumber.intValue();
                this.serializers.put(
                    typarray,
                    value -> types.arraySerializer(value, this.serializers.get(oid), typarray)
                );
                this.parsers.put(
                    typarray,
                    (value, typeId) -> types.arrayParser(value, this.parsers.get(oid), typarray)
                );
            }
            return (Void) null;
        });
    }

    public <T> Promise<interface_.Results<T>> query(
        String query,
        Object[] params,
        interface_.QueryOptions options
    ) {
        return checkReady().then(ignored ->
            runExclusiveTransaction(() -> runQuery(query, params, options))
        ).then(resultObj -> {
            @SuppressWarnings("unchecked")
            var result = (interface_.Results<T>) resultObj;
            return result;
        });
    }

    public Promise<List<interface_.Results<Map<String, Object>>>> exec(
        String query,
        interface_.QueryOptions options
    ) {
        return checkReady().then(ignored ->
            runExclusiveTransaction(() -> runExec(query, options))
        );
    }

    public <T> Promise<interface_.Results<T>> sql(
        List<String> strings,
        Object... params
    ) {
        var templated = templating.query(strings, params);
        return this.query(templated.query(), templated.params().toArray(), null)
            .then(resultObj -> {
                @SuppressWarnings("unchecked")
                var result = (interface_.Results<T>) resultObj;
                return result;
            });
    }

    public Promise<interface_.DescribeQueryResult> describeQuery(String query) {
        return describeQuery(query, null);
    }

    public Promise<interface_.DescribeQueryResult> describeQuery(
        String query,
        interface_.QueryOptions options
    ) {
        var allMessages = new ArrayList<messages.BackendMessage>();
        var errorHolder = new Throwable[1];
        var errorFromSync = new boolean[1];

        var parseOpts = new serializer.ParseOpts();
        parseOpts.text = query;
        if (options != null) {
            parseOpts.types = options.paramTypes();
        }

        var describeOpts = new serializer.PortalOpts();
        describeOpts.type = "S";

        return execProtocolNoSync(serializer.serialize.parse(parseOpts).toByteArray(), options)
            .then(ignored -> execProtocolNoSync(serializer.serialize.describe(describeOpts).toByteArray(), options))
            .then(describeMessages -> {
                allMessages.addAll(castBackendMessages(describeMessages));
                return (Void) null;
            }, error -> {
                errorHolder[0] = unwrap(error);
                return (Void) null;
            })
            .then(ignored -> execProtocolNoSync(serializer.serialize.sync().toByteArray(), options)
                .then(syncMessages -> {
                    allMessages.addAll(syncMessages);
                    return (Void) null;
                }, syncError -> {
                    errorHolder[0] = unwrap(syncError);
                    errorFromSync[0] = true;
                    return (Void) null;
                })
            )
            .then(ignored -> {
                if (errorHolder[0] != null) {
                    var cause = unwrap(errorHolder[0]);
                    if (!errorFromSync[0] && cause instanceof messages.DatabaseError dbError) {
                        throw errors.makePGliteError(dbError, query, null, options);
                    }
                    throw asRuntime(cause);
                }

                messages.ParameterDescriptionMessage paramDescription = null;
                messages.RowDescriptionMessage rowDescription = null;
                for (var message : allMessages) {
                    if (message instanceof messages.ParameterDescriptionMessage pd) {
                        paramDescription = pd;
                    } else if (message instanceof messages.RowDescriptionMessage rd) {
                        rowDescription = rd;
                    }
                }

                var queryParams = new ArrayList<interface_.Field>();
                if (paramDescription != null) {
                    for (var typeId : paramDescription.dataTypeIDs) {
                        queryParams.add(new interface_.Field("", typeId));
                    }
                }

                var resultFields = new ArrayList<interface_.Field>();
                if (rowDescription != null) {
                    for (var field : rowDescription.fields) {
                        resultFields.add(new interface_.Field(field.name, field.dataTypeID));
                    }
                }

                return new interface_.DescribeQueryResult(queryParams, resultFields);
            });
    }

    public <T> Promise<T> transaction(Function<interface_.Transaction, Promise<T>> callback) {
        return checkReady().then(ignored ->
            runExclusiveTransaction(() ->
                runExec("BEGIN", null).then(ignoredBegin -> {
                    this.inTransaction = true;
                    var closed = new AtomicBoolean(false);

                    Runnable checkClosed = () -> {
                        if (closed.get()) {
                            throw new IllegalStateException("Transaction is closed");
                        }
                    };

                    var tx = new interface_.Transaction() {
                        @Override
                        public <R> Promise<interface_.Results<R>> query(
                            String query,
                            Object[] params,
                            interface_.QueryOptions options
                        ) {
                            checkClosed.run();
                            return base.this.query(query, params, options);
                        }

                        @Override
                        public <R> Promise<interface_.Results<R>> sql(List<String> strings, Object... params) {
                            checkClosed.run();
                            return base.this.sql(strings, params).then(resultObj -> {
                                @SuppressWarnings("unchecked")
                                var cast = (interface_.Results<R>) resultObj;
                                return cast;
                            });
                        }

                        @Override
                        public Promise<List<interface_.Results<Map<String, Object>>>> exec(
                            String query,
                            interface_.QueryOptions options
                        ) {
                            checkClosed.run();
                            return runExec(query, options);
                        }

                        @Override
                        public Promise<Void> rollback() {
                            checkClosed.run();
                            return runExec("ROLLBACK", null).then(ignoredRollback -> {
                                closed.set(true);
                                inTransaction = false;
                                return null;
                            });
                        }

                        @Override
                        public Promise<Function<interface_.Transaction, Promise<Void>>> listen(
                            String channel,
                            Consumer<String> callback
                        ) {
                            checkClosed.run();
                            return base.this.listen(channel, callback, this);
                        }

                        @Override
                        public boolean closed() {
                            return closed.get();
                        }
                    };

                    Promise<T> callbackResult;
                    try {
                        callbackResult = callback.apply(tx);
                    } catch (Throwable e) {
                        callbackResult = Promise.reject(asRuntime(e));
                    }

                    return callbackResult.then(value -> {
                        if (!closed.get()) {
                            closed.set(true);
                            return runExec("COMMIT", null).then(ignoredCommit -> {
                                inTransaction = false;
                                return value;
                            }, commitError -> {
                                inTransaction = false;
                                throw asRuntime(unwrap(commitError));
                            });
                        }
                        inTransaction = false;
                        return value;
                    }, callbackError -> {
                        var cause = unwrap(callbackError);
                        if (!closed.get()) {
                            return runExec("ROLLBACK", null).then(ignoredRollback -> {
                                inTransaction = false;
                                throw asRuntime(cause);
                            }, rollbackError -> {
                                inTransaction = false;
                                throw asRuntime(cause);
                            });
                        }
                        inTransaction = false;
                        throw asRuntime(cause);
                    });
                })
            )
        );
    }

    public <T> Promise<T> runExclusive(Function<Void, Promise<T>> fn) {
        return this.runExclusiveQuery(() -> fn.apply(null));
    }

    protected Promise<interface_.Results<Map<String, Object>>> runQuery(
        String query,
        Object[] params,
        interface_.QueryOptions options
    ) {
        return runExclusiveQuery(() -> {
            var activeParams = params != null ? params : new Object[0];
            return handleBlob(options != null ? options.blob() : null).then(ignoredBlob -> {
                var allMessages = new ArrayList<messages.BackendMessage>();
                var errorHolder = new Throwable[1];
                var errorFromSync = new boolean[1];

                var parseOpts = new serializer.ParseOpts();
                parseOpts.text = query;
                if (options != null) {
                    parseOpts.types = options.paramTypes();
                }

                return execProtocolNoSync(serializer.serialize.parse(parseOpts).toByteArray(), options)
                    .then(parseMessages -> {
                        allMessages.addAll(parseMessages);
                        var describeStatementOpts = new serializer.PortalOpts();
                        describeStatementOpts.type = "S";
                        return execProtocolNoSync(
                            serializer.serialize.describe(describeStatementOpts).toByteArray(),
                            options
                        ).then(describeMessages -> {
                            allMessages.addAll(describeMessages);
                            var dataTypeIDs = parse.parseDescribeStatementResults(describeMessages);
                            var values = new Object[activeParams.length];
                            for (var i = 0; i < activeParams.length; i++) {
                                var param = activeParams[i];
                                if (param == null) {
                                    values[i] = null;
                                    continue;
                                }
                                var typeId = i < dataTypeIDs.length ? dataTypeIDs[i] : types.TEXT;
                                if (options != null && options.serializers() != null) {
                                    var customSerializer = options.serializers().get(typeId);
                                    if (customSerializer != null) {
                                        values[i] = customSerializer.serialize(param);
                                        continue;
                                    }
                                }
                                var defaultSerializer = this.serializers.get(typeId);
                                values[i] = defaultSerializer != null
                                    ? defaultSerializer.serialize(param)
                                    : String.valueOf(param);
                            }

                            var bindOpts = new serializer.BindOpts();
                            bindOpts.values = values;
                            return execProtocolNoSync(serializer.serialize.bind(bindOpts).toByteArray(), options)
                                .then(bindMessages -> {
                                    allMessages.addAll(bindMessages);
                                    var describePortalOpts = new serializer.PortalOpts();
                                    describePortalOpts.type = "P";
                                    return execProtocolNoSync(
                                        serializer.serialize.describe(describePortalOpts).toByteArray(),
                                        options
                                    );
                                })
                                .then(portalMessages -> {
                                    allMessages.addAll(castBackendMessages(portalMessages));
                                    return execProtocolNoSync(
                                        serializer.serialize.execute(new serializer.ExecOpts()).toByteArray(),
                                        options
                                    );
                                })
                                .then(executeMessages -> {
                                    allMessages.addAll(castBackendMessages(executeMessages));
                                    return allMessages;
                                });
                        });
                    }, error -> {
                        errorHolder[0] = unwrap(error);
                        return allMessages;
                    })
                    .then(ignoredExecute -> execProtocolNoSync(serializer.serialize.sync().toByteArray(), options)
                        .then(syncMessages -> {
                            allMessages.addAll(syncMessages);
                            return allMessages;
                        }, syncError -> {
                            errorHolder[0] = unwrap(syncError);
                            errorFromSync[0] = true;
                            return allMessages;
                        })
                    )
                    .then(resultMessages -> {
                        if (errorHolder[0] != null) {
                            var cause = unwrap(errorHolder[0]);
                            if (!errorFromSync[0] && cause instanceof messages.DatabaseError dbError) {
                                throw errors.makePGliteError(dbError, query, activeParams, options);
                            }
                            throw asRuntime(cause);
                        }
                        return cleanupBlob().then(ignoredCleanup -> {
                            var syncFuture = !inTransaction
                                ? syncToFs()
                                : Promise.resolve(null);
                            return syncFuture.then(ignoredSync -> getWrittenBlob().then(blob -> {
                                var parsed = parse.parseResults(
                                    castBackendMessages(resultMessages),
                                    parsers,
                                    options,
                                    blob
                                );
                                if (parsed.isEmpty()) {
                                    return new interface_.Results<Map<String, Object>>(
                                        List.of(),
                                        0,
                                        List.of(),
                                        blob
                                    );
                                }
                                return parsed.getFirst();
                            }));
                        });
                    });
            });
        });
    }

    protected Promise<List<interface_.Results<Map<String, Object>>>> runExec(
        String query,
        interface_.QueryOptions options
    ) {
        return runExclusiveQuery(() ->
            handleBlob(options != null ? options.blob() : null).then(ignoredBlob -> {
                var allMessages = new ArrayList<messages.BackendMessage>();
                var errorHolder = new Throwable[1];
                var errorFromSync = new boolean[1];

                return execProtocolNoSync(serializer.serialize.query(query).toByteArray(), options)
                    .then(execMessages -> {
                        allMessages.addAll(execMessages);
                        return allMessages;
                    }, error -> {
                        errorHolder[0] = unwrap(error);
                        return allMessages;
                    })
                    .then(ignoredExec -> execProtocolNoSync(serializer.serialize.sync().toByteArray(), options)
                        .then(syncMessages -> {
                            allMessages.addAll(syncMessages);
                            return allMessages;
                        }, syncError -> {
                            errorHolder[0] = unwrap(syncError);
                            errorFromSync[0] = true;
                            return allMessages;
                        })
                    )
                    .then(resultMessages -> {
                        if (errorHolder[0] != null) {
                            var cause = unwrap(errorHolder[0]);
                            if (!errorFromSync[0] && cause instanceof messages.DatabaseError dbError) {
                                throw errors.makePGliteError(dbError, query, null, options);
                            }
                            throw asRuntime(cause);
                        }
                        return cleanupBlob().then(ignoredCleanup -> {
                            var syncFuture = !inTransaction
                                ? syncToFs()
                                : Promise.resolve(null);
                            return syncFuture.then(ignoredSync -> getWrittenBlob().then(blob ->
                                parse.parseResults(castBackendMessages(resultMessages), parsers, options, blob)
                            ));
                        });
                    });
            })
        );
    }

    protected static <T> Promise<T> runWithSemaphore(
        Semaphore semaphore,
        ThrowingSupplier<Promise<T>> fn
    ) {
        return new Promise<>((resolve, reject) -> {
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                reject.run(e);
                return;
            }

            Promise<T> promise;
            try {
                promise = fn.get();
            } catch (Throwable e) {
                semaphore.release();
                reject.run(e);
                return;
            }

            if (promise == null) {
                semaphore.release();
                resolve.run(null);
                return;
            }

            promise.then(value -> {
                semaphore.release();
                resolve.run(value);
                return null;
            }, error -> {
                semaphore.release();
                reject.run(unwrap(error));
                return null;
            });
        });
    }

    protected static Throwable unwrap(Throwable error) {
        var current = error;
        while (current instanceof RuntimeException runtime && runtime.getCause() != null) {
            if (runtime == runtime.getCause()) {
                break;
            }
            current = runtime.getCause();
        }
        return current;
    }

    protected static RuntimeException asRuntime(Throwable error) {
        if (error instanceof RuntimeException runtime) {
            return runtime;
        }
        return new RuntimeException(error);
    }

    @SuppressWarnings("unchecked")
    protected static List<messages.BackendMessage> castBackendMessages(Object value) {
        return (List<messages.BackendMessage>) value;
    }

    @FunctionalInterface
    protected interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
