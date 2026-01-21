package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.BackendMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.DatabaseError;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.ParameterDescriptionMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.RowDescriptionMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.serializer;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.serializer.serialize;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Blob;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.DescribeQueryResult;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.ExecProtocolOptions;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.ExecProtocolResult;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.QueryOptions;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Transaction;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.templating.TemplateStringsArray;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class base {
    @SuppressWarnings("unchecked")
    public Map<Integer, types.Serializer> serializers = new HashMap<>(
        (Map<Integer, types.Serializer>) (Map<?, ?>) types.serializers
    );
    @SuppressWarnings("unchecked")
    public Map<Integer, types.Parser> parsers = new HashMap<>(
        (Map<Integer, types.Parser>) (Map<?, ?>) types.parsers
    );
    private boolean arrayTypesInitialized = false;

    // # Abstract properties:
    public abstract int debug();

    // # Private properties:
    private boolean inTransaction = false;

    // # Abstract methods:

    /**
     * Execute a postgres wire protocol message
     * @param message The postgres wire protocol message to execute
     * @returns The result of the query
     */
    public abstract CompletableFuture<ExecProtocolResult> execProtocol(
        Uint8Array message,
        ExecProtocolOptions options
    );

    /**
     * Execute a postgres wire protocol message
     * @param message The postgres wire protocol message to execute
     * @returns The parsed results of the query
     */
    public abstract CompletableFuture<List<BackendMessage>> execProtocolStream(
        Uint8Array message,
        ExecProtocolOptions options
    );

    /**
     * Execute a postgres wire protocol message directly without wrapping the response.
     * Only use if `execProtocol()` doesn't suite your needs.
     *
     * **Warning:** This bypasses PGlite's protocol wrappers that manage error/notice messages,
     * transactions, and notification listeners. Only use if you need to bypass these wrappers and
     * don't intend to use the above features.
     *
     * @param message The postgres wire protocol message to execute
     * @returns The direct message data response produced by Postgres
     */
    public abstract CompletableFuture<Uint8Array> execProtocolRaw(
        Uint8Array message,
        ExecProtocolOptions options
    );

    /**
     * Sync the database to the filesystem
     * @returns Promise that resolves when the database is synced to the filesystem
     */
    public abstract CompletableFuture<Void> syncToFs();

    /**
     * Handle a file attached to the current query
     * @param file The file to handle
     */
    public abstract CompletableFuture<Void> _handleBlob(Blob blob);

    /**
     * Get the written file
     */
    public abstract CompletableFuture<Blob> _getWrittenBlob();

    /**
     * Cleanup the current file
     */
    public abstract CompletableFuture<Void> _cleanupBlob();

    public abstract CompletableFuture<Void> _checkReady();
    public abstract <T> CompletableFuture<T> _runExclusiveQuery(
        Supplier<CompletableFuture<T>> fn
    );
    public abstract <T> CompletableFuture<T> _runExclusiveTransaction(
        Supplier<CompletableFuture<T>> fn
    );

    /**
     * Listen for notifications on a channel
     */
    public abstract CompletableFuture<Function<Transaction, CompletableFuture<Void>>> listen(
        String channel,
        Consumer<String> callback,
        Transaction tx
    );

    // # Concrete implementations:

    /**
     * Initialize the array types
     * The oid if the type of an element and the typarray is the oid of the type of the
     * array.
     * We extract these from the database then create the serializers/parsers for
     * each type.
     * This should be called at the end of #init() in the implementing class.
     */
    public CompletableFuture<Void> _initArrayTypes(InitArrayTypesOptions options) {
        var force = options != null && Boolean.TRUE.equals(options.force);
        if (this.arrayTypesInitialized && !force) {
            return CompletableFuture.completedFuture(null);
        }
        this.arrayTypesInitialized = true;

        return this.query(
            """
            SELECT b.oid, b.typarray
            FROM pg_catalog.pg_type a
            LEFT JOIN pg_catalog.pg_type b ON b.oid = a.typelem
            WHERE a.typcategory = 'A'
            GROUP BY b.oid, b.typarray
            ORDER BY b.oid
            """,
            null,
            null
        ).thenAccept(
            typesResult -> {
                for (var typeObj : typesResult.rows) {
                    var type = (Map<?, ?>) typeObj;
                    var oid = ((Number) type.get("oid")).intValue();
                    var typarray = ((Number) type.get("typarray")).intValue();
                    this.serializers.put(
                        typarray,
                        x -> types.arraySerializer(
                            x,
                            this.serializers.get(oid),
                            typarray
                        )
                    );
                    this.parsers.put(
                        typarray,
                        (x, typeId) -> types.arrayParser(
                            x,
                            this.parsers.get(oid),
                            typarray
                        )
                    );
                }
            }
        );
    }

    private CompletableFuture<List<BackendMessage>> execProtocolNoSync(
        Uint8Array message,
        QueryOptions options
    ) {
        var execOptions = new ExecProtocolOptions();
        if (options != null) {
            execOptions.onNotice = options.onNotice;
        }
        execOptions.syncToFs = false;
        return this.execProtocolStream(message, execOptions);
    }

    /**
     * Re-syncs the array types from the database
     * This is useful if you add a new type to the database and want to use it, otherwise pglite won't recognize it.
     */
    public CompletableFuture<Void> refreshArrayTypes() {
        var options = new InitArrayTypesOptions();
        options.force = true;
        return this._initArrayTypes(options);
    }

    /**
     * Execute a single SQL statement
     * This uses the "Extended Query" postgres wire protocol message.
     * @param query The query to execute
     * @param params Optional parameters for the query
     * @returns The result of the query
     */
    public <T> CompletableFuture<Results> query(
        String query,
        Object[] params,
        QueryOptions options
    ) {
        return this._checkReady().thenCompose(
            // We wrap the public query method in the transaction mutex to ensure that
            // only one query can be executed at a time and not concurrently with a
            // transaction.
            ignored -> this._runExclusiveTransaction(
                () -> this.runQuery(query, params, options)
            )
        );
    }

    /**
     * Execute a single SQL statement like with {@link PGlite.query}, but with a
     * templated statement where template values will be treated as parameters.
     *
     * You can use helpers from `/template` to further format the query with
     * identifiers, raw SQL, and nested statements.
     *
     * This uses the "Extended Query" postgres wire protocol message.
     *
     * @param query The query to execute with parameters as template values
     * @returns The result of the query
     *
     * @example
     * ```ts
     * const results = await db.sql`SELECT * FROM ${identifier`foo`} WHERE id = ${id}`
     * ```
     */
    public <T> CompletableFuture<Results> sql(
        TemplateStringsArray sqlStrings,
        Object... params
    ) {
        var templated = templating.query(sqlStrings, params);
        return this.query(templated.query, templated.params, null);
    }

    /**
     * Execute a SQL query, this can have multiple statements.
     * This uses the "Simple Query" postgres wire protocol message.
     * @param query The query to execute
     * @returns The result of the query
     */
    public CompletableFuture<List<Results>> exec(
        String query,
        QueryOptions options
    ) {
        return this._checkReady().thenCompose(
            // We wrap the public exec method in the transaction mutex to ensure that
            // only one query can be executed at a time and not concurrently with a
            // transaction.
            ignored -> this._runExclusiveTransaction(
                () -> this.runExec(query, options)
            )
        );
    }

    /**
     * Internal method to execute a query
     * Not protected by the transaction mutex, so it can be used inside a transaction
     * @param query The query to execute
     * @param params Optional parameters for the query
     * @returns The result of the query
     */
    private <T> CompletableFuture<Results> runQuery(
        String query,
        Object[] params,
        QueryOptions options
    ) {
        return this._runExclusiveQuery(
            () -> {
                // We need to parse, bind and execute a query with parameters
                this.log("runQuery", query, params, options);
                var activeParams = params != null ? params : new Object[0];
                return this._handleBlob(options != null ? options.blob : null)
                    .thenCompose(
                        ignored -> {
                            var results = new ArrayList<BackendMessage>();
                            var errorHolder = new Throwable[1];
                            var errorFromSync = new boolean[1];

                            var parseOpts = new serializer.ParseOpts();
                            parseOpts.text = query;
                            if (options != null) {
                                parseOpts.types = options.paramTypes;
                            }

                            var execFuture = this.execProtocolNoSync(
                                serialize.parse(parseOpts),
                                options
                            ).thenCompose(
                                parseResults -> {
                                    results.addAll(parseResults);

                                    var describeOpts = new serializer.PortalOpts();
                                    describeOpts.type = "S";

                                    return this.execProtocolNoSync(
                                        serialize.describe(describeOpts),
                                        options
                                    ).thenCompose(
                                        describeMessages -> {
                                            var dataTypeIDs = parse.parseDescribeStatementResults(
                                                describeMessages
                                            );

                                            var values = new Object[activeParams.length];
                                            for (var i = 0; i < activeParams.length; i++) {
                                                var param = activeParams[i];
                                                var oid = dataTypeIDs[i];
                                                if (param == null) {
                                                    values[i] = null;
                                                    continue;
                                                }
                                                var serializer = options != null
                                                        && options.serializers != null
                                                    ? options.serializers.get(oid)
                                                    : null;
                                                if (serializer == null) {
                                                    serializer = this.serializers.get(oid);
                                                }
                                                if (serializer != null) {
                                                    values[i] = serializer.serialize(param);
                                                } else {
                                                    values[i] = param.toString();
                                                }
                                            }

                                            var bindOpts = new serializer.BindOpts();
                                            bindOpts.values = values;

                                            return this.execProtocolNoSync(
                                                serialize.bind(bindOpts),
                                                options
                                            ).thenCompose(
                                                bindResults -> {
                                                    results.addAll(bindResults);

                                                    var portalOpts = new serializer.PortalOpts();
                                                    portalOpts.type = "P";

                                                    return this.execProtocolNoSync(
                                                        serialize.describe(portalOpts),
                                                        options
                                                    );
                                                }
                                            ).thenCompose(
                                                portalResults -> {
                                                    results.addAll(portalResults);

                                                    return this.execProtocolNoSync(
                                                        serialize.execute(new serializer.ExecOpts()),
                                                        options
                                                    );
                                                }
                                            ).thenApply(
                                                executeResults -> {
                                                    results.addAll(executeResults);
                                                    return results;
                                                }
                                            );
                                        }
                                    );
                                }
                            );

                            return execFuture.handle(
                                (resultMessages, error) -> {
                                    if (error != null) {
                                        errorHolder[0] = error;
                                    }
                                    return results;
                                }
                            ).thenCompose(
                                ignoredResults -> this.execProtocolNoSync(
                                    serialize.sync(),
                                    options
                                ).handle(
                                    (syncMessages, syncError) -> {
                                        if (syncMessages != null) {
                                            results.addAll(syncMessages);
                                        }
                                        if (syncError != null) {
                                            errorHolder[0] = syncError;
                                            errorFromSync[0] = true;
                                        }
                                        return results;
                                    }
                                )
                            ).thenCompose(
                                resultMessages -> {
                                    if (errorHolder[0] != null) {
                                        var cause = errorHolder[0] instanceof CompletionException
                                            ? errorHolder[0].getCause()
                                            : errorHolder[0];
                                        if (!errorFromSync[0] && cause instanceof DatabaseError) {
                                            var data = new errors.MakePGliteErrorData();
                                            data.e = (DatabaseError) cause;
                                            data.options = options;
                                            data.params = activeParams;
                                            data.query = query;
                                            throw new CompletionException(
                                                errors.makePGliteError(data)
                                            );
                                        }
                                        throw new CompletionException(cause);
                                    }
                                    return this._cleanupBlob().thenCompose(
                                        ignoredCleanup -> {
                                            var syncFuture = !this.inTransaction
                                                ? this.syncToFs()
                                                : CompletableFuture.completedFuture(null);
                                            return syncFuture.thenCompose(
                                                ignoredSync -> this._getWrittenBlob().thenApply(
                                                    blob -> parse.parseResults(
                                                        resultMessages,
                                                        this.parsers,
                                                        options,
                                                        blob
                                                    ).get(0)
                                                )
                                            );
                                        }
                                    );
                                }
                            );
                        }
                    );
            }
        );
    }

    /**
     * Internal method to execute a query
     * Not protected by the transaction mutex, so it can be used inside a transaction
     * @param query The query to execute
     * @param params Optional parameters for the query
     * @returns The result of the query
     */
    private CompletableFuture<List<Results>> runExec(
        String query,
        QueryOptions options
    ) {
        return this._runExclusiveQuery(
            () -> {
                // No params so we can just send the query
                this.log("runExec", query, options);
                return this._handleBlob(options != null ? options.blob : null)
                    .thenCompose(
                        ignored -> {
                            var results = new ArrayList<BackendMessage>();
                            var errorHolder = new Throwable[1];
                            var errorFromSync = new boolean[1];
                            var execFuture = this.execProtocolNoSync(
                                serialize.query(query),
                                options
                            ).thenApply(
                                messageResults -> {
                                    results.addAll(messageResults);
                                    return results;
                                }
                            );

                            return execFuture.handle(
                                (messageResults, error) -> {
                                    if (error != null) {
                                        errorHolder[0] = error;
                                    }
                                    return results;
                                }
                            ).thenCompose(
                                resultMessages -> this.execProtocolNoSync(
                                    serialize.sync(),
                                    options
                                ).handle(
                                    (syncMessages, syncError) -> {
                                        if (syncMessages != null) {
                                            results.addAll(syncMessages);
                                        }
                                        if (syncError != null) {
                                            errorHolder[0] = syncError;
                                            errorFromSync[0] = true;
                                        }
                                        return results;
                                    }
                                )
                            ).thenCompose(
                                resultMessages -> {
                                    if (errorHolder[0] != null) {
                                        var cause = errorHolder[0] instanceof CompletionException
                                            ? errorHolder[0].getCause()
                                            : errorHolder[0];
                                        if (!errorFromSync[0] && cause instanceof DatabaseError) {
                                            var data = new errors.MakePGliteErrorData();
                                            data.e = (DatabaseError) cause;
                                            data.options = options;
                                            data.params = null;
                                            data.query = query;
                                            throw new CompletionException(
                                                errors.makePGliteError(data)
                                            );
                                        }
                                        throw new CompletionException(cause);
                                    }
                                    this._cleanupBlob();
                                    var syncFuture = !this.inTransaction
                                        ? this.syncToFs()
                                        : CompletableFuture.completedFuture(null);
                                    return syncFuture.thenCompose(
                                        ignoredSync -> this._getWrittenBlob().thenApply(
                                            blob -> parse.parseResults(
                                                resultMessages,
                                                this.parsers,
                                                options,
                                                blob
                                            )
                                        )
                                    );
                                }
                            );
                        }
                    );
            }
        );
    }

    /**
     * Describe a query
     * @param query The query to describe
     * @returns A description of the result types for the query
     */
    public CompletableFuture<DescribeQueryResult> describeQuery(
        String query,
        QueryOptions options
    ) {
        var messages = new ArrayList<BackendMessage>();
        var errorHolder = new Throwable[1];
        var errorFromSync = new boolean[1];

        var parseOpts = new serializer.ParseOpts();
        parseOpts.text = query;
        if (options != null) {
            parseOpts.types = options.paramTypes;
        }

        var describeOpts = new serializer.PortalOpts();
        describeOpts.type = "S";

        return this.execProtocolNoSync(
            serialize.parse(parseOpts),
            options
        ).thenCompose(
            ignored -> this.execProtocolNoSync(
                serialize.describe(describeOpts),
                options
            )
        ).thenApply(
            describeMessages -> {
                messages.addAll(describeMessages);
                return null;
            }
        ).handle(
            (ignored, error) -> {
                if (error != null) {
                    errorHolder[0] = error;
                }
                return null;
            }
        ).thenCompose(
            ignored -> this.execProtocolNoSync(
                serialize.sync(),
                options
            ).handle(
                (syncMessages, syncError) -> {
                    if (syncMessages != null) {
                        messages.addAll(syncMessages);
                    }
                    if (syncError != null) {
                        errorHolder[0] = syncError;
                        errorFromSync[0] = true;
                    }
                    return null;
                }
            )
        ).thenApply(
            ignored -> {
                if (errorHolder[0] != null) {
                    var cause = errorHolder[0] instanceof CompletionException
                        ? errorHolder[0].getCause()
                        : errorHolder[0];
                    if (!errorFromSync[0] && cause instanceof DatabaseError) {
                        var data = new errors.MakePGliteErrorData();
                        data.e = (DatabaseError) cause;
                        data.options = options;
                        data.params = null;
                        data.query = query;
                        throw new CompletionException(
                            errors.makePGliteError(data)
                        );
                    }
                    throw new CompletionException(cause);
                }
                var paramDescription = (ParameterDescriptionMessage) null;
                var resultDescription = (RowDescriptionMessage) null;
                for (var message : messages) {
                    if (message instanceof ParameterDescriptionMessage) {
                        paramDescription = (ParameterDescriptionMessage) message;
                    } else if (message instanceof RowDescriptionMessage) {
                        resultDescription = (RowDescriptionMessage) message;
                    }
                }

                var queryParams = new ArrayList<DescribeQueryResult.QueryParam>();
                if (paramDescription != null) {
                    for (var dataTypeID : paramDescription.dataTypeIDs) {
                        var queryParam = new DescribeQueryResult.QueryParam();
                        queryParam.dataTypeID = dataTypeID;
                        queryParam.serializer = this.serializers.get(dataTypeID);
                        queryParams.add(queryParam);
                    }
                }

                var resultFields = new ArrayList<DescribeQueryResult.ResultField>();
                if (resultDescription != null) {
                    for (var field : resultDescription.fields) {
                        var resultField = new DescribeQueryResult.ResultField();
                        resultField.name = field.name;
                        resultField.dataTypeID = field.dataTypeID;
                        resultField.parser = this.parsers.get(field.dataTypeID);
                        resultFields.add(resultField);
                    }
                }

                var result = new DescribeQueryResult();
                result.queryParams = queryParams;
                result.resultFields = resultFields;
                return result;
            }
        );
    }

    /**
     * Execute a transaction
     * @param callback A callback function that takes a transaction object
     * @returns The result of the transaction
     */
    public <T> CompletableFuture<T> transaction(
        Function<Transaction, CompletableFuture<T>> callback
    ) {
        return this._checkReady().thenCompose(
            ignored -> this.<T>_runExclusiveTransaction(
                () -> this.runExec("BEGIN", null).thenCompose(
                    ignoredBegin -> {
                        this.inTransaction = true;

                        // Once a transaction is closed, we throw an error if it's used again
                        var closed = new AtomicBoolean(false);
                        var checkClosed = (Runnable) () -> {
                            if (closed.get()) {
                                throw new RuntimeException("Transaction is closed");
                            }
                        };

                        var tx = new Transaction() {
                            @Override
                            public CompletableFuture<Results> query(
                                String query,
                                Object[] params,
                                QueryOptions options
                            ) {
                                checkClosed.run();
                                return runQuery(query, params, options);
                            }

                            @Override
                            public CompletableFuture<Results> sql(
                                TemplateStringsArray sqlStrings,
                                Object... params
                            ) {
                                var templated = templating.query(sqlStrings, params);
                                return runQuery(templated.query, templated.params, null);
                            }

                            @Override
                            public CompletableFuture<List<Results>> exec(
                                String query,
                                QueryOptions options
                            ) {
                                checkClosed.run();
                                return runExec(query, options);
                            }

                            @Override
                            public CompletableFuture<Void> rollback() {
                                checkClosed.run();
                                // Rollback and set the closed flag to prevent further use of this
                                // transaction
                                return runExec("ROLLBACK", null).thenApply(
                                    ignoredRollback -> {
                                        closed.set(true);
                                        return null;
                                    }
                                );
                            }

                            @Override
                            public CompletableFuture<Function<Transaction, CompletableFuture<Void>>> listen(
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

                        var callbackFuture = (CompletableFuture<T>) null;
                        try {
                            callbackFuture = callback.apply(tx);
                        } catch (Throwable e) {
                            callbackFuture = CompletableFuture.failedFuture(e);
                        }

                        return callbackFuture.<CompletableFuture<T>>handle(
                            (result, error) -> {
                                if (error == null) {
                                    if (!closed.get()) {
                                        closed.set(true);
                                        return runExec("COMMIT", null).handle(
                                            (ignoredCommit, commitError) -> {
                                                this.inTransaction = false;
                                                if (commitError != null) {
                                                    var commitCause = commitError instanceof CompletionException
                                                        ? commitError.getCause()
                                                        : commitError;
                                                    throw new CompletionException(commitCause);
                                                }
                                                return result;
                                            }
                                        );
                                    }
                                    this.inTransaction = false;
                                    return CompletableFuture.completedFuture(result);
                                }

                                var cause = error instanceof CompletionException
                                    ? error.getCause()
                                    : error;
                                if (!closed.get()) {
                                    return runExec("ROLLBACK", null).thenApply(
                                        ignoredRollback -> {
                                            this.inTransaction = false;
                                            throw new CompletionException(cause);
                                        }
                                    );
                                }
                                this.inTransaction = false;
                                throw new CompletionException(cause);
                            }
                        ).thenCompose(resultFuture -> resultFuture);
                    }
                )
            )
        );
    }

    /**
     * Run a function exclusively, no other transactions or queries will be allowed
     * while the function is running.
     * This is useful when working with the execProtocol methods as they are not blocked,
     * and do not block the locks used by transactions and queries.
     * @param fn The function to run
     * @returns The result of the function
     */
    public <T> CompletableFuture<T> runExclusive(
        Supplier<CompletableFuture<T>> fn
    ) {
        return this._runExclusiveQuery(fn);
    }

    /**
     * Internal log function
     */
    private void log(Object... args) {
        if (this.debug() > 0) {
            var builder = new StringBuilder();
            for (var i = 0; i < args.length; i++) {
                if (i > 0) {
                    builder.append(" ");
                }
                builder.append(String.valueOf(args[i]));
            }
            System.out.println(builder);
        }
    }

    public static final class InitArrayTypesOptions {
        public Boolean force;
    }
}
