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
import java.util.function.Function;

public abstract class base {
    protected final Map<Integer, types.Serializer> serializers = new HashMap<>(types.serializers);
    protected final Map<Integer, types.Parser> parsers = new HashMap<>(types.parsers);
    protected final Semaphore queryMutex = new Semaphore(1);
    protected final Semaphore transactionMutex = new Semaphore(1);
    protected volatile boolean inTransaction = false;

    public abstract int debug();

    public abstract Promise<interface_.ExecProtocolResult> execProtocol(
        byte[] message,
        interface_.ExecProtocolOptions options
    );

    public abstract Promise<byte[]> execProtocolRaw(byte[] message, interface_.ExecProtocolOptions options);

    public abstract Promise<Void> syncToFs();

    protected Promise<Void> checkReady() {
        return Promise.resolve(null);
    }

    protected <T> Promise<T> runExclusiveQuery(ThrowingSupplier<Promise<T>> fn) {
        return runWithSemaphore(queryMutex, fn);
    }

    protected <T> Promise<T> runExclusiveTransaction(ThrowingSupplier<Promise<T>> fn) {
        return runWithSemaphore(transactionMutex, fn);
    }

    public Promise<Void> refreshArrayTypes() {
        return Promise.resolve(null);
    }

    public <T> Promise<interface_.Results<T>> query(
        String query,
        Object[] params,
        interface_.QueryOptions options
    ) {
        return checkReady().then(ignored ->
            runExclusiveTransaction(() -> runQuery(query, params, options))
        );
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
            .then(result -> {
                @SuppressWarnings("unchecked")
                var cast = (interface_.Results<T>) result;
                return cast;
            });
    }

    public Promise<interface_.DescribeQueryResult> describeQuery(String query) {
        var parseOpts = new serializer.ParseOpts();
        parseOpts.text = query;
        return execProtocol(serializer.serialize.parse(parseOpts).toByteArray(), protocolOptionsNoSync(null))
            .then(parseResultObj ->
                execProtocol(
                    serializer.serialize.describe(new serializer.PortalOpts()).toByteArray(),
                    protocolOptionsNoSync(null)
                )
            )
            .then(resultObj ->
                execProtocol(serializer.serialize.sync().toByteArray(), protocolOptionsNoSync(null))
                    .then(syncObj -> {
                        var result = (interface_.ExecProtocolResult) resultObj;
                        var sync = (interface_.ExecProtocolResult) syncObj;
                        var merged = new ArrayList<messages.BackendMessage>();
                        merged.addAll(result.messages());
                        merged.addAll(sync.messages());
                        var params = parse.parseDescribeStatementResults(merged);
                        var queryParams = Arrays.stream(params)
                            .mapToObj(typeId -> new interface_.Field("", typeId))
                            .toList();
                        return new interface_.DescribeQueryResult(queryParams, List.of());
                    })
            );
    }

    public <T> Promise<T> transaction(Function<interface_.Transaction, Promise<T>> callback) {
        return runExclusiveTransaction(() -> {
            inTransaction = true;
            var tx = new interface_.Transaction() {
                private volatile boolean txClosed;

                @Override
                public <R> Promise<interface_.Results<R>> query(
                    String query,
                    Object[] params,
                    interface_.QueryOptions options
                ) {
                    if (txClosed) {
                        return Promise.reject(new IllegalStateException("Transaction is closed"));
                    }
                    return base.this.query(query, params, options);
                }

                @Override
                public <R> Promise<interface_.Results<R>> sql(List<String> strings, Object... params) {
                    if (txClosed) {
                        return Promise.reject(new IllegalStateException("Transaction is closed"));
                    }
                    return base.this.sql(strings, params).then(result -> {
                        @SuppressWarnings("unchecked")
                        var cast = (interface_.Results<R>) result;
                        return cast;
                    });
                }

                @Override
                public Promise<List<interface_.Results<Map<String, Object>>>> exec(
                    String query,
                    interface_.QueryOptions options
                ) {
                    if (txClosed) {
                        return Promise.reject(new IllegalStateException("Transaction is closed"));
                    }
                    return base.this.exec(query, options);
                }

                @Override
                public Promise<Void> rollback() {
                    txClosed = true;
                    return Promise.resolve(null);
                }

                @Override
                public Promise<java.util.function.Function<interface_.Transaction, Promise<Void>>> listen(
                    String channel,
                    java.util.function.Consumer<String> callback
                ) {
                    /*
                     * Removed transaction-scoped listen from:
                     * pglite/src/pglite/base.ts
                     *
                     * async listen(channel, callback) {
                     *   return await this.listen(channel, callback, tx);
                     * }
                     */
                    return Promise.reject(new UnsupportedOperationException("transaction.listen is disabled in local-only JDBC mode"));
                }

                @Override
                public boolean closed() {
                    return txClosed;
                }
            };
            return callback.apply(tx).then(value -> {
                inTransaction = false;
                return value;
            }, error -> {
                inTransaction = false;
                throw new RuntimeException(error);
            });
        });
    }

