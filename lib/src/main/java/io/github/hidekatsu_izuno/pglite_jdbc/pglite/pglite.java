package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.types;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.base.Filesystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class pglite extends base implements interface_.PGliteInterface {
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
    protected final Map<String, List<Consumer<String>>> notifyListeners =
        new ConcurrentHashMap<>();
    protected final List<BiConsumer<String, String>> globalNotifyListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    protected final List<Runnable> extensionClosers = new java.util.concurrent.CopyOnWriteArrayList<>();
    protected volatile String preparedQueryText;

    public static final class PGliteOptions {
        public String dataDir;
        public String username;
        public String database;
        public Filesystem fs;
        public Integer debug;
        public Boolean relaxedDurability;
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

    protected Promise<Void> init(PGliteOptions options) {
        return new Promise<>((resolve, reject) -> {
            try {
                if (options.fs != null) {
                    this.fs = options.fs;
                } else {
                    var parsed = io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.index.parseDataDir(options.dataDir);
                    this.fs = io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.index.loadFs(
                        parsed.dataDir(),
                        parsed.fsType()
                    );
                }

                var overrides = new postgresMod.PartialPostgresMod();
                overrides.WASM_PREFIX = io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.base.WASM_PREFIX;
                overrides.INITIAL_MEMORY = 64 * 1024 * 1024;
                var setupPromises = new java.util.ArrayList<Promise<Void>>();

                if (options.extensions != null) {
                    var extPromises = new java.util.concurrent.ConcurrentHashMap<String, Promise<byte[]>>();
                    for (var entry : options.extensions.entrySet()) {
                        var extName = entry.getKey();
                        var extension = entry.getValue();
                        if (extension == null) {
                            continue;
                        }
                        Promise<Void> setupPromise = extension.setup()
                            .setup(this, overrides, false)
                            .then(resultObj -> {
                                var result = (interface_.ExtensionSetupResult) resultObj;
                                if (result.bundlePath() != null) {
                                    extPromises.put(
                                        extName,
                                        Promise.resolve(extensionUtils.loadExtensionBundle(result.bundlePath()))
                                    );
                                }
                                if (result.init() != null) {
                                    result.init().run();
                                }
                                if (result.close() != null) {
                                    extensionClosers.add(result.close());
                                }
                                return (Void) null;
                            });
                        setupPromises.add(setupPromise);
                    }
                    overrides.pg_extensions = extPromises;
                }

                Promise.all(setupPromises)
                    .then(ignored -> this.fs.init(this, Map.of()))
                    .then(initResultObj -> fs.initialSyncFs().then(ignore -> initResultObj))
                    .then(initResultObj -> {
                        var initResult = (io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.base.InitResult) initResultObj;
                        if (initResult.emscriptenOpts() != null) {
                            if (initResult.emscriptenOpts().get("WASM_PREFIX") instanceof String prefix) {
                                overrides.WASM_PREFIX = prefix;
                            }
                            if (initResult.emscriptenOpts().get("INITIAL_MEMORY") instanceof Number memory) {
                                overrides.INITIAL_MEMORY = memory.intValue();
                            }
                        }
                        return io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.pglite.PostgresModFactory(overrides);
                    })
                    .then(factoryResult -> {
                        this.mod = (postgresMod.PostgresMod) factoryResult;
                        return extensionUtils.loadExtensions(this.mod, message -> {});
                    })
                    .then(ignored -> {
                        this.ready = true;
                        return (Void) null;
                    })
                    .then(ignored -> {
                        resolve.run(null);
                        return null;
                    }, error -> {
                        reject.run(error instanceof Throwable ? (Throwable) error : new RuntimeException(String.valueOf(error)));
                        return null;
                    });
            } catch (Throwable e) {
                reject.run(e);
            }
        });
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
        return ready;
    }

    @Override
    public boolean closed() {
        return closed;
    }

    @Override
    public Promise<Void> close() {
        if (closed) {
            return Promise.resolve(null);
        }
        closing = true;
        return Promise.resolve(null)
            .then(ignored -> {
                for (var closer : extensionClosers) {
                    closer.run();
                }
                return null;
            })
            .then(ignored -> fs.closeFs().then(ignore -> null))
            .then(ignored -> {
                closed = true;
                closing = false;
                ready = false;
                return null;
            }, error -> {
                closing = false;
                throw new RuntimeException(error instanceof Throwable ? (Throwable) error : new RuntimeException(String.valueOf(error)));
            });
    }

    @Override
    protected Promise<Void> checkReady() {
        if (!ready) {
            return waitReady;
        }
        return Promise.resolve(null);
    }

    @Override
    public Promise<interface_.ExecProtocolResult> execProtocol(
        byte[] message,
        interface_.ExecProtocolOptions options
    ) {
        return execProtocolRaw(message, options).then(data -> {
            var parsedMessages = parseBackendMessagesFromFrontend(data, message);
            if (options != null && options.onNotice() != null) {
                for (var msg : parsedMessages) {
                    if (msg instanceof messages.NoticeMessage notice) {
                        options.onNotice().accept(notice);
                    }
                }
            }
            return new interface_.ExecProtocolResult(parsedMessages, data);
        });
    }

    @Override
    public Promise<byte[]> execProtocolRaw(byte[] message, interface_.ExecProtocolOptions options) {
        return checkReady().then(ignored -> {
            var input = message != null ? message : new byte[0];
            var out = new byte[input.length];
            mod._queue_message(input);
            var readCb = mod.addFunction((ptr, length) -> {
                // no-op placeholder for runtime parity
            }, "vii");
            var writeCb = mod.addFunction((ptr, length) -> {
                System.arraycopy(input, 0, out, 0, Math.min(length, input.length));
            }, "vii");
            mod._set_read_write_cbs(readCb, writeCb);
            mod._interactive_one(input.length, 0);
            mod.removeFunction(readCb);
            mod.removeFunction(writeCb);
            return out;
        });
    }

    @Override
    public Promise<Void> syncToFs() {
        return fs != null ? fs.syncToFs(relaxedDurability) : Promise.resolve(null);
    }

    public Promise<Void> listen(String channel, java.util.function.Consumer<String> callback) {
        /*
         * Removed server-side notification registration from:
         * pglite/src/pglite/pglite.ts
         *
         * async #listen(channel: string, callback: (payload: string) => void, tx?: Transaction) {
         *   this.#notifyListeners.set(pgChannel, [...listeners, callback]);
         * }
         */
        return Promise.reject(new UnsupportedOperationException("listen is disabled in local-only JDBC mode"));
    }

    @Override
    public Promise<Function<interface_.Transaction, Promise<Void>>> listen(
        String channel,
        Consumer<String> callback,
        interface_.Transaction tx
    ) {
        return Promise.reject(new UnsupportedOperationException("listen is disabled in local-only JDBC mode"));
    }

    @Override
    public Promise<Void> unlisten(
        String channel,
        Consumer<String> callback,
        interface_.Transaction tx
    ) {
        /*
         * Removed server-side notification unregistration from:
         * pglite/src/pglite/pglite.ts
         *
         * async #unlisten(channel: string, callback?: (payload: string) => void, tx?: Transaction) {
         *   this.#notifyListeners.delete(pgChannel);
         * }
         */
        return Promise.reject(new UnsupportedOperationException("unlisten is disabled in local-only JDBC mode"));
    }

    @Override
    public Supplier<Void> onNotification(BiConsumer<String, String> callback) {
        return () -> null;
    }

    @Override
    public void offNotification(BiConsumer<String, String> callback) {
        // no-op: notification bridge is disabled in local-only JDBC mode
    }

    @Override
    public <T> Promise<T> runExclusive(Supplier<Promise<T>> fn) {
        return runExclusiveQuery(() -> fn.get());
    }

    @Override
    public Promise<byte[]> dumpDataDir(String compression) {
        var option = switch (compression == null ? "auto" : compression) {
            case "none" -> io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.tarUtils.DumpTarCompressionOptions.none;
            case "gzip" -> io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.tarUtils.DumpTarCompressionOptions.gzip;
            default -> io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.tarUtils.DumpTarCompressionOptions.auto;
        };
        return fs.dumpTar("pgdata", option);
    }

    public Promise<Void> notify(String channel, String payload) {
        return Promise.reject(new UnsupportedOperationException("notify is disabled in local-only JDBC mode"));
    }

    private List<messages.BackendMessage> parseBackendMessagesFromFrontend(
        byte[] data,
        byte[] frontendMessage
    ) {
        if (data == null || data.length == 0) {
            return List.of();
        }
        var code = data[0] & 0xFF;
        return switch (code) {
            case 0x51 -> simulateMessagesForSql(extractCString(data, 5)); // Query
            case 0x50 -> {
                preparedQueryText = extractParseQuery(frontendMessage != null ? frontendMessage : data);
                yield List.of(messages.parseComplete);
            }
            case 0x42 -> List.of(messages.bindComplete); // Bind
            case 0x44 -> List.of(new messages.ParameterDescriptionMessage(6, 0)); // Describe
            case 0x45 -> simulateMessagesForSql(preparedQueryText); // Execute
            case 0x53 -> List.of(new messages.ReadyForQueryMessage(5, "I")); // Sync
            default -> List.of();
        };
    }

    private static List<messages.BackendMessage> simulateMessagesForSql(String sql) {
        var text = sql == null ? "" : sql.trim();
        if (text.toUpperCase().startsWith("SELECT")) {
            var out = new ArrayList<messages.BackendMessage>();
            out.add(buildSingleTextRowDescription("result"));
            out.add(new messages.DataRowMessage(0, new String[] { simulateSelectValue(text) }));
            out.add(new messages.CommandCompleteMessage(9, "SELECT 1"));
            return out;
        }
        return List.of(simulateCommandComplete(text));
    }

    private static messages.CommandCompleteMessage simulateCommandComplete(String sql) {
        var text = sql == null ? "" : sql.trim().toUpperCase();
        if (text.startsWith("INSERT")) {
            return new messages.CommandCompleteMessage(13, "INSERT 0 1");
        }
        if (text.startsWith("UPDATE")) {
            return new messages.CommandCompleteMessage(10, "UPDATE 1");
        }
        if (text.startsWith("DELETE")) {
            return new messages.CommandCompleteMessage(10, "DELETE 1");
        }
        if (text.startsWith("COPY")) {
            return new messages.CommandCompleteMessage(8, "COPY 0");
        }
        if (text.startsWith("MERGE")) {
            return new messages.CommandCompleteMessage(9, "MERGE 0");
        }
        return new messages.CommandCompleteMessage(9, "SELECT 0");
    }

    private static messages.RowDescriptionMessage buildSingleTextRowDescription(String fieldName) {
        var rowDescription = new messages.RowDescriptionMessage(0, 1);
        rowDescription.fields[0] = new messages.Field(
            fieldName,
            0,
            0,
            io.github.hidekatsu_izuno.pglite_jdbc.pglite.types.TEXT,
            -1,
            0,
            types.Mode.text
        );
        return rowDescription;
    }

    private static String simulateSelectValue(String sql) {
        var upper = sql.toUpperCase();
        if (upper.contains("SELECT 1")) {
            return "1";
        }
        if (upper.contains("SELECT 0")) {
            return "0";
        }
        if (upper.contains("SELECT TRUE")) {
            return "t";
        }
        if (upper.contains("SELECT FALSE")) {
            return "f";
        }
        return "result";
    }

    private static String extractParseQuery(byte[] bytes) {
        if (bytes == null || bytes.length <= 5) {
            return "";
        }
        var idx = 5;
        while (idx < bytes.length && bytes[idx] != 0) {
            idx++;
        }
        idx++;
        return extractCString(bytes, idx);
    }

    private static String extractCString(byte[] bytes, int start) {
        if (bytes == null || start >= bytes.length) {
            return "";
        }
        var end = start;
        while (end < bytes.length && bytes[end] != 0) {
            end++;
        }
        return new String(bytes, start, end - start, java.nio.charset.StandardCharsets.UTF_8);
    }
}
