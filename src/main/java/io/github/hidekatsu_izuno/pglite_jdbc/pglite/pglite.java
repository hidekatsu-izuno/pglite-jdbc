package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.parser;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.base.Filesystem;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.ChicoryPostgresMod;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.pglite.PostgresModFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class pglite extends base implements interface_.PGliteInterface {
    protected static final int DEFAULT_RECV_BUF_SIZE = 1024 * 1024;
    protected static final int MAX_BUFFER_SIZE = 1 << 30;
    protected static final String[] DEFAULT_START_PARAMS = {
        "--single",
        "-F",
        "-O",
        "-j",
        "-c", "search_path=public",
        "-c", "exit_on_error=false",
        "-c", "log_checkpoints=false",
        "-c", "timezone=UTC",
        "-c", "dynamic_shared_memory_type=mmap",
        "-c", "max_worker_processes=0",
        "-c", "max_parallel_workers=0",
        "-c", "max_parallel_workers_per_gather=0",
        "-c", "io_method=sync",
        "-c", "max_parallel_maintenance_workers=0",
    };
    private static final boolean TRACE_INIT = Boolean.getBoolean("pglite.trace_init");
    private static final long NATIVE_CALL_TIMEOUT_MS = Long.getLong(
        "pglite.native_call_timeout_ms",
        120_000L
    );

    protected Filesystem fs;
    protected postgresMod.PostgresMod mod;
    public final String dataDir;
    protected volatile boolean ready;
    protected volatile boolean closing;
    protected volatile boolean closed;
    protected final boolean relaxedDurability;
    protected final int debug;
    protected final Promise<Void> waitReady;
    protected final Semaphore listenMutex = new Semaphore(1);
    protected final Semaphore fsSyncMutex = new Semaphore(1);
    protected volatile boolean fsSyncScheduled;
    protected volatile boolean inTransaction;
    protected final Map<String, List<Consumer<String>>> notifyListeners =
        new ConcurrentHashMap<>();
    protected final List<BiConsumer<String, String>> globalNotifyListeners =
        new CopyOnWriteArrayList<>();
    protected final List<java.util.function.Supplier<Promise<Void>>> extensionClosers =
        new CopyOnWriteArrayList<>();
    protected final List<java.util.function.Supplier<Promise<Void>>> extensionInitializers =
        new CopyOnWriteArrayList<>();

    protected parser.Parser protocolParser = new parser.Parser();
    protected volatile int pgliteWrite = -1;
    protected final List<messages.BackendMessage> currentResults = new ArrayList<>();
    protected volatile boolean currentThrowOnError;
    protected volatile Consumer<messages.NoticeMessage> currentOnNotice;
    protected volatile int pgliteRead = -1;
    protected volatile byte[] outputData = new byte[0];
    protected volatile int readOffset;
    protected volatile messages.DatabaseError currentDatabaseError;
    protected volatile boolean keepRawResponse = true;
    protected volatile byte[] inputData = new byte[DEFAULT_RECV_BUF_SIZE];
    protected volatile int writeOffset;
    private final StringBuilder wasmStderr = new StringBuilder();

    protected volatile byte[] queryReadBuffer;
    protected volatile List<byte[]> queryWriteChunks;
    protected volatile java.util.function.ToIntFunction<byte[]> onData;

    public static final class PGliteOptions {
        public String dataDir;
        public String username;
        public String database;
        public Filesystem fs;
        public Integer debug;
        public Boolean relaxedDurability;
        public Integer initialMemory;
        public Map<String, interface_.Extension> extensions;
    }

    public pglite(PGliteOptions options) {
        var resolved = options != null ? options : new PGliteOptions();
        this.dataDir = resolved.dataDir;
        this.debug = resolved.debug != null ? resolved.debug : 0;
        this.relaxedDurability = Boolean.TRUE.equals(resolved.relaxedDurability);
        this.waitReady = init(resolved);
    }

    public pglite(String dataDir) {
        this(optionWithDataDir(dataDir));
    }

    public pglite() {
        this(new PGliteOptions());
    }

    private static PGliteOptions optionWithDataDir(String dataDir) {
        var options = new PGliteOptions();
        options.dataDir = dataDir;
        return options;
    }

    public static Promise<pglite> create(PGliteOptions options) {
        var pg = new pglite(options);
        return pg.waitReady.then(ignored -> pg);
    }

    public postgresMod.PostgresMod Module() {
        return this.mod;
    }

    protected Promise<Void> init(PGliteOptions options) {
        return new Promise<>((resolve, reject) ->
            Promise.executor().submit(() -> {
                try {
                    initSync(options);
                    resolve.run(null);
                } catch (Throwable e) {
                    reject.run(e);
                }
            })
        );
    }

    private void initSync(PGliteOptions options) {
        traceInit("init:start");
        if (options.fs != null) {
            this.fs = options.fs;
        } else {
            var parsed = io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.index.parseDataDir(options.dataDir);
            this.fs = io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.index.loadFs(
                parsed.dataDir(),
                parsed.fsType()
            );
        }
        traceInit("init:fs-ready type=" + this.fs.getClass().getSimpleName());

        var overrides = new postgresMod.PartialPostgresMod();
        overrides.WASM_PREFIX = io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.base.WASM_PREFIX;
        overrides.INITIAL_MEMORY = options.initialMemory != null
            ? options.initialMemory
            : 64 * 1024 * 1024;
        overrides.noExitRuntime = true;
        overrides.printErr = printArgs -> {
            var text = printArgs.length > 0 ? String.valueOf(printArgs[0]) : "";
            appendWasmStderr(text);
        };

        var extPromises =
            new ConcurrentHashMap<String, CompletableFuture<extensionUtils.ExtensionBlob>>();
        var emscriptenOptions = new ConcurrentHashMap<String, Object>();
        var extSharedPreloadLibraries = new ArrayList<String>();

        if (options.extensions != null) {
            for (var entry : options.extensions.entrySet()) {
                var extName = entry.getKey();
                var extension = entry.getValue();
                if (extension == null) {
                    continue;
                }
                var result = await(extension.setup().setup(this, overrides, false));
                if (result == null) {
                    continue;
                }
                if (result.emscriptenOpts() instanceof Map<?, ?> mapOpts) {
                    for (var opt : mapOpts.entrySet()) {
                        if (opt.getKey() instanceof String key) {
                            emscriptenOptions.put(key, opt.getValue());
                        }
                    }
                }
                if (result.bundlePath() != null) {
                    var bytes = extensionUtils.loadExtensionBundle(result.bundlePath());
                    extPromises.put(
                        extName,
                        CompletableFuture.completedFuture(
                            extensionUtils.toExtensionBlob(bytes)
                        )
                    );
                }
                if (result.sharedPreloadLibraries() != null) {
                    for (var library : result.sharedPreloadLibraries()) {
                        if (library != null && !library.isBlank() && !extSharedPreloadLibraries.contains(library)) {
                            extSharedPreloadLibraries.add(library);
                        }
                    }
                }
                if (result.init() != null) {
                    extensionInitializers.add(result.init());
                }
                if (result.close() != null) {
                    extensionClosers.add(result.close());
                }
            }
        }
        overrides.arguments = buildModuleArguments(options, extSharedPreloadLibraries);
        overrides.pg_extensions = extPromises;
        traceInit("init:extensions-setup-done");

        var initResult = await(this.fs.init(this, emscriptenOptions));
        traceInit("init:fs-init-done");
        await(fs.initialSyncFs());
        traceInit("init:fs-initial-sync-done");

        traceInit("init:build-runtime-start");
        if (initResult != null && initResult.emscriptenOpts() != null) {
            io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.base.mergeIntoPartialPostgresMod(
                overrides,
                initResult.emscriptenOpts()
            );
        }
        if (overrides.__wasiRoot == null) {
            try {
                overrides.__wasiRoot = Files.createTempDirectory("pglite-wasi-").toString();
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Unable to create PGlite WASI root", e);
            }
        }
        this.mod = PostgresModFactory.create(overrides);

        traceInit("init:build-runtime-done");
        traceInit("init:prop trace_host_calls=" + Boolean.getBoolean("pglite.trace_host_calls"));
        setupBlobDevice();
        setupReadWriteCallbacks();
        this.mod._set_read_write_cbs(this.pgliteRead, this.pgliteWrite);
        traceInit("init:callbacks-ready");

        await(extensionUtils.loadExtensions(this.mod, this::log));
        traceInit("init:extensions-load-done");
        if (TRACE_INIT) {
            traceInit(
                "init:data-dir-before-initdb="
                    + (this.mod.FS().analyzePath(initdb.PGDATA).exists()
                        ? java.util.Arrays.toString(this.mod.FS().readdir(initdb.PGDATA))
                        : "<missing>")
            );
        }

        var initdbOverrides = new postgresMod.PartialPostgresMod();
        initdbOverrides.__wasiRoot = overrides.__wasiRoot;
        initdbOverrides.__wasiDataRoot = overrides.__wasiDataRoot;
        initdbOverrides.INITIAL_MEMORY = overrides.INITIAL_MEMORY;
        initdbOverrides.print = overrides.print;
        initdbOverrides.printErr = overrides.printErr;
        var initdbMod = (initdbModFactory.InitdbMod) PostgresModFactory.create(initdbOverrides);
        var initdbResult = await(initdb.getInitdb(
            new initdb.InitdbOptions(
                new InitdbAdapter(initdbMod),
                TRACE_INIT && this.debug == 0 ? 1 : this.debug,
                null,
                null
            )
        ));
        initdbMod._pgl_shutdown();
        traceInit("init:initdb-done status=" + initdbResult.exitCode());
        if (initdbResult.exitCode() != 0) {
            throw new IllegalStateException(
                "INITDB failed: status=" + initdbResult.exitCode()
                    + " stderr=" + initdbResult.stderr()
            );
        }
        runNativeWithTimeout("pgl_start_single_mode", () -> {
            this.mod._pgl_setPGliteActive(1);
            startInSingleMode(options);
            return null;
        });
        traceInit("init:single-mode-done");
        runNativeWithTimeout("pgl_backend", () -> {
            this.mod._pgl_backend();
            return null;
        });
        traceInit("init:backend-done");
        this.ready = true;

        this.execSync("SET search_path TO public;", null);
        traceInit("init:set-search-path-done");
        refreshArrayTypesSync();
        runExtensionInitializersSync();

        traceInit("init:done");
    }

    private static void traceInit(String message) {
        if (!TRACE_INIT) {
            return;
        }
        var line = "[pglite-init] " + message;
        System.err.println(line);
        try {
            Files.writeString(
                Path.of("tmp/pglite-init.log"),
                line + System.lineSeparator(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (Exception ignored) {
            // Best effort diagnostics.
        }
    }

    private void startInSingleMode(PGliteOptions options) {
        var args = new ArrayList<String>();
        args.addAll(Arrays.asList(DEFAULT_START_PARAMS));
        if (this.debug > 0) {
            args.add("-d");
            args.add(Integer.toString(this.debug));
        }
        args.add("-D");
        args.add(io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.base.PGDATA);
        args.add(options.database != null ? options.database : "template1");
        var result = this.mod.callMain(args.toArray(String[]::new));
        if (result != 99) {
            throw new IllegalStateException("PGlite failed to initialize properly: status=" + result);
        }
    }

    private String[] buildModuleArguments(PGliteOptions options, List<String> sharedPreloadLibraries) {
        var pgUser = options.username != null ? options.username : "postgres";
        var pgDatabase = options.database != null ? options.database : "template1";
        var args = new ArrayList<String>();
        args.add("./this.program");
        args.add("PGDATA=" + io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.base.PGDATA);
        args.add("PREFIX=" + io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.base.WASM_PREFIX);
        args.add("PGUSER=" + pgUser);
        args.add("PGDATABASE=" + pgDatabase);
        args.add("MODE=REACT");
        args.add("REPL=N");
        if (this.debug > 0) {
            args.add("-d");
            args.add(Integer.toString(this.debug));
        }
        if (sharedPreloadLibraries != null && !sharedPreloadLibraries.isEmpty()) {
            args.add("-c");
            args.add("shared_preload_libraries=" + String.join(",", sharedPreloadLibraries));
        }
        return args.toArray(String[]::new);
    }

    private static final class InitdbAdapter implements initdb.PGliteForInitdb {
        private final postgresMod.PostgresMod mod;

        private InitdbAdapter(postgresMod.PostgresMod mod) {
            this.mod = mod;
        }

        @Override
        public Module Module() {
            return new ModuleAdapter(mod);
        }

        @Override
        public int callMain(String[] args) {
            if (mod instanceof initdbModFactory.InitdbMod initdbMod) {
                return initdbMod.callMain(args);
            }
            throw new UnsupportedOperationException("Postgres module does not expose callMain");
        }
    }

    private static final class ModuleAdapter implements initdb.PGliteForInitdb.Module {
        private final postgresMod.PostgresMod mod;

        private ModuleAdapter(postgresMod.PostgresMod mod) {
            this.mod = mod;
        }

        @Override
        public Uint8Array HEAPU8() {
            return mod.HEAPU8();
        }

        @Override
        public int stringToUTF8OnStack(String str) {
            return asInitdbMod().stringToUTF8OnStack(str);
        }

        @Override
        public void _pgl_freopen(int path, int mode, int fd) {
            asChicory()._pgl_freopen(path, mode, fd);
        }

        @Override
        public Integer _close(int fd) {
            return asChicory()._close(fd);
        }

        @Override
        public extensionUtils.EmscriptenFS FS() {
            return mod.FS();
        }

        @Override
        public Object __wasi() {
            return asInitdbMod().__wasi();
        }

        @Override
        public Integer _pgl_chdir(int path) {
            return asChicory()._pgl_chdir(path);
        }

        @Override
        public String UTF8ToString(int ptr) {
            return asInitdbMod().UTF8ToString(ptr);
        }

        @Override
        public int addFunction(postgresMod.ReadWriteCallback cb, String signature) {
            return mod.addFunction(cb, signature);
        }

        @Override
        public int _fopen(int path, int mode) {
            return asInitdbMod()._fopen(path, mode);
        }

        @Override
        public int _fclose(int stream) {
            return asInitdbMod()._fclose(stream);
        }

        @Override
        public void _pgl_set_popen_fn(int popenFn) {
            asInitdbMod()._pgl_set_popen_fn(popenFn);
        }

        @Override
        public void _pgl_set_pclose_fn(int pcloseFn) {
            asInitdbMod()._pgl_set_pclose_fn(pcloseFn);
        }

        private initdbModFactory.InitdbMod asInitdbMod() {
            if (mod instanceof initdbModFactory.InitdbMod initdbMod) {
                return initdbMod;
            }
            throw new UnsupportedOperationException("Postgres module does not expose initdb helpers");
        }

        private ChicoryPostgresMod asChicory() {
            if (mod instanceof ChicoryPostgresMod chicory) {
                return chicory;
            }
            throw new UnsupportedOperationException("Postgres module does not expose Chicory helpers");
        }
    }

    private <T> T runNativeWithTimeout(String name, Callable<T> callable) {
        var future = Promise.executor().submit(callable);
        try {
            return future.get(NATIVE_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            future.cancel(true);
            throw new RuntimeException(
                "Native call timed out: " + name + " (" + NATIVE_CALL_TIMEOUT_MS + "ms)"
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void runExtensionInitializersSync() {
        for (var initializer : extensionInitializers) {
            await(initializer.get());
        }
    }

    @Override
    public Promise<Void> waitReady() {
        return waitReady;
    }

    @Override
    public int debug() {
        return debug;
    }

    @Override
    public boolean ready() {
        return ready && !closing && !closed;
    }

    @Override
    public boolean closed() {
        return closed;
    }

    @Override
    public Promise<Void> close() {
        return asPromise(() -> {
            closeSync();
            return null;
        });
    }

    private void closeSync() {
        if (closed) {
            return;
        }
        closing = true;
        try {
            for (var closer : extensionClosers) {
                await(closer.get());
            }
            if (this.mod != null) {
                this.mod._pgl_shutdown();
            }
            if (this.mod != null && this.pgliteRead >= 0) {
                this.mod.removeFunction(this.pgliteRead);
                this.pgliteRead = -1;
            }
            if (this.mod != null && this.pgliteWrite >= 0) {
                this.mod.removeFunction(this.pgliteWrite);
                this.pgliteWrite = -1;
            }
            if (fs != null) {
                await(fs.closeFs());
            }
            closed = true;
            ready = false;
        } finally {
            closing = false;
        }
    }

    @Override
    protected Promise<Void> checkReady() {
        return asPromise(() -> {
            checkReadySync();
            return null;
        });
    }

    @Override
    protected void checkReadySync() {
        if (this.closing) {
            throw new IllegalStateException("PGlite is closing");
        }
        if (this.closed) {
            throw new IllegalStateException("PGlite is closed");
        }
        if (!ready) {
            await(waitReady);
        }
    }

    public byte[] execProtocolRawSync(byte[] message) {
        if (this.mod == null) {
            throw new IllegalStateException("Postgres module is not initialized");
        }
        clearWasmStderr();
        var input = message != null ? message : new byte[0];
        this.readOffset = 0;
        this.writeOffset = 0;
        this.outputData = input;
        if (this.keepRawResponse && this.inputData.length != DEFAULT_RECV_BUF_SIZE) {
            this.inputData = new byte[DEFAULT_RECV_BUF_SIZE];
        }

        if (input.length > 0 && input[0] == 0) {
            return processStartupPacketSync(input);
        }

        try {
            while (this.readOffset < input.length || this.mod._pq_buffer_remaining_data() > 0) {
                this.mod._PostgresMainLoopOnce();
            }
        } finally {
            this.mod._PostgresSendReadyForQueryIfNecessary();
            this.mod._pgl_pq_flush();
            this.outputData = new byte[0];
        }

        if (this.keepRawResponse && this.writeOffset > 0) {
            return Arrays.copyOf(this.inputData, this.writeOffset);
        }
        return new byte[0];
    }

    private byte[] processStartupPacketSync(byte[] message) {
        this.readOffset = 0;
        this.writeOffset = 0;
        this.outputData = message;
        var myProcPort = this.mod._pgl_getMyProcPort();
        var result = this.mod._ProcessStartupPacket(myProcPort, true, true);
        if (result != 0) {
            throw new IllegalStateException("Cannot process startup packet");
        }

        this.mod._pgl_sendConnData();
        this.mod._pgl_pq_flush();
        this.outputData = new byte[0];

        if (this.keepRawResponse && this.writeOffset > 0) {
            return Arrays.copyOf(this.inputData, this.writeOffset);
        }
        return new byte[0];
    }

    @Override
    public Promise<byte[]> execProtocolRaw(byte[] message, interface_.ExecProtocolOptions options) {
        var resolved = resolveExecProtocolOptions(options);
        return asPromise(() -> execProtocolRawResolvedSync(message, resolved));
    }

    private byte[] execProtocolRawResolvedSync(
        byte[] message,
        interface_.ExecProtocolOptions options
    ) {
        checkReadySync();
        var data = execProtocolRawSync(message);
        if (options.syncToFs()) {
            syncToFsSync();
        }
        return data;
    }

    private void resetProtocolState() {
        this.currentResults.clear();
        this.currentThrowOnError = false;
        this.currentOnNotice = null;
        this.currentDatabaseError = null;
    }

    private void appendWasmStderr(String text) {
        synchronized (wasmStderr) {
            wasmStderr.append(text);
            if (wasmStderr.length() > 16 * 1024) {
                wasmStderr.delete(0, wasmStderr.length() - 16 * 1024);
            }
        }
    }

    private void clearWasmStderr() {
        synchronized (wasmStderr) {
            wasmStderr.setLength(0);
        }
    }

    private String currentWasmStderr() {
        synchronized (wasmStderr) {
            return wasmStderr.toString();
        }
    }

    private messages.DatabaseError databaseErrorFromWasmExit(Throwable rawError) {
        var cause = unwrap(rawError);
        if (!(cause instanceof com.dylibso.chicory.wasi.WasiExitException exit)
            || exit.exitCode() != 100) {
            return null;
        }
        var stderr = currentWasmStderr();
        for (var line : stderr.split("\\R")) {
            var trimmed = line.trim();
            if (trimmed.startsWith("ERROR:")) {
                var message = trimmed.substring("ERROR:".length()).trim();
                var error = new messages.DatabaseError(message, 0, "error");
                error.severity = "ERROR";
                if (message.contains("does not exist")) {
                    error.code = "42P01";
                }
                return error;
            }
            if (trimmed.startsWith("FATAL:")) {
                var message = trimmed.substring("FATAL:".length()).trim();
                var error = new messages.DatabaseError(message, 0, "error");
                error.severity = "FATAL";
                return error;
            }
        }
        return null;
    }

    private interface_.ExecProtocolResult execProtocolSyncInternal(
        byte[] message,
        interface_.ExecProtocolOptions options
    ) {
        this.currentThrowOnError = options.throwOnError();
        this.currentOnNotice = options.onNotice();
        this.currentResults.clear();
        this.currentDatabaseError = null;

        try {
            byte[] data;
            try {
                data = this.execProtocolRawResolvedSync(message, options);
            } catch (Throwable rawError) {
                var dbError = this.currentDatabaseError;
                if (dbError == null) {
                    dbError = databaseErrorFromWasmExit(rawError);
                }
                if (options.throwOnError() && dbError != null) {
                    this.protocolParser = new parser.Parser();
                    throw dbError;
                }
                throw rawError;
            }
            var dbError = this.currentDatabaseError;
            var result = new interface_.ExecProtocolResult(
                List.copyOf(this.currentResults),
                data
            );
            if (options.throwOnError() && dbError != null) {
                this.protocolParser = new parser.Parser();
                throw dbError;
            }
            return result;
        } finally {
            resetProtocolState();
        }
    }

    private List<messages.BackendMessage> execProtocolStreamSyncInternal(
        byte[] message,
        interface_.ExecProtocolOptions options
    ) {
        this.currentThrowOnError = options.throwOnError();
        this.currentOnNotice = options.onNotice();
        this.currentResults.clear();
        this.currentDatabaseError = null;
        this.keepRawResponse = false;

        try {
            try {
                this.execProtocolRawResolvedSync(message, options);
            } catch (Throwable rawError) {
                var dbError = this.currentDatabaseError;
                if (dbError == null) {
                    dbError = databaseErrorFromWasmExit(rawError);
                }
                if (options.throwOnError() && dbError != null) {
                    this.protocolParser = new parser.Parser();
                    throw dbError;
                }
                throw rawError;
            }
            var dbError = this.currentDatabaseError;
            var result = List.copyOf(this.currentResults);
            if (options.throwOnError() && dbError != null) {
                this.protocolParser = new parser.Parser();
                throw dbError;
            }
            return result;
        } finally {
            this.keepRawResponse = true;
            resetProtocolState();
        }
    }

    @Override
    public Promise<interface_.ExecProtocolResult> execProtocol(
        byte[] message,
        interface_.ExecProtocolOptions options
    ) {
        var resolved = resolveExecProtocolOptions(options);
        return asPromise(() -> execProtocolSyncInternal(message, resolved));
    }

    public Promise<List<messages.BackendMessage>> execProtocolStream(
        byte[] message,
        interface_.ExecProtocolOptions options
    ) {
        var resolved = resolveExecProtocolOptions(options);
        return asPromise(() -> execProtocolStreamSyncInternal(message, resolved));
    }

    private interface_.ExecProtocolOptions resolveExecProtocolOptions(interface_.ExecProtocolOptions options) {
        if (options != null) {
            return options;
        }
        return new interface_.ExecProtocolOptions(true, true, null);
    }

    private void performSyncToFsSync() {
        runWithSemaphoreSync(this.fsSyncMutex, () -> {
            this.fsSyncScheduled = false;
            await(this.fs.syncToFs(this.relaxedDurability));
            return null;
        });
    }

    @Override
    public Promise<Void> syncToFs() {
        if (this.relaxedDurability) {
            syncToFsSync();
            return Promise.resolve(null);
        }
        return asPromise(() -> {
            syncToFsSync();
            return null;
        });
    }

    @Override
    protected void syncToFsSync() {
        if (this.fs == null) {
            return;
        }
        if (this.fsSyncScheduled) {
            return;
        }
        this.fsSyncScheduled = true;

        if (this.relaxedDurability) {
            Promise.executor().submit(() -> {
                try {
                    performSyncToFsSync();
                } catch (Throwable ignored) {
                    // Best effort in relaxed durability mode.
                }
            });
            return;
        }
        performSyncToFsSync();
    }

    @Override
    protected Promise<Void> handleBlob(byte[] blob) {
        return asPromise(() -> {
            handleBlobSync(blob);
            return null;
        });
    }

    @Override
    protected void handleBlobSync(byte[] blob) {
        this.queryReadBuffer = blob;
        this.queryWriteChunks = null;
        if (this.mod != null && this.mod.FS().analyzePath("/dev/blob").exists()) {
            this.mod.FS().writeFile("/dev/blob", blob != null ? blob : new byte[0]);
        }
    }

    @Override
    protected Promise<byte[]> getWrittenBlob() {
        return asPromise(this::getWrittenBlobSync);
    }

    @Override
    protected byte[] getWrittenBlobSync() {
        if (this.queryWriteChunks == null || this.queryWriteChunks.isEmpty()) {
            if (this.mod != null && this.mod.FS().analyzePath("/dev/blob").exists()) {
                var data = this.mod.FS().readFile("/dev/blob");
                return data.length == 0 ? null : data;
            }
            return null;
        }
        var total = 0;
        for (var chunk : this.queryWriteChunks) {
            total += chunk.length;
        }
        var merged = new byte[total];
        var offset = 0;
        for (var chunk : this.queryWriteChunks) {
            System.arraycopy(chunk, 0, merged, offset, chunk.length);
            offset += chunk.length;
        }
        this.queryWriteChunks = null;
        return merged;
    }

    @Override
    protected Promise<Void> cleanupBlob() {
        return asPromise(() -> {
            cleanupBlobSync();
            return null;
        });
    }

    @Override
    protected void cleanupBlobSync() {
        this.queryReadBuffer = null;
        this.queryWriteChunks = null;
        if (this.mod != null && this.mod.FS().analyzePath("/dev/blob").exists()) {
            this.mod.FS().writeFile("/dev/blob", new byte[0]);
        }
    }

    @Override
    public Promise<Function<interface_.Transaction, Promise<Void>>> listen(
        String channel,
        Consumer<String> callback,
        interface_.Transaction tx
    ) {
        return asPromise(() ->
            runWithSemaphoreSync(this.listenMutex, () -> listenInnerSync(channel, callback, tx))
        );
    }

    private Function<interface_.Transaction, Promise<Void>> listenInnerSync(
        String channel,
        Consumer<String> callback,
        interface_.Transaction tx
    ) {
        var pgChannel = utils.toPostgresName(channel);
        var listeners = this.notifyListeners.computeIfAbsent(
            pgChannel,
            key -> new CopyOnWriteArrayList<>()
        );
        listeners.add(callback);

        try {
            if (tx != null) {
                await(tx.exec("LISTEN " + channel, null));
            } else {
                this.execSync("LISTEN " + channel, null);
            }
        } catch (Throwable error) {
            listeners.remove(callback);
            if (listeners.isEmpty()) {
                this.notifyListeners.remove(pgChannel);
            }
            throw asRuntime(unwrap(error));
        }

        return providedTx -> this.unlisten(channel, callback, providedTx);
    }

    @Override
    public Promise<Void> unlisten(
        String channel,
        Consumer<String> callback,
        interface_.Transaction tx
    ) {
        return asPromise(() -> {
            runWithSemaphoreSync(this.listenMutex, () -> {
                unlistenInnerSync(channel, callback, tx);
                return null;
            });
            return null;
        });
    }

    private void unlistenInnerSync(
        String channel,
        Consumer<String> callback,
        interface_.Transaction tx
    ) {
        var pgChannel = utils.toPostgresName(channel);
        var listeners = this.notifyListeners.get(pgChannel);

        if (callback != null && listeners != null) {
            listeners.remove(callback);
            if (!listeners.isEmpty()) {
                return;
            }
        }

        if (tx != null) {
            await(tx.exec("UNLISTEN " + channel, null));
        } else {
            this.execSync("UNLISTEN " + channel, null);
        }

        var current = this.notifyListeners.get(pgChannel);
        if (current != null && current.isEmpty()) {
            this.notifyListeners.remove(pgChannel);
        }
    }

    @Override
    public Supplier<Void> onNotification(BiConsumer<String, String> callback) {
        this.globalNotifyListeners.add(callback);
        return () -> {
            this.globalNotifyListeners.remove(callback);
            return null;
        };
    }

    @Override
    public void offNotification(BiConsumer<String, String> callback) {
        this.globalNotifyListeners.remove(callback);
    }

    @Override
    public <T> Promise<T> runExclusive(Supplier<Promise<T>> fn) {
        return asPromise(() ->
            runExclusiveQuerySync(() -> await(fn.get()))
        );
    }

    @Override
    public Promise<Void> execProtocolRawStream(
        byte[] message,
        interface_.ExecProtocolOptionsStream options
    ) {
        var resolved = options != null
            ? options
            : new interface_.ExecProtocolOptionsStream(true, data -> {});
        return asPromise(() -> {
            checkReadySync();
            var previousOnData = this.onData;
            this.onData = bytes -> {
                resolved.onRawData().accept(bytes);
                return bytes.length;
            };
            try {
                execProtocolRawSync(message);
                if (resolved.syncToFs()) {
                    syncToFsSync();
                }
                return null;
            } finally {
                this.onData = previousOnData;
            }
        });
    }

    @Override
    public Promise<interface_.DumpDataDirResult> dumpDataDir(
        io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.tarUtils.DumpTarCompressionOptions compression
    ) {
        return asPromise(() -> dumpDataDirSync(compression));
    }

    private interface_.DumpDataDirResult dumpDataDirSync(
        io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.tarUtils.DumpTarCompressionOptions compression
    ) {
        var option = compression != null
            ? compression
            : io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.tarUtils.DumpTarCompressionOptions.auto;
        var tarball = await(fs.dumpTar("pgdata", option));
        var extension =
            option == io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.tarUtils.DumpTarCompressionOptions.gzip
                || option == io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.tarUtils.DumpTarCompressionOptions.auto
                ? ".tgz"
                : ".tar";
        var dbname = this.dataDir != null && !this.dataDir.isBlank()
            ? java.nio.file.Path.of(this.dataDir).getFileName().toString()
            : "pgdata";
        return new interface_.DumpDataDirResult(tarball, extension, dbname + extension);
    }

    public Promise<Void> notify(String channel, String payload) {
        return asPromise(() -> {
            var escapedPayload = payload == null ? "" : payload.replace("'", "''");
            this.execSync("NOTIFY " + channel + ", '" + escapedPayload + "'", null);
            return null;
        });
    }

    private void setupReadWriteCallbacks() {
        this.pgliteWrite = this.mod.addFunction((ptr, length) -> {
            var bytes = new byte[length];
            this.mod.copyFromHeap(ptr, bytes, 0, length);
            var handler = this.onData;
            if (handler != null) {
                return handler.applyAsInt(bytes);
            }
            this.protocolParser.parse(new Uint8Array(bytes), this::parseMessage);
            if (this.keepRawResponse) {
                ensureInputCapacity(length);
                System.arraycopy(bytes, 0, this.inputData, this.writeOffset, length);
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
            this.mod.copyToHeap(ptr, this.outputData, this.readOffset, length);
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
                    var contents = queryReadBuffer;
                    if (contents == null) {
                        throw new RuntimeException("No /dev/blob blob to read");
                    }
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
                    queryWriteChunks.add(chunk);
                    return length;
                }

                @Override
                public int llseek(int offset, int whence, int position) {
                    var contents = queryReadBuffer;
                    if (contents == null) {
                        throw new RuntimeException("No /dev/blob blob to llseek");
                    }
                    var next = offset;
                    if (whence == 1) {
                        next += position;
                    } else if (whence == 2) {
                        next = contents.length;
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
        var newBuffer = new byte[newSize];
        System.arraycopy(this.inputData, 0, newBuffer, 0, this.writeOffset);
        this.inputData = newBuffer;
    }

    private void parseMessage(messages.BackendMessage message) {
        if (this.currentDatabaseError != null) {
            return;
        }
        if (message instanceof messages.DatabaseError error) {
            if (this.currentThrowOnError) {
                this.currentDatabaseError = error;
            }
        } else if (message instanceof messages.NoticeMessage notice) {
            if (this.currentOnNotice != null) {
                this.currentOnNotice.accept(notice);
            }
        } else if (message instanceof messages.CommandCompleteMessage command) {
            if ("BEGIN".equals(command.text)) {
                this.inTransaction = true;
            } else if ("COMMIT".equals(command.text) || "ROLLBACK".equals(command.text)) {
                this.inTransaction = false;
            }
        } else if (message instanceof messages.NotificationResponseMessage notify) {
            receiveNotification(notify.channel, notify.payload);
        }
        this.currentResults.add(message);
    }

    private void receiveNotification(String channel, String payload) {
        var listeners = this.notifyListeners.get(channel);
        if (listeners != null) {
            for (var listener : listeners) {
                listener.accept(payload);
            }
        }
        for (var listener : this.globalNotifyListeners) {
            listener.accept(channel, payload);
        }
    }

    private void log(String message) {
        if (this.debug > 0) {
            System.out.println(message);
        }
    }
}
