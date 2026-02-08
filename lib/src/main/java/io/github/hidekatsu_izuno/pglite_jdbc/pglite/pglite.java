package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.BackendMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.CommandCompleteMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.DatabaseError;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.NoticeMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.NotificationResponseMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.parser;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.serializer;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.ArrayBuffer;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.index.ParseDataDirResult;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Blob;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.DescribeQueryResult;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.DumpTarCompressionOptions;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.ExecProtocolOptions;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.ExecProtocolResult;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Extensions;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.PGliteInterface;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.PGliteOptions;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.QueryOptions;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Transaction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Java port shell for pglite.ts.
 * The full Wasm host wiring is provided by release/pglite.java and postgresMod.java.
 */
public class pglite extends base implements PGliteInterface<Extensions> {
    public io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.base.Filesystem fs;
    protected postgresMod.PostgresMod mod;

    public final String dataDir;

    private volatile boolean ready = false;
    private volatile boolean closing = false;
    private volatile boolean closed = false;
    private volatile boolean inTransaction = false;
    private boolean relaxedDurability = false;

    private final CompletableFuture<Void> waitReady;

    private final Semaphore queryMutex = new Semaphore(1);
    private final Semaphore transactionMutex = new Semaphore(1);
    private final Semaphore listenMutex = new Semaphore(1);
    private final Semaphore fsSyncMutex = new Semaphore(1);
    private volatile boolean fsSyncScheduled = false;

    private final int debug;

    private final Extensions extensions;
    private final List<Supplier<CompletableFuture<Void>>> extensionsClose = new ArrayList<>();
    private final Map<String, Object> extensionNamespaces = new ConcurrentHashMap<>();

    private ArrayBuffer queryReadBuffer;
    private List<Uint8Array> queryWriteChunks;

    private int pgliteWrite = -1;
    private final List<BackendMessage> currentResults = new ArrayList<>();
    private boolean currentThrowOnError = false;
    private Consumer<NoticeMessage> currentOnNotice;

    private int pgliteRead = -1;
    private Uint8Array outputData = new Uint8Array(0);
    private int readOffset = 0;
    private DatabaseError currentDatabaseError;
    private boolean keepRawResponse = true;
    private static final int DEFAULT_RECV_BUF_SIZE = 1024 * 1024;
    private static final int MAX_BUFFER_SIZE = 1 << 30;
    private Uint8Array inputData = new Uint8Array(0);
    private int writeOffset = 0;
    private parser.Parser protocolParser = new parser.Parser();

    private final Map<String, Set<Consumer<String>>> notifyListeners =
        new ConcurrentHashMap<>();
    private final Set<BiConsumer<String, String>> globalNotifyListeners =
        ConcurrentHashMap.newKeySet();

    public pglite(String dataDir, PGliteOptions<Extensions> options) {
        var resolved = options != null ? options : new PGliteOptions<Extensions>();
        if (dataDir != null) {
            resolved.dataDir = dataDir;
        }

        this.dataDir = resolved.dataDir;
        this.debug = resolved.debug != null ? resolved.debug : 0;
        this.relaxedDurability = Boolean.TRUE.equals(resolved.relaxedDurability);
        this.extensions = resolved.extensions;
        if (resolved.parsers != null) {
            this.parsers.putAll(resolved.parsers);
        }
        if (resolved.serializers != null) {
            this.serializers.putAll(resolved.serializers);
        }

        this.waitReady = init(resolved);
    }

    public pglite(PGliteOptions<Extensions> options) {
        this(null, options);
    }

    public pglite(String dataDir) {
        this(dataDir, new PGliteOptions<Extensions>());
    }

    public pglite() {
        this(null, new PGliteOptions<Extensions>());
    }

    public static <TExtensions extends Extensions> CompletableFuture<pglite> create(
        PGliteOptions<TExtensions> options
    ) {
        var pg = new pglite((PGliteOptions<Extensions>) (PGliteOptions<?>) options);
        return pg.waitReady().thenApply(ignored -> pg);
    }

