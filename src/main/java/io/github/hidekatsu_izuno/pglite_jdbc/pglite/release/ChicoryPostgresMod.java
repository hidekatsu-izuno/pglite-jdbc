package io.github.hidekatsu_izuno.pglite_jdbc.pglite.release;

import com.dylibso.chicory.runtime.ByteArrayMemory;
import com.dylibso.chicory.runtime.HostFunction;
import com.dylibso.chicory.runtime.ImportFunction;
import com.dylibso.chicory.runtime.ImportMemory;
import com.dylibso.chicory.runtime.ImportValues;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Memory;
import com.dylibso.chicory.wasi.WasiOptions;
import com.dylibso.chicory.wasi.WasiPreview1;
import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.types.FunctionImport;
import com.dylibso.chicory.wasm.types.FunctionType;
import com.dylibso.chicory.wasm.types.MemoryImport;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.extensionUtils;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.initdbModFactory;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.postgresMod;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array;
import java.io.ByteArrayInputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class ChicoryPostgresMod implements postgresMod.PostgresMod, initdbModFactory.InitdbMod {
    private static final int DEFAULT_INITIAL_PAGES = 2048;
    private static final int DEFAULT_MAX_PAGES = 32768;
    private static final int PGLITE_EXIT_ALIVE = 99;
    private static final int POSTGRES_MAIN_LONGJMP = 100;
    private static final boolean TRACE_HOST_CALLS = Boolean.getBoolean("pglite.trace_host_calls");
    private static final boolean TRACE_WASI_CALLS = Boolean.getBoolean("pglite.trace_wasi_calls");
    private static final boolean TRACE_ENV_CALLS = Boolean.getBoolean("pglite.trace_env_calls");
    private static final boolean TRACE_EXEC = Boolean.getBoolean("pglite.trace_exec");

    private final postgresMod.PartialPostgresMod overrides;
    private final URL moduleUrl;
    private final Map<Integer, postgresMod.ReadWriteCallback> callbacks = new HashMap<>();
    private final Map<String, Integer> functionImportIndices = new HashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Path root;
    private final Path pgRoot;
    private final Path homeRoot;
    private final Path dataRoot;
    private final SimpleFS fs;
    private final WasiPreview1 wasi;
    private Memory memory;
    private Instance instance;
    private int nextCallback = 1;
    private int socketRead;
    private int socketWrite;
    private int systemFn;
    private int popenFn;
    private int pcloseFn;
    private int blobRead;
    private int blobWrite;
    private int blobLlseek;
    private int tempRet0;
    private Integer fdBufferMax;
    private int callbackPiRegistrations;
    private int callbackIiiRegistrations;
    private int nextTableCallbackSlot = -1;

    public ChicoryPostgresMod(postgresMod.PartialPostgresMod overrides, URL moduleUrl) {
        this.overrides = overrides != null ? overrides : new postgresMod.PartialPostgresMod();
        this.moduleUrl = moduleUrl;
        try {
            this.root = resolveRoot(this.overrides);
            this.pgRoot = root.resolve("pglite");
            this.homeRoot = root.resolve("home");
            this.dataRoot = this.overrides.__wasiDataRoot != null
                ? Path.of(this.overrides.__wasiDataRoot).toAbsolutePath().normalize()
                : root.resolve("data");
            bootstrapStaticFiles();
            this.fs = new SimpleFS(root);
            var wasiOptions = WasiOptions.builder()
                .withArguments(wasiArguments(this.overrides))
                .withDirectory("/", root)
                .withDirectory("/pglite", pgRoot)
                .withDirectory("/home", homeRoot)
                .withStdin(new ByteArrayInputStream(new byte[0]))
                .withStdout(System.out, false)
                .withStderr(System.err, false);
            if (this.overrides.__wasiDataRoot != null) {
                wasiOptions.withDirectory("/data", dataRoot);
            }
            var env = mergedEnv();
            for (var entry : env.entrySet()) {
                wasiOptions.withEnvironment(entry.getKey(), entry.getValue());
            }
            this.wasi = WasiPreview1.builder().withOptions(wasiOptions.build()).build();
            this.memory = createMemory(this.overrides);
            instantiate();
            runHooks();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static java.util.List<String> wasiArguments(postgresMod.PartialPostgresMod overrides) {
        var args = new ArrayList<String>();
        args.add(overrides.thisProgram != null ? overrides.thisProgram : "/pglite/bin/postgres");
        if (overrides.arguments != null) {
            args.addAll(java.util.Arrays.asList(overrides.arguments));
        }
        return args;
    }

    private static Path resolveRoot(postgresMod.PartialPostgresMod overrides) throws Exception {
        if (overrides.__wasiRoot != null) {
            var root = Path.of(overrides.__wasiRoot).toAbsolutePath().normalize();
            Files.createDirectories(root);
            return root;
        }
        return Files.createTempDirectory("pglite-wasi-");
    }

    private Memory createMemory(postgresMod.PartialPostgresMod overrides) {
        var initialMemory = overrides.INITIAL_MEMORY;
        var initialPages = initialMemory != null
            ? Math.max(1, (initialMemory + Memory.PAGE_SIZE - 1) / Memory.PAGE_SIZE)
            : DEFAULT_INITIAL_PAGES;
        return new ByteArrayMemory(new com.dylibso.chicory.wasm.types.MemoryLimits(initialPages, DEFAULT_MAX_PAGES));
    }

    private Map<String, String> mergedEnv() {
        var env = new HashMap<String, String>();
        env.put("PGDATA", "/data");
        env.put("HOME", "/home/postgres");
        env.put("USER", "postgres");
        env.put("LOGNAME", "postgres");
        env.put("ICU_DATA", "/pglite/icu");
        env.put("TZ", "UTC");
        env.put("PGTZ", "UTC");
        env.put("PGCLIENTENCODING", "UTF8");
        if (overrides.ENV != null) {
            env.putAll(overrides.ENV);
        }
        if (overrides.PGLITE_ENV != null) {
            env.putAll(overrides.PGLITE_ENV);
        }
        return env;
    }

    private void bootstrapStaticFiles() throws Exception {
        Files.createDirectories(pgRoot.resolve("bin"));
        Files.createDirectories(homeRoot.resolve("postgres"));
        if (overrides.__wasiDataRoot != null) {
            Files.createDirectories(dataRoot);
        }
        writeStatic(pgRoot.resolve("bin/initdb"), "PGlite is the best!\n");
        writeStatic(pgRoot.resolve("bin/pg_dump"), "PGlite is the best!\n");
        writeStatic(pgRoot.resolve("bin/postgres"), "PGlite is the best!\n");
        writeStatic(pgRoot.resolve("pgstdin"), "PGlite is the best!\n");
        writeStatic(pgRoot.resolve("pgstdout"), "PGlite is the best!\n");
        writeStatic(pgRoot.resolve("password"), "password\n");
        writeStatic(
            homeRoot.resolve("postgres/.pgpass"),
            String.join(
                "\n",
                "# PGlite pgpass file",
                "localhost:5432:postgres:password:md532e12f215ba27cb750c9e093ce4b5127",
                "localhost:5432:postgres:postgres:md53175bce1d3201d16594cebf9d7eb3f9d",
                "localhost:5432:postgres:login:md5d5745f9425eceb269f9fe01d0bef06ff",
                ""
            )
        );
        copyInstallDir("share");
        copyInstallDir("lib");
        copyInstallDir("icu");
    }

    private void writeStatic(Path path, String text) throws Exception {
        if (!Files.exists(path)) {
            Files.createDirectories(path.getParent());
            Files.writeString(path, text);
        }
    }

    private void copyInstallDir(String name) throws Exception {
        var target = pgRoot.resolve(name);
        if (Files.exists(target) || moduleUrl == null || !"file".equals(moduleUrl.getProtocol())) {
            return;
        }
        var source = Path.of(moduleUrl.toURI()).getParent().resolve(name);
        if (Files.exists(source)) {
            copyTree(source, target);
        }
    }

    private void copyTree(Path source, Path target) throws Exception {
        try (var stream = Files.walk(source)) {
            for (var path : stream.toList()) {
                var dest = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(path, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void instantiate() throws Exception {
        var wasmBytes = overrides.wasmModule != null
            ? overrides.wasmModule
            : moduleUrl.openStream().readAllBytes();
        var module = Parser.parse(wasmBytes);
        growCallbackTables(module);
        var functions = new ArrayList<ImportFunction>();
        var wasiFunctions = new HashMap<String, ImportFunction>();
        for (var fn : wasi.toHostFunctions()) {
            wasiFunctions.put(fn.module() + "." + fn.name(), fn);
        }
        var functionIndex = 0;
        for (var imp : module.importSection().stream().toList()) {
            if (imp instanceof FunctionImport fn) {
                var type = module.typeSection().getType(fn.typeIndex());
                functionImportIndices.put(imp.module() + "." + imp.name(), functionIndex);
                functionIndex++;
                if ("wasi_snapshot_preview1".equals(imp.module())) {
                    var wasiFunction = wasiFunctions.get(imp.module() + "." + imp.name());
                    functions.add(new HostFunction(imp.module(), imp.name(), type, (inst, args) -> {
                        if (TRACE_WASI_CALLS) {
                            if (tracedWasiCall(imp.name())) {
                                System.err.println("[wasi] " + imp.name() + " " + java.util.Arrays.toString(args) + wasiPathSuffix(inst.memory(), imp.name(), args));
                            }
                        }
                        var callArgs = args;
                        if ("path_open".equals(imp.name()) && args.length >= 8) {
                            callArgs = args.clone();
                            callArgs[7] = ((int) callArgs[7]) & ~0x1e;
                        }
                        var result = "path_rename".equals(imp.name())
                            ? wasiPathRename(inst.memory(), callArgs)
                            : wasiFunction.handle().apply(inst, callArgs);
                        if (TRACE_WASI_CALLS && tracedWasiCall(imp.name())) {
                            System.err.println("[wasi] " + imp.name() + " -> " + java.util.Arrays.toString(result));
                        }
                        return result;
                    }));
                    continue;
                }
                functions.add(new HostFunction(imp.module(), imp.name(), type, (inst, args) -> hostCall(imp.module(), imp.name(), type, args)));
            }
        }
        var imports = ImportValues.builder()
            .withFunctions(functions)
            .addMemory(new ImportMemory("env", "memory", memory))
            .build();
        var builder = Instance.builder(module)
            .withImportValues(imports)
            .withInitialize(true)
            .withStart(false);
        if (TRACE_EXEC) {
            var counter = new java.util.concurrent.atomic.AtomicLong();
            builder.withUnsafeExecutionListener((instruction, stack) -> {
                var value = counter.incrementAndGet();
                if (instruction.opcode() == com.dylibso.chicory.wasm.types.OpCode.CALL_INDIRECT) {
                    var tableIndex = instruction.opcode() == com.dylibso.chicory.wasm.types.OpCode.CALL_INDIRECT && stack.size() > 0
                        ? (int) stack.peek()
                        : -1;
                    var tableRef = tableIndex >= 0 && instance != null && tableIndex < instance.table(0).size()
                        ? instance.table(0).ref(tableIndex)
                        : -1;
                    if (tableRef <= 0) {
                        System.err.println(
                            "[exec] count=" + value
                                + " addr=" + instruction.address()
                                + " op=" + instruction.opcode()
                                + " operands=" + java.util.Arrays.toString(instruction.operands())
                                + " tableIndex=" + tableIndex
                                + " tableRef=" + tableRef
                                + " stack=" + stack.size()
                        );
                    }
                }
            });
        }
        this.instance = builder.build();
        this.memory = this.instance.memory();
        callIfExists("__wasm_init_memory");
        callIfExists("__wasm_call_ctors");
    }

    private boolean tracedWasiCall(String name) {
        return switch (name) {
            case "path_open", "path_rename", "fd_read", "fd_fdstat_get", "fd_seek", "fd_tell", "fd_close", "fd_pread" -> true;
            default -> false;
        };
    }

    private String wasiPathSuffix(Memory callMemory, String name, long[] args) {
        try {
            return switch (name) {
                case "path_open" -> " path=" + callMemory.readString((int) args[2], (int) args[3]);
                case "path_rename" -> " old=" + callMemory.readString((int) args[1], (int) args[2])
                    + " new=" + callMemory.readString((int) args[4], (int) args[5]);
                case "path_filestat_get", "path_create_directory", "path_remove_directory", "path_unlink_file" ->
                    " path=" + callMemory.readString((int) args[2], (int) args[3]);
                case "path_readlink" -> " path=" + callMemory.readString((int) args[2], (int) args[3]);
                default -> "";
            };
        } catch (RuntimeException e) {
            return " path=<decode-error>";
        }
    }

    private long[] wasiPathRename(Memory callMemory, long[] args) {
        try {
            var oldPath = callMemory.readString((int) args[1], (int) args[2]);
            var newPath = callMemory.readString((int) args[4], (int) args[5]);
            var source = resolveWasiPath(oldPath);
            var target = resolveWasiPath(newPath);
            Files.createDirectories(target.getParent());
            Files.move(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return new long[] {0};
        } catch (java.nio.file.NoSuchFileException e) {
            return new long[] {44};
        } catch (Exception e) {
            if (TRACE_WASI_CALLS) {
                System.err.println("[wasi] path_rename fallback failed: " + e);
            }
            return new long[] {58};
        }
    }

    private Path resolveWasiPath(String path) {
        var normalized = path != null && path.startsWith("/") ? path.substring(1) : path;
        return root.resolve(normalized == null ? "" : normalized).normalize();
    }

    private void growCallbackTables(com.dylibso.chicory.wasm.WasmModule module) {
        if (module.tableSection() == null) {
            return;
        }
        for (var i = 0; i < module.tableSection().tableCount(); i++) {
            module.tableSection().getTable(i).limits().grow(16);
        }
    }

    private void runHooks() {
        if (overrides.preInit != null) {
            overrides.preInit.forEach(fn -> fn.accept(this));
        }
        if (overrides.preRun != null) {
            overrides.preRun.forEach(fn -> fn.accept(this));
        }
        if (overrides.onRuntimeInitialized != null) {
            overrides.onRuntimeInitialized.run();
        }
        if (overrides.postRun != null) {
            overrides.postRun.forEach(fn -> fn.accept(this));
        }
    }

    private long[] hostCall(String module, String name, FunctionType type, long[] args) {
        var result = switch (module) {
            case "env" -> envCall(name, args);
            case "pglite" -> pgliteCall(name, args);
            default -> 0L;
        };
        if (type.returns().isEmpty()) {
            return new long[0];
        }
        return new long[] { result };
    }

    private long envCall(String name, long[] args) {
        if (TRACE_ENV_CALLS && (name.startsWith("invoke_") || name.contains("setjmp") || name.contains("longjmp") || name.contains("TempRet0"))) {
            System.err.println("[env] " + name + " " + java.util.Arrays.toString(args));
        }
        if (name.startsWith("invoke_")) {
            var tableIndex = (int) args[0];
            if (tableIndex == 0) {
                return 0L;
            }
            var fn = instance.table(0).ref(tableIndex);
            if (fn == 0) {
                return 0L;
            }
            var callArgs = java.util.Arrays.copyOfRange(args, 1, args.length);
            try {
                var ret = instance.getMachine().call(fn, callArgs);
                return ret != null && ret.length > 0 ? ret[0] : 0L;
            } catch (EmscriptenLongjmp e) {
                tempRet0 = 0;
                return 0L;
            } catch (com.dylibso.chicory.wasm.ChicoryException e) {
                if (e.getCause() instanceof EmscriptenLongjmp) {
                    tempRet0 = 0;
                    return 0L;
                }
                if (e.getMessage() != null && e.getMessage().contains("uninitialized element")) {
                    return 0L;
                }
                throw e;
            }
        }
        return switch (name) {
            case "__wasm_setjmp", "__wasm_setjmp_test" -> 0L;
            case "getTempRet0" -> tempRet0;
            case "setTempRet0" -> {
                tempRet0 = (int) args[0];
                yield 0L;
            }
            case "__wasm_longjmp", "emscripten_longjmp" -> throw new EmscriptenLongjmp((int) args[0], (int) args[1]);
            default -> 0L;
        };
    }

    private long pgliteCall(String name, long[] args) {
        if (TRACE_HOST_CALLS && ("system".equals(name) || "popen".equals(name) || "pclose".equals(name) || name.startsWith("socket_"))) {
            System.err.println("[pglite-host] " + name + " " + java.util.Arrays.toString(args));
            if ("popen".equals(name) && args.length >= 2) {
                System.err.println(
                    "[pglite-host] popen strings cmd="
                        + debugCString((int) args[0])
                        + " mode="
                        + debugCString((int) args[1])
                );
            }
        }
        return switch (name) {
            case "random" -> {
                var length = (int) args[1];
                var bytes = new byte[length];
                random.nextBytes(bytes);
                memory.write((int) args[0], bytes);
                yield 0L;
            }
            case "socket_read" -> invokeCallback(socketRead, (int) args[0], (int) args[1]);
            case "socket_write" -> invokeCallback(socketWrite, (int) args[0], (int) args[1]);
            case "system" -> systemFn != 0 ? invokeCallback(systemFn, (int) args[0]) : 1L;
            case "popen" -> popenFn != 0 ? invokeCallback(popenFn, (int) args[0], (int) args[1]) : 0L;
            case "pclose" -> pcloseFn != 0 ? invokeCallback(pcloseFn, (int) args[0]) : 0L;
            case "blob_read" -> invokeCallback(blobRead, (int) args[0], (int) args[1], (int) args[2]);
            case "blob_write" -> invokeCallback(blobWrite, (int) args[0], (int) args[1], (int) args[2]);
            case "blob_llseek" -> invokeCallback(blobLlseek, (int) args[0], (int) args[1]);
            default -> 0L;
        };
    }

    private long invokeCallback(int id, int... args) {
        var callback = callbacks.get(id);
        if (callback == null) {
            if (TRACE_HOST_CALLS) {
                System.err.println("[pglite-host] missing callback " + id + " " + java.util.Arrays.toString(args));
            }
            return 0L;
        }
        return callback.apply(args.length > 0 ? args[0] : 0, args.length > 1 ? args[1] : 0);
    }

    private String debugCString(int ptr) {
        try {
            var bytes = memory.readBytes(ptr, 16);
            return UTF8ToString(ptr) + " " + java.util.Arrays.toString(bytes);
        } catch (RuntimeException e) {
            return "<decode-error " + e.getMessage() + ">";
        }
    }

    private long call(String name, long... args) {
        var ret = instance.export(name).apply(args);
        return ret != null && ret.length > 0 ? ret[0] : 0L;
    }

    private void callIfExists(String name) {
        try {
            instance.export(name).apply();
        } catch (RuntimeException ignored) {
            // Optional export.
        }
    }

    private long callIfExists(String name, long... args) {
        try {
            return call(name, args);
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    @Override
    public String WASM_PREFIX() {
        return overrides.WASM_PREFIX != null ? overrides.WASM_PREFIX : "/pglite";
    }

    @Override
    public Integer INITIAL_MEMORY() {
        return overrides.INITIAL_MEMORY;
    }

    @Override
    public Integer FD_BUFFER_MAX() {
        return fdBufferMax;
    }

    @Override
    public void setFD_BUFFER_MAX(Integer value) {
        fdBufferMax = value;
    }

    @Override
    public Uint8Array HEAP8() {
        return HEAPU8();
    }

    @Override
    public Uint8Array HEAPU8() {
        return new MemoryUint8Array(memory);
    }

    @Override
    public Map<String, CompletableFuture<extensionUtils.ExtensionBlob>> pg_extensions() {
        return overrides.pg_extensions != null ? overrides.pg_extensions : Map.of();
    }

    @Override
    public int _pgl_initdb() {
        return callMain(overrides.arguments != null ? overrides.arguments : new String[0]);
    }

    @Override
    public void _pgl_backend() {
        call("pgl_startPGlite");
    }

    @Override
    public void _pgl_setPGliteActive(int active) {
        call("pgl_setPGliteActive", active);
    }

    @Override
    public void _pgl_shutdown() {
        callIfExists("pgl_run_atexit_funcs");
    }

    @Override
    public void _interactive_write(int msgLength) {
        _interactive_one(msgLength, 0);
    }

    @Override
    public void _interactive_one(int length, int peek) {
        do {
            _PostgresMainLoopOnce();
        } while (call("pq_buffer_remaining_data") > 0);
        _PostgresSendReadyForQueryIfNecessary();
        _pgl_pq_flush();
    }

    @Override
    public void _PostgresMainLoopOnce() {
        try {
            call("PostgresMainLoopOnce");
        } catch (ExitStatus exit) {
            if (exit.status != POSTGRES_MAIN_LONGJMP) {
                throw exit;
            }
            _PostgresMainLongJmp();
        }
    }

    @Override
    public void _PostgresMainLongJmp() {
        call("PostgresMainLongJmp");
    }

    @Override
    public void _PostgresSendReadyForQueryIfNecessary() {
        call("PostgresSendReadyForQueryIfNecessary");
    }

    @Override
    public void _pgl_pq_flush() {
        call("pgl_pq_flush");
    }

    @Override
    public int _pq_buffer_remaining_data() {
        return (int) call("pq_buffer_remaining_data");
    }

    @Override
    public int _pgl_getMyProcPort() {
        return (int) call("pgl_getMyProcPort");
    }

    @Override
    public int _ProcessStartupPacket(int myProcPort, boolean sslDone, boolean gssDone) {
        return (int) call("ProcessStartupPacket", myProcPort, sslDone ? 1 : 0, gssDone ? 1 : 0);
    }

    @Override
    public void _pgl_sendConnData() {
        call("pgl_sendConnData");
    }

    @Override
    public void _set_read_write_cbs(int read_cb, int write_cb) {
        this.socketRead = read_cb;
        this.socketWrite = write_cb;
        call("pgl_set_rw_cbs", read_cb, write_cb);
    }

    @Override
    public int addFunction(postgresMod.ReadWriteCallback cb, String signature) {
        var dispatcher = dispatcherImport(signature);
        var functionIndex = functionImportIndices.get(dispatcher);
        if (functionIndex == null) {
            var id = nextCallback++;
            callbacks.put(id, cb);
            return id;
        }
        var table = instance.table(0);
        var id = allocateTableCallbackSlot(table);
        table.setRef(id, functionIndex, instance);
        callbacks.put(id, cb);
        return id;
    }

    private int allocateTableCallbackSlot(com.dylibso.chicory.runtime.TableInstance table) {
        if (nextTableCallbackSlot < 0) {
            nextTableCallbackSlot = Math.max(1, table.size() - 16);
        }
        if (nextTableCallbackSlot >= table.size()) {
            throw new IllegalStateException(
                "No wasm table slots for callback; size=" + table.size()
                    + " limits=" + table.limits()
            );
        }
        return nextTableCallbackSlot++;
    }

    private int findExistingTableSlot(com.dylibso.chicory.runtime.TableInstance table, int functionIndex) {
        for (var i = 1; i < table.size(); i++) {
            if (table.ref(i) == functionIndex) {
                return i;
            }
        }
        return -1;
    }

    private int findFreeTableSlot(com.dylibso.chicory.runtime.TableInstance table) {
        for (var i = 1; i < table.size(); i++) {
            if (!callbacks.containsKey(i) && table.ref(i) == 0) {
                return i;
            }
        }
        var grown = table.grow(1, 0, instance);
        if (grown < 0) {
            throw new IllegalStateException(
                "No free wasm table slots for callback; size=" + table.size()
                    + " limits=" + table.limits()
            );
        }
        return grown;
    }

    private String dispatcherImport(String signature) {
        return switch (signature) {
            case "ppi" -> "pglite.popen";
            case "pi" -> popenFn != 0 || callbackIiiRegistrations > 0 || callbackPiRegistrations++ > 0
                ? "pglite.pclose"
                : "pglite.system";
            case "iii" -> callbackIiiRegistrations++ == 0 ? "pglite.socket_write" : "pglite.socket_read";
            case "ii" -> "pglite.socket_read";
            default -> "pglite.socket_read";
        };
    }

    @Override
    public void removeFunction(int f) {
        callbacks.remove(f);
    }

    @Override
    public void copyFromHeap(int ptr, byte[] dest, int destOffset, int length) {
        var bytes = memory.readBytes(ptr, length);
        System.arraycopy(bytes, 0, dest, destOffset, length);
    }

    @Override
    public void copyToHeap(int ptr, byte[] src, int srcOffset, int length) {
        memory.write(ptr, src, srcOffset, length);
    }

    @Override
    public postgresMod.EmscriptenRuntime runtime() {
        return new RuntimeAdapter();
    }

    @Override
    public extensionUtils.EmscriptenFS FS() {
        return fs;
    }

    @Override
    public int callMain(String[] args) {
        if (usesInitialWasiArguments(args)) {
            try {
                return (int) call("__main_void");
            } catch (com.dylibso.chicory.wasi.WasiExitException exit) {
                return exit.exitCode();
            } catch (ExitStatus exit) {
                return exit.status;
            }
        }
        var argvWithProgram = new ArrayList<String>();
        argvWithProgram.add(overrides.thisProgram != null ? overrides.thisProgram : "/pglite/bin/postgres");
        if (args != null) {
            argvWithProgram.addAll(java.util.Arrays.asList(args));
        }
        var malloc = instance.export("malloc");
        var free = instance.export("free");
        var ptrs = new ArrayList<Integer>();
        try {
            for (var arg : argvWithProgram) {
                ptrs.add(writeCString(malloc, arg));
            }
            var argv = (int) malloc.apply((ptrs.size() + 1) * 4)[0];
            for (var i = 0; i < ptrs.size(); i++) {
                memory.writeI32(argv + i * 4, ptrs.get(i));
            }
            memory.writeI32(argv + ptrs.size() * 4, 0);
            try {
                return (int) call("__main_argc_argv", argvWithProgram.size(), argv);
            } catch (com.dylibso.chicory.wasi.WasiExitException exit) {
                return exit.exitCode();
            } catch (ExitStatus exit) {
                return exit.status;
            }
        } finally {
            for (var ptr : ptrs) {
                free.apply(ptr);
            }
        }
    }

    private boolean usesInitialWasiArguments(String[] args) {
        return java.util.Arrays.equals(
            args != null ? args : new String[0],
            overrides.arguments != null ? overrides.arguments : new String[0]
        );
    }

    private int writeCString(com.dylibso.chicory.runtime.ExportFunction malloc, String text) {
        var bytes = (text + "\0").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var ptr = (int) malloc.apply(bytes.length)[0];
        memory.write(ptr, bytes);
        return ptr;
    }

    @Override
    public Map<String, String> ENV() {
        return mergedEnv();
    }

    @Override
    public Object PROXYFS() {
        return new Object();
    }

    @Override
    public String UTF8ToString(int ptr) {
        return memory.readCString(ptr);
    }

    @Override
    public int stringToUTF8OnStack(String s) {
        return writeCString(instance.export("malloc"), s);
    }

    @Override
    public void _pgl_set_system_fn(int systemFn) {
        this.systemFn = systemFn;
        call("pgl_set_system_fn", systemFn);
    }

    @Override
    public void _pgl_set_popen_fn(int popenFn) {
        this.popenFn = popenFn;
        call("pgl_set_popen_fn", popenFn);
    }

    @Override
    public void _pgl_set_pclose_fn(int pcloseFn) {
        this.pcloseFn = pcloseFn;
        call("pgl_set_pclose_fn", pcloseFn);
    }

    @Override
    public int _fopen(int path, int mode) {
        return (int) callIfExists("fopen", path, mode);
    }

    @Override
    public int _fclose(int stream) {
        return (int) callIfExists("fclose", stream);
    }

    @Override
    public void _fflush(int stream) {
        callIfExists("fflush", stream);
    }

    @Override
    public int _pclose(int stream) {
        return (int) pgliteCall("pclose", new long[] { stream });
    }

    @Override
    public int ___errno_location() {
        return 0;
    }

    @Override
    public int _strerror(int errno) {
        return 0;
    }

    @Override
    public int _pipe(int fd) {
        return (int) callIfExists("pipe", fd);
    }

    @Override
    public Boolean __wasi() {
        return true;
    }

    public void _pgl_freopen(int path, int mode, int fd) {
        callIfExists("pgl_freopen", path, mode, fd);
    }

    public Integer _close(int fd) {
        return (int) callIfExists("close", fd);
    }

    public Integer _pgl_chdir(int path) {
        return (int) callIfExists("pgl_chdir", path);
    }

    private final class RuntimeAdapter implements postgresMod.EmscriptenRuntime {
        @Override
        public extensionUtils.EmscriptenFS FS() {
            return fs;
        }

        @Override
        public byte[] getPreloadedPackage(String name, int size) {
            try {
                return Files.readAllBytes(Path.of(name));
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public void addRunDependency(String key) {}

        @Override
        public void removeRunDependency(String key) {}

        @Override
        public void preRun() {}

        @Override
        public void postRun() {}

        @Override
        public int makedev(int major, int minor) {
            return fs.makedev(major, minor);
        }

        @Override
        public void registerDevice(int devId, postgresMod.DeviceOps ops) {
            fs.registerDevice(devId, ops);
        }

        @Override
        public void mkdev(String path, int devId) {
            fs.mkdev(path, devId);
        }
    }

    private static final class ExitStatus extends RuntimeException {
        private final int status;

        private ExitStatus(int status) {
            this.status = status;
        }
    }

    private static final class EmscriptenLongjmp extends RuntimeException {
        private final int env;
        private final int value;

        private EmscriptenLongjmp(int env, int value) {
            this.env = env;
            this.value = value;
        }
    }

    private static final class MemoryUint8Array extends Uint8Array {
        private final Memory memory;

        private MemoryUint8Array(Memory memory) {
            super(memory.pages() * Memory.PAGE_SIZE);
            this.memory = memory;
        }

        @Override
        public byte get(int index) {
            if (index < 0 || index >= this.length) {
                throw new IndexOutOfBoundsException("index out of range");
            }
            return memory.readBytes(index, 1)[0];
        }

        @Override
        public void set(int index, int value) {
            if (index < 0 || index >= this.length) {
                throw new IndexOutOfBoundsException("index out of range");
            }
            memory.write(index, new byte[] {(byte) (value & 0xFF)});
        }

        @Override
        public void set(byte[] source, int offset) {
            if (offset < 0 || offset > this.length) {
                throw new IndexOutOfBoundsException("offset out of range");
            }
            if (source.length + offset > this.length) {
                throw new IndexOutOfBoundsException("source length out of range");
            }
            if (source.length > 0) {
                memory.write(offset, source);
            }
        }

        @Override
        public void set(Uint8Array source, int offset) {
            set(source.toByteArray(), offset);
        }

        @Override
        public byte[] toByteArray() {
            return memory.readBytes(0, this.length);
        }
    }

    private final class SimpleFS implements extensionUtils.EmscriptenFS {
        private final Path root;
        private final Map<String, Path> mounts = new HashMap<>();
        private final Map<Integer, postgresMod.DeviceOps> devices = new HashMap<>();
        private final Map<String, Integer> devicePaths = new HashMap<>();

        private SimpleFS(Path root) {
            this.root = root;
            this.mounts.put("/", root);
        }

        @Override
        public void createPath(String parent, String path, boolean canRead, boolean canWrite) {
            mkdirTree(join(parent, path));
        }

        @Override
        public void createDataFile(String path, String name, Object data, boolean canRead, boolean canWrite, boolean canOwn) {
            writeFile(join(path, name), bytesOf(data));
        }

        @Override
        public void createPreloadedFile(
            String parent,
            String name,
            Object data,
            boolean canRead,
            boolean canWrite,
            extensionUtils.Log onload,
            extensionUtils.Log onerror,
            boolean dontCreateFile
        ) {
            try {
                if (!dontCreateFile) {
                    writeFile(join(parent, name), bytesOf(data));
                }
                if (onload != null) {
                    onload.apply();
                }
            } catch (RuntimeException e) {
                if (onerror != null) {
                    onerror.apply(e);
                } else {
                    throw e;
                }
            }
        }

        @Override
        public extensionUtils.AnalyzePathResult analyzePath(String path) {
            return new extensionUtils.AnalyzePathResult(Files.exists(resolve(path)) || devicePaths.containsKey(normalize(path)));
        }

        @Override
        public void mkdirTree(String path) {
            try {
                Files.createDirectories(resolve(path));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void writeFile(String path, byte[] data) {
            try {
                var target = resolve(path);
                Files.createDirectories(target.getParent());
                Files.write(target, data != null ? data : new byte[0]);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public byte[] readFile(String path) {
            try {
                return Files.readAllBytes(resolve(path));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void unlink(String path) {
            try {
                Files.deleteIfExists(resolve(path));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void createLazyFile(String parent, String name, Object data, boolean canRead, boolean canWrite) {
            createDataFile(parent, name, data, canRead, canWrite, false);
        }

        @Override
        public void createDevice(String parent, String name, Object input, Object output) {
            writeFile(join(parent, name), new byte[0]);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void mount(Object type, Object opts, String mountpoint) {
            if (opts instanceof Map<?, ?> map) {
                var rootOpt = map.get("root");
                var fsOpt = map.get("fs");
                if (rootOpt != null && fsOpt instanceof SimpleFS otherFs) {
                    mounts.put(normalize(mountpoint), otherFs.resolve(String.valueOf(rootOpt)));
                    return;
                }
                if (rootOpt != null) {
                    mounts.put(normalize(mountpoint), Path.of(String.valueOf(rootOpt)).toAbsolutePath().normalize());
                    return;
                }
            }
            mkdirTree(mountpoint);
        }

        @Override
        public void unmount(String mountpoint) {
            mounts.remove(normalize(mountpoint));
        }

        @Override
        public void symlink(String target, String path) {
            try {
                var link = resolve(path);
                Files.createDirectories(link.getParent());
                if (!Files.exists(link)) {
                    Files.createSymbolicLink(link, Path.of(target));
                }
            } catch (UnsupportedOperationException e) {
                writeFile(path, target.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public extensionUtils.FsStat stat(String path) {
            try {
                var target = resolve(path);
                var stat = new extensionUtils.FsStat();
                stat.directory = Files.isDirectory(target);
                stat.size = Files.exists(target) ? Files.size(target) : 0;
                stat.mtimeMs = Files.exists(target) ? Files.getLastModifiedTime(target).toMillis() : 0;
                stat.mode = stat.directory ? 0040000 : 0100000;
                return stat;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String[] readdir(String path) {
            try (var stream = Files.list(resolve(path))) {
                var names = stream.map(p -> p.getFileName().toString()).sorted().toList();
                var out = new ArrayList<String>();
                out.add(".");
                out.add("..");
                out.addAll(names);
                return out.toArray(String[]::new);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void syncfs(boolean populate, extensionUtils.SyncfsCallback done) {
            if (done != null) {
                done.apply(null);
            }
        }

        @Override
        public void registerDevice(int devId, Object ops) {
            if (ops instanceof postgresMod.DeviceOps deviceOps) {
                devices.put(devId, deviceOps);
            }
        }

        @Override
        public int makedev(int major, int minor) {
            return (major << 8) | minor;
        }

        @Override
        public void mkdev(String path, int dev) {
            devicePaths.put(normalize(path), dev);
            writeFile(path, new byte[0]);
        }

        @Override
        public void rmdir(String path) {
            try {
                Files.deleteIfExists(resolve(path));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void chmod(String path, int mode) {}

        @Override
        public void utime(String path, long atime, long mtime) {
            try {
                Files.setLastModifiedTime(resolve(path), java.nio.file.attribute.FileTime.fromMillis(mtime));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Object NODEFS() {
            return extensionUtils.NODEFS_MARKER;
        }

        @Override
        public String __root() {
            return root.toString();
        }

        private postgresMod.DeviceOps deviceFor(String path) {
            var dev = devicePaths.get(normalize(path));
            return dev != null ? devices.get(dev) : null;
        }

        private Path resolve(String path) {
            var normalized = normalize(path);
            var mountPoint = "/";
            for (var candidate : mounts.keySet()) {
                if (normalized.equals(candidate) || normalized.startsWith(candidate.endsWith("/") ? candidate : candidate + "/")) {
                    if (candidate.length() > mountPoint.length()) {
                        mountPoint = candidate;
                    }
                }
            }
            var base = mounts.getOrDefault(mountPoint, root);
            var relative = normalized.substring(mountPoint.length());
            if (relative.startsWith("/")) {
                relative = relative.substring(1);
            }
            return base.resolve(relative).normalize();
        }

        private String normalize(String path) {
            if (path == null || path.isBlank()) {
                return "/";
            }
            var normalized = path.replace('\\', '/');
            if (!normalized.startsWith("/")) {
                normalized = "/" + normalized;
            }
            while (normalized.contains("//")) {
                normalized = normalized.replace("//", "/");
            }
            if (normalized.length() > 1 && normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return normalized;
        }

        private String join(String parent, String name) {
            var p = normalize(parent);
            if (name == null || name.isEmpty()) {
                return p;
            }
            return normalize(p + "/" + name);
        }

        private byte[] bytesOf(Object data) {
            if (data == null) {
                return new byte[0];
            }
            if (data instanceof byte[] bytes) {
                return bytes;
            }
            if (data instanceof Uint8Array array) {
                return array.toByteArray();
            }
            if (data instanceof String text) {
                return text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }
            throw new IllegalArgumentException("Unsupported file data: " + data.getClass());
        }
    }
}
