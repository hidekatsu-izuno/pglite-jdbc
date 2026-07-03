package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.serializer;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class base {
    private static final boolean TRACE_PROTOCOL = Boolean.getBoolean("pglite.trace_protocol");
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

    public abstract Promise<byte[]> execProtocolRaw(
        byte[] message,
        interface_.ExecProtocolOptions options
    );

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

    protected interface_.ExecProtocolResult execProtocolSync(
        byte[] message,
        interface_.ExecProtocolOptions options
    ) {
        return await(this.execProtocol(message, options));
    }

    protected byte[] execProtocolRawSyncInternal(
        byte[] message,
        interface_.ExecProtocolOptions options
    ) {
        return await(this.execProtocolRaw(message, options));
    }

    protected void syncToFsSync() {
        await(this.syncToFs());
    }

    protected void handleBlobSync(byte[] blob) {
        await(this.handleBlob(blob));
    }

    protected byte[] getWrittenBlobSync() {
        return await(this.getWrittenBlob());
    }

    protected void cleanupBlobSync() {
        await(this.cleanupBlob());
    }

    protected void checkReadySync() {
        await(this.checkReady());
    }

    protected <T> T runExclusiveQuerySync(ThrowingSupplier<T> fn) {
        return runWithSemaphoreSync(queryMutex, fn);
    }

    protected <T> T runExclusiveTransactionSync(ThrowingSupplier<T> fn) {
        return runWithSemaphoreSync(transactionMutex, fn);
    }

    protected <T> Promise<T> runExclusiveQuery(ThrowingSupplier<Promise<T>> fn) {
        return runWithSemaphore(queryMutex, fn);
    }

    protected <T> Promise<T> runExclusiveTransaction(ThrowingSupplier<Promise<T>> fn) {
        return runWithSemaphore(transactionMutex, fn);
    }

    private List<messages.BackendMessage> execProtocolNoSyncSync(
        byte[] message,
        interface_.QueryOptions options
    ) {
        var execOptions = new interface_.ExecProtocolOptions(
            false,
            true,
            options != null ? options.onNotice() : null
        );
        return execProtocolSync(message, execOptions).messages();
    }

    public Promise<Void> refreshArrayTypes() {
        return asPromise(() -> {
            refreshArrayTypesSync();
            return null;
        });
    }

    protected void refreshArrayTypesSync() {
        if (this.arrayTypesInitialized) {
            return;
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

        var result = this.querySync(sql, null, null);
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
    }

    public <T> Promise<interface_.Results<T>> query(
        String query,
        Object[] params,
        interface_.QueryOptions options
    ) {
        return asPromise(() -> querySync(query, params, options));
    }

    public <T> interface_.Results<T> querySync(
        String query,
        Object[] params,
        interface_.QueryOptions options
    ) {
        checkReadySync();
        @SuppressWarnings("unchecked")
        var result = (interface_.Results<T>) runExclusiveTransactionSync(
            () -> runQuerySync(query, params, options)
        );
        return result;
    }

    public Promise<List<interface_.Results<Map<String, Object>>>> exec(
        String query,
        interface_.QueryOptions options
    ) {
        return asPromise(() -> execSync(query, options));
    }

    public List<interface_.Results<Map<String, Object>>> execSync(
        String query,
        interface_.QueryOptions options
    ) {
        checkReadySync();
        return runExclusiveTransactionSync(() -> runExecSync(query, options));
    }

    public <T> Promise<interface_.Results<T>> sql(
        List<String> strings,
        Object... params
    ) {
        return asPromise(() -> sqlSync(strings, params));
    }

    public <T> interface_.Results<T> sqlSync(
        List<String> strings,
        Object... params
    ) {
        var templated = templating.query(strings, params);
        return querySync(templated.query(), templated.params().toArray(), null);
    }

    public Promise<interface_.DescribeQueryResult> describeQuery(String query) {
        return describeQuery(query, null);
    }

    public Promise<interface_.DescribeQueryResult> describeQuery(
        String query,
        interface_.QueryOptions options
    ) {
        return asPromise(() -> describeQuerySync(query, options));
    }

    public interface_.DescribeQueryResult describeQuerySync(
        String query,
        interface_.QueryOptions options
    ) {
        checkReadySync();
        var allMessages = new ArrayList<messages.BackendMessage>();
        Throwable errorHolder = null;

        var parseOpts = new serializer.ParseOpts();
        parseOpts.text = query;
        if (options != null) {
            parseOpts.types = options.paramTypes();
        }

        var describeOpts = new serializer.PortalOpts();
        describeOpts.type = "S";

        try {
            execProtocolNoSyncSync(serializer.serialize.parse(parseOpts).toByteArray(), options);
            allMessages.addAll(
                execProtocolNoSyncSync(serializer.serialize.describe(describeOpts).toByteArray(), options)
            );
        } catch (Throwable error) {
            errorHolder = unwrap(error);
        }

        try {
            allMessages.addAll(execProtocolNoSyncSync(serializer.serialize.sync().toByteArray(), options));
        } catch (Throwable syncError) {
            if (errorHolder == null) {
                errorHolder = unwrap(syncError);
            }
        }

        if (errorHolder != null) {
            var cause = unwrap(errorHolder);
            if (cause instanceof messages.DatabaseError dbError) {
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

        var queryParams = new ArrayList<interface_.QueryParamField>();
        if (paramDescription != null) {
            for (var typeId : paramDescription.dataTypeIDs) {
                queryParams.add(new interface_.QueryParamField(typeId, serializerFor(typeId, options)));
            }
        }

        var resultFields = new ArrayList<interface_.ResultField>();
        if (rowDescription != null) {
            for (var field : rowDescription.fields) {
                resultFields.add(
                    new interface_.ResultField(
                        field.name,
                        field.dataTypeID,
                        field.dataTypeModifier,
                        field.tableID,
                        field.columnID,
                        parserFor(field.dataTypeID, options)
                    )
                );
            }
        }

        return new interface_.DescribeQueryResult(queryParams, resultFields);
    }

    private interface_.Serializer serializerFor(int typeId, interface_.QueryOptions options) {
        if (options != null && options.serializers() != null) {
            var serializer = options.serializers().get(typeId);
            if (serializer != null) {
                return serializer;
            }
        }
        var serializer = this.serializers.get(typeId);
        return serializer == null ? null : serializer::serialize;
    }

    private interface_.Parser parserFor(int typeId, interface_.QueryOptions options) {
        if (options != null && options.parsers() != null) {
            var parser = options.parsers().get(typeId);
            if (parser != null) {
                return parser;
            }
        }
        var parser = this.parsers.get(typeId);
        return parser == null ? null : parser::parse;
    }

    public <T> Promise<T> transaction(Function<interface_.Transaction, Promise<T>> callback) {
        return asPromise(() -> transactionSync(callback));
    }

    public <T> T transactionSync(Function<interface_.Transaction, Promise<T>> callback) {
        checkReadySync();
        return runExclusiveTransactionSync(() -> {
            runExecSync("BEGIN", null);
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
                    return asPromise(() -> {
                        checkClosed.run();
                        @SuppressWarnings("unchecked")
                        var result = (interface_.Results<R>) runQuerySync(query, params, options);
                        return result;
                    });
                }

                @Override
                public <R> Promise<interface_.Results<R>> sql(List<String> strings, Object... params) {
                    return asPromise(() -> {
                        checkClosed.run();
                        @SuppressWarnings("unchecked")
                        var result = (interface_.Results<R>) sqlSync(strings, params);
                        return result;
                    });
                }

                @Override
                public Promise<List<interface_.Results<Map<String, Object>>>> exec(
                    String query,
                    interface_.QueryOptions options
                ) {
                    return asPromise(() -> {
                        checkClosed.run();
                        return runExecSync(query, options);
                    });
                }

                @Override
                public Promise<Void> rollback() {
                    return asPromise(() -> {
                        checkClosed.run();
                        runExecSync("ROLLBACK", null);
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

            try {
                var value = await(callbackResult);
                if (!closed.get()) {
                    closed.set(true);
                    runExecSync("COMMIT", null);
                }
                inTransaction = false;
                return value;
            } catch (Throwable callbackError) {
                var cause = unwrap(callbackError);
                if (!closed.get()) {
                    try {
                        runExecSync("ROLLBACK", null);
                    } catch (Throwable ignored) {
                        // Preserve callback failure.
                    }
                }
                inTransaction = false;
                throw asRuntime(cause);
            }
        });
    }

    public <T> Promise<T> runExclusive(Function<Void, Promise<T>> fn) {
        return asPromise(() ->
            runExclusiveQuerySync(() -> await(fn.apply(null)))
        );
    }

    protected interface_.Results<Map<String, Object>> runQuerySync(
        String query,
        Object[] params,
        interface_.QueryOptions options
    ) {
        return runExclusiveQuerySync(() -> {
            var activeParams = params != null ? params : new Object[0];
            handleBlobSync(options != null ? options.blob() : null);
            var allMessages = new ArrayList<messages.BackendMessage>();
            Throwable errorHolder = null;

            var parseOpts = new serializer.ParseOpts();
            parseOpts.text = query;
            if (options != null) {
                parseOpts.types = options.paramTypes();
            }

            try {
                allMessages.addAll(
                    execProtocolNoSyncSync(serializer.serialize.parse(parseOpts).toByteArray(), options)
                );

                var describeStatementOpts = new serializer.PortalOpts();
                describeStatementOpts.type = "S";
                var describeMessages = execProtocolNoSyncSync(
                    serializer.serialize.describe(describeStatementOpts).toByteArray(),
                    options
                );
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
                allMessages.addAll(
                    execProtocolNoSyncSync(serializer.serialize.bind(bindOpts).toByteArray(), options)
                );

                var describePortalOpts = new serializer.PortalOpts();
                describePortalOpts.type = "P";
                allMessages.addAll(
                    execProtocolNoSyncSync(
                        serializer.serialize.describe(describePortalOpts).toByteArray(),
                        options
                    )
                );

                allMessages.addAll(
                    execProtocolNoSyncSync(
                        serializer.serialize.execute(new serializer.ExecOpts()).toByteArray(),
                        options
                    )
                );
            } catch (Throwable error) {
                errorHolder = unwrap(error);
            }

            try {
                allMessages.addAll(execProtocolNoSyncSync(serializer.serialize.sync().toByteArray(), options));
            } catch (Throwable syncError) {
                if (errorHolder == null) {
                    errorHolder = unwrap(syncError);
                }
            }

            if (errorHolder != null) {
                var cause = unwrap(errorHolder);
                if (cause instanceof messages.DatabaseError dbError) {
                    throw errors.makePGliteError(dbError, query, activeParams, options);
                }
                throw asRuntime(cause);
            }

            var blob = getWrittenBlobSync();
            cleanupBlobSync();
            if (!inTransaction) {
                syncToFsSync();
            }
            traceMessages("query", allMessages);
            var parsed = parse.parseResults(allMessages, parsers, options, blob);
            if (parsed.isEmpty()) {
                return new interface_.Results<Map<String, Object>>(
                    List.of(),
                    0,
                    List.of(),
                    blob
                );
            }
            @SuppressWarnings("unchecked")
            var result = (interface_.Results<Map<String, Object>>) (interface_.Results<?>) parsed.getFirst();
            return result;
        });
    }

    protected List<interface_.Results<Map<String, Object>>> runExecSync(
        String query,
        interface_.QueryOptions options
    ) {
        return runExclusiveQuerySync(() -> {
            handleBlobSync(options != null ? options.blob() : null);
            var allMessages = new ArrayList<messages.BackendMessage>();
            Throwable errorHolder = null;

            try {
                allMessages.addAll(
                    execProtocolNoSyncSync(serializer.serialize.query(query).toByteArray(), options)
                );
            } catch (Throwable error) {
                errorHolder = unwrap(error);
            }

            try {
                allMessages.addAll(execProtocolNoSyncSync(serializer.serialize.sync().toByteArray(), options));
            } catch (Throwable syncError) {
                if (errorHolder == null) {
                    errorHolder = unwrap(syncError);
                }
            }

            if (errorHolder != null) {
                var cause = unwrap(errorHolder);
                if (cause instanceof messages.DatabaseError dbError) {
                    throw errors.makePGliteError(dbError, query, null, options);
                }
                throw asRuntime(cause);
            }

            var blob = getWrittenBlobSync();
            cleanupBlobSync();
            if (!inTransaction) {
                syncToFsSync();
            }
            traceMessages("exec", allMessages);
            @SuppressWarnings("unchecked")
            var results = (List<interface_.Results<Map<String, Object>>>) (List<?>) parse.parseResults(
                allMessages,
                parsers,
                options,
                blob
            );
            return results;
        });
    }

    private static void traceMessages(String prefix, List<messages.BackendMessage> messageList) {
        if (!TRACE_PROTOCOL) {
            return;
        }
        var names = new ArrayList<String>();
        for (var message : messageList) {
            names.add(message.name());
        }
        System.err.println("[protocol] " + prefix + " " + names);
    }

    protected static <T> T runWithSemaphoreSync(
        Semaphore semaphore,
        ThrowingSupplier<T> fn
    ) {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        try {
            return fn.get();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            semaphore.release();
        }
    }

    protected static <T> Promise<T> runWithSemaphore(
        Semaphore semaphore,
        ThrowingSupplier<Promise<T>> fn
    ) {
        return asPromise(() ->
            runWithSemaphoreSync(semaphore, () -> await(fn.get()))
        );
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

    protected static <T> Promise<T> asPromise(ThrowingSupplier<T> fn) {
        try {
            return Promise.resolve(fn.get());
        } catch (Throwable e) {
            return Promise.reject(unwrap(e));
        }
    }

    protected static <T> T await(Promise<T> promise) {
        if (promise == null) {
            return null;
        }
        return promise.join();
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