    public static <TExtensions extends Extensions> CompletableFuture<pglite> create(
        String dataDir,
        PGliteOptions<TExtensions> options
    ) {
        var pg = new pglite(dataDir, (PGliteOptions<Extensions>) (PGliteOptions<?>) options);
        return pg.waitReady().thenApply(ignored -> pg);
    }

    private CompletableFuture<Void> init(PGliteOptions<Extensions> options) {
        return CompletableFuture.supplyAsync(
            () -> {
                if (options != null && options.fs != null) {
                    this.fs = (
                        io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.base.Filesystem
                    ) options.fs;
                } else {
                    ParseDataDirResult parsed = io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.index
                        .parseDataDir(options != null ? options.dataDir : null);
                    this.fs = io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.index
                        .loadFs(parsed.dataDir, parsed.fsType)
                        .join();
                }
                return null;
            }
        ).thenCompose(
            ignored -> {
                var moduleOverrides = new postgresMod.PartialPostgresMod();
                moduleOverrides.WASM_PREFIX =
                    io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.base.WASM_PREFIX;
                moduleOverrides.INITIAL_MEMORY =
                    options != null ? options.initialMemory : null;
                moduleOverrides.noExitRuntime = true;
                var pgUser = options != null && options.username != null
                    ? options.username
                    : "postgres";
                var pgDatabase = options != null && options.database != null
                    ? options.database
                    : "template1";
                var args = new ArrayList<String>();
                args.add("./this.program");
                args.add(
                    "PGDATA=" + io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.base.PGDATA
                );
                args.add(
                    "PREFIX=" +
                    io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.base.WASM_PREFIX
                );
                args.add("PGUSER=" + pgUser);
                args.add("PGDATABASE=" + pgDatabase);
                args.add("MODE=REACT");
                args.add("REPL=N");
                if (this.debug > 0) {
                    args.add("-d");
                    args.add(Integer.toString(this.debug));
                }
                moduleOverrides.arguments = args.toArray(String[]::new);
                moduleOverrides.pg_extensions = new ConcurrentHashMap<>();
                var extensionInitFns = new ArrayList<Supplier<CompletableFuture<Void>>>();
                if (this.extensions != null) {
                    for (var entry : this.extensions.entrySet()) {
                        var extName = entry.getKey();
                        var extDef = entry.getValue();
                        if (extDef instanceof java.net.URL) {
                            var url = (java.net.URL) extDef;
                            moduleOverrides.pg_extensions.put(
                                extName,
                                CompletableFuture.completedFuture(
                                    extensionUtils.loadExtensionBundle(url)
                                )
                            );
                        } else if (extDef instanceof String) {
                            var path = (String) extDef;
                            moduleOverrides.pg_extensions.put(
                                extName,
                                CompletableFuture.completedFuture(
                                    extensionUtils.loadExtensionBundle(path)
                                )
                            );
                        } else if (extDef instanceof interface_.Extension<?>) {
                            var extension = (interface_.Extension<?>) extDef;
                            var setupResult = extension.setup
                                .apply(this, moduleOverrides, false)
                                .join();
                            if (setupResult != null) {
                                if (setupResult.namespaceObj != null) {
                                    this.extensionNamespaces.put(extName, setupResult.namespaceObj);
                                }
                                if (setupResult.bundlePath != null) {
                                    moduleOverrides.pg_extensions.put(
                                        extName,
                                        CompletableFuture.completedFuture(
                                            extensionUtils.loadExtensionBundle(setupResult.bundlePath)
                                        )
                                    );
                                }
                                if (setupResult.init != null) {
                                    extensionInitFns.add(setupResult.init);
                                }
                                if (setupResult.close != null) {
                                    this.extensionsClose.add(setupResult.close);
                                }
                            }
                        }
                    }
                }
                return io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.pglite
                    .PostgresModFactory(moduleOverrides)
                    .thenCompose(
                        module -> {
                            this.mod = module;
                            setupBlobDevice();
                            setupReadWriteCallbacks();
                            this.mod._set_read_write_cbs(this.pgliteRead, this.pgliteWrite);
                            return this.fs.initialSyncFs().thenApply(ignoredSync -> module);
                        }
                    )
                    .thenCompose(
                        module -> {
                            extensionUtils.loadExtensions(
                                new extensionUtils.PostgresMod() {
                                    @Override
                                    public Map<String, CompletableFuture<extensionUtils.ExtensionBlob>> pg_extensions() {
                                        return module.pg_extensions();
                                    }

                                    @Override
                                    public String WASM_PREFIX() {
                                        return module.WASM_PREFIX();
                                    }

                                    @Override
                                    public extensionUtils.EmscriptenFS FS() {
                                        return module.FS();
                                    }
                                },
                                this::log
                            );

                            var idb = this.mod._pgl_initdb();
                            if (idb == 0) {
                                throw new RuntimeException("INITDB failed to return value");
                            }
                            if ((idb & 0b0001) != 0) {
                                throw new RuntimeException("INITDB failed: status=" + idb);
                            }
                            this.mod._pgl_backend();
                            this.ready = true;
                            return this.exec("SET search_path TO public;", null).thenCompose(
                                    ignoredExec -> this._initArrayTypes(null)
                                )
                                .thenCompose(
                                    ignoredInit -> {
                                        var initFuture = CompletableFuture.<Void>completedFuture(
                                            null
                                        );
                                        for (var initFn : extensionInitFns) {
                                            initFuture = initFuture.thenCompose(
                                                ignoredInitFn -> initFn.get()
                                            );
                                        }
                                        return initFuture;
                                    }
                                )
                                .thenApply(ignoredInit -> null);
                        }
                    );
            }
        );
    }

