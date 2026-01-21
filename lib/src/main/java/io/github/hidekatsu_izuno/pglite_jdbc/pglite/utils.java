package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import com.dylibso.chicory.runtime.ImportValues;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.WasmModule;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.BackendMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.ParameterDescriptionMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.serializer;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.serializer.serialize;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.ArrayBuffer;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class utils {
    public static final boolean IN_NODE = true;

    private static CompletableFuture<byte[]> wasmDownloadPromise;

    public static CompletableFuture<Void> startWasmDownload() {
        if (IN_NODE || wasmDownloadPromise != null) {
            return CompletableFuture.completedFuture(null);
        }
        var modulePath = wasmModulePath();
        wasmDownloadPromise = CompletableFuture.supplyAsync(() -> readFile(modulePath));
        return wasmDownloadPromise.thenApply(ignored -> null);
    }

    // This is a global cache of the PGlite Wasm module to avoid having to re-download or
    // compile it on subsequent calls.
    private static WasmModule cachedWasmModule;

    private static final int TEXT = 25;

    private static final Pattern PARAMETER_PATTERN = Pattern.compile("\\$([0-9]+)");

    public static final class WasmInstantiation {
        public final Instance instance;
        public final WasmModule module;

        public WasmInstantiation(Instance instance, WasmModule module) {
            this.instance = instance;
            this.module = module;
        }
    }

    public static CompletableFuture<WasmInstantiation> instantiateWasm(
        ImportValues imports,
        WasmModule module
    ) {
        if (module != null || cachedWasmModule != null) {
            var activeModule = module != null ? module : cachedWasmModule;
            return CompletableFuture.supplyAsync(
                () -> new WasmInstantiation(
                    Instance.builder(activeModule).withImportValues(imports).build(),
                    activeModule
                )
            );
        }

        var modulePath = wasmModulePath();
        if (IN_NODE) {
            return CompletableFuture.supplyAsync(
                () -> {
                    var bytes = readFile(modulePath);
                    var newModule = Parser.parse(bytes);
                    var instance = Instance.builder(newModule)
                        .withImportValues(imports)
                        .build();
                    cachedWasmModule = newModule;
                    return new WasmInstantiation(instance, newModule);
                }
            );
        } else {
            if (wasmDownloadPromise == null) {
                wasmDownloadPromise = CompletableFuture.supplyAsync(
                    () -> readFile(modulePath)
                );
            }
            return wasmDownloadPromise.thenApply(
                bytes -> {
                    var newModule = Parser.parse(bytes);
                    var instance = Instance.builder(newModule)
                        .withImportValues(imports)
                        .build();
                    cachedWasmModule = newModule;
                    return new WasmInstantiation(instance, newModule);
                }
            );
        }
    }

    public static CompletableFuture<ArrayBuffer> getFsBundle() {
        var fsBundlePath = fsBundlePath();
        if (IN_NODE) {
            return CompletableFuture.supplyAsync(
                () -> new Uint8Array(readFile(fsBundlePath)).buffer
            );
        } else {
            return CompletableFuture.supplyAsync(
                () -> new Uint8Array(readFile(fsBundlePath)).buffer
            );
        }
    }

    public static String uuid() {
        // best case, `crypto.randomUUID` is available
        var bytes = new byte[16];
        try {
            var secureRandom = new SecureRandom();
            secureRandom.nextBytes(bytes);
        } catch (RuntimeException e) {
            var fallback = new java.util.Random();
            fallback.nextBytes(bytes);
        }

        // `crypto.getRandomValues` is available even in non-secure contexts
        // fallback to Math.random, if the Crypto API is completely missing
        bytes[6] = (byte) ((bytes[6] & 0x0f) | 0x40); // Set the 4 most significant bits to 0100
        bytes[8] = (byte) ((bytes[8] & 0x3f) | 0x80); // Set the 2 most significant bits to 10

        var hexValues = new String[bytes.length];
        for (var i = 0; i < bytes.length; i++) {
            var hex = Integer.toHexString(bytes[i] & 0xFF);
            hexValues[i] = hex.length() == 1 ? "0" + hex : hex;
        }

        return hexValues[0]
            + hexValues[1]
            + hexValues[2]
            + hexValues[3]
            + "-"
            + hexValues[4]
            + hexValues[5]
            + "-"
            + hexValues[6]
            + hexValues[7]
            + "-"
            + hexValues[8]
            + hexValues[9]
            + "-"
            + hexValues[10]
            + hexValues[11]
            + hexValues[12]
            + hexValues[13]
            + hexValues[14]
            + hexValues[15];
    }

    /**
     * Formats a query with parameters
     * Expects that any tables/relations referenced in the query exist in the database
     * due to requiring them to be present to describe the parameters types.
     * `tx` is optional, and to be used when formatQuery is called during a transaction.
     * @param pg - The PGlite instance
     * @param query - The query to format
     * @param params - The parameters to format the query with
     * @param tx - The transaction to use, defaults to the PGlite instance
     * @returns The formatted query
     */
    public static CompletableFuture<String> formatQuery(
        PGliteInterface pg,
        String query,
        Object[] params,
        Transaction tx
    ) {
        if (params == null || params.length == 0) {
            // no params so no formatting needed
            return CompletableFuture.completedFuture(query);
        }

        var queryExecutor = tx != null ? tx : pg;
        var messages = new ArrayList<BackendMessage>();
        var options = new ExecProtocolOptions();
        options.syncToFs = false;

        var parseOpts = new serializer.ParseOpts();
        parseOpts.text = query;

        var describeOpts = new serializer.PortalOpts();
        describeOpts.type = "S";

        // Get the types of the parameters
        var parseDescribeFuture = pg.execProtocol(
            serialize.parse(parseOpts),
            options
        ).thenCompose(
            ignored -> pg.execProtocol(serialize.describe(describeOpts), options)
        ).thenApply(
            result -> {
                messages.addAll(result.messages);
                return null;
            }
        );

        var syncFuture = parseDescribeFuture.handle(
            (ignored, error) -> {
                var syncCall = pg.execProtocol(serialize.sync(), options)
                    .thenApply(
                        result -> {
                            messages.addAll(result.messages);
                            return null;
                        }
                    );
                if (error != null) {
                    return syncCall.handle(
                        (ignoredSync, syncError) -> {
                            if (syncError != null) {
                                throw new CompletionException(syncError);
                            }
                            throw new CompletionException(error);
                        }
                    );
                }
                return syncCall;
            }
        ).thenCompose(Function.identity());

        return syncFuture.thenCompose(
            ignored -> {
                var dataTypeIDs = parseDescribeStatementResults(messages);
                // replace $1, $2, etc with  %1L, %2L, etc
                var subbedQuery = substituteParameters(query);

                var paramTypes = new int[dataTypeIDs.length + 1];
                paramTypes[0] = TEXT;
                if (dataTypeIDs.length > 0) {
                    System.arraycopy(dataTypeIDs, 0, paramTypes, 1, dataTypeIDs.length);
                }

                var formatArgs = new StringBuilder();
                for (var i = 0; i < params.length; i++) {
                    if (i > 0) {
                        formatArgs.append(", ");
                    }
                    formatArgs.append("$").append(i + 2);
                }

                var formattedQuery =
                    "SELECT format($1, " + formatArgs + ") as query";
                var queryParams = new Object[params.length + 1];
                queryParams[0] = subbedQuery;
                System.arraycopy(params, 0, queryParams, 1, params.length);

                var queryOptions = new QueryOptions();
                queryOptions.paramTypes = paramTypes;

                return queryExecutor.query(
                    formattedQuery,
                    queryParams,
                    queryOptions
                ).thenApply(
                    result -> result.rows.get(0).query
                );
            }
        );
    }

    /**
     * Debounce a function to ensure that only one instance of the function is running at
     * a time.
     * - If the function is called while an instance is already running, the new
     * call is scheduled to run after the current instance completes.
     * - If there is already a scheduled call, it is replaced with the new call.
     * @param fn - The function to debounce
     * @returns A debounced version of the function
     */
    public static <R> AsyncFunction<R> debounceMutex(AsyncFunction<R> fn) {
        class NextCall {
            private final Object[] args;
            private final CompletableFuture<R> future;

            private NextCall(Object[] args, CompletableFuture<R> future) {
                this.args = args;
                this.future = future;
            }
        }

        var semaphore = new Semaphore(1);
        var next = new AtomicReference<NextCall>();
        var isRunning = new AtomicBoolean(false);

        var processNext = new Runnable() {
            @Override
            public void run() {
                NextCall call;
                try {
                    semaphore.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new CompletionException(e);
                }
                try {
                    call = next.getAndSet(null);
                    if (call == null) {
                        isRunning.set(false);
                        return;
                    }
                } finally {
                    semaphore.release();
                }

                isRunning.set(true);
                fn.apply(call.args).whenComplete(
                    (result, error) -> {
                        if (error != null) {
                            call.future.completeExceptionally(error);
                        } else {
                            call.future.complete(result);
                        }
                        run();
                    }
                );
            }
        };

        return (Object... args) -> {
            var future = new CompletableFuture<R>();
            var start = false;
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
                return future;
            }
            try {
                var pending = next.get();
                if (pending != null) {
                    pending.future.complete(null);
                }
                next.set(new NextCall(args, future));
                if (!isRunning.get()) {
                    isRunning.set(true);
                    start = true;
                }
            } finally {
                semaphore.release();
            }
            if (start) {
                processNext.run();
            }
            return future;
        };
    }

    /**
     * Postgresql handles quoted names as CaseSensitive and unquoted as lower case.
     * If input is quoted, returns an unquoted string (same casing)
     * If input is unquoted, returns a lower-case string
     */
    public static String toPostgresName(String input) {
        var output = "";
        if (input.startsWith("\"") && input.endsWith("\"")) {
            // Postgres sensitive case
            output = input.substring(1, input.length() - 1);
        } else {
            // Postgres case insensitive - all to lower
            output = input.toLowerCase();
        }
        return output;
    }

    private static String substituteParameters(String query) {
        var matcher = PARAMETER_PATTERN.matcher(query);
        var buffer = new StringBuffer();
        while (matcher.find()) {
            var num = matcher.group(1);
            matcher.appendReplacement(buffer, "%" + num + "L");
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static int[] parseDescribeStatementResults(
        List<BackendMessage> messages
    ) {
        for (var message : messages) {
            if (message instanceof ParameterDescriptionMessage) {
                return ((ParameterDescriptionMessage) message).dataTypeIDs;
            }
        }
        return new int[0];
    }

    private static byte[] readFile(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new CompletionException(e);
        }
    }

    private static Path wasmModulePath() {
        return Paths.get("pglite", "src", "pglite", "release", "pglite.wasm");
    }

    private static Path fsBundlePath() {
        return Paths.get("pglite", "src", "pglite", "release", "pglite.data");
    }

    public interface AsyncFunction<R> {
        CompletableFuture<R> apply(Object... args);
    }

    public interface QueryExecutor {
        CompletableFuture<Results> query(
            String query,
            Object[] params,
            QueryOptions options
        );
    }

    public interface PGliteInterface extends QueryExecutor {
        CompletableFuture<ExecProtocolResult> execProtocol(
            Uint8Array message,
            ExecProtocolOptions options
        );
    }

    public interface Transaction extends QueryExecutor {
    }

    public static final class ExecProtocolOptions {
        public Boolean syncToFs;
    }

    public static final class QueryOptions {
        public int[] paramTypes;
    }

    public static final class ExecProtocolResult {
        public List<BackendMessage> messages;
        public Uint8Array data;
    }

    public static final class Results {
        public List<FormatQueryRow> rows;
    }

    public static final class FormatQueryRow {
        public String query;
    }

    private utils() {
    }
}