    protected Promise<interface_.Results<Map<String, Object>>> runQuery(
        String query,
        Object[] params,
        interface_.QueryOptions options
    ) {
        return runExclusiveQuery(() -> {
            var parseOpts = new serializer.ParseOpts();
            parseOpts.text = query;
            if (options != null) {
                parseOpts.types = options.paramTypes();
            }

            var bindOpts = new serializer.BindOpts();
            bindOpts.values = params != null ? params : new Object[0];
            var allMessages = new ArrayList<messages.BackendMessage>();

            return execProtocol(serializer.serialize.parse(parseOpts).toByteArray(), protocolOptionsNoSync(options))
                .then(parseResultObj -> {
                    var parseResult = (interface_.ExecProtocolResult) parseResultObj;
                    allMessages.addAll(parseResult.messages());
                    return execProtocol(serializer.serialize.bind(bindOpts).toByteArray(), protocolOptionsNoSync(options));
                })
                .then(bindResultObj -> {
                    var bindResult = (interface_.ExecProtocolResult) bindResultObj;
                    allMessages.addAll(bindResult.messages());
                    return execProtocol(
                        serializer.serialize.execute(new serializer.ExecOpts()).toByteArray(),
                        protocolOptionsNoSync(options)
                    );
                })
                .then(execResultObj -> {
                    var execResult = (interface_.ExecProtocolResult) execResultObj;
                    allMessages.addAll(execResult.messages());
                    return execProtocol(serializer.serialize.sync().toByteArray(), protocolOptionsNoSync(options));
                })
                .then(resultObj -> {
                    var result = (interface_.ExecProtocolResult) resultObj;
                    allMessages.addAll(result.messages());
                    var parsed = parse.parseResults(
                        allMessages,
                        parsers,
                        options,
                        options != null ? options.blob() : null
                    );
                    if (parsed.isEmpty()) {
                        return new interface_.Results<Map<String, Object>>(List.of(), 0, List.of(), null);
                    }
                    return parsed.getFirst();
                })
                .then(resultObj -> {
                    @SuppressWarnings("unchecked")
                    var result = (interface_.Results<Map<String, Object>>) resultObj;
                    if (!inTransaction) {
                        return syncToFs().then(ignore2 -> result);
                    }
                    return Promise.resolve(result);
                });
        });
    }

    protected Promise<List<interface_.Results<Map<String, Object>>>> runExec(
        String query,
        interface_.QueryOptions options
    ) {
        return runExclusiveQuery(() ->
            execProtocol(serializer.serialize.query(query).toByteArray(), protocolOptionsNoSync(options))
                .then(resultObj -> execProtocol(serializer.serialize.sync().toByteArray(), protocolOptionsNoSync(options))
                    .then(syncResultObj -> {
                        var result = (interface_.ExecProtocolResult) resultObj;
                        var syncResult = (interface_.ExecProtocolResult) syncResultObj;
                        var merged = new ArrayList<messages.BackendMessage>();
                        merged.addAll(result.messages());
                        merged.addAll(syncResult.messages());
                        var parsed = parse.parseResults(
                            merged,
                            parsers,
                            options,
                            options != null ? options.blob() : null
                        );
                        if (!inTransaction) {
                            return syncToFs().then(ignore -> parsed);
                        }
                        return Promise.resolve(parsed);
                    })
                )
        );
    }

    protected static interface_.ExecProtocolOptions protocolOptionsNoSync(interface_.QueryOptions options) {
        return new interface_.ExecProtocolOptions(
            false,
            false,
            options != null ? options.onNotice() : null
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
            try {
                fn.get().then(value -> {
                    semaphore.release();
                    return value;
                }, error -> {
                    semaphore.release();
                    if (error instanceof Throwable throwable) {
                        throw new RuntimeException(throwable);
                    }
                    throw new RuntimeException(String.valueOf(error));
                }).then(value -> {
                    @SuppressWarnings("unchecked")
                    var cast = (T) value;
                    resolve.run(cast);
                    return null;
                }, err -> {
                    reject.run(err instanceof Throwable ? (Throwable) err : new RuntimeException(String.valueOf(err)));
                    return null;
                });
            } catch (Throwable e) {
                semaphore.release();
                reject.run(e);
            }
        });
    }

    @FunctionalInterface
    protected interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