    public postgresMod.PostgresMod Module() {
        return this.mod;
    }

    @Override
    public CompletableFuture<Void> waitReady() {
        return this.waitReady;
    }

    @Override
    public int debug() {
        return this.debug;
    }

    @Override
    public boolean ready() {
        return this.ready && !this.closing && !this.closed;
    }

    @Override
    public boolean closed() {
        return this.closed;
    }

    @Override
    public CompletableFuture<Void> close() {
        return this._checkReady().thenCompose(
            ignored -> {
                this.closing = true;
                CompletableFuture<Void> closeFuture = CompletableFuture.completedFuture(
                    null
                );
                for (var closeFn : this.extensionsClose) {
                    closeFuture = closeFuture.thenCompose(ignoredClose -> closeFn.get());
                }
                return closeFuture.thenCompose(
                    ignoredClose -> {
                        if (this.mod != null) {
                            return this.execProtocol(
                                    serializer.serialize.end(),
                                    new ExecProtocolOptions()
                                )
                                .handle((ret, err) -> null)
                                .thenRun(() -> this.mod._pgl_shutdown());
                        }
                        return CompletableFuture.completedFuture(null);
                    }
                ).thenCompose(
                    ignoredClose -> {
                        if (this.mod != null && this.pgliteRead >= 0) {
                            this.mod.removeFunction(this.pgliteRead);
                            this.pgliteRead = -1;
                        }
                        if (this.mod != null && this.pgliteWrite >= 0) {
                            this.mod.removeFunction(this.pgliteWrite);
                            this.pgliteWrite = -1;
                        }
                        if (this.fs != null) {
                            return this.fs.closeFs();
                        }
                        return CompletableFuture.completedFuture(null);
                    }
                ).thenApply(
                    ignoredClose -> {
                        this.closed = true;
                        this.closing = false;
                        return null;
                    }
                );
            }
        );
    }

