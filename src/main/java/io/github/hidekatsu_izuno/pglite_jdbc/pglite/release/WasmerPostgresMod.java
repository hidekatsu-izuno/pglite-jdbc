package io.github.hidekatsu_izuno.pglite_jdbc.pglite.release;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.extensionUtils;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.postgresMod;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array;
import io.github.hidekatsu_izuno.pglite_jdbc.wasmer.WasmerLibrary;
import io.github.hidekatsu_izuno.pglite_jdbc.wasmer.WasmerArtifacts;
import io.github.hidekatsu_izuno.pglite_jdbc.wasmer.WasmerNativeLoader;
import io.github.hidekatsu_izuno.pglite_jdbc.wasmer.WasmerTypes;
import io.github.hidekatsu_izuno.pglite_jdbc.wasmer.WasmerTypes.WasmByteVec;
import io.github.hidekatsu_izuno.pglite_jdbc.wasmer.WasmerTypes.WasmExporttypeVec;
import io.github.hidekatsu_izuno.pglite_jdbc.wasmer.WasmerTypes.WasmExternVec;
import io.github.hidekatsu_izuno.pglite_jdbc.wasmer.WasmerTypes.WasmImporttypeVec;
import io.github.hidekatsu_izuno.pglite_jdbc.wasmer.WasmerTypes.WasmLimits;
import io.github.hidekatsu_izuno.pglite_jdbc.wasmer.WasmerTypes.WasmValVec;
import io.github.hidekatsu_izuno.pglite_jdbc.wasmer.WasmerTypes.WasmVal;
import io.github.hidekatsu_izuno.pglite_jdbc.wasmer.WasmerTypes.WasmerNamedExternVec;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WasmerPostgresMod implements WasmProcess, EmscriptenHost.Runtime {
    private static final int WASMER_BACKEND_CRANELIFT = 0;
    private static final int DEFAULT_INITIAL_PAGES = 2048;
    private static final int DEFAULT_MAX_PAGES = 32768;
    private static final int POSTGRES_MAIN_LONGJMP = 100;
    private static final boolean TRACE_HOST_CALLS = Boolean.getBoolean("pglite.trace_host_calls");
    private static final boolean TRACE_ENV_CALLS = Boolean.getBoolean("pglite.trace_env_calls");
    private static final long NATIVE_CALL_TIMEOUT_MS = Long.getLong(
        "pglite.native_call_timeout_ms",
        120_000L
    );
    private static final Object MODULE_CACHE_LOCK = new Object();
    private static final Map<String, byte[]> WASM_BYTES_CACHE = new HashMap<>();
    private static final Map<String, Pointer> COMPILED_MODULE_CACHE = new HashMap<>();
    private static final java.util.Set<String> PROVIDED_DYNAMIC_LIBRARIES = java.util.Set.of(
        "libc++.so", "libc++abi.so", "libc.so", "libdl.so",
        "libwasi-emulated-getpid.so", "libwasi-emulated-mman.so",
        "libwasi-emulated-process-clocks.so", "libwasi-emulated-signal.so"
    );

    private final postgresMod.PartialPostgresMod overrides;
    private final URL moduleUrl;
    private final WasmerLibrary lib;
    private final Map<Integer, postgresMod.ReadWriteCallback> callbacks = new HashMap<>();
    private final Map<Integer, Integer> tableCallbacks = new HashMap<>();
    private final Map<Integer, Integer> semaphores = new HashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Path root;
    private final Path pgRoot;
    private final Path homeRoot;
    private final Path dataRoot;
    private final SimpleFS fs;
    private final ArrayList<Object> keepAlive = new ArrayList<>();
    private final ArrayList<Runnable> nativeResources = new ArrayList<>();
    private final WasmerRuntimeContext context;
    private final boolean ownsContext;

    private Pointer store;
    private Pointer module;
    private Pointer instance;
    private Pointer wasiEnv;
    private Pointer memory;
    private Pointer memoryData;
    private long memoryDataSize;
    private final Map<String, Pointer> exports = new HashMap<>();
    private final Map<String, Pointer> globalExports = new HashMap<>();
    private final Map<String, Pointer> tableExports = new HashMap<>();
    private final Map<String, Pointer> got = new HashMap<>();
    private final Map<String, DynamicSymbol> mainSymbols = new HashMap<>();
    private final Map<String, DynamicLibrary> loadedLibsByName = new HashMap<>();
    private final Map<Integer, DynamicLibrary> loadedLibsByHandle = new HashMap<>();
    private int nextDynamicHandle = 1;
    private int nextDynamicTableSlot;
    private int longjmpFunctionIndex;
    private int dlErrorPtr;

    private final Map<String, Pointer> emscriptenFunctions = new HashMap<>();
    private EmscriptenHost emscripten;
    private Pointer emscriptenStack;
    private int mainMemoryBase;
    private int nextCallback = 1;
    private int socketRead;
    private int socketWrite;
    private int systemFn;
    private int popenFn;
    private int pcloseFn;
    private postgresMod.DeviceOps blobDevice;
    private Integer fdBufferMax;
    private volatile RuntimeException pendingHostException;
    private WasmerEhProvider ehProvider;
    private final AtomicBoolean nativeClosed = new AtomicBoolean();

    public WasmerPostgresMod(postgresMod.PartialPostgresMod overrides, URL moduleUrl) {
        this.overrides = overrides != null ? overrides : new postgresMod.PartialPostgresMod();
        this.moduleUrl = moduleUrl;
        this.lib = WasmerNativeLoader.get();
        if (this.lib == null) {
            throw new IllegalStateException("Wasmer native library is not available", WasmerNativeLoader.loadError());
        }
        try {
            if (this.overrides.__pgliteEhProvider instanceof WasmerEhProvider provider) {
                this.context = provider.context;
                this.ownsContext = false;
                this.ehProvider = provider;
            } else {
                this.context = new WasmerRuntimeContext(this.lib);
                this.ownsContext = true;
            }
            this.store = context.store;
            this.root = resolveRoot(this.overrides);
            this.pgRoot = root.resolve("pglite");
            this.homeRoot = root.resolve("home");
            this.dataRoot = this.overrides.__wasiDataRoot != null
                ? Path.of(this.overrides.__wasiDataRoot).toAbsolutePath().normalize()
                : root.resolve("data");
            bootstrapStaticFiles();
            this.fs = new SimpleFS(root);
            runOnWasmerThread(() -> {
                instantiate();
                return null;
            });
            runHooks();
            runOnWasmerThread(() -> {
                callIfExists("__wasm_call_ctors");
                if (emscripten != null) {
                    _pgl_set_popen_fn(addFunction((command, mode) -> (int) openLocaleList(command, mode), "iii"));
                    _pgl_set_pclose_fn(addFunction((stream, ignored) -> _fclose(stream), "ii"));
                }
                return null;
            });
        } catch (UnsupportedOperationException e) {
            closeNative();
            throw e;
        } catch (Exception e) {
            closeNative();
            if (e.getCause() instanceof UnsupportedOperationException unsupported) {
                throw unsupported;
            }
            throw new RuntimeException(e);
        }
    }

    private <T> T runOnWasmerThread(Callable<T> callable) {
        return context.call(callable);
    }

    private static Path resolveRoot(postgresMod.PartialPostgresMod overrides) throws Exception {
        if (overrides.__wasiRoot != null) {
            var root = Path.of(overrides.__wasiRoot).toAbsolutePath().normalize();
            Files.createDirectories(root);
            return root;
        }
        return Files.createTempDirectory("pglite-wasi-");
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
        Files.createDirectories(dataRoot);
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
        if (Files.exists(target) || moduleUrl == null) {
            return;
        }
        if ("file".equals(moduleUrl.getProtocol())) {
            var source = Path.of(moduleUrl.toURI()).getParent().resolve(name);
            if (Files.exists(source)) {
                copyTree(source, target);
            }
            return;
        }
        if ("jar".equals(moduleUrl.getProtocol())) {
            copyTreeFromJar(name, target);
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

    private void copyTreeFromJar(String name, Path target) throws Exception {
        var connection = (JarURLConnection) moduleUrl.openConnection();
        var rootEntry = connection.getEntryName();
        if (rootEntry == null) {
            return;
        }
        var lastSlash = rootEntry.lastIndexOf('/');
        rootEntry = lastSlash >= 0 ? rootEntry.substring(0, lastSlash + 1) : "";
        var sourceRoot = rootEntry + name + "/";
        try (var jar = connection.getJarFile()) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                var entryName = entry.getName();
                if (!entryName.startsWith(sourceRoot)) {
                    continue;
                }
                var relative = entryName.substring(sourceRoot.length());
                if (relative.isEmpty()) {
                    continue;
                }
                var dest = target.resolve(relative);
                if (entry.isDirectory()) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    try (var in = jar.getInputStream(entry)) {
                        Files.copy(in, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    private static String wasiHostPath(Path path) {
        var absolute = path.toAbsolutePath().normalize();
        if (java.io.File.separatorChar == '\\') {
            // Wasmer 7.4's host filesystem is rooted at the current drive's
            // root and rejects Windows drive prefixes in preopen paths.
            var currentRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath().getRoot();
            if (!absolute.getRoot().equals(currentRoot)) {
                throw new UnsupportedOperationException("Wasmer Windows preopens must use the working directory's drive: "
                    + currentRoot + "; requested " + absolute);
            }
            return "/" + absolute.getRoot().relativize(absolute).toString().replace('\\', '/');
        }
        return absolute.toString();
    }

    private void instantiate() throws Exception {
        var wasmBytes = loadWasmBytes();
        if (!WasmRuntimeFactory.hasExceptionHandling(wasmBytes)) {
            instantiateEmscripten(wasmBytes);
            return;
        }
        longjmpFunctionIndex = WasmLinking.exportedFunctionIndex(wasmBytes, "__wasm_longjmp");
        nextDynamicTableSlot = WasmLinking.tableMinimum(wasmBytes);
        var moduleCacheKey = moduleCacheKey(wasmBytes);
        synchronized (MODULE_CACHE_LOCK) {
            module = COMPILED_MODULE_CACHE.get(moduleCacheKey);
            if (module == null) {
                module = compileModule(wasmBytes);
                COMPILED_MODULE_CACHE.put(moduleCacheKey, module);
            }
        }
        var program = overrides.thisProgram != null ? overrides.thisProgram : "/pglite/bin/postgres";
        var wasiConfig = lib.wasi_config_new(program);
        if (wasiConfig == null) {
            throw wasmerError("wasi_config_new");
        }
        if (overrides.arguments != null) {
            for (var arg : overrides.arguments) {
                lib.wasi_config_arg(wasiConfig, arg);
            }
        }
        // Surefire uses its streams for the fork protocol, so keep guest
        // output captured except while explicitly diagnosing bootstrap.
        if (Boolean.getBoolean("pglite.wasmer_inherit_output")) {
            lib.wasi_config_inherit_stdout(wasiConfig);
            lib.wasi_config_inherit_stderr(wasiConfig);
        } else {
            lib.wasi_config_capture_stdout(wasiConfig);
            lib.wasi_config_capture_stderr(wasiConfig);
        }
        lib.wasi_config_inherit_stdin(wasiConfig);
        if (!lib.wasi_config_mapdir(wasiConfig, "/", wasiHostPath(root))) {
            throw wasmerError("wasi_config_mapdir /");
        }
        if (!lib.wasi_config_mapdir(wasiConfig, "/pglite", wasiHostPath(pgRoot))) {
            throw wasmerError("wasi_config_mapdir /pglite");
        }
        if (!lib.wasi_config_mapdir(wasiConfig, "/home", wasiHostPath(homeRoot))) {
            throw wasmerError("wasi_config_mapdir /home");
        }
        if (!lib.wasi_config_mapdir(wasiConfig, "/data", wasiHostPath(dataRoot))) {
            throw wasmerError("wasi_config_mapdir /data");
        }
        for (var entry : mergedEnv().entrySet()) {
            lib.wasi_config_env(wasiConfig, entry.getKey(), entry.getValue());
        }
        wasiEnv = lib.wasi_env_new(store, wasiConfig);
        if (wasiEnv == null) {
            throw wasmerError("wasi_env_new");
        }

        var importTypes = new WasmImporttypeVec.ByReference();
        lib.wasm_module_imports(module, importTypes);
        var named = new WasmerNamedExternVec.ByReference();
        lib.wasmer_named_extern_vec_new_empty(named);
        if (!lib.wasi_get_unordered_imports(wasiEnv, module, named)) {
            throw wasmerError("wasi_get_unordered_imports");
        }

        var imports = new WasmExternVec.ByReference();
        lib.wasm_extern_vec_new_uninitialized(imports, importTypes.size);
        try {
            for (var i = 0; i < importTypes.size; i++) {
                var importTypePtr = readPointerAt(importTypes.data, i);
                var moduleName = readName(lib.wasm_importtype_module(importTypePtr));
                var importName = readName(lib.wasm_importtype_name(importTypePtr));
                var externtype = lib.wasm_importtype_type(importTypePtr);
                var kind = lib.wasm_externtype_kind(externtype);
                Pointer externPtr;
                if ("wasi_snapshot_preview1".equals(moduleName)) {
                    // Match the JavaScript runtime: keep proc_exit in the
                    // host so a PGlite longjmp exit can be handled by
                    // callMain instead of terminating the Wasmer WASI env.
                    externPtr = "proc_exit".equals(importName)
                        ? createHostFunc(moduleName, importName, externtype)
                        : findNamedExtern(named, moduleName, importName);
                    if (externPtr == null) {
                        throw new IllegalStateException("Missing WASI import: " + moduleName + "." + importName);
                    }
                    if (!"proc_exit".equals(importName) && Boolean.getBoolean("pglite.trace_wasi_calls")) {
                        var owned = lib.wasm_extern_copy(externPtr);
                        nativeResources.add(() -> lib.wasm_extern_delete(owned));
                        externPtr = createHostFunc(moduleName, importName, externtype,
                            lib.wasm_extern_as_func(owned));
                    }
                } else if (kind == WasmerTypes.WASM_EXTERN_FUNC) {
                    externPtr = ehImport(moduleName, importName);
                    if (externPtr == null) {
                        externPtr = createHostFunc(moduleName, importName, externtype);
                    }
                } else if (kind == WasmerTypes.WASM_EXTERN_MEMORY) {
                    externPtr = createMemoryImport(externtype);
                } else if (kind == WasmerTypes.WASM_EXTERN_TAG) {
                    externPtr = ehTagImport(moduleName, importName);
                    if (externPtr == null) {
                        throw new IllegalStateException("Unsupported tag import: " + moduleName + "." + importName);
                    }
                } else {
                    throw new UnsupportedOperationException(
                        "Unsupported import kind " + kind + " for " + moduleName + "." + importName
                    );
                }
                writePointerAt(imports.data, i, lib.wasm_extern_copy(externPtr));
            }

            var trapOut = new Pointer[1];
            instance = lib.wasm_instance_new(store, module, imports, trapOut);
            if (instance == null) {
                throw trapOrWasmerError("wasm_instance_new", trapOut[0]);
            }
        } finally {
            lib.wasm_extern_vec_delete(imports);
        }
        if (!lib.wasi_env_initialize_instance(wasiEnv, store, instance)) {
            throw wasmerError("wasi_env_initialize_instance");
        }

        indexExports();
        if (ehProvider == null) {
            var cLongjmpTag = exports.get("__c_longjmp");
            var wasmLongjmp = exports.get("__wasm_longjmp");
            if (cLongjmpTag == null || wasmLongjmp == null) {
                throw new IllegalStateException("pglite.wasm must export __c_longjmp and __wasm_longjmp");
            }
            ehProvider = new WasmerEhProvider(context, cLongjmpTag, wasmLongjmp);
        }
        memory = exports.get("memory");
        if (memory == null) {
            throw new IllegalStateException("Missing memory export");
        }
        refreshMemoryView();
        callIfExists("__wasm_init_memory");

        lib.wasm_importtype_vec_delete(importTypes);
        lib.wasmer_named_extern_vec_delete(named);
    }

    private void instantiateEmscripten(byte[] bytes) {
        emscripten = new EmscriptenHost(this, fs::resolve, mergedEnv());
        emscriptenFunctions.putAll(instantiateHostAdapter("host/emscripten.wasm", null));
        var metadata = DylinkMetadata.parse(bytes);
        mainMemoryBase = 1024;
        var stackTop = (int) align(mainMemoryBase + metadata.memorySize + 8388608L, 16);
        emscriptenStack = newI32Global(stackTop, WasmerTypes.WASM_VAR);
        nextDynamicTableSlot = metadata.tableSize + 1;
        var key = moduleCacheKey(bytes);
        synchronized (MODULE_CACHE_LOCK) {
            module = COMPILED_MODULE_CACHE.get(key);
            if (module == null) {
                module = compileModule(bytes);
                COMPILED_MODULE_CACHE.put(key, module);
            }
        }
        var types = new WasmImporttypeVec.ByReference();
        lib.wasm_module_imports(module, types);
        var imports = new WasmExternVec.ByReference();
        lib.wasm_extern_vec_new_uninitialized(imports, types.size);
        try {
            for (var i = 0; i < types.size; i++) {
                var type = readPointerAt(types.data, i);
                var namespace = readName(lib.wasm_importtype_module(type));
                var name = readName(lib.wasm_importtype_name(type));
                var externalType = lib.wasm_importtype_type(type);
                var value = switch (lib.wasm_externtype_kind(externalType)) {
                    case WasmerTypes.WASM_EXTERN_FUNC -> lib.wasm_func_as_extern(emscriptenFunctions.get(namespace + "." + name));
                    case WasmerTypes.WASM_EXTERN_MEMORY -> createMemoryImport(externalType);
                    case WasmerTypes.WASM_EXTERN_TABLE -> {
                        var limits = new WasmLimits.ByReference();
                        limits.min = nextDynamicTableSlot;
                        limits.max = -1;
                        limits.write();
                        var tableType = lib.wasm_tabletype_new(lib.wasm_valtype_new(129), limits);
                        var table = lib.wasm_table_new(store, tableType, null);
                        lib.wasm_tabletype_delete(tableType);
                        if (table == null) throw wasmerError("wasm_table_new min=" + limits.min + " type=" + tableType);
                        nativeResources.add(() -> lib.wasm_table_delete(table));
                        tableExports.put("__indirect_function_table", table);
                        yield lib.wasm_table_as_extern(table);
                    }
                    case WasmerTypes.WASM_EXTERN_GLOBAL -> lib.wasm_global_as_extern(switch (name) {
                        case "__memory_base" -> newI32Global(mainMemoryBase, WasmerTypes.WASM_CONST);
                        case "__table_base" -> newI32Global(1, WasmerTypes.WASM_CONST);
                        case "__stack_pointer" -> emscriptenStack;
                        case "__heap_base" -> newI32Global(stackTop, WasmerTypes.WASM_VAR);
                        default -> throw new UnsupportedOperationException("Unknown main global: " + namespace + "." + name);
                    });
                    default -> throw new UnsupportedOperationException("Unknown Emscripten import: " + name);
                };
                writePointerAt(imports.data, i, lib.wasm_extern_copy(value));
            }
            var trap = new Pointer[1];
            instance = lib.wasm_instance_new(store, module, imports, trap);
            if (instance == null) throw trapOrWasmerError("wasm_instance_new", trap[0]);
        } finally {
            lib.wasm_extern_vec_delete(imports);
            lib.wasm_importtype_vec_delete(types);
        }
        indexExports();
        for (var entry : WasmLinking.exportedFunctionTableIndexes(bytes, 1).entrySet()) {
            var symbol = mainSymbols.get(entry.getKey());
            if (symbol != null) mainSymbols.put(entry.getKey(), DynamicSymbol.function(symbol.function, entry.getValue()));
        }
        for (var entry : new HashMap<>(globalExports).entrySet()) {
            var value = i32Value(0);
            lib.wasm_global_get(entry.getValue(), value);
            value.read(); value.of.setType(int.class); value.of.read();
            var relocated = newI32Global(value.of.i32 + mainMemoryBase, WasmerTypes.WASM_CONST);
            mainSymbols.put(entry.getKey(), DynamicSymbol.global(relocated));
        }
        refreshMemoryView();
        callIfExists("__wasm_apply_data_relocs");
    }

    public long invokeEmscripten(String signature, long[] args) {
        var savedStack = call("emscripten_stack_get_current");
        var reference = lib.wasm_table_get(dynamicTable(), (int) args[0]);
        if (reference == null) throw new IllegalStateException("Null indirect function " + args[0]);
        var function = lib.wasm_ref_as_func(reference);
        try {
            return callFunc(function, java.util.Arrays.copyOfRange(args, 1, args.length));
        } catch (EmscriptenHost.Longjmp jump) {
            call("_emscripten_stack_restore", savedStack);
            call("setThrew", 1, 0);
            return 0;
        } finally {
            lib.wasm_func_delete(function);
            lib.wasm_ref_delete(reference);
        }
    }

    public long emTableCall(int index, long... args) {
        var reference = lib.wasm_table_get(dynamicTable(), index);
        if (reference == null) throw new IllegalStateException("Null indirect function " + index);
        var function = lib.wasm_ref_as_func(reference);
        try { return callFunc(function, args); }
        finally { lib.wasm_func_delete(function); lib.wasm_ref_delete(reference); }
    }

    public long emCall(String name, long... args) { return call(name, args); }
    public byte[] emRead(int pointer, int length) { return readBytes(pointer, length); }
    public void emWrite(int pointer, byte[] bytes) { writeBytes(pointer, bytes); }
    public String emString(int pointer) { return readCString(pointer); }
    public EmscriptenHost.Heap emMemory() { return emHeap; }
    private final EmscriptenHost.Heap emHeap = new EmscriptenHost.Heap() {
        private Pointer pointer() { refreshMemoryView(); return memoryData; }
        public int getInt(long p) { return pointer().getInt(p); }
        public long getLong(long p) { return pointer().getLong(p); }
        public short getShort(long p) { return pointer().getShort(p); }
        public void setInt(long p, int v) { pointer().setInt(p, v); }
        public void setLong(long p, long v) { pointer().setLong(p, v); }
        public void setShort(long p, short v) { pointer().setShort(p, v); }
        public void setByte(long p, byte v) { pointer().setByte(p, v); }
        public void setMemory(long p, long n, byte v) { pointer().setMemory(p, n, v); }
    };
    public int emGrow(int size) { ensureMemory(size); return 1; }
    public void emExit(int status) { throw new ExitStatus(status); }
    public postgresMod.DeviceOps emDevice(String path) {
        var id = fs.devicePaths.get(path);
        return id == null ? null : fs.devices.get(id);
    }

    private Pointer createMemoryImport(Pointer externtype) {
        var memorytype = lib.wasm_externtype_as_memorytype_const(externtype);
        var limitsPtr = lib.wasm_memorytype_limits(memorytype);
        var limits = new WasmLimits(limitsPtr);
        limits.read();
        var initial = overrides.INITIAL_MEMORY != null
            ? Math.max(1, (overrides.INITIAL_MEMORY + 65535) / 65536)
            : DEFAULT_INITIAL_PAGES;
        var min = Math.max(limits.min, initial);
        var max = limits.max == 0 ? DEFAULT_MAX_PAGES : limits.max;
        var newLimits = new WasmLimits.ByReference();
        newLimits.min = min;
        newLimits.max = max;
        newLimits.write();
        var mt = lib.wasm_memorytype_new(newLimits);
        memory = lib.wasm_memory_new(store, mt);
        lib.wasm_memorytype_delete(mt);
        if (memory == null) {
            throw wasmerError("wasm_memory_new");
        }
        var ownedMemory = memory;
        nativeResources.add(() -> lib.wasm_memory_delete(ownedMemory));
        return lib.wasm_memory_as_extern(memory);
    }

    private Pointer ehImport(String moduleName, String importName) {
        if (!"env".equals(moduleName) || !("__wasm_longjmp".equals(importName) || "emscripten_longjmp".equals(importName))) {
            return null;
        }
        var provider = requiredEhProvider();
        return lib.wasm_func_as_extern(provider.wasmLongjmp);
    }

    private Pointer ehTagImport(String moduleName, String importName) {
        if (!"env".equals(moduleName) || !"__c_longjmp".equals(importName)) {
            return null;
        }
        var provider = requiredEhProvider();
        // Wasmer's C API has no wasm_tag_as_extern entry point.  The value
        // returned by wasm_instance_exports is already a wasm_extern_t and
        // can be reused directly as an import in the same store.
        return provider.cLongjmpTag;
    }

    private WasmerEhProvider requiredEhProvider() {
        var provider = ehProvider;
        if (provider == null) {
            throw new IllegalStateException("pglite.wasm EH provider is required");
        }
        if (provider.context != context) {
            throw new IllegalStateException("pglite.wasm EH provider must use the same Wasmer store");
        }
        return provider;
    }

    private Pointer createHostFunc(String moduleName, String importName, Pointer externtype) {
        return createHostFunc(moduleName, importName, externtype, null);
    }

    private Pointer createHostFunc(String moduleName, String importName, Pointer externtype, Pointer delegate) {
        var functype = lib.wasm_externtype_as_functype_const(externtype);
        var functypeCopy = lib.wasm_functype_copy(functype);
        var paramKinds = valKinds(lib.wasm_functype_params(functypeCopy));
        var resultKinds = valKinds(lib.wasm_functype_results(functypeCopy));
        WasmerLibrary.WasmFuncCallbackWithEnv callback = (env, args, results) -> {
            try {
                var argValues = readArgs(args, paramKinds);
                if (delegate != null) {
                    System.err.println("[wasmer-wasi] " + importName + " " + java.util.Arrays.toString(argValues));
                    var trap = lib.wasm_func_call(delegate, args, results);
                    System.err.println("[wasmer-wasi] " + importName + " returned");
                    return trap;
                }
                var result = hostCall(moduleName, importName, argValues, resultKinds);
                writeResults(results, resultKinds, result);
                return null;
            } catch (RuntimeException e) {
                pendingHostException = e;
                var message = new WasmByteVec.ByReference();
                var bytes = (e.toString() + "\0").getBytes(StandardCharsets.UTF_8);
                lib.wasm_byte_vec_new(message, bytes.length, bytes);
                try {
                    return lib.wasm_trap_new(store, message);
                } finally {
                    lib.wasm_byte_vec_delete(message);
                }
            }
        };
        keepAlive.add(callback);
        var env = new Memory(8);
        env.setLong(0, keepAlive.size());
        keepAlive.add(env);
        var func = lib.wasm_func_new_with_env(store, functypeCopy, callback, env, null);
        lib.wasm_functype_delete(functypeCopy);
        if (func == null) {
            throw wasmerError("wasm_func_new_with_env " + moduleName + "." + importName);
        }
        nativeResources.add(() -> lib.wasm_func_delete(func));
        return lib.wasm_func_as_extern(func);
    }

    private byte[] valKinds(Pointer valtypeVecPtr) {
        if (valtypeVecPtr == null) {
            return new byte[0];
        }
        var size = valtypeVecPtr.getLong(0);
        var data = valtypeVecPtr.getPointer(NativePointerSize.SIZE);
        var kinds = new byte[(int) size];
        for (var i = 0; i < size; i++) {
            var valtype = readPointerAt(data, i);
            kinds[i] = lib.wasm_valtype_kind(valtype);
        }
        return kinds;
    }

    private long[] readArgs(WasmValVec.ByReference args, byte[] paramKinds) {
        var values = new long[paramKinds.length];
        if (args == null || args.size == 0 || args.data == null) {
            return values;
        }
        for (var i = 0; i < paramKinds.length; i++) {
            var offset = i * 16L;
            values[i] = switch (paramKinds[i]) {
                case WasmerTypes.WASM_I32 -> args.data.getInt(offset + 8);
                case WasmerTypes.WASM_I64 -> args.data.getLong(offset + 8);
                case WasmerTypes.WASM_F32 -> Float.floatToIntBits(args.data.getFloat(offset + 8));
                case WasmerTypes.WASM_F64 -> Double.doubleToLongBits(args.data.getDouble(offset + 8));
                default -> 0L;
            };
        }
        return values;
    }

    private void writeResults(WasmValVec.ByReference results, byte[] resultKinds, long result) {
        if (results == null || resultKinds.length == 0 || results.data == null || results.size == 0) {
            return;
        }
        results.data.setByte(0, resultKinds[0]);
        switch (resultKinds[0]) {
            case WasmerTypes.WASM_I32 -> results.data.setInt(8, (int) result);
            case WasmerTypes.WASM_I64 -> results.data.setLong(8, result);
            case WasmerTypes.WASM_F32 -> results.data.setFloat(8, Float.intBitsToFloat((int) result));
            case WasmerTypes.WASM_F64 -> results.data.setDouble(8, Double.longBitsToDouble(result));
            default -> results.data.setLong(8, 0L);
        }
    }

    private Pointer findNamedExtern(WasmerNamedExternVec.ByReference named, String moduleName, String importName) {
        for (var i = 0; i < named.size; i++) {
            var namedExtern = readPointerAt(named.data, i);
            var name = readName(lib.wasmer_named_extern_name(namedExtern));
            if (importName.equals(name)) {
                return lib.wasmer_named_extern_unwrap(namedExtern);
            }
        }
        return null;
    }

    private void indexExports() {
        var exportTypes = new WasmExporttypeVec.ByReference();
        lib.wasm_module_exports(module, exportTypes);
        var exportExterns = new WasmExternVec.ByReference();
        lib.wasm_instance_exports(instance, exportExterns);
        for (var i = 0; i < exportTypes.size; i++) {
            var exportType = readPointerAt(exportTypes.data, i);
            var name = readName(lib.wasm_exporttype_name(exportType));
            var externPtr = readPointerAt(exportExterns.data, i);
            var kind = lib.wasm_externtype_kind(lib.wasm_exporttype_type(exportType));
            // Wasmer's Wasm C API exposes a tag as an opaque wasm_extern_t,
            // but does not expose the tag conversion functions from the newer
            // exception-handling C API. Keep the raw extern so it can be
            // reused when extension modules import the shared longjmp tag.
            if ("__c_longjmp".equals(name)) {
                exports.put(name, externPtr);
            } else if (kind == WasmerTypes.WASM_EXTERN_MEMORY) {
                exports.put(name, lib.wasm_extern_as_memory(externPtr));
            } else if (kind == WasmerTypes.WASM_EXTERN_FUNC) {
                var func = lib.wasm_extern_as_func(externPtr);
                if (func != null) {
                    exports.put(name, func);
                    mainSymbols.put(name, DynamicSymbol.function(func, 0));
                }
            } else if (kind == WasmerTypes.WASM_EXTERN_GLOBAL) {
                var global = lib.wasm_extern_as_global(externPtr);
                globalExports.put(name, global);
                mainSymbols.put(name, DynamicSymbol.global(global));
            } else if (kind == WasmerTypes.WASM_EXTERN_TABLE) {
                tableExports.put(name, lib.wasm_extern_as_table(externPtr));
            } else if (kind == WasmerTypes.WASM_EXTERN_TAG) {
                exports.put(name, externPtr);
            }
        }
        lib.wasm_exporttype_vec_delete(exportTypes);
        // The vector owns the exported C handles; casts from them are borrowed.
        nativeResources.add(() -> lib.wasm_extern_vec_delete(exportExterns));
    }

    public long emDlopen(int handle) {
        var id = (int) dlopen(handle + 36, emMemory().getInt(handle + 4));
        if (id == 0) {
            if (dlErrorPtr != 0) call("__dl_seterr", dlErrorPtr);
            return 0;
        }
        loadedLibsByHandle.put(handle, loadedLibsByHandle.remove(id));
        return 1;
    }

    public long emDlsym(int handle, int symbol) { return dlsym(handle, symbol); }

    private long dlopen(int filePtr, int mode) {
        try {
            var fileName = normalizeDynamicLibraryName(readCString(filePtr));
            var existing = loadedLibsByName.get(fileName);
            if (existing == null) {
                var wasmFileName = dynamicLibraryFileName(fileName);
                existing = instantiateDynamicLibrary(wasmFileName, Files.readAllBytes(fs.resolve(wasmFileName)));
                loadedLibsByName.put(fileName, existing);
                loadedLibsByName.put(wasmFileName, existing);
            }
            var handle = nextDynamicHandle++;
            loadedLibsByHandle.put(handle, existing);
            clearDlError();
            return handle;
        } catch (Throwable e) {
            setDlError(e.getMessage() != null ? e.getMessage() : e.toString());
            return 0L;
        }
    }

    private long dlsym(int handle, int symbolPtr) {
        var library = loadedLibsByHandle.get(handle);
        var symbol = readCString(symbolPtr);
        if (library == null) {
            setDlError("unknown dynamic library handle: " + handle);
            return 0L;
        }
        var resolved = library.symbols.get(symbol);
        if (resolved == null || resolved.tableIndex == null) {
            if (TRACE_ENV_CALLS) {
                System.err.println("[wasmer-env] dlsym miss " + symbol);
            }
            setDlError("dynamic library symbol not found: " + symbol);
            return 0L;
        }
        clearDlError();
        if (TRACE_ENV_CALLS) {
            System.err.println("[wasmer-env] dlsym " + symbol + " -> " + resolved.tableIndex);
        }
        return resolved.tableIndex;
    }

    private long dlclose(int handle) {
        loadedLibsByHandle.remove(handle);
        clearDlError();
        return 0L;
    }

    private void setDlError(String text) {
        clearDlError();
        dlErrorPtr = writeCString(text == null ? "" : text);
    }

    private void clearDlError() {
        if (dlErrorPtr != 0) {
            callIfExists("free", dlErrorPtr);
            dlErrorPtr = 0;
        }
    }

    private String normalizeDynamicLibraryName(String name) {
        return Path.of(name).normalize().toString().replace('\\', '/');
    }

    private String dynamicLibraryFileName(String name) {
        if (fs.analyzePath(name).exists()) {
            return name;
        }
        if (name.endsWith(".so") && fs.analyzePath(name + ".wasm").exists()) {
            return name + ".wasm";
        }
        return name;
    }

    private DynamicLibrary instantiateDynamicLibrary(String name, byte[] bytes) throws Exception {
        var metadata = DylinkMetadata.parse(bytes);
        for (var needed : metadata.neededDynlibs) {
            if (!loadedLibsByName.containsKey(needed) && !PROVIDED_DYNAMIC_LIBRARIES.contains(needed)) {
                var neededFile = dynamicLibraryFileName(needed);
                var dependency = instantiateDynamicLibrary(neededFile, Files.readAllBytes(fs.resolve(neededFile)));
                loadedLibsByName.put(needed, dependency);
                loadedLibsByName.put(neededFile, dependency);
            }
        }
        var memoryBase = metadata.memorySize == 0 ? 0 : align(call("malloc", metadata.memorySize + (1 << metadata.memoryAlign) + 1), 1 << metadata.memoryAlign);
        if (metadata.memorySize != 0) {
            ensureMemory((int) memoryBase + metadata.memorySize);
            memoryData.setMemory(memoryBase, metadata.memorySize, (byte) 0);
        }
        var tableBase = metadata.tableSize == 0 ? 0 : nextDynamicTableSlot;
        nextDynamicTableSlot += metadata.tableSize;
        var table = dynamicTable();
        var tableSize = lib.wasm_table_size(table);
        if (nextDynamicTableSlot > tableSize && !lib.wasm_table_grow(table, nextDynamicTableSlot - tableSize, null)) {
            throw wasmerError("wasm_table_grow");
        }
        var stack = emscriptenStack;
        if (emscripten == null) {
            var stackBase = align(call("malloc", 64 * 1024 + 16), 16);
            ensureMemory((int) stackBase + 64 * 1024);
            stack = newI32Global((int) stackBase + 64 * 1024, WasmerTypes.WASM_VAR);
        }

        var dynamicModule = compileDynamicModule(bytes);
        nativeResources.add(() -> lib.wasm_module_delete(dynamicModule));
        var imports = dynamicImports(dynamicModule, memoryBase, tableBase, stack);
        var trapOut = new Pointer[1];
        var dynamicInstance = lib.wasm_instance_new(store, dynamicModule, imports, trapOut);
        lib.wasm_extern_vec_delete(imports);
        if (dynamicInstance == null) {
            throw trapOrWasmerError("wasm_instance_new " + name, trapOut[0]);
        }
        nativeResources.add(() -> lib.wasm_instance_delete(dynamicInstance));
        var library = new DynamicLibrary(dynamicModule, dynamicInstance, memoryBase);
        var symbolTableIndexes = new HashMap<String, Integer>();
        symbolTableIndexes.putAll(WasmLinking.exportedFunctionTableIndexes(bytes, tableBase));
        library.indexExports(symbolTableIndexes);
        mergeSymbols(library.symbols);
        resolveGot(library);
        callDynamicIfExists(library, "__wasm_apply_data_relocs");
        callDynamicIfExists(library, "_initialize");
        callDynamicIfExists(library, "__wasm_call_ctors");
        return library;
    }

    private Pointer compileDynamicModule(byte[] bytes) {
        return compileModule(bytes);
    }

    private Pointer compileModule(byte[] wasm) {
        var headless = lib.wasmer_is_headless();
        var bytes = headless ? WasmerArtifacts.read(wasm, lib.wasmer_version()) : wasm;
        var vector = new WasmByteVec.ByReference();
        lib.wasm_byte_vec_new(vector, bytes.length, bytes);
        try {
            var compiled = headless ? lib.wasm_module_deserialize(store, vector)
                : lib.wasm_module_new(store, vector);
            if (compiled == null) throw wasmerError(headless ? "wasm_module_deserialize" : "wasm_module_new");
            return compiled;
        } finally {
            lib.wasm_byte_vec_delete(vector);
        }
    }

    private WasmExternVec.ByReference dynamicImports(Pointer dynamicModule, long memoryBase, int tableBase, Pointer stack) {
        var types = new WasmImporttypeVec.ByReference();
        lib.wasm_module_imports(dynamicModule, types);
        var imports = new WasmExternVec.ByReference();
        lib.wasm_extern_vec_new_uninitialized(imports, types.size);
        for (var i = 0; i < types.size; i++) {
            var type = readPointerAt(types.data, i);
            var moduleName = readName(lib.wasm_importtype_module(type));
            var name = readName(lib.wasm_importtype_name(type));
            var externType = lib.wasm_importtype_type(type);
            var kind = lib.wasm_externtype_kind(externType);
            var value = switch (kind) {
                case WasmerTypes.WASM_EXTERN_FUNC -> dynamicFunctionImport(moduleName, name, externType);
                case WasmerTypes.WASM_EXTERN_MEMORY -> lib.wasm_memory_as_extern(memory);
                case WasmerTypes.WASM_EXTERN_TABLE -> lib.wasm_table_as_extern(dynamicTable());
                case WasmerTypes.WASM_EXTERN_GLOBAL -> lib.wasm_global_as_extern(dynamicGlobal(name, externType, memoryBase, tableBase, stack));
                case WasmerTypes.WASM_EXTERN_TAG -> dynamicTagImport(moduleName, name);
                default -> throw new IllegalStateException("unsupported dynamic import " + moduleName + "." + name);
            };
            if (value == null) throw new IllegalStateException("unresolved dynamic import " + moduleName + "." + name);
            writePointerAt(imports.data, i, lib.wasm_extern_copy(value));
        }
        lib.wasm_importtype_vec_delete(types);
        return imports;
    }

    private Pointer dynamicFunctionImport(String moduleName, String name, Pointer type) {
        var symbol = resolveSymbol(name);
        if (symbol != null && symbol.function != null) return lib.wasm_func_as_extern(symbol.function);
        if (emscripten != null) {
            var function = emscriptenFunctions.get(moduleName + "." + name);
            if (function == null) throw new IllegalStateException("Unresolved Emscripten symbol: " + name);
            return lib.wasm_func_as_extern(function);
        }
        var eh = ehImport(moduleName, name);
        return eh != null ? eh : createHostFunc(moduleName, name, type);
    }

    private Pointer dynamicTagImport(String moduleName, String name) {
        return ehTagImport(moduleName, name);
    }

    private Pointer dynamicGlobal(String name, Pointer externtype, long memoryBase, int tableBase, Pointer stack) {
        return switch (name) {
            case "__memory_base" -> newI32Global((int) memoryBase, WasmerTypes.WASM_CONST);
            case "__table_base" -> newI32Global(tableBase, WasmerTypes.WASM_CONST);
            case "__stack_pointer" -> stack;
            default -> got.computeIfAbsent(name, ignored -> globalForImport(externtype));
        };
    }

    private Pointer globalForImport(Pointer externtype) {
        var type = lib.wasm_externtype_as_globaltype_const(externtype);
        var content = lib.wasm_globaltype_content(type);
        var kind = lib.wasm_valtype_kind(content);
        var mutability = lib.wasm_globaltype_mutability(type);
        var valueType = lib.wasm_valtype_new(kind);
        var copy = lib.wasm_globaltype_new(valueType, mutability);
        try {
            var value = new WasmVal.ByReference();
            value.kind = kind;
            value.of = new WasmerTypes.WasmValOf();
            value.of.setType(kind == WasmerTypes.WASM_I64 ? long.class : int.class);
            if (kind == WasmerTypes.WASM_I64) value.of.i64 = 0; else value.of.i32 = 0;
            value.of.write(); value.write();
            var global = lib.wasm_global_new(store, copy, value);
            nativeResources.add(() -> lib.wasm_global_delete(global));
            return global;
        } finally { lib.wasm_globaltype_delete(copy); }
    }

    private Pointer newI32Global(int value, byte mutability) {
        var valtype = lib.wasm_valtype_new(WasmerTypes.WASM_I32);
        var type = lib.wasm_globaltype_new(valtype, mutability);
        try {
            var global = lib.wasm_global_new(store, type, i32Value(value));
            nativeResources.add(() -> lib.wasm_global_delete(global));
            return global;
        }
        finally { lib.wasm_globaltype_delete(type); }
    }

    private WasmVal.ByReference i32Value(int value) {
        var result = new WasmVal.ByReference(); result.kind = WasmerTypes.WASM_I32;
        result.of = new WasmerTypes.WasmValOf(); result.of.setType(int.class); result.of.i32 = value;
        result.of.write(); result.write(); return result;
    }

    private void setGlobalI32(Pointer global, int value) { lib.wasm_global_set(global, i32Value(value)); }

    private Pointer dynamicTable() {
        var table = tableExports.get("__indirect_function_table");
        if (table == null && !tableExports.isEmpty()) table = tableExports.values().iterator().next();
        if (table == null) throw new IllegalStateException("pglite.wasm does not export its indirect function table");
        return table;
    }

    private int registerTableFunction(Pointer function) {
        var table = dynamicTable();
        var index = nextDynamicTableSlot++;
        var size = lib.wasm_table_size(table);
        if (index >= size && !lib.wasm_table_grow(table, index - size + 1, null)) {
            throw wasmerError("wasm_table_grow");
        }
        var reference = lib.wasm_func_as_ref(function);
        try {
            if (!lib.wasm_table_set(table, index, reference)) throw wasmerError("wasm_table_set");
        } finally {
            lib.wasm_ref_delete(reference);
        }
        return index;
    }

    private DynamicSymbol resolveSymbol(String name) {
        var result = mainSymbols.get(name);
        if (result != null) return result;
        for (var library : loadedLibsByName.values()) {
            result = library.symbols.get(name);
            if (result != null) return result;
        }
        return null;
    }

    private void mergeSymbols(Map<String, DynamicSymbol> symbols) {
        for (var entry : symbols.entrySet()) {
            if (entry.getValue().global != null) {
                var target = got.get(entry.getKey());
                if (target != null) copyGlobal(entry.getValue().global, target);
            }
        }
    }

    private void resolveGot(DynamicLibrary library) {
        for (var entry : got.entrySet()) {
            var symbol = resolveSymbol(entry.getKey());
            if (symbol == null) symbol = library.symbols.get(entry.getKey());
            if (symbol != null && symbol.global != null) copyGlobal(symbol.global, entry.getValue());
            else if (symbol != null && symbol.function != null) {
                var index = symbol.tableIndex != null && symbol.tableIndex != 0
                    ? symbol.tableIndex : registerTableFunction(symbol.function);
                if (mainSymbols.get(entry.getKey()) == symbol) {
                    mainSymbols.put(entry.getKey(), DynamicSymbol.function(symbol.function, index));
                }
                setGlobalI32(entry.getValue(), index);
            }
        }
    }

    private void copyGlobal(Pointer source, Pointer target) {
        var value = i32Value(0);
        lib.wasm_global_get(source, value);
        value.read();
        value.of.setType(int.class);
        value.of.read();
        setGlobalI32(target, value.of.i32);
    }

    private void callDynamicIfExists(DynamicLibrary library, String name) {
        var function = library.functions.get(name);
        if (function != null) callFunc(function);
    }

    private long align(long value, long alignment) { return alignment <= 1 ? value : (value + alignment - 1) & -alignment; }

    private static final class DynamicSymbol {
        private final Pointer function;
        private final Pointer global;
        private final Integer tableIndex;
        private DynamicSymbol(Pointer function, Pointer global, Integer tableIndex) { this.function = function; this.global = global; this.tableIndex = tableIndex; }
        static DynamicSymbol function(Pointer function, int tableIndex) { return new DynamicSymbol(function, null, tableIndex); }
        static DynamicSymbol global(Pointer global) { return new DynamicSymbol(null, global, null); }
    }

    private final class DynamicLibrary {
        private final Pointer module;
        private final Pointer instance;
        private final long memoryBase;
        private final Map<String, Pointer> functions = new HashMap<>();
        private final Map<String, DynamicSymbol> symbols = new HashMap<>();
        private DynamicLibrary(Pointer module, Pointer instance, long memoryBase) {
            this.module = module; this.instance = instance; this.memoryBase = memoryBase;
        }
        private void indexExports(Map<String, Integer> tableIndexes) {
            var types = new WasmExporttypeVec.ByReference(); lib.wasm_module_exports(module, types);
            var values = new WasmExternVec.ByReference(); lib.wasm_instance_exports(instance, values);
            for (var i = 0; i < types.size; i++) {
                var type = readPointerAt(types.data, i);
                var name = readName(lib.wasm_exporttype_name(type));
                var value = readPointerAt(values.data, i);
                var kind = lib.wasm_externtype_kind(lib.wasm_exporttype_type(type));
                if (kind == WasmerTypes.WASM_EXTERN_FUNC) {
                    var function = lib.wasm_extern_as_func(value);
                    functions.put(name, function);
                    var tableIndex = tableIndexes.get(name);
                    if (tableIndex == null) {
                        tableIndex = registerTableFunction(function);
                    }
                    symbols.put(name, DynamicSymbol.function(function, tableIndex));
                } else if (kind == WasmerTypes.WASM_EXTERN_GLOBAL) {
                    var global = lib.wasm_extern_as_global(value);
                    if (emscripten != null) {
                        var offset = i32Value(0);
                        lib.wasm_global_get(global, offset);
                        offset.read(); offset.of.setType(int.class); offset.of.read();
                        global = newI32Global(offset.of.i32 + (int) memoryBase, WasmerTypes.WASM_CONST);
                    }
                    symbols.put(name, DynamicSymbol.global(global));
                }
            }
            lib.wasm_exporttype_vec_delete(types);
            nativeResources.add(() -> lib.wasm_extern_vec_delete(values));
        }
    }


    private record DylinkMetadata(int memorySize, int memoryAlign, int tableSize, int tableAlign, java.util.List<String> neededDynlibs) {
        static DylinkMetadata parse(byte[] wasm) {
            var section = WasmLinking.customSection(wasm, "dylink.0");
            if (section == null) section = WasmLinking.customSection(wasm, "dylink");
            if (section == null) return new DylinkMetadata(0, 0, 0, 0, java.util.List.of());
            var r = new WasmLinking.Reader(section); var memorySize = 0; var memoryAlign = 0; var tableSize = 0; var tableAlign = 0;
            var needed = new ArrayList<String>();
            if (section.length > 1 && (section[0] & 0xff) <= 4 && (section[0] & 0xff) > 0) {
                while (!r.done()) { var type = r.u32(); var length = r.u32(); var end = Math.min(section.length, r.position + length);
                    if (type == 1) { memorySize = r.u32(); memoryAlign = r.u32(); tableSize = r.u32(); tableAlign = r.u32(); }
                    else if (type == 2) for (var i = r.u32(); i > 0; i--) needed.add(r.string()); r.position = end;
                }
            } else { memorySize = r.u32(); memoryAlign = r.u32(); tableSize = r.u32(); tableAlign = r.u32(); for (var i = r.done() ? 0 : r.u32(); i > 0; i--) needed.add(r.string()); }
            return new DylinkMetadata(memorySize, memoryAlign, tableSize, tableAlign, needed);
        }
    }

    /** Minimal binary reader for the dylink and element sections emitted by wasm-ld. */
    private static final class WasmLinking {

        static Map<String, Integer> exportedFunctionTableIndexes(byte[] wasm, int tableBase) {
            var functionIndexes = functionTableIndexes(wasm, tableBase);
            var result = new HashMap<String, Integer>();
            for (var entry : exportedFunctions(wasm).entrySet()) {
                var tableIndex = functionIndexes.get(entry.getValue());
                if (tableIndex != null) result.put(entry.getKey(), tableIndex);
            }
            return result;
        }

        private static Map<String, Integer> exportedFunctions(byte[] wasm) {
            var section = section(wasm, 7);
            if (section == null) return Map.of();
            var result = new java.util.LinkedHashMap<String, Integer>();
            var reader = new Reader(section);
            for (var n = reader.u32(); n > 0; n--) {
                var name = reader.string(); var kind = reader.byte_(); var index = reader.u32();
                if (kind == 0) result.put(name, index);
            }
            return result;
        }

        static byte[] customSection(byte[] wasm, String name) {
            var reader = new Reader(wasm);
            reader.position = 8;
            while (!reader.done()) {
                var id = reader.byte_();
                var length = reader.u32();
                var end = Math.addExact(reader.position, length);
                if (end > wasm.length) throw new IllegalArgumentException("Truncated WASM section");
                if (id == 0 && name.equals(reader.string())) {
                    return java.util.Arrays.copyOfRange(wasm, reader.position, end);
                }
                reader.position = end;
            }
            return null;
        }

        static int tableMinimum(byte[] wasm) {
            var payload = section(wasm, 4);
            if (payload == null) throw new IllegalStateException("pglite.wasm has no function table");
            var r = new Reader(payload);
            r.u32(); // Table count
            r.byte_(); // Element type
            r.u32(); // Limits flags
            return r.u32(); // Minimum table size
        }

        static int exportedFunctionIndex(byte[] wasm, String wanted) {
            var section = section(wasm, 7); if (section == null) return -1; var r = new Reader(section);
            for (var n = r.u32(); n > 0; n--) { var name = r.string(); var kind = r.byte_(); var index = r.u32(); if (kind == 0 && wanted.equals(name)) return index; }
            return -1;
        }
        static Map<Integer, Integer> functionTableIndexes(byte[] wasm, int tableBase) {
            var section = section(wasm, 9); var result = new HashMap<Integer, Integer>(); if (section == null) return result; var r = new Reader(section);
            for (var n = r.u32(); n > 0 && !r.done(); n--) { var flags = r.u32(); var active = flags == 0 || flags == 2;
                var offset = 0; if (active) { if (flags == 2) r.u32(); offset = r.initExprOffset(); }
                if (flags == 1 || flags == 2 || flags == 3) r.byte_();
                if (flags <= 3) { var count = r.u32(); for (var i = 0; i < count; i++) result.put(r.u32(), tableBase + offset + i); }
                else { var count = r.u32(); for (var i = 0; i < count; i++) r.skipExpr(); }
            } return result;
        }
        static byte[] section(byte[] wasm, int wanted) { var r = new Reader(wasm); r.position = 8; while (!r.done()) { var id = r.byte_(); var len = r.u32(); var end = Math.min(wasm.length, r.position + len); if (id == wanted) return java.util.Arrays.copyOfRange(wasm, r.position, end); r.position = end; } return null; }
        static final class Reader {
            final byte[] data; int position; Reader(byte[] data) { this.data = data; } boolean done() { return position >= data.length; }
            int byte_() { return position < data.length ? data[position++] & 0xff : 0; }
            int u32() { var v = 0; for (var shift = 0; shift < 35 && !done(); shift += 7) { var b = byte_(); v |= (b & 127) << shift; if ((b & 128) == 0) break; } return v; }
            String string() { var n = u32(); var end = Math.min(data.length, position + n); var s = new String(data, position, end - position, StandardCharsets.UTF_8); position = end; return s; }
            int initExprOffset() { var value = 0; while (!done()) { var op = byte_(); if (op == 0x0b) return value; if (op == 0x41) value = u32(); else if (op == 0x23) u32(); } return value; }
            void skipExpr() { while (!done() && byte_() != 0x0b) { } }
        }
    }

    private void refreshMemoryView() {
        memoryData = lib.wasm_memory_data(memory);
        memoryDataSize = lib.wasm_memory_data_size(memory);
    }

    private byte[] loadWasmBytes() throws Exception {
        if (overrides.wasmModule != null) {
            return overrides.wasmModule;
        }
        var cacheKey = moduleUrl.toString();
        synchronized (MODULE_CACHE_LOCK) {
            var cached = WASM_BYTES_CACHE.get(cacheKey);
            if (cached != null) {
                return cached;
            }
            try (var input = moduleUrl.openStream()) {
                var bytes = input.readAllBytes();
                WASM_BYTES_CACHE.put(cacheKey, bytes);
                return bytes;
            }
        }
    }

    private static String moduleCacheKey(byte[] wasmBytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(wasmBytes));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash WebAssembly module", e);
        }
    }

    private long hostCall(String module, String name, long[] args, byte[] resultKinds) {
        if (emscripten != null) return emscripten.call(module, name, args);
        var result = switch (module) {
            case "env" -> envCall(name, args);
            case "pglite" -> pgliteCall(name, args);
            case "wasi_snapshot_preview1" -> wasiCall(name, args);
            default -> 0L;
        };
        return resultKinds.length == 0 ? 0L : result;
    }

    private long wasiCall(String name, long[] args) {
        if ("proc_exit".equals(name)) {
            throw new ExitStatus(args.length == 0 ? 0 : (int) args[0]);
        }
        return 0L;
    }

    private long envCall(String name, long[] args) {
        if (TRACE_ENV_CALLS) {
            System.err.println("[wasmer-env] " + name + " " + java.util.Arrays.toString(args));
        }
        return switch (name) {
            case "dlopen" -> dlopen((int) args[0], (int) args[1]);
            case "dlsym" -> dlsym((int) args[0], (int) args[1]);
            case "dlclose" -> dlclose((int) args[0]);
            case "dlerror" -> dlErrorPtr;
            case "arc4random" -> random.nextInt() & 0xffffffffL;
            case "timingsafe_bcmp" -> timingsafeBcmp((int) args[0], (int) args[1], (int) args[2]);
            case "getifaddrs" -> 0L;
            case "freeifaddrs" -> 0L;
            case "sem_destroy" -> {
                semaphores.remove((int) args[0]);
                yield 0L;
            }
            case "sem_init" -> {
                semaphores.put((int) args[0], (int) args[2]);
                yield 0L;
            }
            case "sem_trywait", "sem_wait" -> semWait((int) args[0]);
            case "sem_post" -> {
                semaphores.merge((int) args[0], 1, Integer::sum);
                yield 0L;
            }
            case "pthread_atfork" -> 0L;
            case "__cxa_allocate_exception" -> call("malloc", args[0]);
            case "__cxa_throw" -> throw new IllegalStateException("Uncaught C++ exception in pglite.wasm");
            default -> 0L;
        };
    }

    private long timingsafeBcmp(int a, int b, int len) {
        var left = readBytes(a, len);
        var right = readBytes(b, len);
        var diff = 0;
        for (var i = 0; i < len; i++) {
            diff |= left[i] ^ right[i];
        }
        return diff == 0 ? 0L : 1L;
    }

    private long semWait(int address) {
        var value = semaphores.getOrDefault(address, 0);
        if (value <= 0) {
            return -1L;
        }
        semaphores.put(address, value - 1);
        return 0L;
    }

    private long pgliteCall(String name, long[] args) {
        if (TRACE_HOST_CALLS) {
            System.err.println("[wasmer-pglite] " + name + " " + java.util.Arrays.toString(args));
        }
        return switch (name) {
            case "random" -> {
                var length = (int) args[1];
                var bytes = new byte[length];
                random.nextBytes(bytes);
                writeBytes((int) args[0], bytes);
                yield 0L;
            }
            case "socket_read" -> invokeCallback(socketRead, (int) args[0], (int) args[1]);
            case "socket_write" -> invokeCallback(socketWrite, (int) args[0], (int) args[1]);
            case "system" -> systemFn != 0 ? invokeCallback(systemFn, (int) args[0]) : 1L;
            case "popen" -> popenFn != 0 ? invokeCallback(popenFn, (int) args[0], (int) args[1]) : openLocaleList((int) args[0], (int) args[1]);
            case "pclose" -> pcloseFn != 0 ? invokeCallback(pcloseFn, (int) args[0]) : _fclose((int) args[0]);
            case "blob_read" -> blobRead((int) args[0], (int) args[1], (int) args[2]);
            case "blob_write" -> blobWrite((int) args[0], (int) args[1], (int) args[2]);
            case "blob_llseek" -> blobLlseek((int) args[0], (int) args[1]);
            default -> 0L;
        };
    }

    private long openLocaleList(int command, int mode) {
        if (!"locale -a".equals(readCString(command)) || !"r".equals(readCString(mode))) {
            throw new UnsupportedOperationException("Unsupported child command: " + readCString(command));
        }
        // WASI libc exposes these locales but has no shell or locale executable.
        fs.writeFile("/pglite/locale-a", "C\nC.UTF-8\nPOSIX\n".getBytes(StandardCharsets.UTF_8));
        var path = writeCString("/pglite/locale-a");
        try {
            return _fopen(path, mode);
        } finally {
            call("free", path);
        }
    }

    private long blobRead(int ptr, int length, int position) {
        if (blobDevice == null) {
            return 0L;
        }
        var buffer = new byte[length];
        var read = blobDevice.read(buffer, 0, length, position);
        if (read > 0) {
            writeBytes(ptr, java.util.Arrays.copyOf(buffer, read));
        }
        return read;
    }

    private long blobWrite(int ptr, int length, int position) {
        if (blobDevice == null) {
            return 0L;
        }
        return blobDevice.write(readBytes(ptr, length), 0, length, position);
    }

    private long blobLlseek(int offset, int whence) {
        if (blobDevice == null) {
            return -1L;
        }
        return blobDevice.llseek(offset, whence, 0);
    }

    private long invokeCallback(int id, int... args) {
        var callback = callbacks.get(id);
        if (callback == null) {
            return 0L;
        }
        return callback.apply(args.length > 0 ? args[0] : 0, args.length > 1 ? args[1] : 0);
    }

    private long call(String name, long... args) {
        return runOnWasmerThread(() -> {
            var func = exports.get(name);
            if (func == null) {
                throw new IllegalStateException("Missing export: " + name);
            }
            return callFunc(func, args);
        });
    }

    private void callIfExists(String name) {
        if (exports.containsKey(name)) call(name);
    }

    private long callIfExists(String name, long... args) {
        return exports.containsKey(name) ? call(name, args) : 0L;
    }

    private long callFunc(Pointer func, long... args) {
        refreshMemoryView();
        var arity = (int) lib.wasm_func_param_arity(func);
        var resultArity = (int) lib.wasm_func_result_arity(func);
        var argsVec = new WasmValVec.ByReference();
        Memory argsMem = null;
        var functionType = lib.wasm_func_type(func);
        var argumentKinds = valKinds(lib.wasm_functype_params(functionType));
        lib.wasm_functype_delete(functionType);
        if (arity > 0) {
            argsMem = new Memory(arity * 16L);
            argsMem.clear();
            for (var i = 0; i < arity; i++) {
                argsMem.setByte(i * 16L, argumentKinds[i]);
                var argument = i < args.length ? args[i] : 0L;
                if (argumentKinds[i] == WasmerTypes.WASM_I32 || argumentKinds[i] == WasmerTypes.WASM_F32) {
                    argsMem.setInt(i * 16L + 8, (int) argument);
                } else {
                    argsMem.setLong(i * 16L + 8, argument);
                }
            }
            argsVec.size = arity;
            argsVec.data = argsMem;
        } else {
            argsVec.size = 0;
            argsVec.data = null;
        }
        argsVec.write();

        var resultsVec = new WasmValVec.ByReference();
        Memory resultsMem = null;
        if (resultArity > 0) {
            resultsMem = new Memory(resultArity * 16L);
            resultsMem.clear();
            resultsVec.size = resultArity;
            resultsVec.data = resultsMem;
        } else {
            resultsVec.size = 0;
            resultsVec.data = null;
        }
        resultsVec.write();

        var trap = lib.wasm_func_call(func, argsVec, resultsVec);
        if (pendingHostException != null) {
            var pending = pendingHostException;
            pendingHostException = null;
            if (trap != null) lib.wasm_trap_delete(trap);
            throw pending;
        }
        if (trap != null) {
            throw trapError(trap);
        }
        refreshMemoryView();
        if (resultArity == 0 || resultsMem == null) {
            return 0L;
        }
        var kind = resultsMem.getByte(0);
        return switch (kind) {
            case WasmerTypes.WASM_I32 -> resultsMem.getInt(8);
            case WasmerTypes.WASM_I64 -> resultsMem.getLong(8);
            case WasmerTypes.WASM_F32 -> Float.floatToIntBits(resultsMem.getFloat(8));
            case WasmerTypes.WASM_F64 -> Double.doubleToLongBits(resultsMem.getDouble(8));
            default -> resultsMem.getInt(8);
        };
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

    private byte[] readBytes(int ptr, int length) {
        refreshMemoryView();
        if (ptr < 0 || length < 0 || (long) ptr + length > memoryDataSize) {
            throw new IndexOutOfBoundsException("memory read out of range");
        }
        return memoryData.getByteArray(ptr, length);
    }

    private void writeBytes(int ptr, byte[] bytes) {
        refreshMemoryView();
        if (ptr < 0 || (long) ptr + bytes.length > memoryDataSize) {
            ensureMemory(ptr + bytes.length);
            refreshMemoryView();
        }
        memoryData.write(ptr, bytes, 0, bytes.length);
    }

    private void ensureMemory(int size) {
        var pageSize = 64 * 1024;
        var neededPages = (size + pageSize - 1) / pageSize;
        var current = lib.wasm_memory_size(memory);
        if (neededPages > current) {
            if (!lib.wasm_memory_grow(memory, neededPages - current)) {
                throw wasmerError("wasm_memory_grow");
            }
            refreshMemoryView();
        }
    }

    private int writeI32(int ptr, int value) {
        refreshMemoryView();
        memoryData.setInt(ptr, value);
        return value;
    }

    private String readCString(int ptr) {
        refreshMemoryView();
        var end = ptr;
        while (end < memoryDataSize && memoryData.getByte(end) != 0) {
            end++;
        }
        return new String(memoryData.getByteArray(ptr, end - ptr), StandardCharsets.UTF_8);
    }

    private int writeCString(String text) {
        var bytes = (text + "\0").getBytes(StandardCharsets.UTF_8);
        var ptr = (int) call("malloc", bytes.length);
        writeBytes(ptr, bytes);
        return ptr;
    }

    private static Pointer readPointerAt(Pointer base, long index) {
        return base.getPointer(index * NativePointerSize.SIZE);
    }

    private static void writePointerAt(Pointer base, long index, Pointer value) {
        base.setPointer(index * NativePointerSize.SIZE, value);
    }

    private String readName(Pointer namePtr) {
        if (namePtr == null) {
            return "";
        }
        var size = namePtr.getLong(0);
        var data = namePtr.getPointer(NativePointerSize.SIZE);
        if (data == null || size <= 0) {
            return "";
        }
        return new String(data.getByteArray(0, (int) size), StandardCharsets.UTF_8);
    }

    private RuntimeException wasmerError(String what) {
        var length = lib.wasmer_last_error_length();
        if (length <= 0) {
            return new IllegalStateException(what + " failed");
        }
        var buffer = new byte[length];
        lib.wasmer_last_error_message(buffer, length);
        return new IllegalStateException(what + " failed: " + new String(buffer, StandardCharsets.UTF_8).trim());
    }

    private RuntimeException trapOrWasmerError(String what, Pointer trap) {
        if (trap != null) {
            return trapError(trap);
        }
        return wasmerError(what);
    }

    private RuntimeException trapError(Pointer trap) {
        var text = "wasm trap";
        try {
            var msg = new WasmByteVec.ByReference();
            msg.size = 0;
            msg.data = null;
            msg.write();
            lib.wasm_trap_message(trap, msg);
            msg.read();
            if (msg.size > 0 && msg.data != null) {
                text = new String(msg.data.getByteArray(0, (int) Math.min(msg.size, 4096)), StandardCharsets.UTF_8);
            }
            try {
                lib.wasm_byte_vec_delete(msg);
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
        var frames = new WasmExternVec.ByReference();
        lib.wasm_trap_trace(trap, frames);
        frames.read();
        var isLongjmp = "uncaught exception".equals(text.replace("\0", "").trim())
            && frames.size > 0
            && lib.wasm_frame_func_index(readPointerAt(frames.data, 0)) == longjmpFunctionIndex;
        var trace = new StringBuilder(text.replace("\0", ""));
        for (var i = 0; i < frames.size; i++) {
            var frame = readPointerAt(frames.data, i);
            trace.append("\n  wasm function ").append(lib.wasm_frame_func_index(frame))
                .append(" + ").append(lib.wasm_frame_func_offset(frame));
        }
        lib.wasm_frame_vec_delete(frames);
        text = trace.toString();
        try {
            lib.wasm_trap_delete(trap);
        } catch (Throwable ignored) {
        }
        return isLongjmp ? new LongjmpException(text) : new IllegalStateException(text);
    }

    private static final class LongjmpException extends RuntimeException {
        private LongjmpException(String message) { super(message); }
    }

    /**
     * A provider must stay in the same Wasmer store as every module importing its tag.
     * Wasmer externs cannot be transferred between stores.
     */
    private static final class WasmerRuntimeContext {
        private static final Object ENGINE_LOCK = new Object();
        private static Pointer sharedEngine;
        private final WasmerLibrary lib;
        private volatile Thread workerThread;
        private final ExecutorService thread = Executors.newSingleThreadExecutor(r -> {
            var worker = new Thread(r, "pglite-wasmer");
            workerThread = worker;
            worker.setDaemon(true);
            return worker;
        });
        private final Pointer engine;
        private final Pointer store;

        private WasmerRuntimeContext(WasmerLibrary lib) {
            this.lib = lib;
            synchronized (ENGINE_LOCK) {
                if (sharedEngine == null) {
                    if (lib.wasmer_is_headless()) {
                        // The 7.4.2 headless C API's config default can select an
                        // unavailable compiler. The default engine avoids that path.
                        sharedEngine = lib.wasm_engine_new();
                    } else {
                        var config = lib.wasm_config_new();
                        if (config == null) throw new IllegalStateException("wasm_config_new failed");
                        lib.wasm_config_set_backend(config, WASMER_BACKEND_CRANELIFT);
                        sharedEngine = lib.wasm_engine_new_with_config(config);
                    }
                    if (sharedEngine == null) {
                        throw new IllegalStateException("wasm_engine_new failed");
                    }
                }
                engine = sharedEngine;
            }
            store = lib.wasm_store_new(engine);
            if (store == null) {
                throw new IllegalStateException("wasm_store_new failed");
            }
        }

        private <T> T call(Callable<T> callable) {
            if (Thread.currentThread() == workerThread) {
                try {
                    return callable.call();
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            var future = thread.submit(callable);
            try {
                return future.get(NATIVE_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new RuntimeException(
                    "Wasmer native call timed out after " + NATIVE_CALL_TIMEOUT_MS + "ms",
                    e
                );
            } catch (ExecutionException e) {
                var cause = e.getCause();
                if (cause instanceof RuntimeException re) {
                    throw re;
                }
                throw new RuntimeException(cause != null ? cause : e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }

        private void close() {
            lib.wasm_store_delete(store);
        }

        private void shutdown() {
            thread.shutdownNow();
            try {
                thread.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static final class WasmerEhProvider {
        private final WasmerRuntimeContext context;
        private final Pointer cLongjmpTag;
        private final Pointer wasmLongjmp;

        private WasmerEhProvider(WasmerRuntimeContext context, Pointer cLongjmpTag, Pointer wasmLongjmp) {
            this.context = context;
            this.cLongjmpTag = cLongjmpTag;
            this.wasmLongjmp = wasmLongjmp;
        }
    }

    @Override
    public void close() {
        closeNative();
    }

    private void closeNative() {
        if (!nativeClosed.compareAndSet(false, true)) {
            return;
        }
        try {
            runOnWasmerThread(() -> {
                clearDlError();
                if (emscripten != null) emscripten.close();
                loadedLibsByName.clear();
                loadedLibsByHandle.clear();
                got.clear();
                // Avoid wasi_env_delete before initialize; it can panic on uninitialized env.
                if (wasiEnv != null && instance != null) {
                    try {
                        lib.wasi_env_delete(wasiEnv);
                    } catch (Throwable ignored) {
                    }
                    wasiEnv = null;
                } else {
                    wasiEnv = null;
                }
                if (instance != null) {
                    try {
                        lib.wasm_instance_delete(instance);
                    } catch (Throwable ignored) {
                    }
                    instance = null;
                }
                // Every C handle holds a strong reference to the Wasmer store.
                // Releasing the store alone cannot free its linear memories.
                for (var i = nativeResources.size() - 1; i >= 0; i--) {
                    nativeResources.get(i).run();
                }
                nativeResources.clear();
                memory = null;
                memoryData = null;
                memoryDataSize = 0;
                // Compiled modules belong to the process-wide engine cache.
                // Keep them alive after an instance closes to avoid compiling
                // pglite.wasm again for every JDBC connection.
                module = null;
                exports.clear();
                globalExports.clear();
                tableExports.clear();
                mainSymbols.clear();
                if (ownsContext) {
                    context.close();
                }
                // Native callbacks must stay reachable until the store is destroyed.
                keepAlive.clear();
                return null;
            });
        } catch (Throwable ignored) {
        } finally {
            if (ownsContext) {
                context.shutdown();
            }
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
        refreshMemoryView();
        return new MemoryUint8Array();
    }

    @Override
    public Map<String, extensionUtils.ExtensionBlob> pg_extensions() {
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
        call("pgl_setPGliteActive", 0);
        try {
            // The owner supplies the protocol Terminate packet to socket_read.
            call("PostgresMainLoopOnce");
        } catch (ExitStatus exit) {
            if (exit.status != 0) {
                throw exit;
            }
        }
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
        } catch (EmscriptenHost.Longjmp jump) {
            _PostgresMainLongJmp();
        } catch (LongjmpException jump) {
            _PostgresMainLongJmp();
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
        callIfExists("pgl_set_rw_cbs", read_cb, write_cb);
    }

    @Override
    public int addFunction(postgresMod.ReadWriteCallback cb, String signature) {
        if (emscripten != null) return runOnWasmerThread(() -> {
            return addEmscriptenCallback(cb, signature);
        });
        // Host imports (socket_*/system/popen/pclose) invoke Java callbacks directly.
        // These IDs belong to the host callback registry, not the WASM function table.
        var id = nextCallback++;
        callbacks.put(id, cb);
        return id;
    }

    private int addEmscriptenCallback(postgresMod.ReadWriteCallback cb, String signature) {
        var params = signature.length() - 1;
        if (params < 1 || params > 2 || signature.charAt(0) == 'v') {
            throw new IllegalArgumentException("Unsupported ReadWriteCallback signature: " + signature);
        }
        var id = nextCallback++;
        callbacks.put(id, cb);
        var functions = instantiateHostAdapter("host/callback-" + params + ".wasm", Integer.toString(id));
        var index = registerTableFunction(functions.get("java.callback"));
        tableCallbacks.put(index, id);
        return index;
    }

    private Map<String, Pointer> instantiateHostAdapter(String resource, String callbackId) {
        byte[] bytes;
        try (var input = WasmerPostgresMod.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing host adapter: " + resource);
            bytes = input.readAllBytes();
        } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
        var adapter = compileModule(bytes);
        nativeResources.add(() -> lib.wasm_module_delete(adapter));
        var types = new WasmImporttypeVec.ByReference();
        lib.wasm_module_imports(adapter, types);
        var imports = new WasmExternVec.ByReference();
        lib.wasm_extern_vec_new_uninitialized(imports, types.size);
        var functions = new HashMap<String, Pointer>();
        try {
            for (var i = 0; i < types.size; i++) {
                var type = readPointerAt(types.data, i);
                var namespace = readName(lib.wasm_importtype_module(type));
                var name = callbackId != null ? callbackId : readName(lib.wasm_importtype_name(type));
                var function = createHostFunc(namespace, name, lib.wasm_importtype_type(type));
                writePointerAt(imports.data, i, lib.wasm_extern_copy(function));
            }
            var trap = new Pointer[1];
            var adapterInstance = lib.wasm_instance_new(store, adapter, imports, trap);
            if (adapterInstance == null) throw trapOrWasmerError("host adapter " + resource, trap[0]);
            nativeResources.add(() -> lib.wasm_instance_delete(adapterInstance));
            var exports = new WasmExternVec.ByReference();
            var exportTypes = new WasmExporttypeVec.ByReference();
            lib.wasm_instance_exports(adapterInstance, exports);
            lib.wasm_module_exports(adapter, exportTypes);
            for (var i = 0; i < exports.size; i++) {
                var name = readName(lib.wasm_exporttype_name(readPointerAt(exportTypes.data, i)));
                functions.put(name, lib.wasm_extern_as_func(readPointerAt(exports.data, i)));
            }
            lib.wasm_exporttype_vec_delete(exportTypes);
            nativeResources.add(() -> lib.wasm_extern_vec_delete(exports));
        } finally {
            lib.wasm_extern_vec_delete(imports);
            lib.wasm_importtype_vec_delete(types);
        }
        return functions;
    }

    public long emCallback(String name, long[] args) {
        return invokeCallback(Integer.parseInt(name), java.util.Arrays.stream(args).mapToInt(v -> (int) v).toArray());
    }

    @Override
    public void removeFunction(int f) {
        if (emscripten != null) {
            runOnWasmerThread(() -> {
                var id = tableCallbacks.remove(f);
                if (id != null) {
                    callbacks.remove(id);
                    if (!lib.wasm_table_set(dynamicTable(), f, null)) throw wasmerError("wasm_table_set");
                }
                return null;
            });
        } else {
            callbacks.remove(f);
        }
    }

    @Override
    public void copyFromHeap(int ptr, byte[] dest, int destOffset, int length) {
        var bytes = readBytes(ptr, length);
        System.arraycopy(bytes, 0, dest, destOffset, length);
    }

    @Override
    public void copyToHeap(int ptr, byte[] src, int srcOffset, int length) {
        var slice = new byte[length];
        System.arraycopy(src, srcOffset, slice, 0, length);
        writeBytes(ptr, slice);
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
    public Object __pgliteEhProvider() {
        return ehProvider;
    }

    @Override
    public int callMain(String[] args) {
        return callArgv("__main_argc_argv", overrides.thisProgram != null ? overrides.thisProgram : "/pglite/bin/postgres", args);
    }

    /** Emulates a child process with independent memory and WASI descriptors. */
    public WasmerPostgresMod createProcess(String program) {
        var options = new postgresMod.PartialPostgresMod();
        options.thisProgram = program;
        options.__wasiRoot = root.toString();
        options.__wasiDataRoot = dataRoot.toString();
        options.wasmModule = overrides.wasmModule;
        options.INITIAL_MEMORY = overrides.INITIAL_MEMORY;
        options.print = overrides.print;
        options.printErr = overrides.printErr;
        var url = emscripten != null && program.endsWith("/initdb")
            ? WasmerPostgresMod.class.getResource("initdb.wasm") : moduleUrl;
        if (emscripten != null && program.endsWith("/initdb")) options.wasmModule = null;
        return new WasmerPostgresMod(options, url);
    }

    @Override
    public int callInitdbMain(String[] args) {
        return callArgv(emscripten != null ? "__main_argc_argv" : "pglite_initdb_main", "/pglite/bin/initdb", args);
    }

    @Override
    public void resetAfterProcExit() {
        callIfExists("pglite_reset_after_proc_exit");
    }

    private int callArgv(String entryPoint, String program, String[] args) {
        var argvWithProgram = new ArrayList<String>();
        argvWithProgram.add(program);
        if (args != null) {
            argvWithProgram.addAll(java.util.Arrays.asList(args));
        }
        var ptrs = new ArrayList<Integer>();
        var argv = 0;
        try {
            for (var arg : argvWithProgram) {
                ptrs.add(writeCString(arg));
            }
            argv = (int) call("malloc", (ptrs.size() + 1) * 4L);
            for (var i = 0; i < ptrs.size(); i++) {
                writeI32(argv + i * 4, ptrs.get(i));
            }
            writeI32(argv + ptrs.size() * 4, 0);
            try {
                return (int) call(entryPoint, argvWithProgram.size(), argv);
            } catch (ExitStatus exit) {
                return exit.status;
            }
        } finally {
            for (var ptr : ptrs) {
                callIfExists("free", ptr);
            }
            if (argv != 0) {
                callIfExists("free", argv);
            }
        }
    }

    @Override public void print(String text) {
        if (overrides.print != null) overrides.print.accept(new Object[] {text});
    }
    @Override public void printErr(String text) {
        if (overrides.printErr != null) overrides.printErr.accept(new Object[] {text});
        if (Boolean.getBoolean("pglite.trace_init")) System.err.print(text);
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
        return readCString(ptr);
    }

    @Override
    public int stringToUTF8OnStack(String s) {
        return writeCString(s);
    }

    @Override
    public void _pgl_set_system_fn(int systemFn) {
        this.systemFn = systemFn;
        callIfExists("pgl_set_system_fn", systemFn);
    }

    @Override
    public void _pgl_set_popen_fn(int popenFn) {
        this.popenFn = popenFn;
        callIfExists("pgl_set_popen_fn", popenFn);
    }

    @Override
    public void _pgl_set_pclose_fn(int pcloseFn) {
        this.pcloseFn = pcloseFn;
        callIfExists("pgl_set_pclose_fn", pcloseFn);
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

    @Override
    public String __wasiDataRoot() {
        return dataRoot.toString();
    }

    @Override
    public void _pgl_freopen(int path, int mode, int fd) {
        callIfExists("pgl_freopen", path, mode, fd);
    }

    @Override
    public Integer _close(int fd) {
        return (int) callIfExists("close", fd);
    }

    @Override
    public Integer _pgl_chdir(int path) {
        if (emscripten != null) return emscripten.chdir(readCString(path));
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
            if (devId == makedev(64, 0)) {
                blobDevice = ops;
            }
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

        private ExitStatus(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private static final class NativePointerSize {
        private static final int SIZE = com.sun.jna.Native.POINTER_SIZE;
    }

    private final class MemoryUint8Array extends Uint8Array {
        private MemoryUint8Array() {
            super((int) Math.min(Integer.MAX_VALUE, memoryDataSize));
        }

        @Override
        public byte get(int index) {
            refreshMemoryView();
            return memoryData.getByte(index);
        }

        @Override
        public void set(int index, int value) {
            refreshMemoryView();
            memoryData.setByte(index, (byte) (value & 0xFF));
        }

        @Override
        public void set(byte[] source, int offset) {
            writeBytes(offset, source);
        }

        @Override
        public void set(Uint8Array source, int offset) {
            set(source.toByteArray(), offset);
        }

        @Override
        public byte[] toByteArray() {
            refreshMemoryView();
            return memoryData.getByteArray(0, this.length);
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
            this.mounts.put("/data", dataRoot);
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
        public void mount(Object type, Object opts, String mountpoint) {
            if (opts instanceof Map<?, ?> map) {
                var rootOpt = map.get("root");
                var fsOpt = map.get("fs");
                if (rootOpt != null && fsOpt instanceof extensionUtils.EmscriptenFS otherFs) {
                    var otherRoot = otherFs.__root();
                    if (otherRoot != null) {
                        var guestRoot = String.valueOf(rootOpt);
                        var resolved = Path.of(otherRoot);
                        if (!"/".equals(guestRoot) && !guestRoot.isBlank()) {
                            var relative = guestRoot.startsWith("/") ? guestRoot.substring(1) : guestRoot;
                            resolved = resolved.resolve(relative).normalize();
                        }
                        mounts.put(normalize(mountpoint), resolved);
                        return;
                    }
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
                writeFile(path, target.getBytes(StandardCharsets.UTF_8));
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
                return text.getBytes(StandardCharsets.UTF_8);
            }
            throw new IllegalArgumentException("Unsupported file data: " + data.getClass());
        }
    }
}