    @Override
    public CompletableFuture<Void> _handleBlob(Blob blob) {
        if (blob == null) {
            this.queryReadBuffer = null;
            return CompletableFuture.completedFuture(null);
        }
        if (blob instanceof io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.tarUtils.TarBlob) {
            var data = ((io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.tarUtils.TarBlob) blob)
                .arrayBuffer();
            this.queryReadBuffer = new Uint8Array(data).buffer;
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.failedFuture(
            new UnsupportedOperationException("Unsupported blob implementation")
        );
    }

    @Override
    public CompletableFuture<Void> _cleanupBlob() {
        this.queryReadBuffer = null;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Blob> _getWrittenBlob() {
        if (this.queryWriteChunks == null || this.queryWriteChunks.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        var total = 0;
        for (var chunk : this.queryWriteChunks) {
            total += chunk.byteLength;
        }
        var merged = new byte[total];
        var offset = 0;
        for (var chunk : this.queryWriteChunks) {
            var bytes = chunk.toByteArray();
            System.arraycopy(bytes, 0, merged, offset, bytes.length);
            offset += bytes.length;
        }
        this.queryWriteChunks = null;
        return CompletableFuture.completedFuture(
            new io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.tarUtils.FileBlob(
                merged,
                "query.bin",
                "application/octet-stream"
            )
        );
    }

    @Override
    public CompletableFuture<Void> _checkReady() {
        if (this.closing) {
            return CompletableFuture.failedFuture(new RuntimeException("PGlite is closing"));
        }
        if (this.closed) {
            return CompletableFuture.failedFuture(new RuntimeException("PGlite is closed"));
        }
        if (!this.ready) {
            return this.waitReady;
        }
        return CompletableFuture.completedFuture(null);
    }

    public Uint8Array execProtocolRawSync(Uint8Array message) {
        if (this.mod == null) {
            throw new IllegalStateException("Postgres module is not initialized");
        }
        this.readOffset = 0;
        this.writeOffset = 0;
        this.outputData = message;
        if (this.keepRawResponse && this.inputData.length != DEFAULT_RECV_BUF_SIZE) {
            this.inputData = new Uint8Array(DEFAULT_RECV_BUF_SIZE);
        }

        this.mod._interactive_one(message.length, message.get(0) & 0xFF);
        this.outputData = new Uint8Array(0);

        if (this.keepRawResponse && this.writeOffset > 0) {
            return new Uint8Array(this.inputData.buffer, 0, this.writeOffset);
        }
        return new Uint8Array(0);
    }

    @Override
    public CompletableFuture<Uint8Array> execProtocolRaw(
        Uint8Array message,
        ExecProtocolOptions options
    ) {
        var resolved = options != null ? options : new ExecProtocolOptions();
        return CompletableFuture.supplyAsync(() -> execProtocolRawSync(message)).thenCompose(
            data -> {
                if (resolved.syncToFs == null || resolved.syncToFs.booleanValue()) {
                    return syncToFs().thenApply(ignored -> data);
                }
                return CompletableFuture.completedFuture(data);
            }
        );
    }

    @Override
    public CompletableFuture<ExecProtocolResult> execProtocol(
        Uint8Array message,
        ExecProtocolOptions options
    ) {
        var resolved = options != null ? options : new ExecProtocolOptions();
        this.currentThrowOnError =
            resolved.throwOnError == null || resolved.throwOnError.booleanValue();
        this.currentOnNotice = resolved.onNotice;
        this.currentResults.clear();
        this.currentDatabaseError = null;
        return this.execProtocolRaw(message, resolved).thenApply(
            data -> {
                var throwOnError =
                    resolved.throwOnError == null || resolved.throwOnError.booleanValue();
                var dbError = this.currentDatabaseError;
                var result = new ExecProtocolResult();
                result.messages = new ArrayList<>(this.currentResults);
                result.data = data;
                this.currentResults.clear();
                this.currentThrowOnError = false;
                this.currentOnNotice = null;
                this.currentDatabaseError = null;
                if (throwOnError && dbError != null) {
                    this.protocolParser = new parser.Parser();
                    throw new CompletionException(dbError);
                }
                return result;
            }
        );
    }

    @Override
    public CompletableFuture<List<BackendMessage>> execProtocolStream(
        Uint8Array message,
        ExecProtocolOptions options
    ) {
        var resolved = options != null ? options : new ExecProtocolOptions();
        this.currentThrowOnError =
            resolved.throwOnError == null || resolved.throwOnError.booleanValue();
        this.currentOnNotice = resolved.onNotice;
        this.currentResults.clear();
        this.currentDatabaseError = null;
        this.keepRawResponse = false;
        return this.execProtocolRaw(message, resolved).thenApply(
            ret -> {
                this.keepRawResponse = true;
                var throwOnError =
                    resolved.throwOnError == null || resolved.throwOnError.booleanValue();
                var dbError = this.currentDatabaseError;
                var result = new ArrayList<>(this.currentResults);
                this.currentResults.clear();
                this.currentThrowOnError = false;
                this.currentOnNotice = null;
                this.currentDatabaseError = null;
                if (throwOnError && dbError != null) {
                    this.protocolParser = new parser.Parser();
                    throw new CompletionException(dbError);
                }
                return result;
            }
        );
    }

    public boolean isInTransaction() {
        return this.inTransaction;
    }

    @Override
    public CompletableFuture<Void> syncToFs() {
        if (this.fs == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (this.fsSyncScheduled) {
            return CompletableFuture.completedFuture(null);
        }
        this.fsSyncScheduled = true;

        Supplier<CompletableFuture<Void>> doSync = () ->
            withSemaphore(this.fsSyncMutex, () -> {
                this.fsSyncScheduled = false;
                return this.fs.syncToFs(this.relaxedDurability);
            });

        if (this.relaxedDurability) {
            doSync.get();
            return CompletableFuture.completedFuture(null);
        }
        return doSync.get();
    }

    private void log(Object... args) {
        if (this.debug > 0) {
            var parts = new ArrayList<String>(args.length);
            for (var arg : args) {
                parts.add(String.valueOf(arg));
            }
            System.out.println(String.join(" ", parts));
        }
    }

    @Override
    public CompletableFuture<Function<Transaction, CompletableFuture<Void>>> listen(
        String channel,
        Consumer<String> callback,
        Transaction tx
    ) {
        return this._runExclusiveListen(() -> this.listenInner(channel, callback, tx));
    }

    private CompletableFuture<Function<Transaction, CompletableFuture<Void>>> listenInner(
        String channel,
        Consumer<String> callback,
        Transaction tx
    ) {
        var pgChannel = utils.toPostgresName(channel);
        var set = this.notifyListeners.computeIfAbsent(
            pgChannel,
            key -> ConcurrentHashMap.newKeySet()
        );
        set.add(callback);

        var activeTx = tx != null ? tx : null;
        var listenFuture = activeTx != null
            ? activeTx.exec("LISTEN " + channel, null).thenApply(ignored -> null)
            : this.exec("LISTEN " + channel, null).thenApply(ignored -> null);

        return listenFuture.handle(
            (ignored, error) -> {
                if (error != null) {
                    set.remove(callback);
                    if (set.isEmpty()) {
                        this.notifyListeners.remove(pgChannel);
                    }
                    var cause = error instanceof CompletionException
                        ? error.getCause()
                        : error;
                    throw new CompletionException(cause);
                }
                return (Function<Transaction, CompletableFuture<Void>>) providedTx ->
                    this.unlisten(channel, callback, providedTx);
            }
        );
    }

    @Override
    public CompletableFuture<Void> unlisten(
        String channel,
        Consumer<String> callback,
        Transaction tx
    ) {
        return this._runExclusiveListen(() -> this.unlistenInner(channel, callback, tx));
    }

    private CompletableFuture<Void> unlistenInner(
        String channel,
        Consumer<String> callback,
        Transaction tx
    ) {
        var pgChannel = utils.toPostgresName(channel);
        var listeners = this.notifyListeners.get(pgChannel);

        Supplier<CompletableFuture<Void>> cleanUp = () -> {
            var execFuture = tx != null
                ? tx.exec("UNLISTEN " + channel, null).thenApply(ignored -> null)
                : this.exec("UNLISTEN " + channel, null).thenApply(ignored -> null);
            return execFuture.thenApply(
                ignored -> {
                    var current = this.notifyListeners.get(pgChannel);
                    if (current != null && current.isEmpty()) {
                        this.notifyListeners.remove(pgChannel);
                    }
                    return null;
                }
            );
        };

        if (callback != null && listeners != null) {
            listeners.remove(callback);
            if (listeners.isEmpty()) {
                return cleanUp.get();
            }
            return CompletableFuture.completedFuture(null);
        }
        return cleanUp.get();
    }

    @Override
    public Runnable onNotification(BiConsumer<String, String> callback) {
        this.globalNotifyListeners.add(callback);
        return () -> this.globalNotifyListeners.remove(callback);
    }

    @Override
    public void offNotification(BiConsumer<String, String> callback) {
        this.globalNotifyListeners.remove(callback);
    }

    @Override
    public CompletableFuture<Blob> dumpDataDir(DumpTarCompressionOptions compression) {
        return this._checkReady().thenCompose(
            ignored -> {
                var dbname = this.dataDir != null && this.dataDir.contains("/")
                    ? this.dataDir.substring(this.dataDir.lastIndexOf('/') + 1)
                    : (this.dataDir != null ? this.dataDir : "pgdata");
                var compressionType = compression != null
                    ? io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.tarUtils.DumpTarCompressionOptions.auto
                    : io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.tarUtils.DumpTarCompressionOptions.auto;
                return this.fs.dumpTar(dbname, compressionType);
            }
        );
    }

    @Override
    public CompletableFuture<DescribeQueryResult> describeQuery(String query) {
        return super.describeQuery(query, null);
    }

    @Override
    public <T> CompletableFuture<T> _runExclusiveQuery(
        Supplier<CompletableFuture<T>> fn
    ) {
        return withSemaphore(this.queryMutex, fn);
    }

    @Override
    public <T> CompletableFuture<T> _runExclusiveTransaction(
        Supplier<CompletableFuture<T>> fn
    ) {
        return withSemaphore(this.transactionMutex, fn);
    }

    private <T> CompletableFuture<T> _runExclusiveListen(
        Supplier<CompletableFuture<T>> fn
    ) {
        return withSemaphore(this.listenMutex, fn);
    }

    private <T> CompletableFuture<T> withSemaphore(
        Semaphore semaphore,
        Supplier<CompletableFuture<T>> fn
    ) {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CompletableFuture.failedFuture(e);
        }

        CompletableFuture<T> future;
        try {
            future = fn.get();
            if (future == null) {
                future = CompletableFuture.completedFuture(null);
            }
        } catch (Throwable e) {
            semaphore.release();
            return CompletableFuture.failedFuture(e);
        }

        return future.whenComplete((result, error) -> semaphore.release());
    }

    public void _receiveNotification(String channel, String payload) {
        var listeners = this.notifyListeners.getOrDefault(channel, Collections.emptySet());
        for (var listener : listeners) {
            listener.accept(payload);
        }
        for (var listener : this.globalNotifyListeners) {
            listener.accept(channel, payload);
        }
    }

    private void setupReadWriteCallbacks() {
        this.pgliteWrite = this.mod.addFunction((ptr, length) -> {
            var bytes = new byte[length];
            this.mod.copyFromHeap(ptr, bytes, 0, length);
            this.protocolParser.parse(new Uint8Array(bytes), this::parseMessage);
            if (this.keepRawResponse) {
                ensureInputCapacity(length);
                this.inputData.set(bytes, this.writeOffset);
                this.writeOffset += length;
                return this.inputData.length;
            }
            return length;
        }, "iii");

        this.pgliteRead = this.mod.addFunction((ptr, maxLength) -> {
            var length = this.outputData.length - this.readOffset;
            if (length > maxLength) {
                length = maxLength;
            }
            if (length <= 0) {
                return 0;
            }
            var chunk = this.outputData.toByteArray();
            this.mod.copyToHeap(ptr, chunk, this.readOffset, length);
            this.readOffset += length;
            return length;
        }, "iii");
    }

    private void setupBlobDevice() {
        var runtime = this.mod.runtime();
        var devId = runtime.makedev(64, 0);
        runtime.registerDevice(
            devId,
            new postgresMod.DeviceOps() {
                @Override
                public int read(byte[] buffer, int offset, int length, int position) {
                    var buf = queryReadBuffer;
                    if (buf == null) {
                        throw new RuntimeException("No /dev/blob blob to read");
                    }
                    var contents = new Uint8Array(buf).toByteArray();
                    if (position >= contents.length) {
                        return 0;
                    }
                    var size = Math.min(contents.length - position, length);
                    System.arraycopy(contents, position, buffer, offset, size);
                    return size;
                }

                @Override
                public int write(byte[] buffer, int offset, int length, int position) {
                    if (queryWriteChunks == null) {
                        queryWriteChunks = new ArrayList<>();
                    }
                    var chunk = new byte[length];
                    System.arraycopy(buffer, offset, chunk, 0, length);
                    queryWriteChunks.add(new Uint8Array(chunk));
                    return length;
                }

                @Override
                public int llseek(int offset, int whence, int position) {
                    var buf = queryReadBuffer;
                    if (buf == null) {
                        throw new RuntimeException("No /dev/blob blob to llseek");
                    }
                    var next = offset;
                    if (whence == 1) {
                        next += position;
                    } else if (whence == 2) {
                        next = new Uint8Array(buf).length;
                    }
                    if (next < 0) {
                        throw new RuntimeException("Invalid seek");
                    }
                    return next;
                }
            }
        );
        runtime.mkdev("/dev/blob", devId);
    }

    private void ensureInputCapacity(int additionalLength) {
        var requiredSize = this.writeOffset + additionalLength;
        if (requiredSize <= this.inputData.length) {
            return;
        }
        var newSize = this.inputData.length + (this.inputData.length >> 1) + requiredSize;
        if (newSize > MAX_BUFFER_SIZE) {
            newSize = MAX_BUFFER_SIZE;
        }
        if (newSize < requiredSize) {
            throw new IllegalStateException("Protocol response exceeds max buffer size");
        }
        var newBuffer = new Uint8Array(newSize);
        newBuffer.set(new Uint8Array(this.inputData.buffer, 0, this.writeOffset));
        this.inputData = newBuffer;
    }

    private void parseMessage(BackendMessage message) {
        if (this.currentDatabaseError != null) {
            return;
        }
        if (message instanceof DatabaseError) {
            if (this.currentThrowOnError) {
                this.currentDatabaseError = (DatabaseError) message;
            }
        } else if (message instanceof NoticeMessage) {
            if (this.currentOnNotice != null) {
                this.currentOnNotice.accept((NoticeMessage) message);
            }
        } else if (message instanceof CommandCompleteMessage) {
            var text = ((CommandCompleteMessage) message).text;
            if ("BEGIN".equals(text)) {
                this.inTransaction = true;
            } else if ("COMMIT".equals(text) || "ROLLBACK".equals(text)) {
                this.inTransaction = false;
            }
        } else if (message instanceof NotificationResponseMessage) {
            var notify = (NotificationResponseMessage) message;
            _receiveNotification(notify.channel, notify.payload);
        }
        this.currentResults.add(message);
    }

    @Override
    public int size() {
        return this.extensionNamespaces.size();
    }

    @Override
    public boolean isEmpty() {
        return this.extensionNamespaces.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return this.extensionNamespaces.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return this.extensionNamespaces.containsValue(value);
    }

    @Override
    public Object get(Object key) {
        return this.extensionNamespaces.get(key);
    }

    @Override
    public Object put(String key, Object value) {
        return this.extensionNamespaces.put(key, value);
    }

    @Override
    public Object remove(Object key) {
        return this.extensionNamespaces.remove(key);
    }

    @Override
    public void putAll(Map<? extends String, ? extends Object> m) {
        this.extensionNamespaces.putAll(m);
    }

    @Override
    public void clear() {
        this.extensionNamespaces.clear();
    }

    @Override
    public Set<String> keySet() {
        return this.extensionNamespaces.keySet();
    }

    @Override
    public java.util.Collection<Object> values() {
        return this.extensionNamespaces.values();
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return this.extensionNamespaces.entrySet();
    }
}
