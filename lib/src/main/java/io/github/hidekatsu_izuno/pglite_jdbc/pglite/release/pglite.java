package io.github.hidekatsu_izuno.pglite_jdbc.pglite.release;

import com.dylibso.chicory.runtime.HostFunction;
import com.dylibso.chicory.runtime.ImportGlobal;
import com.dylibso.chicory.runtime.ImportMemory;
import com.dylibso.chicory.runtime.ImportTable;
import com.dylibso.chicory.runtime.ImportValues;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.TrapException;
import com.dylibso.chicory.runtime.ExecutionListener;
import com.dylibso.chicory.runtime.ByteArrayMemory;
import com.dylibso.chicory.runtime.Memory;
import com.dylibso.chicory.runtime.PgliteInterpreterMachine;
import com.dylibso.chicory.runtime.GlobalInstance;
import com.dylibso.chicory.runtime.TableInstance;
import com.dylibso.chicory.wasi.WasiOptions;
import com.dylibso.chicory.wasi.WasiPreview1;
import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.WasmModule;
import com.dylibso.chicory.wasm.types.ExternalType;
import com.dylibso.chicory.wasm.types.Export;
import com.dylibso.chicory.wasm.types.FunctionType;
import com.dylibso.chicory.wasm.types.FunctionImport;
import com.dylibso.chicory.wasm.types.GlobalImport;
import com.dylibso.chicory.wasm.types.MemoryImport;
import com.dylibso.chicory.wasm.types.Table;
import com.dylibso.chicory.wasm.types.TableImport;
import com.dylibso.chicory.wasm.types.UnknownCustomSection;
import com.dylibso.chicory.wasm.types.ValType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.extensionUtils;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.postgresMod;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.utils;
import java.io.ByteArrayOutputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class pglite {
    private static final boolean TRACE_CALL_INDIRECT = Boolean.getBoolean(
        "pglite.trace_call_indirect"
    );
    private static final boolean TRACE_INVOKE = Boolean.getBoolean("pglite.trace_invoke");
    private static final boolean TRACE_EXEC = Boolean.getBoolean("pglite.trace_exec");
    private static final boolean MANIFEST_FALLBACK = Boolean.getBoolean("pglite.manifest.fallback");
    private static final String MANIFEST_RESOURCE = "pglite.data.manifest.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final java.util.concurrent.ConcurrentLinkedDeque<String> EXEC_TRACE =
        new java.util.concurrent.ConcurrentLinkedDeque<>();
    private static final ThreadLocal<Integer> INVOKE_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Map<Integer, Integer>> INVOKE_REENTRY_COUNTS =
        ThreadLocal.withInitial(HashMap::new);
    private static final int INVOKE_DEPTH_LIMIT = Integer.getInteger(
        "pglite.invoke_depth_limit",
        512
    );
    private static final int INVOKE_REENTRY_LIMIT = Integer.getInteger(
        "pglite.invoke_reentry_limit",
        64
    );
    private static final ScheduledExecutorService TIMER_EXECUTOR = Executors.newSingleThreadScheduledExecutor(
        runnable -> {
            var thread = new Thread(runnable, "pglite-runtime-timer");
            thread.setDaemon(true);
            return thread;
        }
    );

    public static CompletableFuture<postgresMod.PostgresMod> PostgresModFactory(
        postgresMod.PartialPostgresMod moduleOverrides
    ) {
        INVOKE_DEPTH.set(0);
        INVOKE_REENTRY_COUNTS.get().clear();

        var overrides = moduleOverrides != null
            ? moduleOverrides
            : new postgresMod.PartialPostgresMod();
        var module = loadModule(overrides);

        var wasi = WasiPreview1.builder()
            .withOptions(buildWasiOptions(overrides))
            .build();

        var modRef = new AtomicReference<RuntimePostgresMod>();
        var imports = buildImports(module, wasi, modRef, overrides);
        var instanceBuilder = Instance.builder(module)
            .withImportValues(imports)
            .withMachineFactory(PgliteInterpreterMachine::new);
        if (TRACE_CALL_INDIRECT || TRACE_EXEC) {
            instanceBuilder.withUnsafeExecutionListener(
                new ExecutionListener() {
                    @Override
                    public void onExecution(
                        com.dylibso.chicory.wasm.types.Instruction instruction,
                        com.dylibso.chicory.runtime.MStack stack
                    ) {
                        if (TRACE_EXEC) {
                            if (
                                instruction.opcode() == com.dylibso.chicory.wasm.types.OpCode.CALL ||
                                instruction.opcode() == com.dylibso.chicory.wasm.types.OpCode.CALL_INDIRECT
                            ) {
                                recordExecTrace(
                                    instruction.opcode() +
                                    "@" +
                                    instruction.address() +
                                    " operand0=" +
                                    (instruction.operandCount() > 0 ? instruction.operand(0) : -1) +
                                    " top=" +
                                    (stack.size() > 0 ? stack.peek() : -1) +
                                    " size=" +
                                    stack.size()
                                );
                            }
                        }
                        if (
                            instruction.opcode() == com.dylibso.chicory.wasm.types.OpCode.CALL_INDIRECT
                        ) {
                            var top = stack.size() > 0 ? stack.peek() : -1;
                            appendTrace(
                                "[pglite-trace] CALL_INDIRECT addr=" + instruction.address() +
                                " typeIdx=" +
                                (instruction.operandCount() > 0 ? instruction.operand(0) : -1) +
                                " tableIdx(top)=" +
                                top +
                                " stackSize=" +
                                stack.size()
                            );
                        }
                    }
                }
            );
        }
        var instance = instanceBuilder.build();

        var mod = new RuntimePostgresMod(instance, overrides, wasi);
        modRef.set(mod);
        mod.runtime().preRun();
        mod.initializeRuntime(overrides != null ? overrides.arguments : null);
        mod.runtime().postRun();
        return CompletableFuture.completedFuture(mod);
    }

    private static WasmModule loadModule(postgresMod.PartialPostgresMod overrides) {
        if (overrides != null && overrides.wasmModuleUrl != null) {
            return com.dylibso.chicory.wasm.Parser.parse(
                utils.readFile(overrides.wasmModuleUrl)
            );
        }
        return com.dylibso.chicory.wasm.Parser.parse(utils.readFile("pglite.wasm"));
    }

    private static WasiOptions buildWasiOptions(postgresMod.PartialPostgresMod overrides) {
        var builder = WasiOptions.builder();
        builder.withStdout(System.out);
        builder.withStderr(System.err);
        builder.withEnvironment("PGUSER", "postgres");
        builder.withEnvironment("PGDATABASE", "template1");
        builder.withEnvironment("PGDATA", "/tmp/pglite/base");
        builder.withEnvironment("PGHOST", "/tmp");
        if (overrides != null && overrides.arguments != null) {
            builder.withArguments(Arrays.asList(overrides.arguments));
        } else {
            builder.withArguments(java.util.List.of("pglite"));
        }
        return builder.build();
    }

    private static ImportValues buildImports(
        WasmModule module,
        WasiPreview1 wasi,
        AtomicReference<RuntimePostgresMod> modRef,
        postgresMod.PartialPostgresMod overrides
    ) {
        var builder = ImportValues.builder();
        var wasiFunctions = new HashMap<String, HostFunction>();
        for (var fn : wasi.toHostFunctions()) {
            wasiFunctions.put(fn.module() + "." + fn.name(), fn);
        }
        var unsupportedImports = new ArrayList<String>();
        for (var i = 0; i < module.importSection().importCount(); i++) {
            var importDecl = module.importSection().getImport(i);
            var importKey = importDecl.module() + "." + importDecl.name() + "#" + i;
            if (importDecl.importType() == ExternalType.FUNCTION) {
                if (
                    "env".equals(importDecl.module()) &&
                    importDecl.name().startsWith("invoke_")
                ) {
                    var fnImport = (FunctionImport) importDecl;
                    var fnType = module.typeSection().getType(fnImport.typeIndex());
                    builder.addFunction(
                        new HostFunction(
                            "env",
                            importDecl.name(),
                            fnType,
                            (instance, args) -> invokeCallback(modRef.get(), importDecl.name(), args)
                        )
                    );
                    continue;
                }
                if ("wasi_snapshot_preview1".equals(importDecl.module())) {
                    var wasiFn = wasiFunctions.get(importDecl.module() + "." + importDecl.name());
                    if (wasiFn == null) {
                        unsupportedImports.add(importKey);
                        continue;
                    }
                    var fnImport = (FunctionImport) importDecl;
                    var fnType = module.typeSection().getType(fnImport.typeIndex());
                    if (
                        "environ_get".equals(importDecl.name()) ||
                        "environ_sizes_get".equals(importDecl.name()) ||
                        "clock_time_get".equals(importDecl.name()) ||
                        "proc_exit".equals(importDecl.name()) ||
                        "fd_close".equals(importDecl.name()) ||
                        "fd_fdstat_get".equals(importDecl.name()) ||
                        "fd_pread".equals(importDecl.name()) ||
                        "fd_pwrite".equals(importDecl.name()) ||
                        "fd_read".equals(importDecl.name()) ||
                        "fd_seek".equals(importDecl.name()) ||
                        "fd_sync".equals(importDecl.name()) ||
                        "fd_datasync".equals(importDecl.name()) ||
                        "fd_write".equals(importDecl.name())
                    ) {
                        builder.addFunction(
                            new HostFunction(
                                importDecl.module(),
                                importDecl.name(),
                                fnType,
                                (instance, args) -> handleWasiFunction(
                                    modRef.get(),
                                    importDecl.name(),
                                    args
                                )
                            )
                        );
                        continue;
                    }
                    builder.addFunction(
                        new HostFunction(
                            importDecl.module(),
                            importDecl.name(),
                            fnType,
                            (instance, args) -> {
                                if ("proc_exit".equals(importDecl.name())) {
                                    throw new ExitStatusException(
                                        "proc_exit",
                                        args.length > 0 ? (int) args[0] : 0
                                    );
                                }
                                return wasiFn.handle().apply(instance, args);
                            }
                        )
                    );
                    continue;
                }
                var fnImport = (FunctionImport) importDecl;
                var fnType = module.typeSection().getType(fnImport.typeIndex());
                builder.addFunction(
                    new HostFunction(
                        importDecl.module(),
                        importDecl.name(),
                        fnType,
                        (instance, args) -> handleEnvFunction(
                            modRef.get(),
                            importDecl.name(),
                            args,
                            fnType.returns().size()
                        )
                    )
                );
                continue;
            }
            if (importDecl.importType() == ExternalType.GLOBAL) {
                var globalImport = (GlobalImport) importDecl;
                long valueLow = 0L;
                if ("env".equals(importDecl.module()) && "__memory_base".equals(importDecl.name())) {
                    valueLow = readLongProperty("pglite.memory_base", 1024L);
                } else if (
                    "env".equals(importDecl.module()) &&
                    "__stack_pointer".equals(importDecl.name())
                ) {
                    valueLow = readLongProperty(
                        "pglite.stack_pointer",
                        2_765_600L
                    );
                } else if (
                    "GOT.mem".equals(importDecl.module()) &&
                    "__heap_base".equals(importDecl.name())
                ) {
                    valueLow = readLongProperty(
                        "pglite.heap_base",
                        2_765_600L
                    );
                } else if ("env".equals(importDecl.module()) && "__table_base".equals(importDecl.name())) {
                    valueLow = readLongProperty("pglite.table_base", 1L);
                }
                builder.addGlobal(
                    new ImportGlobal(
                        importDecl.module(),
                        importDecl.name(),
                        new GlobalInstance(
                            valueLow,
                            0L,
                            globalImport.type(),
                            globalImport.mutabilityType()
                        )
                    )
                );
                continue;
            }
            if (importDecl.importType() == ExternalType.MEMORY) {
                var memoryImport = (MemoryImport) importDecl;
                builder.addMemory(
                    new ImportMemory(
                        importDecl.module(),
                        importDecl.name(),
                        new ByteArrayMemory(memoryImport.limits())
                    )
                );
                continue;
            }
            if (importDecl.importType() == ExternalType.TABLE) {
                var tableImport = (TableImport) importDecl;
                var limits = tableImport.limits();
                var min = limits.min();
                var max = limits.max();
                if (
                    "env".equals(importDecl.module()) &&
                    "__indirect_function_table".equals(importDecl.name())
                ) {
                    var tableBase = readLongProperty("pglite.table_base", 1L);
                    if (tableBase > 0) {
                        min += tableBase;
                        if (max > 0 && min > max) {
                            max = min;
                        }
                    }
                }
                var tableDef = new Table(
                    tableImport.entryType(),
                    new com.dylibso.chicory.wasm.types.TableLimits(min, max, limits.shared())
                );
                builder.addTable(
                    new ImportTable(
                        importDecl.module(),
                        importDecl.name(),
                        new TableInstance(tableDef, -1)
                    )
                );
                continue;
            }
            unsupportedImports.add(importKey);
        }

        if (!unsupportedImports.isEmpty()) {
            throw new RuntimeBridgeException(
                "buildImports",
                "Unsupported wasm imports: " + String.join(", ", unsupportedImports)
            );
        }

        return builder.build();
    }

    private static long readLongProperty(String key, long defaultValue) {
        var raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static java.util.List<String> buildEnvironEntries(
        postgresMod.PartialPostgresMod overrides
    ) {
        var executableName = "./this.program";
        if (
            overrides != null &&
            overrides.arguments != null &&
            overrides.arguments.length > 0 &&
            overrides.arguments[0] != null &&
            !overrides.arguments[0].isBlank()
        ) {
            executableName = overrides.arguments[0];
        }

        var env = new LinkedHashMap<String, String>();
        env.put("USER", "web_user");
        env.put("LOGNAME", "web_user");
        env.put("PATH", "/");
        env.put("PWD", "/");
        env.put("HOME", "/home/web_user");
        env.put("LANG", "C.UTF-8");
        env.put("_", executableName);

        if (overrides != null && overrides.arguments != null) {
            for (var arg : overrides.arguments) {
                if (arg == null || arg.isBlank()) {
                    continue;
                }
                var idx = arg.indexOf('=');
                if (idx <= 0 || idx >= arg.length() - 1) {
                    continue;
                }
                var key = arg.substring(0, idx);
                var value = arg.substring(idx + 1);
                env.put(key, value);
            }
        }

        var out = new ArrayList<String>(env.size());
        for (var entry : env.entrySet()) {
            out.add(entry.getKey() + "=" + entry.getValue());
        }
        return out;
    }

    private static void appendTrace(String line) {
        try {
            var path = Path.of("tmp/pglite-trace.log");
            var msg = line + System.lineSeparator();
            Files.writeString(
                path,
                msg,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (Exception ignored) {
        }
    }

    private static void recordExecTrace(String line) {
        if (!TRACE_EXEC) {
            return;
        }
        EXEC_TRACE.addLast(line);
        while (EXEC_TRACE.size() > 1024) {
            EXEC_TRACE.pollFirst();
        }
    }

    private static String recentExecTrace() {
        if (!TRACE_EXEC || EXEC_TRACE.isEmpty()) {
            return "[]";
        }
        return EXEC_TRACE.toString();
    }

    private static long[] handleEnvFunction(
        RuntimePostgresMod mod,
        String name,
        long[] args,
        int returnCount
    ) {
        var runtime = mod != null ? mod.runtimeImpl() : null;
        if (runtime != null) {
            runtime.recordHostNote("env." + name + " enter args=" + Arrays.toString(args));
        }
        var ret = 0L;
        switch (name) {
            case "emscripten_date_now":
            case "emscripten_get_now":
                ret = System.currentTimeMillis();
                break;
            case "emscripten_resize_heap":
                ret = runtime != null ? runtime.emscriptenResizeHeap(args) : 0L;
                break;
            case "_emscripten_memcpy_js":
                if (runtime != null) {
                    runtime.emscriptenMemcpyJs(args);
                }
                ret = 0L;
                break;
            case "getaddrinfo":
                ret = runtime != null ? runtime.getaddrinfo(args) : -2L;
                break;
            case "getnameinfo":
                ret = runtime != null ? runtime.getnameinfo(args) : -2L;
                break;
            case "__call_sighandler":
                if (runtime == null) {
                    throw new RuntimeBridgeException(
                        "__call_sighandler",
                        "Runtime is not initialized"
                    );
                }
                runtime.callSigHandler(args);
                ret = 0L;
                break;
            case "setTempRet0":
                if (runtime != null) {
                    runtime.setTempRet0(args);
                }
                ret = 0L;
                break;
            case "getTempRet0":
                ret = runtime != null ? runtime.getTempRet0(args) : 0L;
                break;
            case "emscripten_asm_const_int":
                ret = runtime != null ? runtime.emscriptenAsmConstInt(args) : 0L;
                break;
            case "__syscall_openat":
                ret = runtime != null ? runtime.syscallOpenAt(args) : -1L;
                break;
            case "__syscall_close":
                ret = runtime != null ? runtime.syscallClose(args) : -1L;
                break;
            case "__syscall_dup":
                ret = runtime != null ? runtime.syscallDup(args) : -1L;
                break;
            case "__syscall_dup3":
                ret = runtime != null ? runtime.syscallDup3(args) : -1L;
                break;
            case "__syscall_pipe":
                ret = runtime != null ? runtime.syscallPipe(args) : -1L;
                break;
            case "__syscall_pipe2":
                ret = runtime != null ? runtime.syscallPipe2(args) : -1L;
                break;
            case "__syscall_socket":
                ret = runtime != null ? runtime.syscallSocket(args) : -1L;
                break;
            case "__syscall_bind":
                ret = runtime != null ? runtime.syscallBind(args) : -1L;
                break;
            case "__syscall_sendto":
                ret = runtime != null ? runtime.syscallSendto(args) : -1L;
                break;
            case "__syscall_recvfrom":
                ret = runtime != null ? runtime.syscallRecvfrom(args) : -1L;
                break;
            case "__syscall_read":
            case "__syscall_pread64":
                ret = runtime != null ? runtime.syscallRead(args) : -1L;
                break;
            case "__syscall_write":
            case "__syscall_pwrite64":
                ret = runtime != null ? runtime.syscallWrite(args) : -1L;
                break;
            case "__syscall_lseek":
            case "__syscall__llseek":
                ret = runtime != null ? runtime.syscallLseek(args) : -1L;
                break;
            case "__syscall_fstat64":
                ret = runtime != null ? runtime.syscallFstat64(args) : -1L;
                break;
            case "__syscall_newfstatat":
                ret = runtime != null ? runtime.syscallNewfstatat(args) : -1L;
                break;
            case "__syscall_stat64":
                ret = runtime != null ? runtime.syscallStat64(args) : -1L;
                break;
            case "__syscall_lstat64":
                ret = runtime != null ? runtime.syscallLstat64(args) : -1L;
                break;
            case "__syscall_getcwd":
                ret = runtime != null ? runtime.syscallGetCwd(args) : -1L;
                break;
            case "__syscall_mkdirat":
                ret = runtime != null ? runtime.syscallMkdirAt(args) : -1L;
                break;
            case "__syscall_symlinkat":
                ret = runtime != null ? runtime.syscallSymlinkAt(args) : -1L;
                break;
            case "__syscall_unlinkat":
            case "__syscall_rmdir":
                ret = runtime != null ? runtime.syscallUnlinkAt(args) : -1L;
                break;
            case "__syscall_renameat":
                ret = runtime != null ? runtime.syscallRenameAt(args) : -1L;
                break;
            case "__syscall_fadvise64":
                ret = runtime != null ? runtime.syscallFadvise64(args) : -1L;
                break;
            case "__syscall_fallocate":
                ret = runtime != null ? runtime.syscallFallocate(args) : -1L;
                break;
            case "__syscall__newselect":
                ret = runtime != null ? runtime.syscallNewselect(args) : -1L;
                break;
            case "__syscall_fcntl64":
                ret = runtime != null ? runtime.syscallFcntl64(args) : -1L;
                break;
            case "__syscall_ioctl":
                ret = runtime != null ? runtime.syscallIoctl(args) : -1L;
                break;
            case "__syscall_getdents64":
                ret = runtime != null ? runtime.syscallGetdents64(args) : -1L;
                break;
            case "__syscall_readlinkat":
                ret = runtime != null ? runtime.syscallReadlinkAt(args) : -1L;
                break;
            case "__syscall_ftruncate64":
            case "__syscall_truncate64":
                ret = runtime != null ? runtime.syscallTruncate64(args) : -1L;
                break;
            case "__syscall_fdatasync":
                ret = runtime != null ? runtime.syscallFdatasync(args) : -1L;
                break;
            case "__syscall_chdir":
                ret = runtime != null ? runtime.syscallChdir(args) : -1L;
                break;
            case "__syscall_faccessat":
                ret = runtime != null ? runtime.syscallFaccessAt(args) : -1L;
                break;
            case "__syscall_chmod":
                ret = runtime != null ? runtime.syscallChmod(args) : -1L;
                break;
            case "_emscripten_system":
                ret = runtime != null ? runtime.emscriptenSystem(args) : -52L;
                break;
            case "_emscripten_runtime_keepalive_clear":
                if (runtime != null) {
                    runtime.emscriptenRuntimeKeepaliveClear(args);
                }
                ret = 0L;
                break;
            case "_setitimer_js":
                ret = runtime != null ? runtime.setitimerJs(args) : 0L;
                break;
            case "_gmtime_js":
                if (runtime != null) {
                    runtime.gmtimeJs(args);
                }
                ret = 0L;
                break;
            case "_localtime_js":
                if (runtime != null) {
                    runtime.localtimeJs(args);
                }
                ret = 0L;
                break;
            case "_tzset_js":
                if (runtime != null) {
                    runtime.tzsetJs(args);
                }
                ret = 0L;
                break;
            case "_mmap_js":
                ret = runtime != null ? runtime.mmapJs(args) : -1L;
                break;
            case "_munmap_js":
                ret = runtime != null ? runtime.munmapJs(args) : -1L;
                break;
            case "_dlopen_js":
                ret = runtime != null ? runtime.dlopenJs(args) : 0L;
                break;
            case "_dlsym_js":
                ret = runtime != null ? runtime.dlsymJs(args) : 0L;
                break;
            case "exit":
            case "emscripten_force_exit":
                throw new ExitStatusException(name, args.length > 0 ? (int) args[0] : 0);
            case "proc_exit":
                throw new ExitStatusException(name, args.length > 0 ? (int) args[0] : 0);
            case "_emscripten_throw_longjmp":
                ret = runtime != null ? runtime.emscriptenThrowLongjmp(args) : 0L;
                break;
            case "_abort_js":
            case "__assert_fail":
                throw new RuntimeBridgeException(name, "Wasm requested abort");
            default:
                if (name.startsWith("__syscall_")) {
                    if (Boolean.getBoolean("pglite.strict_syscall")) {
                        throw new RuntimeBridgeException(
                            "handleEnvFunction",
                            "Unknown syscall import: " + name + " args=" + Arrays.toString(args)
                        );
                    }
                    ret = -52L; // ENOSYS
                    break;
                }
                throw new RuntimeBridgeException(
                    "handleEnvFunction",
                    "Unknown env import: " + name + " args=" + Arrays.toString(args)
                );
        }
        if (returnCount <= 0) {
            if (runtime != null) {
                runtime.recordHostCall(name, args, ret);
            }
            return new long[] {};
        }
        var out = new long[returnCount];
        out[0] = ret;
        if (runtime != null) {
            runtime.recordHostCall(name, args, ret);
        }
        return out;
    }

    private static long[] handleWasiFunction(RuntimePostgresMod mod, String name, long[] args) {
        if (mod == null) {
            throw new RuntimeBridgeException(
                "handleWasiFunction",
                "Wasm runtime not initialized for " + name
            );
        }
        if (!RuntimeWasiContract.isSupported(name)) {
            if (Boolean.getBoolean("pglite.wasi.lenient")) {
                return new long[] { ENOSYS_WASI };
            }
            throw new RuntimeBridgeException(
                "handleWasiFunction",
                "Unknown wasi import: " + name + " args=" + Arrays.toString(args)
            );
        }
        var runtime = mod.runtimeImpl();
        runtime.recordHostNote("wasi." + name + " enter args=" + Arrays.toString(args));
        var ret = switch (name) {
            case "environ_get" -> runtime.wasiEnvironGet(args);
            case "environ_sizes_get" -> runtime.wasiEnvironSizesGet(args);
            case "clock_time_get" -> runtime.wasiClockTimeGet(args);
            case "proc_exit" -> runtime.wasiProcExit(args);
            case "fd_close" -> runtime.wasiFdClose(args);
            case "fd_fdstat_get" -> runtime.wasiFdFdstatGet(args);
            case "fd_pread" -> runtime.wasiFdPread(args);
            case "fd_pwrite" -> runtime.wasiFdPwrite(args);
            case "fd_read" -> runtime.wasiFdRead(args);
            case "fd_seek" -> runtime.wasiFdSeek(args);
            case "fd_sync" -> runtime.wasiFdSync(args);
            case "fd_datasync" -> runtime.wasiFdSync(args);
            case "fd_write" -> runtime.wasiFdWrite(args);
            default -> throw new RuntimeBridgeException(
                "handleWasiFunction",
                "Unhandled supported wasi import: " + name
            );
        };
        runtime.recordHostCall("wasi." + name, args, ret);
        return new long[] { ret };
    }

    private static final long ENOSYS_WASI = 52L;

    private static long[] invokeCallback(RuntimePostgresMod mod, String name, long[] args) {
        var depth = INVOKE_DEPTH.get() + 1;
        INVOKE_DEPTH.set(depth);
        var tableIndex = Integer.MIN_VALUE;
        try {
            if (mod == null) {
                throw new RuntimeBridgeException(
                    "invokeCallback",
                    "Wasm callback invoked before module initialization"
                );
            }
            if (args.length == 0) {
                return new long[] {};
            }

            tableIndex = (int) args[0];
            var reentryCounts = INVOKE_REENTRY_COUNTS.get();
            var reentryDepth = reentryCounts.getOrDefault(tableIndex, 0) + 1;
            reentryCounts.put(tableIndex, reentryDepth);
            if (reentryDepth > INVOKE_REENTRY_LIMIT) {
                if (TRACE_INVOKE) {
                    appendTrace(
                        "[pglite-invoke] reentry-limit " + name +
                        " tableIndex=" + tableIndex +
                        " depth=" + reentryDepth
                    );
                }
                if (mod.hasExport("setThrew")) {
                    try {
                        mod.instance.export("setThrew").apply(1, 0);
                    } catch (RuntimeException ignored) {
                    }
                }
                if (
                    "invoke_v".equals(name) ||
                    "invoke_vi".equals(name) ||
                    "invoke_vii".equals(name)
                ) {
                    return new long[] {};
                }
                return new long[] { 0L };
            }
            if (depth > INVOKE_DEPTH_LIMIT) {
                var table = mod.instance.table(0);
                var tableSize = table.size();
                var funcRef = (tableIndex >= 0 && tableIndex < tableSize)
                    ? table.ref(tableIndex)
                    : -1;
                var owner = (tableIndex >= 0 && tableIndex < tableSize)
                    ? table.instance(tableIndex)
                    : null;
                var message = "invoke recursion depth exceeded: depth=" +
                    depth +
                    ", name=" +
                    name +
                    ", tableIndex=" +
                    tableIndex +
                    ", funcRef=" +
                    funcRef +
                    ", ownerHash=" +
                    (owner != null ? Integer.toHexString(System.identityHashCode(owner)) : "null") +
                    ", callbackKeys=" +
                    mod.callbackKeysSnapshot();
                if (TRACE_INVOKE) {
                    appendTrace("[pglite-invoke] depth-limit " + message);
                }
                if (mod.hasExport("setThrew")) {
                    try {
                        mod.instance.export("setThrew").apply(1, 0);
                    } catch (RuntimeException ignored) {
                    }
                }
                if (
                    "invoke_v".equals(name) ||
                    "invoke_vi".equals(name) ||
                    "invoke_vii".equals(name)
                ) {
                    return new long[] {};
                }
                return new long[] { 0L };
            }

            var callback = mod.callback(tableIndex);
            if (callback != null) {
                var ptr = args.length > 1 ? (int) args[1] : 0;
                var len = args.length > 2 ? (int) args[2] : 0;
                var ret = callback.apply(ptr, len);
                if (
                    "invoke_v".equals(name) ||
                    "invoke_vi".equals(name) ||
                    "invoke_vii".equals(name)
                ) {
                    return new long[] {};
                }
                return new long[] { ret };
            }

            try {
                // Emscripten invoke_* uses function table index as the first argument.
                var table = mod.instance.table(0);
                if (tableIndex < 0 || tableIndex >= table.size()) {
                    throw new RuntimeBridgeException(
                        "invokeCallback",
                        "table index out of range: index=" +
                        tableIndex +
                        ", tableSize=" +
                        table.size() +
                        ", args=" +
                        Arrays.toString(args) +
                        ", callbackKeys=" +
                        mod.callbackKeysSnapshot()
                    );
                }
                if (tableIndex == 0) {
                    if (mod.hasExport("setThrew")) {
                        try {
                            mod.instance.export("setThrew").apply(1, 0);
                        } catch (RuntimeException ignored) {
                        }
                    }
                    if (
                        "invoke_v".equals(name) ||
                        "invoke_vi".equals(name) ||
                        "invoke_vii".equals(name)
                    ) {
                        return new long[] {};
                    }
                    return new long[] { 0L };
                }
                var funcRef = mod.instance.table(0).requiredRef(tableIndex);
                var owner = table.instance(tableIndex);
                if (owner == null) {
                    throw new RuntimeBridgeException(
                        "invokeCallback",
                        "table owner is null: index=" +
                        tableIndex +
                        ", funcRef=" +
                        funcRef +
                        ", args=" +
                        Arrays.toString(args)
                    );
                }
                var callArgs = new long[Math.max(0, args.length - 1)];
                if (callArgs.length > 0) {
                    System.arraycopy(args, 1, callArgs, 0, callArgs.length);
                }
                if (TRACE_INVOKE) {
                    appendTrace(
                        "[pglite-invoke] " + name +
                        " tableIndex=" + tableIndex +
                        " funcRef=" + funcRef +
                        " argc=" + callArgs.length
                    );
                }
                var importFunction = resolveImportedFunction(owner, funcRef);
                if (
                    importFunction != null &&
                    "env".equals(importFunction.module()) &&
                    importFunction.name().startsWith("invoke_")
                ) {
                    if (TRACE_INVOKE) {
                        appendTrace(
                            "[pglite-invoke] invoke-import-recursion-guard " +
                            name +
                            " tableIndex=" +
                            tableIndex +
                            " funcRef=" +
                            funcRef +
                            " import=" +
                            importFunction.module() +
                            "." +
                            importFunction.name()
                        );
                    }
                    if (mod.hasExport("setThrew")) {
                        try {
                            mod.instance.export("setThrew").apply(1, 0);
                        } catch (RuntimeException ignored) {
                        }
                    }
                    if (
                        "invoke_v".equals(name) ||
                        "invoke_vi".equals(name) ||
                        "invoke_vii".equals(name)
                    ) {
                        return new long[] {};
                    }
                    return new long[] { 0L };
                }
                var result = owner.getMachine().call(funcRef, callArgs);
                if (
                    "invoke_v".equals(name) ||
                    "invoke_vi".equals(name) ||
                    "invoke_vii".equals(name)
                ) {
                    return new long[] {};
                }
                return result;
            } catch (RuntimeException tableDispatchError) {
                if (
                    tableDispatchError instanceof TrapException ||
                    tableDispatchError instanceof RuntimeLongjmpException ||
                    (tableDispatchError instanceof com.dylibso.chicory.wasm.ChicoryException chicory &&
                        chicory.getMessage() != null &&
                        chicory.getMessage().contains("uninitialized element"))
                ) {
                    if (mod.hasExport("setThrew")) {
                        try {
                            mod.instance.export("setThrew").apply(1, 0);
                        } catch (RuntimeException ignored) {
                        }
                    }
                    if (
                        "invoke_v".equals(name) ||
                        "invoke_vi".equals(name) ||
                        "invoke_vii".equals(name)
                    ) {
                        return new long[] {};
                    }
                    return new long[] { 0L };
                }
                if (TRACE_INVOKE) {
                    appendTrace(
                        "[pglite-invoke] dispatch-failed " + name +
                        " tableIndex=" + tableIndex +
                        " err=" + tableDispatchError.getClass().getSimpleName() +
                        ": " + tableDispatchError.getMessage()
                    );
                }
                throw tableDispatchError;
            }
        } finally {
            if (tableIndex != Integer.MIN_VALUE) {
                var reentryCounts = INVOKE_REENTRY_COUNTS.get();
                var current = reentryCounts.getOrDefault(tableIndex, 0);
                if (current <= 1) {
                    reentryCounts.remove(tableIndex);
                } else {
                    reentryCounts.put(tableIndex, current - 1);
                }
            }
            var nextDepth = Math.max(0, depth - 1);
            if (nextDepth == 0) {
                INVOKE_DEPTH.remove();
                INVOKE_REENTRY_COUNTS.remove();
            } else {
                INVOKE_DEPTH.set(nextDepth);
            }
        }
    }

    private static FunctionImport resolveImportedFunction(Instance owner, int functionIndex) {
        if (owner == null || functionIndex < 0) {
            return null;
        }
        if (owner.function(functionIndex) != null) {
            return null;
        }
        var importSection = owner.module().importSection();
        var importedFunctionIndex = 0;
        for (var i = 0; i < importSection.importCount(); i++) {
            var importDecl = importSection.getImport(i);
            if (importDecl.importType() != ExternalType.FUNCTION) {
                continue;
            }
            if (importedFunctionIndex == functionIndex) {
                if (importDecl instanceof FunctionImport functionImport) {
                    return functionImport;
                }
                return null;
            }
            importedFunctionIndex++;
        }
        return null;
    }

    private static final class RuntimePostgresMod implements postgresMod.PostgresMod {
        private static final FunctionType CALLBACK_SIG_III = FunctionType.of(
            new ValType[] { ValType.I32, ValType.I32 },
            new ValType[] { ValType.I32 }
        );
        private static final WasmModule CALLBACK_STUB_III_MODULE = Parser.parse(
            buildCallbackStubWasm("iii")
        );
        private final Instance instance;
        private final WasiPreview1 wasi;
        private final String wasmPrefix;
        private final Integer initialMemory;
        private Integer fdBufferMax = 1024 * 1024;
        private final Map<Integer, postgresMod.ReadWriteCallback> callbacks =
            new ConcurrentHashMap<>();
        private final Map<Integer, Instance> callbackFunctionInstances = new ConcurrentHashMap<>();
        private final Map<String, CompletableFuture<extensionUtils.ExtensionBlob>> extensions;
        private final EmscriptenRuntimeImpl runtime;

        private RuntimePostgresMod(
            Instance instance,
            postgresMod.PartialPostgresMod overrides,
            WasiPreview1 wasi
        ) {
            this.instance = instance;
            this.wasi = wasi;
            this.wasmPrefix = overrides != null && overrides.WASM_PREFIX != null
                ? overrides.WASM_PREFIX
                : "/tmp/pglite";
            this.initialMemory = overrides != null ? overrides.INITIAL_MEMORY : null;
            this.extensions = overrides != null && overrides.pg_extensions != null
                ? overrides.pg_extensions
                : Collections.emptyMap();
            if (overrides != null && overrides.FD_BUFFER_MAX != null) {
                this.fdBufferMax = overrides.FD_BUFFER_MAX;
            }
            this.runtime = new EmscriptenRuntimeImpl(this, overrides);
        }

        private postgresMod.ReadWriteCallback callback(int id) {
            return this.callbacks.get(id);
        }

        private String callbackKeysSnapshot() {
            if (this.callbacks.isEmpty()) {
                return "[]";
            }
            var keys = new ArrayList<Integer>();
            for (var key : this.callbacks.keySet()) {
                keys.add(key);
                if (keys.size() >= 8) {
                    break;
                }
            }
            return keys.toString();
        }

        private static byte[] buildCallbackStubWasm(String signature) {
            if (!"iii".equals(signature)) {
                throw new RuntimeBridgeException(
                    "buildCallbackStubWasm",
                    "unsupported callback signature: " + signature
                );
            }
            try {
                var out = new ByteArrayOutputStream();
                out.write(0x00);
                out.write(0x61);
                out.write(0x73);
                out.write(0x6d);
                out.write(0x01);
                out.write(0x00);
                out.write(0x00);
                out.write(0x00);

                var typePayload = new ByteArrayOutputStream();
                writeUleb(typePayload, 1);
                typePayload.write(0x60);
                writeUleb(typePayload, 2);
                typePayload.write(0x7f);
                typePayload.write(0x7f);
                writeUleb(typePayload, 1);
                typePayload.write(0x7f);
                writeSection(out, 1, typePayload.toByteArray());

                var importPayload = new ByteArrayOutputStream();
                writeUleb(importPayload, 1);
                writeName(importPayload, "env");
                writeName(importPayload, "callback");
                importPayload.write(0x00);
                writeUleb(importPayload, 0);
                writeSection(out, 2, importPayload.toByteArray());

                var exportPayload = new ByteArrayOutputStream();
                writeUleb(exportPayload, 1);
                writeName(exportPayload, "f");
                exportPayload.write(0x00);
                writeUleb(exportPayload, 0);
                writeSection(out, 7, exportPayload.toByteArray());

                return out.toByteArray();
            } catch (Exception e) {
                throw new RuntimeBridgeException(
                    "buildCallbackStubWasm",
                    "failed to build callback stub: " + e.getMessage()
                );
            }
        }

        private static void writeSection(ByteArrayOutputStream out, int id, byte[] payload) {
            out.write(id);
            writeUleb(out, payload.length);
            out.write(payload, 0, payload.length);
        }

        private static void writeName(ByteArrayOutputStream out, String value) {
            var bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            writeUleb(out, bytes.length);
            out.write(bytes, 0, bytes.length);
        }

        private static void writeUleb(ByteArrayOutputStream out, int value) {
            var v = value;
            do {
                var b = v & 0x7f;
                v >>>= 7;
                if (v != 0) {
                    b |= 0x80;
                }
                out.write(b);
            } while (v != 0);
        }

        private void initializeRuntime(String[] args) {
            if (hasExport("__wasm_apply_data_relocs")) {
                this.instance.export("__wasm_apply_data_relocs").apply();
            }
            if (hasExport("__wasm_call_ctors")) {
                this.instance.export("__wasm_call_ctors").apply();
            }
            runMain(args);
        }

        private void runMain(String[] args) {
            if (!hasExport("__main_argc_argv") || !hasExport("malloc")) {
                return;
            }
            var argList = new ArrayList<String>();
            argList.add("./this.program");
            if (args != null && args.length > 0) {
                argList.addAll(Arrays.asList(args));
            }
            var argPtrs = new ArrayList<Integer>();
            try {
                var malloc = this.instance.export("malloc");
                for (var arg : argList) {
                    var bytes = arg.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    var ptr = (int) malloc.apply(bytes.length + 1)[0];
                    this.instance.memory().write(ptr, bytes, 0, bytes.length);
                    this.instance.memory().writeByte(ptr + bytes.length, (byte) 0);
                    argPtrs.add(ptr);
                }
                var argvPtr = (int) malloc.apply((argPtrs.size() + 1) * 4)[0];
                for (var i = 0; i < argPtrs.size(); i++) {
                    this.instance.memory().writeI32(argvPtr + (i * 4), argPtrs.get(i));
                }
                this.instance.memory().writeI32(argvPtr + (argPtrs.size() * 4), 0);
                this.instance.export("__main_argc_argv").apply(argPtrs.size(), argvPtr);
            } catch (ExitStatusException e) {
                if (e.code != 0) {
                    throw new RuntimeBridgeException(
                        "__main_argc_argv",
                        "main exited with status=" + e.code
                    );
                }
            } catch (RuntimeException e) {
                throw new RuntimeBridgeException("__main_argc_argv", e);
            }
        }

        private boolean hasExport(String name) {
            var section = this.instance.module().exportSection();
            for (var i = 0; i < section.exportCount(); i++) {
                if (name.equals(section.getExport(i).name())) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String WASM_PREFIX() {
            return this.wasmPrefix;
        }

        @Override
        public Integer INITIAL_MEMORY() {
            return this.initialMemory;
        }

        @Override
        public Integer FD_BUFFER_MAX() {
            return this.fdBufferMax;
        }

        @Override
        public void setFD_BUFFER_MAX(Integer value) {
            this.fdBufferMax = value;
        }

        @Override
        public Uint8Array HEAP8() {
            return new Uint8Array(
                this.instance.memory().readBytes(0, this.instance.memory().pages() * 65536)
            );
        }

        @Override
        public Uint8Array HEAPU8() {
            return HEAP8();
        }

        @Override
        public Map<String, CompletableFuture<extensionUtils.ExtensionBlob>> pg_extensions() {
            return this.extensions;
        }

        @Override
        public int _pgl_initdb() {
            try {
                var ret = this.instance.export("pgl_initdb").apply();
                return ret.length == 0 ? 0 : (int) ret[0];
            } catch (ExitStatusException e) {
                throw new RuntimeBridgeException(
                    "_pgl_initdb",
                    "process exited with code=" +
                    e.code +
                    ", execTrace=" +
                    recentExecTrace() +
                    ", stderr=" +
                    this.runtime.recentStderr() +
                    ", recentHostCalls=" +
                    this.runtime.recentHostCalls()
                );
            } catch (RuntimeException e) {
                throw new RuntimeBridgeException(
                    "_pgl_initdb",
                    "runtime error: " +
                    e.getClass().getSimpleName() +
                    ": " +
                    e.getMessage() +
                    ", stack=" +
                    shortStack(e) +
                    ", execTrace=" +
                    recentExecTrace() +
                    ", stderr=" +
                    this.runtime.recentStderr() +
                    ", recentHostCalls=" +
                    this.runtime.recentHostCalls()
                );
            }
        }

        @Override
        public void _pgl_backend() {
            try {
                this.instance.export("pgl_backend").apply();
            } catch (ExitStatusException e) {
                throw new RuntimeBridgeException(
                    "_pgl_backend",
                    "process exited with code=" +
                    e.code +
                    ", execTrace=" +
                    recentExecTrace() +
                    ", stderr=" +
                    this.runtime.recentStderr() +
                    ", recentHostCalls=" +
                    this.runtime.recentHostCalls()
                );
            } catch (RuntimeException e) {
                throw new RuntimeBridgeException(
                    "_pgl_backend",
                    "runtime error: " +
                    e.getClass().getSimpleName() +
                    ": " +
                    e.getMessage() +
                    ", stack=" +
                    shortStack(e) +
                    ", execTrace=" +
                    recentExecTrace() +
                    ", stderr=" +
                    this.runtime.recentStderr() +
                    ", recentHostCalls=" +
                    this.runtime.recentHostCalls()
                );
            }
        }

        @Override
        public void _pgl_shutdown() {
            RuntimeException shutdownError = null;
            try {
                this.instance.export("pgl_shutdown").apply();
            } catch (RuntimeException e) {
                shutdownError = e;
            } finally {
                try {
                    this.runtime.shutdown();
                } catch (RuntimeException e) {
                    if (shutdownError == null) {
                        shutdownError = e;
                    }
                }
                try {
                    this.wasi.close();
                } catch (RuntimeException e) {
                    if (shutdownError == null) {
                        shutdownError = e;
                    }
                }
            }
            if (shutdownError != null) {
                throw shutdownError;
            }
        }

        @Override
        public void _interactive_write(int msgLength) {
            this.instance.export("interactive_write").apply(msgLength);
        }

        @Override
        public void _interactive_one(int length, int peek) {
            this.instance.export("interactive_one").apply(length, peek);
        }

        @Override
        public void _set_read_write_cbs(int read_cb, int write_cb) {
            this.instance.export("set_read_write_cbs").apply(read_cb, write_cb);
        }

        @Override
        public int addFunction(postgresMod.ReadWriteCallback cb, String signature) {
            var normalizedSignature = signature;
            postgresMod.ReadWriteCallback callback;
            if ("vii".equals(signature)) {
                normalizedSignature = "iii";
                callback = (ptr, len) -> {
                    cb.apply(ptr, len);
                    return 0;
                };
            } else {
                callback = cb;
            }
            if (!"iii".equals(normalizedSignature)) {
                throw new RuntimeBridgeException(
                    "addFunction",
                    "unsupported callback signature: " + signature
                );
            }
            var callbackFn = callback;
            var callbackInstance = Instance.builder(CALLBACK_STUB_III_MODULE)
                .withImportValues(
                    ImportValues.builder()
                        .addFunction(
                            new HostFunction(
                                "env",
                                "callback",
                                CALLBACK_SIG_III,
                                (instance, args) -> {
                                    var ptr = args.length > 0 ? (int) args[0] : 0;
                                    var len = args.length > 1 ? (int) args[1] : 0;
                                    return new long[] { callbackFn.apply(ptr, len) };
                                }
                            )
                        )
                        .build()
                )
                .build();
            var tableSize = this.instance.table(0).size();
            var tableBase = (int) readLongProperty("pglite.table_base", 1L);
            var slot = this.runtime.ensureFunctionTableSlot(
                callbackInstance,
                0,
                Math.max(0, tableBase),
                tableSize
            );
            this.callbacks.put(slot, cb);
            this.callbackFunctionInstances.put(slot, callbackInstance);
            return slot;
        }

        @Override
        public void removeFunction(int f) {
            this.callbacks.remove(f);
            this.callbackFunctionInstances.remove(f);
            var table = this.instance.table(0);
            if (f >= 0 && f < table.size()) {
                table.setRef(f, -1, null);
            }
        }

        @Override
        public void copyFromHeap(int ptr, byte[] dest, int destOffset, int length) {
            var bytes = this.instance.memory().readBytes(ptr, length);
            System.arraycopy(bytes, 0, dest, destOffset, length);
        }

        @Override
        public void copyToHeap(int ptr, byte[] src, int srcOffset, int length) {
            this.instance.memory().write(ptr, src, srcOffset, length);
        }

        @Override
        public postgresMod.EmscriptenRuntime runtime() {
            return this.runtime;
        }

        private EmscriptenRuntimeImpl runtimeImpl() {
            return this.runtime;
        }

        private static String shortStack(Throwable error) {
            var frames = error.getStackTrace();
            if (frames == null || frames.length == 0) {
                return "[]";
            }
            var out = new StringBuilder("[");
            var max = Math.min(frames.length, 8);
            for (var i = 0; i < max; i++) {
                if (i > 0) {
                    out.append(" | ");
                }
                out.append(frames[i]);
            }
            if (frames.length > max) {
                out.append(" | ...");
            }
            out.append(']');
            return out.toString();
        }

        @Override
        public extensionUtils.EmscriptenFS FS() {
            return this.runtime.FS();
        }
    }

    private static final class EmscriptenRuntimeImpl implements postgresMod.EmscriptenRuntime {
        private final RuntimePostgresMod mod;
        private final Path rootDir;
        private final extensionUtils.EmscriptenFS fs;
        private final Set<String> runDependencies = ConcurrentHashMap.newKeySet();
        private final AtomicInteger pendingRuns = new AtomicInteger(0);
        private final RuntimeDeviceRegistry deviceRegistry = new RuntimeDeviceRegistry();
        private final java.util.List<java.util.function.Consumer<postgresMod.PostgresMod>> preRun;
        private final java.util.List<java.util.function.Consumer<postgresMod.PostgresMod>> postRun;
        private final java.util.List<String> environmentEntries;
        private final RuntimeTimerRegistry timerRegistry = new RuntimeTimerRegistry(this::recordHostNote);
        private final Map<Integer, SeekableByteChannel> fdTable = new ConcurrentHashMap<>();
        private final Map<Integer, Path> fdPathTable = new ConcurrentHashMap<>();
        private final Map<Integer, Integer> fdFlagsTable = new ConcurrentHashMap<>();
        private final Map<Integer, Integer> fdDeviceTable = new ConcurrentHashMap<>();
        private final Map<Integer, Integer> fdDevicePosition = new ConcurrentHashMap<>();
        private final Map<Integer, PipeState> pipeReadTable = new ConcurrentHashMap<>();
        private final Map<Integer, PipeState> pipeWriteTable = new ConcurrentHashMap<>();
        private final Map<Integer, Integer> socketTypeTable = new ConcurrentHashMap<>();
        private final Map<Integer, RuntimeSockState> socketStateTable = new ConcurrentHashMap<>();
        private final Map<String, Integer> socketBindings = new ConcurrentHashMap<>();
        private final Map<Integer, java.util.List<String>> dirEntries = new ConcurrentHashMap<>();
        private final Map<Integer, Integer> dirCursor = new ConcurrentHashMap<>();
        private final Map<Integer, MmapRegion> mmapRegions = new ConcurrentHashMap<>();
        private final Map<Integer, Integer> stdioAliases = new ConcurrentHashMap<>();
        private final Map<String, String> dnsNameToAddr = new ConcurrentHashMap<>();
        private final Map<String, String> dnsAddrToName = new ConcurrentHashMap<>();
        private final Map<String, DynamicLibrary> loadedLibsByName = new ConcurrentHashMap<>();
        private final Map<Integer, DynamicLibrary> loadedLibsByHandle = new ConcurrentHashMap<>();
        private final Map<String, Integer> mainFunctionTableSlots = new ConcurrentHashMap<>();
        private final AtomicInteger dnsAddressId = new AtomicInteger(1);
        private final String wasmPostgresVersion;
        private final RuntimeDlErrorState dlErrorState = new RuntimeDlErrorState();
        private final RuntimeAsmConstRegistry asmConstRegistry = RuntimeAsmConstRegistry.create();
        private final RuntimeFsNodeIdIndex fsNodeIdIndex = new RuntimeFsNodeIdIndex();
        private final java.security.SecureRandom secureRandom = new java.security.SecureRandom();
        private String cwd = "/";
        private long tempRet0 = 0L;
        private boolean asmConstPostMessageInstalled = false;
        private boolean asmConstCustomMessageHandlerInstalled = false;
        private volatile boolean preloaded = false;
        private final StringBuilder stderrBuffer = new StringBuilder();
        private final StringBuilder stdoutBuffer = new StringBuilder();
        private final boolean traceHostCalls = Boolean.getBoolean("pglite.trace_host_calls");
        private final boolean traceWasmStdio = Boolean.getBoolean("pglite.trace_wasm_stdio");
        private final java.util.concurrent.ConcurrentLinkedDeque<String> recentHostCalls =
            new java.util.concurrent.ConcurrentLinkedDeque<>();
        private static final int AT_FDCWD = -100;
        private static final int AT_SYMLINK_NOFOLLOW = 0x100;
        private static final int AT_EACCESS = 0x200;
        private static final int AT_REMOVEDIR = 0x200;
        private static final int AT_EMPTY_PATH = 0x1000;
        private static final int EACCES = 2;
        private static final int EAGAIN = 6;
        private static final int EBADF = 8;
        private static final int EEXIST = 20;
        private static final int EFAULT = 21;
        private static final int EINVAL = 28;
        private static final int EIO = 29;
        private static final int EISDIR = 31;
        private static final int EDESTADDRREQ = 17;
        private static final int ENODEV = 43;
        private static final int ENOENT = 44;
        private static final int ENOMEM = 48;
        private static final int EAFNOSUPPORT = 5;
        private static final int EOVERFLOW = 61;
        private static final int ENOTCONN = 53;
        private static final int ENOTDIR = 54;
        private static final int ENOTTY = 59;
        private static final int ENOSYS = 52;
        private static final int EPROTONOSUPPORT = 66;
        private static final int ERANGE = 68;
        private static final int ESPIPE = 70;
        private static final int EMFILE = 33;
        private static final int AF_INET = 2;
        private static final int AF_INET6 = 10;
        private static final int SOCK_STREAM = 1;
        private static final int SOCK_DGRAM = 2;
        private static final int MAX_OPEN_FDS = 4096;
        private static final long INT53_MAX = 9_007_199_254_740_992L;
        private static final long INT53_MIN = -9_007_199_254_740_992L;

        private EmscriptenRuntimeImpl(
            RuntimePostgresMod mod,
            postgresMod.PartialPostgresMod overrides
        ) {
            this.mod = mod;
            try {
                this.rootDir = Files.createTempDirectory("pglite-runtime-");
            } catch (Exception e) {
                throw new RuntimeBridgeException("EmscriptenRuntimeImpl", e);
            }
            this.fs = new EmscriptenFsImpl(this.rootDir, this.deviceRegistry);
            this.preRun = overrides != null && overrides.preRun != null
                ? new ArrayList<>(overrides.preRun)
                : new ArrayList<>();
            this.postRun = overrides != null && overrides.postRun != null
                ? new ArrayList<>(overrides.postRun)
                : new ArrayList<>();
            this.environmentEntries = buildEnvironEntries(overrides);
            this.wasmPostgresVersion = detectWasmPostgresVersion();
            this.stdioAliases.put(0, 0);
            this.stdioAliases.put(1, 1);
            this.stdioAliases.put(2, 2);
            bootstrapStandardTree();
            registerBuiltinDevices();
            registerMainLibrary();
        }

        @Override
        public extensionUtils.EmscriptenFS FS() {
            return this.fs;
        }

        private void shutdown() {
            this.timerRegistry.clearAll();
            closeAllOpenChannels();
            this.fdTable.clear();
            this.fdPathTable.clear();
            this.fdFlagsTable.clear();
            this.fdDeviceTable.clear();
            this.fdDevicePosition.clear();
            this.pipeReadTable.clear();
            this.pipeWriteTable.clear();
            this.socketTypeTable.clear();
            this.socketStateTable.clear();
            this.socketBindings.clear();
            this.dirEntries.clear();
            this.dirCursor.clear();
            this.mmapRegions.clear();
            this.runDependencies.clear();
        }

        private void closeAllOpenChannels() {
            var closed = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<SeekableByteChannel, Boolean>()
            );
            for (var channel : this.fdTable.values()) {
                if (channel == null || !closed.add(channel)) {
                    continue;
                }
                try {
                    channel.close();
                } catch (Exception ignored) {
                    // Best effort cleanup for shutdown.
                }
            }
        }

        @Override
        public byte[] getPreloadedPackage(String name, int size) {
            if (!"pglite.data".equals(name)) {
                throw new RuntimeBridgeException(
                    "getPreloadedPackage",
                    "Unknown package: " + name
                );
            }
            var data = utils.readFile(name);
            if (size > 0 && data.length != size) {
                throw new RuntimeBridgeException(
                    "getPreloadedPackage",
                    "Invalid package size: " + data.length + " != " + size
                );
            }
            return data;
        }

        @Override
        public void addRunDependency(String key) {
            if (key == null || key.isBlank()) {
                throw new RuntimeBridgeException("addRunDependency", "dependency key is empty");
            }
            if (this.runDependencies.add(key)) {
                this.pendingRuns.incrementAndGet();
            }
        }

        @Override
        public void removeRunDependency(String key) {
            if (this.runDependencies.remove(key)) {
                this.pendingRuns.updateAndGet(current -> Math.max(0, current - 1));
            }
        }

        @Override
        public void preRun() {
            if (!this.preloaded) {
                preloadPgliteData();
                this.preloaded = true;
            }
            for (var fn : this.preRun) {
                fn.accept(this.mod);
            }
            ensureRunDependenciesSettled("preRun");
        }

        @Override
        public void postRun() {
            for (var fn : this.postRun) {
                fn.accept(this.mod);
            }
            ensureRunDependenciesSettled("postRun");
        }

        private void ensureRunDependenciesSettled(String phase) {
            if (this.pendingRuns.get() == 0 && this.runDependencies.isEmpty()) {
                return;
            }
            throw new RuntimeBridgeException(
                phase,
                "pending run dependencies: count=" +
                this.pendingRuns.get() +
                " keys=" +
                this.runDependencies
            );
        }

        @Override
        public int makedev(int major, int minor) {
            return (major << 8) | (minor & 0xFF);
        }

        @Override
        public void registerDevice(int devId, postgresMod.DeviceOps ops) {
            this.deviceRegistry.registerDevice(devId, ops, "runtime.registerDevice");
        }

        @Override
        public void mkdev(String path, int devId) {
            this.deviceRegistry.mkdev(path, devId, "runtime.mkdev");
            this.fs.writeFile(normalize(path), new byte[0]);
        }

        private void registerBuiltinDevices() {
            var randomOps = new postgresMod.DeviceOps() {
                @Override
                public int read(byte[] buffer, int offset, int length, int position) {
                    if (length <= 0) {
                        return 0;
                    }
                    var out = new byte[length];
                    secureRandom.nextBytes(out);
                    System.arraycopy(out, 0, buffer, offset, length);
                    return length;
                }

                @Override
                public int write(byte[] buffer, int offset, int length, int position) {
                    return Math.max(0, length);
                }

                @Override
                public int llseek(int offset, int whence, int position) {
                    return 0;
                }
            };
            var randomDev = makedev(1, 8);
            registerDevice(randomDev, randomOps);
            mkdev("/dev/random", randomDev);
            var urandomDev = makedev(1, 9);
            registerDevice(urandomDev, randomOps);
            mkdev("/dev/urandom", urandomDev);

            var nullDev = makedev(1, 3);
            registerDevice(
                nullDev,
                new postgresMod.DeviceOps() {
                    @Override
                    public int read(byte[] buffer, int offset, int length, int position) {
                        return 0;
                    }

                    @Override
                    public int write(byte[] buffer, int offset, int length, int position) {
                        return Math.max(0, length);
                    }

                    @Override
                    public int llseek(int offset, int whence, int position) {
                        return 0;
                    }
                }
            );
            mkdev("/dev/null", nullDev);
        }

        private postgresMod.DeviceOps resolveDeviceOps(int fd) {
            var devId = this.fdDeviceTable.get(fd);
            if (devId == null) {
                return null;
            }
            return this.deviceRegistry.runtimeDeviceOps(devId);
        }

        private int readDevice(int fd, byte[] buffer, int offset, int length)
            throws ErrnoException {
            var ops = resolveDeviceOps(fd);
            if (ops == null) {
                throw new ErrnoException(EBADF);
            }
            var position = this.fdDevicePosition.getOrDefault(fd, 0);
            var read = ops.read(buffer, offset, length, position);
            if (read < 0) {
                throw new ErrnoException(EIO);
            }
            this.fdDevicePosition.put(fd, position + read);
            return read;
        }

        private int writeDevice(int fd, byte[] buffer, int offset, int length)
            throws ErrnoException {
            var ops = resolveDeviceOps(fd);
            if (ops == null) {
                throw new ErrnoException(EBADF);
            }
            var position = this.fdDevicePosition.getOrDefault(fd, 0);
            var written = ops.write(buffer, offset, length, position);
            if (written < 0) {
                throw new ErrnoException(EIO);
            }
            this.fdDevicePosition.put(fd, position + written);
            return written;
        }

        private int llseekDevice(int fd, long offset, int whence) throws ErrnoException {
            if (offset < Integer.MIN_VALUE || offset > Integer.MAX_VALUE) {
                throw new ErrnoException(EINVAL);
            }
            var ops = resolveDeviceOps(fd);
            if (ops == null) {
                throw new ErrnoException(EBADF);
            }
            var position = this.fdDevicePosition.getOrDefault(fd, 0);
            var next = ops.llseek((int) offset, whence, position);
            if (next < 0) {
                throw new ErrnoException(EINVAL);
            }
            this.fdDevicePosition.put(fd, next);
            return next;
        }

        private void recordHostCall(String name, long[] args, long ret) {
            if (!this.traceHostCalls) {
                return;
            }
            if (
                "wasi.fd_write".equals(name) ||
                "__syscall_write".equals(name) ||
                "emscripten_date_now".equals(name) ||
                "_emscripten_memcpy_js".equals(name) ||
                "_emscripten_throw_longjmp".equals(name)
            ) {
                return;
            }
            var line = name + " args=" + formatArgs(args) + " ret=" + ret;
            this.recentHostCalls.addLast(line);
            while (this.recentHostCalls.size() > 5000) {
                this.recentHostCalls.pollFirst();
            }
            appendHostTraceFile(line);
        }

        private void recordHostNote(String note) {
            if (!this.traceHostCalls) {
                return;
            }
            if (note.startsWith("wasi.fd_write")) {
                return;
            }
            this.recentHostCalls.addLast(note);
            while (this.recentHostCalls.size() > 5000) {
                this.recentHostCalls.pollFirst();
            }
            appendHostTraceFile(note);
        }

        private void appendHostTraceFile(String line) {
            try {
                Files.writeString(
                    Path.of("tmp/pglite-host.log"),
                    line + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
                );
            } catch (Exception ignored) {
                // Best effort diagnostics.
            }
        }

        private static String formatArgs(long[] args) {
            if (args == null || args.length == 0) {
                return "[]";
            }
            var out = new StringBuilder("[");
            for (var i = 0; i < args.length; i++) {
                if (i > 0) {
                    out.append('|');
                }
                out.append(args[i]);
            }
            out.append(']');
            return out.toString();
        }

        private String recentHostCalls() {
            if (!this.traceHostCalls || this.recentHostCalls.isEmpty()) {
                return "[]";
            }
            return this.recentHostCalls.toString();
        }

        private String recentStderr() {
            return trimCapture(this.stderrBuffer);
        }

        private void appendStdout(byte[] bytes) {
            appendCapture(this.stdoutBuffer, bytes);
            if (this.traceWasmStdio) {
                System.out.print(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            }
        }

        private void appendStderr(byte[] bytes) {
            appendCapture(this.stderrBuffer, bytes);
            if (this.traceWasmStdio) {
                System.err.print(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            }
        }

        private static void appendCapture(StringBuilder builder, byte[] bytes) {
            if (bytes.length == 0) {
                return;
            }
            builder.append(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            if (builder.length() > 16_384) {
                builder.delete(0, builder.length() - 16_384);
            }
        }

        private static String trimCapture(StringBuilder builder) {
            if (builder.length() == 0) {
                return "<empty>";
            }
            var text = builder.toString().trim();
            return text.isEmpty() ? "<empty>" : text;
        }

        private static long err(int errno) {
            return -errno;
        }

        private void emscriptenMemcpyJs(long[] args) {
            if (args.length < 3) {
                return;
            }
            var dest = args[0];
            var src = args[1];
            var len = args[2];
            if (len <= 0L) {
                return;
            }
            var mem = this.mod.instance.memory();
            var heapSize = mem.pages() * 65_536L;
            var clampedSrc = Math.max(0L, Math.min(src, heapSize));
            var clampedDest = Math.max(0L, Math.min(dest, heapSize));
            var clampedEnd = Math.max(0L, Math.min(src + len, heapSize));
            var copyLen = clampedEnd - clampedSrc;
            if (copyLen <= 0L || clampedDest >= heapSize) {
                return;
            }
            copyLen = Math.min(copyLen, heapSize - clampedDest);
            if (copyLen <= 0L) {
                return;
            }
            // Emscripten uses HEAPU8.copyWithin, which clamps out-of-range indexes.
            var bytes = mem.readBytes((int) clampedSrc, (int) copyLen);
            mem.write((int) clampedDest, bytes, 0, (int) copyLen);
        }

        private void callSigHandler(long[] args) {
            if (args.length < 2) {
                throw new RuntimeBridgeException(
                    "__call_sighandler",
                    "expected (fp, sig), got " + Arrays.toString(args)
                );
            }
            var fp = (int) args[0];
            var sig = (int) args[1];
            var table = this.mod.instance.table(0);
            if (fp < 0 || fp >= table.size()) {
                throw new RuntimeBridgeException(
                    "__call_sighandler",
                    "table index out of range: " + fp + " size=" + table.size()
                );
            }
            var functionIndex = table.ref(fp);
            var owner = table.instance(fp);
            if (functionIndex < 0 || owner == null) {
                throw new RuntimeBridgeException(
                    "__call_sighandler",
                    "empty table slot: " + fp
                );
            }
            try {
                owner.getMachine().call(functionIndex, new long[] { sig });
            } catch (RuntimeException e) {
                throw new RuntimeBridgeException(
                    "__call_sighandler",
                    new RuntimeException("dispatch failed: fp=" + fp + " sig=" + sig, e)
                );
            }
        }

        private void setTempRet0(long[] args) {
            this.tempRet0 = args.length > 0 ? (int) args[0] : 0L;
        }

        private long getTempRet0(long[] args) {
            return this.tempRet0;
        }

        private void applyFdBufferMaxAsmConst(java.util.List<Object> args) {
            if (args.isEmpty() || !(args.get(0) instanceof Number number)) {
                return;
            }
            var value = number.intValue();
            if (value > 0) {
                this.mod.setFD_BUFFER_MAX(value);
            }
        }

        private void markPostMessageAsmConst(java.util.List<Object> args) {
            this.asmConstPostMessageInstalled = true;
        }

        private void markCustomMessageHandlerAsmConst(java.util.List<Object> args) {
            this.asmConstCustomMessageHandlerInstalled = true;
        }

        private void setDlError(String context, String detail) {
            var message = context + ": " + detail;
            this.dlErrorState.set(message);
            writeDlErrorToWasm(message);
        }

        private String lastDlError() {
            return this.dlErrorState.get();
        }

        private void clearDlError() {
            this.dlErrorState.clear();
            writeDlErrorToWasm("");
        }

        private void writeDlErrorToWasm(String message) {
            if (!this.mod.hasExport("__dl_seterr")) {
                return;
            }
            var ptr = 0;
            try {
                if (message != null && !message.isEmpty()) {
                    var payload = (message + "\0").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    var mallocRet = this.mod.instance.export("malloc").apply(payload.length);
                    if (mallocRet.length == 0 || mallocRet[0] == 0L) {
                        recordHostNote("__dl_seterr skipped: malloc failed");
                        return;
                    }
                    ptr = (int) mallocRet[0];
                    this.mod.instance.memory().write(ptr, payload, 0, payload.length);
                }
                this.mod.instance.export("__dl_seterr").apply(ptr, 0);
            } catch (RuntimeException e) {
                recordHostNote("__dl_seterr failed: " + e.getMessage());
            } finally {
                if (ptr != 0 && this.mod.hasExport("free")) {
                    try {
                        this.mod.instance.export("free").apply(ptr);
                    } catch (RuntimeException ignored) {
                    }
                }
            }
        }

        private long emscriptenAsmConstInt(long[] args) {
            var code = args.length > 0 ? args[0] : -1L;
            var sigPtr = args.length > 1 ? (int) args[1] : 0;
            var argBuf = args.length > 2 ? (int) args[2] : 0;
            var asmArgs = readEmAsmArgs(sigPtr, argBuf);
            var handled = this.asmConstRegistry.dispatch(this, code, asmArgs);
            if (handled) {
                return 0L;
            }
            var strictAsmConst = Boolean.getBoolean("pglite.strict_asm_const");
            if (strictAsmConst) {
                throw new RuntimeBridgeException(
                    "emscripten_asm_const_int",
                    "Unimplemented asm const code=" +
                    code +
                    " args=" +
                    Arrays.toString(args) +
                    " decodedArgs=" +
                    asmArgs
                );
            }
            if (Boolean.getBoolean("pglite.asm_const_compat")) {
                recordHostNote(
                    "emscripten_asm_const_int compat ignored code=" +
                    code +
                    " args=" +
                    Arrays.toString(args) +
                    " decodedArgs=" +
                    asmArgs
                );
                return 0L;
            }
            recordHostNote(
                "emscripten_asm_const_int unknown code=" +
                code +
                " args=" +
                Arrays.toString(args) +
                " decodedArgs=" +
                asmArgs
            );
            return err(EINVAL);
        }

        private java.util.List<Object> readEmAsmArgs(int sigPtr, int argBuf) {
            var out = new ArrayList<Object>();
            if (sigPtr == 0) {
                return out;
            }
            var mem = this.mod.instance.memory();
            var sig = sigPtr;
            var buf = argBuf;
            while (true) {
                var ch = mem.readBytes(sig, 1)[0] & 0xFF;
                sig++;
                if (ch == 0) {
                    break;
                }
                var wide = ch != 'i' && ch != 'p';
                if (wide && (buf % 8 != 0)) {
                    buf += 4;
                }
                var value = switch (ch) {
                    case 'p' -> Integer.toUnsignedLong((int) mem.readI32(buf));
                    case 'j' -> readI64(buf);
                    case 'i' -> (long) mem.readI32(buf);
                    default -> Double.doubleToRawLongBits(readF64(buf));
                };
                out.add(value);
                buf += wide ? 8 : 4;
            }
            return out;
        }

        private long readI64(int ptr) {
            var bytes = this.mod.instance.memory().readBytes(ptr, 8);
            return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
        }

        private double readF64(int ptr) {
            var bytes = this.mod.instance.memory().readBytes(ptr, 8);
            return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getDouble();
        }

        private long emscriptenResizeHeap(long[] args) {
            if (args.length < 1) {
                return 0L;
            }
            var requestedSize = Integer.toUnsignedLong((int) args[0]);
            var memory = this.mod.instance.memory();
            var currentBytes = (long) memory.pages() * Memory.PAGE_SIZE;
            if (requestedSize <= currentBytes) {
                return 1L;
            }

            var maxHeapSize = Math.min(
                2_147_483_648L,
                (long) memory.maximumPages() * Memory.PAGE_SIZE
            );
            if (requestedSize > maxHeapSize) {
                return 0L;
            }

            var alignedTarget = ((requestedSize + Memory.PAGE_SIZE - 1L) / Memory.PAGE_SIZE) *
                Memory.PAGE_SIZE;
            var targetPages = (int) (alignedTarget / Memory.PAGE_SIZE);
            var currentPages = memory.pages();
            if (targetPages <= currentPages) {
                return 1L;
            }
            var deltaPages = targetPages - currentPages;
            var grownFrom = memory.grow(deltaPages);
            return grownFrom >= 0 ? 1L : 0L;
        }

        private long emscriptenSystem(long[] args) {
            if (args.length < 1 || args[0] == 0L) {
                return 0L;
            }
            return err(ENOSYS);
        }

        private void registerMainLibrary() {
            var exports = new LinkedHashMap<String, Export>();
            var exportOrder = new ArrayList<String>();
            var section = this.mod.instance.module().exportSection();
            for (var i = 0; i < section.exportCount(); i++) {
                var export = section.getExport(i);
                exports.put(export.name(), export);
                exportOrder.add(export.name());
            }
            var main = new DynamicLibrary(
                "__main__",
                0,
                true,
                true,
                this.mod.instance,
                0,
                this.mod.instance.table(0).size(),
                exports,
                exportOrder
            );
            this.loadedLibsByName.put("__main__", main);
            this.loadedLibsByHandle.put(0, main);
        }

        private long dlopenJs(long[] args) {
            if (args.length < 1) {
                setDlError("_dlopen_js", "missing handle pointer");
                return 0L;
            }
            var handlePtr = (int) args[0];
            if (handlePtr == 0) {
                setDlError("_dlopen_js", "handle pointer is null");
                return 0L;
            }
            var filename = "";
            try {
                filename = readCString(handlePtr + 36);
                if (filename == null || filename.isBlank()) {
                    setDlError("_dlopen_js", "library path is empty");
                    return 0L;
                }
                filename = normalizeDlPathLikeJs(filename);
                var flags = this.mod.instance.memory().readInt(handlePtr + 4);
                loadDynamicLibrary(filename, handlePtr, flags);
                clearDlError();
                return 1L;
            } catch (RuntimeBridgeException e) {
                var normalized = filename != null ? filename : "";
                setDlError(
                    "_dlopen_js",
                    "Could not load dynamic lib: " + normalized + "\n" + e.getMessage()
                );
                recordHostNote("_dlopen_js failed: " + e.getMessage());
                return 0L;
            } catch (RuntimeException e) {
                if (isMemoryAccessFault(e)) {
                    setDlError("_dlopen_js", "handle pointer fault: " + e.getMessage());
                    recordHostNote("_dlopen_js failed: " + e.getMessage());
                    return 0L;
                }
                var normalized = filename != null ? filename : "";
                setDlError(
                    "_dlopen_js",
                    "Could not load dynamic lib: " +
                    normalized +
                    "\n" +
                    e.getClass().getSimpleName() +
                    ": " +
                    e.getMessage()
                );
                recordHostNote("_dlopen_js failed: " + e.getMessage());
                return 0L;
            } catch (Exception e) {
                var normalized = filename != null ? filename : "";
                setDlError(
                    "_dlopen_js",
                    "Could not load dynamic lib: " +
                    normalized +
                    "\n" +
                    e.getClass().getSimpleName() +
                    ": " +
                    e.getMessage()
                );
                recordHostNote("_dlopen_js failed: " + e.getMessage());
                return 0L;
            }
        }

        private String normalizeDlPathLikeJs(String path) {
            if (path == null || path.isEmpty()) {
                return path;
            }
            var normalized = java.nio.file.Paths.get(path).normalize().toString();
            if (path.startsWith("/") && !normalized.startsWith("/")) {
                normalized = "/" + normalized;
            }
            return normalized.replace('\\', '/');
        }

        private long dlsymJs(long[] args) {
            try {
                if (args.length < 3) {
                    setDlError("_dlsym_js", "invalid argument count: " + args.length);
                    return 0L;
                }
                var handlePtr = (int) args[0];
                String symbol;
                try {
                    symbol = readCString((int) args[1]);
                } catch (RuntimeException e) {
                    if (isMemoryAccessFault(e)) {
                        setDlError("_dlsym_js", "symbol pointer fault: " + e.getMessage());
                        return 0L;
                    }
                    throw e;
                }
                var symbolIndexPtr = (int) args[2];
                if (symbol == null || symbol.isEmpty()) {
                    setDlError("_dlsym_js", "symbol is empty");
                    return 0L;
                }

                var lib = handlePtr == 0
                    ? resolveDlsymDefaultLibrary(symbol)
                    : this.loadedLibsByHandle.get(handlePtr);
                if (lib == null && handlePtr != 0) {
                    setDlError("_dlsym_js", "library handle not found: " + handlePtr);
                    return 0L;
                }
                if (lib == null) {
                    setDlError("_dlsym_js", "main library not initialized");
                    return 0L;
                }

                var export = lib.exports.get(symbol);
                if (export == null) {
                    setDlError(
                        "_dlsym_js",
                        "Tried to lookup unknown symbol \"" +
                        symbol +
                        "\" in dynamic lib: " +
                        lib.name
                    );
                    recordHostNote("_dlsym_js unknown symbol \"" + symbol + "\" in " + lib.name);
                    return 0L;
                }

                if (export.exportType() == ExternalType.FUNCTION) {
                    var targetLib = lib;
                    var pointer = lib.functionPointers.get(symbol);
                    if (pointer == null) {
                        var resolved = ensureFunctionTableSlot(
                            targetLib.instance,
                            export.index(),
                            targetLib.tableBase,
                            targetLib.tableBase + Math.max(0, targetLib.tableSize)
                        );
                        var symbolIndex = lib.exportOrder.indexOf(symbol);
                        if (!writeDlsymSymbolIndex(symbolIndexPtr, symbolIndex)) {
                            return 0L;
                        }
                        var existing = lib.functionPointers.putIfAbsent(symbol, resolved);
                        pointer = existing != null ? existing : resolved;
                    }
                    clearDlError();
                    return pointer;
                }
                if (export.exportType() == ExternalType.GLOBAL) {
                    clearDlError();
                    return (int) lib.instance.global(export.index()).getValueLow();
                }
                setDlError(
                    "_dlsym_js",
                    "Tried to lookup unknown symbol \"" +
                    symbol +
                    "\" in dynamic lib: " +
                    lib.name
                );
                return 0L;
            } catch (RuntimeException e) {
                setDlError("_dlsym_js", e.getClass().getSimpleName() + ": " + e.getMessage());
                recordHostNote("_dlsym_js failed: " + e.getMessage());
                return 0L;
            }
        }

        private DynamicLibrary resolveDlsymDefaultLibrary(String symbol) {
            var main = this.loadedLibsByHandle.get(0);
            if (main != null && main.exports.containsKey(symbol)) {
                return main;
            }
            var visited = Collections.newSetFromMap(
                new IdentityHashMap<DynamicLibrary, Boolean>()
            );
            if (main != null) {
                visited.add(main);
            }
            for (var lib : this.loadedLibsByHandle.values()) {
                if (lib == null || visited.contains(lib)) {
                    continue;
                }
                visited.add(lib);
                if (!lib.global) {
                    continue;
                }
                if (lib.exports.containsKey(symbol)) {
                    return lib;
                }
            }
            return main;
        }

        private boolean writeDlsymSymbolIndex(int symbolIndexPtr, int symbolIndex) {
            if (symbolIndexPtr == 0) {
                return true;
            }
            try {
                this.mod.instance.memory().writeI32(symbolIndexPtr, symbolIndex);
                return true;
            } catch (RuntimeException e) {
                if (isMemoryAccessFault(e)) {
                    setDlError("_dlsym_js", "symbol index pointer fault: " + e.getMessage());
                    return false;
                }
                throw e;
            }
        }

        private static boolean isMemoryAccessFault(Throwable e) {
            var message = e.getMessage();
            if (message == null) {
                return false;
            }
            var lower = message.toLowerCase(java.util.Locale.ROOT);
            return lower.contains("out of bounds") ||
                lower.contains("outside memory") ||
                lower.contains("memory");
        }

        private DynamicLibrary loadDynamicLibrary(String rawName, int handlePtr, int flags)
            throws Exception {
            var normalizedName = normalizeResolvedPath(resolveLibraryPath(rawName));
            var existing = this.loadedLibsByName.get(normalizedName);
            if (existing != null) {
                if (handlePtr != 0) {
                    this.loadedLibsByHandle.put(handlePtr, existing);
                }
                return existing;
            }

            var binary = readDynamicLibraryBinary(normalizedName, handlePtr);
            var module = Parser.parse(binary);
            var metadata = parseDylinkMetadata(module);
            for (var needed : metadata.neededDynlibs) {
                loadDynamicLibrary(needed, 0, flags);
            }

            var table = this.mod.instance.table(0);
            var tableBase = table.size();
            if (metadata.tableSize > 0) {
                var grownFrom = table.grow(metadata.tableSize, -1, null);
                if (grownFrom < 0) {
                    throw new RuntimeBridgeException(
                        "loadDynamicLibrary",
                        "unable to grow table for " + normalizedName
                    );
                }
            }
            var memoryBase = allocateDynamicMemory(metadata.memorySize, metadata.memoryAlign);

            if (handlePtr != 0) {
                this.mod.instance.memory().writeByte(handlePtr + 8, (byte) 1);
                this.mod.instance.memory().writeI32(handlePtr + 12, memoryBase);
                this.mod.instance.memory().writeI32(handlePtr + 16, metadata.memorySize);
                this.mod.instance.memory().writeI32(handlePtr + 20, tableBase);
                this.mod.instance.memory().writeI32(handlePtr + 24, metadata.tableSize);
            }

            var imports = buildDynamicLibraryImports(module, memoryBase, tableBase);
            var instance = Instance.builder(module).withImportValues(imports).build();
            if (hasExport(instance, "__wasm_apply_data_relocs")) {
                instance.export("__wasm_apply_data_relocs").apply();
            }
            if (hasExport(instance, "__wasm_call_ctors")) {
                instance.export("__wasm_call_ctors").apply();
            }

            var exports = new LinkedHashMap<String, Export>();
            var exportOrder = new ArrayList<String>();
            var exportSection = module.exportSection();
            for (var i = 0; i < exportSection.exportCount(); i++) {
                var export = exportSection.getExport(i);
                exports.put(export.name(), export);
                exportOrder.add(export.name());
            }
            var lib = new DynamicLibrary(
                normalizedName,
                handlePtr,
                (flags & 0x100) != 0,
                (flags & 0x1000) != 0,
                instance,
                tableBase,
                metadata.tableSize,
                exports,
                exportOrder
            );
            this.loadedLibsByName.put(normalizedName, lib);
            if (handlePtr != 0) {
                this.loadedLibsByHandle.put(handlePtr, lib);
            }
            return lib;
        }

        private ImportValues buildDynamicLibraryImports(
            WasmModule module,
            int memoryBase,
            int tableBase
        ) {
            var builder = ImportValues.builder();
            var mainExportSection = this.mod.instance.module().exportSection();
            var importedStackPointer = findImportedGlobal("env", "__stack_pointer");

            for (var i = 0; i < module.importSection().importCount(); i++) {
                var importDecl = module.importSection().getImport(i);
                if (importDecl.importType() == ExternalType.FUNCTION) {
                    var fnImport = (FunctionImport) importDecl;
                    var fnType = module.typeSection().getType(fnImport.typeIndex());
                    var export = findMainExport(mainExportSection, importDecl.name(), ExternalType.FUNCTION);
                    if (export != null) {
                        builder.addFunction(
                            new HostFunction(
                                importDecl.module(),
                                importDecl.name(),
                                fnType,
                                (instance, args) -> this.mod.instance.getMachine().call(
                                    export.index(),
                                    args
                                )
                            )
                        );
                        continue;
                    }
                    if ("wasi_snapshot_preview1".equals(importDecl.module())) {
                        builder.addFunction(
                            new HostFunction(
                                importDecl.module(),
                                importDecl.name(),
                                fnType,
                                (instance, args) -> handleWasiFunction(
                                    this.mod,
                                    importDecl.name(),
                                    args
                                )
                            )
                        );
                        continue;
                    }
                    builder.addFunction(
                        new HostFunction(
                            importDecl.module(),
                            importDecl.name(),
                            fnType,
                            (instance, args) -> handleEnvFunction(
                                this.mod,
                                importDecl.name(),
                                args,
                                fnType.returns().size()
                            )
                        )
                    );
                    continue;
                }

                if (importDecl.importType() == ExternalType.GLOBAL) {
                    var globalImport = (GlobalImport) importDecl;
                    if ("env".equals(importDecl.module()) && "__stack_pointer".equals(importDecl.name())) {
                        if (importedStackPointer == null) {
                            throw new RuntimeBridgeException(
                                "buildDynamicLibraryImports",
                                "missing env.__stack_pointer"
                            );
                        }
                        builder.addGlobal(
                            new ImportGlobal(
                                importDecl.module(),
                                importDecl.name(),
                                importedStackPointer
                            )
                        );
                        continue;
                    }
                    var value = 0L;
                    if ("env".equals(importDecl.module()) && "__memory_base".equals(importDecl.name())) {
                        value = memoryBase;
                    } else if ("env".equals(importDecl.module()) && "__table_base".equals(importDecl.name())) {
                        value = tableBase;
                    } else if ("GOT.func".equals(importDecl.module())) {
                        value = resolveMainSymbolAddress(importDecl.name(), true);
                    } else if ("GOT.mem".equals(importDecl.module())) {
                        value = resolveMainSymbolAddress(importDecl.name(), false);
                    } else {
                        value = resolveMainSymbolAddress(importDecl.name(), false);
                    }
                    builder.addGlobal(
                        new ImportGlobal(
                            importDecl.module(),
                            importDecl.name(),
                            new GlobalInstance(
                                value,
                                0L,
                                globalImport.type(),
                                globalImport.mutabilityType()
                            )
                        )
                    );
                    continue;
                }

                if (importDecl.importType() == ExternalType.MEMORY) {
                    builder.addMemory(
                        new ImportMemory(
                            importDecl.module(),
                            importDecl.name(),
                            this.mod.instance.memory()
                        )
                    );
                    continue;
                }

                if (importDecl.importType() == ExternalType.TABLE) {
                    builder.addTable(
                        new ImportTable(
                            importDecl.module(),
                            importDecl.name(),
                            this.mod.instance.table(0)
                        )
                    );
                    continue;
                }
            }

            return builder.build();
        }

        private GlobalInstance findImportedGlobal(String module, String name) {
            for (var global : this.mod.instance.imports().globals()) {
                if (module.equals(global.module()) && name.equals(global.name())) {
                    return global.instance();
                }
            }
            return null;
        }

        private Export findMainExport(
            com.dylibso.chicory.wasm.types.ExportSection section,
            String name,
            ExternalType type
        ) {
            for (var i = 0; i < section.exportCount(); i++) {
                var export = section.getExport(i);
                if (type == export.exportType() && name.equals(export.name())) {
                    return export;
                }
            }
            return null;
        }

        private long resolveMainSymbolAddress(String symbolName, boolean functionOnly) {
            var section = this.mod.instance.module().exportSection();
            var fn = findMainExport(section, symbolName, ExternalType.FUNCTION);
            if (fn != null) {
                return this.mainFunctionTableSlots.computeIfAbsent(symbolName, ignored ->
                    ensureFunctionTableSlot(
                        this.mod.instance,
                        fn.index(),
                        0,
                        this.mod.instance.table(0).size()
                    )
                );
            }
            if (functionOnly) {
                return 0L;
            }
            var global = findMainExport(section, symbolName, ExternalType.GLOBAL);
            if (global != null) {
                return this.mod.instance.global(global.index()).getValueLow();
            }
            return 0L;
        }

        private int ensureFunctionTableSlot(Instance owner, int functionIndex, int start, int end) {
            var table = this.mod.instance.table(0);
            var lower = Math.max(0, start);
            var upper = Math.min(table.size(), Math.max(lower, end));
            for (var i = lower; i < upper; i++) {
                if (table.ref(i) == functionIndex && table.instance(i) == owner) {
                    return i;
                }
            }
            for (var i = 0; i < table.size(); i++) {
                if (table.ref(i) == functionIndex && table.instance(i) == owner) {
                    return i;
                }
            }
            for (var i = lower; i < table.size(); i++) {
                if (isEmptyTableSlot(table, i)) {
                    table.setRef(i, functionIndex, owner);
                    return i;
                }
            }
            if (table.size() <= lower) {
                var missing = (lower - table.size()) + 1;
                var grownFrom = table.grow(missing, -1, null);
                if (grownFrom < 0) {
                    throw new RuntimeBridgeException(
                        "ensureFunctionTableSlot",
                        "unable to grow table for function " + functionIndex
                    );
                }
                table.setRef(lower, functionIndex, owner);
                return lower;
            }
            var grownFrom = table.grow(1, -1, null);
            if (grownFrom < 0) {
                throw new RuntimeBridgeException(
                    "ensureFunctionTableSlot",
                    "unable to grow table for function " + functionIndex
                );
            }
            table.setRef(grownFrom, functionIndex, owner);
            return grownFrom;
        }

        private static boolean isEmptyTableSlot(TableInstance table, int slot) {
            return table.ref(slot) < 0 || table.instance(slot) == null;
        }

        private static boolean hasExport(Instance instance, String name) {
            var section = instance.module().exportSection();
            for (var i = 0; i < section.exportCount(); i++) {
                if (name.equals(section.getExport(i).name())) {
                    return true;
                }
            }
            return false;
        }

        private int allocateDynamicMemory(int size, int alignLog2) {
            if (size <= 0) {
                return 0;
            }
            var align = 1;
            if (alignLog2 > 0) {
                align = 1 << Math.min(alignLog2, 24);
            }
            var allocSize = size + align;
            int base;
            if (this.mod.hasExport("calloc")) {
                var ret = this.mod.instance.export("calloc").apply(allocSize, 1);
                if (ret.length == 0 || ret[0] == 0L) {
                    throw new RuntimeBridgeException(
                        "allocateDynamicMemory",
                        "calloc failed for " + allocSize
                    );
                }
                base = (int) ret[0];
            } else {
                var ret = this.mod.instance.export("malloc").apply(allocSize);
                if (ret.length == 0 || ret[0] == 0L) {
                    throw new RuntimeBridgeException(
                        "allocateDynamicMemory",
                        "malloc failed for " + allocSize
                    );
                }
                base = (int) ret[0];
                this.mod.instance.memory().fill((byte) 0, base, base + allocSize);
            }
            return (base + align - 1) & -align;
        }

        private byte[] readDynamicLibraryBinary(String normalizedName, int handlePtr)
            throws Exception {
            if (handlePtr != 0) {
                var dataPtr = this.mod.instance.memory().readInt(handlePtr + 28);
                var dataSize = this.mod.instance.memory().readInt(handlePtr + 32);
                if (dataPtr != 0 && dataSize > 0) {
                    return sanitizeWasmBinary(
                        this.mod.instance.memory().readBytes(dataPtr, dataSize),
                        normalizedName
                    );
                }
            }
            var realPath = resolve(normalizedName);
            if (!Files.exists(realPath)) {
                throw new RuntimeBridgeException(
                    "readDynamicLibraryBinary",
                    "library not found: " + normalizedName
                );
            }
            return sanitizeWasmBinary(Files.readAllBytes(realPath), normalizedName);
        }

        private static byte[] sanitizeWasmBinary(byte[] binary, String libName) {
            if (binary.length >= 4 && binary[0] == 0 && binary[1] == 0x61 && binary[2] == 0x73 && binary[3] == 0x6d) {
                return binary;
            }
            for (var i = 1; i + 3 < binary.length; i++) {
                if (
                    binary[i] == 0 &&
                    binary[i + 1] == 0x61 &&
                    binary[i + 2] == 0x73 &&
                    binary[i + 3] == 0x6d
                ) {
                    return Arrays.copyOfRange(binary, i, binary.length);
                }
            }
            throw new RuntimeBridgeException(
                "sanitizeWasmBinary",
                "invalid wasm binary for " + libName
            );
        }

        private String resolveLibraryPath(String libName) {
            if (libName == null || libName.isBlank()) {
                return "";
            }
            var path = libName.trim();
            if (path.startsWith("$libdir/")) {
                path = this.mod.WASM_PREFIX() + "/lib/postgresql/" + path.substring("$libdir/".length());
            } else if ("$libdir".equals(path)) {
                path = this.mod.WASM_PREFIX() + "/lib/postgresql";
            } else if (!path.startsWith("/")) {
                path = this.mod.WASM_PREFIX() + "/lib/postgresql/" + path;
            }
            if (!path.endsWith(".so")) {
                path = path + ".so";
            }
            return path;
        }

        private static DylinkMetadata parseDylinkMetadata(WasmModule module) {
            var dylink = module.customSection("dylink.0");
            if (dylink instanceof UnknownCustomSection) {
                return parseDylinkSection("dylink.0", ((UnknownCustomSection) dylink).bytes());
            }
            dylink = module.customSection("dylink");
            if (dylink instanceof UnknownCustomSection) {
                return parseDylinkSection("dylink", ((UnknownCustomSection) dylink).bytes());
            }
            return new DylinkMetadata();
        }

        private static DylinkMetadata parseDylinkSection(String name, byte[] bytes) {
            var metadata = new DylinkMetadata();
            var reader = new UlebReader(bytes);
            if ("dylink".equals(name)) {
                metadata.memorySize = reader.readUleb();
                metadata.memoryAlign = reader.readUleb();
                metadata.tableSize = reader.readUleb();
                metadata.tableAlign = reader.readUleb();
                var count = reader.readUleb();
                for (var i = 0; i < count; i++) {
                    metadata.neededDynlibs.add(reader.readString());
                }
                return metadata;
            }
            while (reader.hasRemaining()) {
                var subsectionType = reader.readU8();
                var subsectionSize = reader.readUleb();
                var end = reader.position() + subsectionSize;
                if (subsectionType == 1) {
                    metadata.memorySize = reader.readUleb();
                    metadata.memoryAlign = reader.readUleb();
                    metadata.tableSize = reader.readUleb();
                    metadata.tableAlign = reader.readUleb();
                } else if (subsectionType == 2) {
                    var count = reader.readUleb();
                    for (var i = 0; i < count; i++) {
                        metadata.neededDynlibs.add(reader.readString());
                    }
                }
                reader.seek(end);
            }
            return metadata;
        }

        private void emscriptenRuntimeKeepaliveClear(long[] args) {
            this.timerRegistry.clearAll();
        }

        private int activeTimerCount() {
            return this.timerRegistry.activeTimerCount();
        }

        private int keepaliveCount() {
            return this.timerRegistry.keepaliveCount();
        }

        private long setitimerJs(long[] args) {
            if (args.length < 2) {
                return 0L;
            }
            var which = (int) args[0];
            var timeoutMs = decodeTimeoutMillis(args[1]);
            this.timerRegistry.setTimeout(which, timeoutMs, () -> fireEmscriptenTimeout(which));
            return 0L;
        }

        private static long decodeTimeoutMillis(long rawValue) {
            var asDouble = Double.longBitsToDouble(rawValue);
            if (Double.isFinite(asDouble)) {
                var rounded = (long) Math.ceil(asDouble);
                if (rounded == 0L && rawValue > 0L && rawValue <= Integer.MAX_VALUE) {
                    return rawValue;
                }
                return Math.max(0L, rounded);
            }
            return Math.max(0L, rawValue);
        }

        private void fireEmscriptenTimeout(int which) {
            if (!this.mod.hasExport("__emscripten_timeout")) {
                return;
            }
            try {
                var nowMs = System.nanoTime() / 1_000_000.0;
                this.mod.instance.export("__emscripten_timeout").apply(
                    which,
                    Double.doubleToRawLongBits(nowMs)
                );
            } catch (RuntimeLongjmpException longjmp) {
                recordHostNote("__emscripten_timeout raised longjmp for timer " + which);
            } catch (RuntimeException e) {
                recordHostNote("__emscripten_timeout failed: " + e.getMessage());
            }
        }

        private long emscriptenThrowLongjmp(long[] args) {
            if (this.mod.hasExport("setThrew")) {
                try {
                    this.mod.instance.export("setThrew").apply(1, 0);
                } catch (RuntimeException ignored) {
                }
            }
            throw new RuntimeLongjmpException(
                "_emscripten_throw_longjmp",
                "requested longjmp unwind"
            );
        }

        private void gmtimeJs(long[] args) {
            if (args.length < 2) {
                return;
            }
            var epochSeconds = args[0];
            var tmPtr = (int) args[1];
            var dateTime = java.time.Instant.ofEpochSecond(epochSeconds)
                .atZone(java.time.ZoneOffset.UTC);
            writeTmCommon(dateTime, tmPtr);
        }

        private void localtimeJs(long[] args) {
            if (args.length < 2) {
                return;
            }
            var epochSeconds = args[0];
            var tmPtr = (int) args[1];
            var zone = java.time.ZoneId.systemDefault();
            var dateTime = java.time.Instant.ofEpochSecond(epochSeconds).atZone(zone);
            writeTmCommon(dateTime, tmPtr);

            var currentOffsetMinutes = -dateTime.getOffset().getTotalSeconds() / 60;
            var year = dateTime.getYear();
            var winterOffset = timezoneOffsetMinutes(zone, year, 1, 1);
            var summerOffset = timezoneOffsetMinutes(zone, year, 7, 1);
            var dst = (
                summerOffset != winterOffset &&
                currentOffsetMinutes == Math.min(winterOffset, summerOffset)
            )
                ? 1
                : 0;
            this.mod.instance.memory().writeI32(tmPtr + 32, dst);
            this.mod.instance.memory().writeI32(tmPtr + 36, -(currentOffsetMinutes * 60));
        }

        private void tzsetJs(long[] args) {
            if (args.length < 4) {
                return;
            }
            var timezonePtr = (int) args[0];
            var daylightPtr = (int) args[1];
            var stdNamePtr = (int) args[2];
            var dstNamePtr = (int) args[3];

            var zone = java.time.ZoneId.systemDefault();
            var currentYear = java.time.ZonedDateTime.now(zone).getYear();
            var winterOffset = timezoneOffsetMinutes(zone, currentYear, 1, 1);
            var summerOffset = timezoneOffsetMinutes(zone, currentYear, 7, 1);
            var stdTimezoneOffset = Math.max(winterOffset, summerOffset);
            if (timezonePtr != 0) {
                this.mod.instance.memory().writeI32(timezonePtr, stdTimezoneOffset * 60);
            }
            if (daylightPtr != 0) {
                this.mod.instance.memory().writeI32(
                    daylightPtr,
                    winterOffset != summerOffset ? 1 : 0
                );
            }

            var winterName = extractZoneName(winterOffset);
            var summerName = extractZoneName(summerOffset);
            if (summerOffset < winterOffset) {
                if (stdNamePtr != 0) {
                    writeUtf8Bounded(winterName, stdNamePtr, 17);
                }
                if (dstNamePtr != 0) {
                    writeUtf8Bounded(summerName, dstNamePtr, 17);
                }
            } else {
                if (dstNamePtr != 0) {
                    writeUtf8Bounded(winterName, dstNamePtr, 17);
                }
                if (stdNamePtr != 0) {
                    writeUtf8Bounded(summerName, stdNamePtr, 17);
                }
            }
        }

        private long mmapJs(long[] args) {
            try {
                if (args.length < 7) {
                    return err(EINVAL);
                }
                var len = (int) args[0];
                var prot = (int) args[1];
                var flags = (int) args[2];
                var fd = (int) args[3];
                var offset = args[4];
                var allocatedPtr = (int) args[5];
                var addrPtr = (int) args[6];
                if (len < 0 || offset < 0) {
                    return err(EINVAL);
                }
                if (len == 0) {
                    if (allocatedPtr != 0) {
                        this.mod.instance.memory().writeI32(allocatedPtr, 0);
                    }
                    if (addrPtr != 0) {
                        this.mod.instance.memory().writeI32(addrPtr, 0);
                    }
                    return 0;
                }

                var mallocRet = this.mod.instance.export("malloc").apply(len);
                if (mallocRet.length == 0 || mallocRet[0] == 0L) {
                    return err(ENOMEM);
                }
                var ptr = (int) mallocRet[0];
                var data = new byte[len];
                if (fd >= 0) {
                    if (
                        this.pipeReadTable.containsKey(fd) ||
                        this.pipeWriteTable.containsKey(fd) ||
                        this.fdDeviceTable.containsKey(fd) ||
                        this.dirEntries.containsKey(fd)
                    ) {
                        this.mod.instance.export("free").apply(ptr);
                        return err(ENODEV);
                    }
                    var channel = this.fdTable.get(fd);
                    if (channel == null) {
                        this.mod.instance.export("free").apply(ptr);
                        return err(EBADF);
                    }
                    var oldPos = channel.position();
                    try {
                        channel.position(offset);
                        var written = 0;
                        while (written < len) {
                            var chunk = java.nio.ByteBuffer.wrap(data, written, len - written);
                            var read = channel.read(chunk);
                            if (read <= 0) {
                                break;
                            }
                            written += read;
                        }
                    } finally {
                        channel.position(oldPos);
                    }
                }
                this.mod.instance.memory().write(ptr, data, 0, data.length);
                if (allocatedPtr != 0) {
                    this.mod.instance.memory().writeI32(allocatedPtr, 1);
                }
                if (addrPtr != 0) {
                    this.mod.instance.memory().writeI32(addrPtr, ptr);
                }
                this.mmapRegions.put(ptr, new MmapRegion(fd, offset, len, true, prot, flags));
                return 0;
            } catch (Exception e) {
                if (isMemoryAccessFault(e)) {
                    return err(EFAULT);
                }
                return err(EIO);
            }
        }

        private long munmapJs(long[] args) {
            try {
                if (args.length < 6) {
                    return err(EINVAL);
                }
                var addr = (int) args[0];
                var len = (int) args[1];
                var prot = (int) args[2];
                var fd = (int) args[4];
                var offset = args[5];
                if (len <= 0) {
                    return 0;
                }
                var region = this.mmapRegions.get(addr);
                if ((prot & 2) != 0) {
                    var writeFd = region != null ? region.fd : fd;
                    var writeOffset = region != null ? region.offset : offset;
                    var channel = this.fdTable.get(writeFd);
                    if (channel != null) {
                        var bytes = this.mod.instance.memory().readBytes(addr, len);
                        var oldPos = channel.position();
                        try {
                            channel.position(Math.max(0L, writeOffset));
                            writeFully(channel, bytes);
                        } finally {
                            channel.position(oldPos);
                        }
                    }
                }
                if (region != null) {
                    this.mmapRegions.remove(addr);
                    if (region.allocated) {
                        this.mod.instance.export("free").apply(addr);
                    }
                }
                return 0;
            } catch (Exception e) {
                if (isMemoryAccessFault(e)) {
                    return err(EFAULT);
                }
                return err(EIO);
            }
        }

        private void writeTmCommon(java.time.ZonedDateTime dateTime, int tmPtr) {
            this.mod.instance.memory().writeI32(tmPtr, dateTime.getSecond());
            this.mod.instance.memory().writeI32(tmPtr + 4, dateTime.getMinute());
            this.mod.instance.memory().writeI32(tmPtr + 8, dateTime.getHour());
            this.mod.instance.memory().writeI32(tmPtr + 12, dateTime.getDayOfMonth());
            this.mod.instance.memory().writeI32(tmPtr + 16, dateTime.getMonthValue() - 1);
            this.mod.instance.memory().writeI32(tmPtr + 20, dateTime.getYear() - 1900);
            this.mod.instance.memory().writeI32(tmPtr + 24, dateTime.getDayOfWeek().getValue() % 7);
            this.mod.instance.memory().writeI32(tmPtr + 28, dateTime.getDayOfYear() - 1);
        }

        private static int timezoneOffsetMinutes(
            java.time.ZoneId zone,
            int year,
            int month,
            int day
        ) {
            var local = java.time.LocalDate.of(year, month, day).atStartOfDay(zone);
            return -local.getOffset().getTotalSeconds() / 60;
        }

        private static String extractZoneName(int timezoneOffsetMinutes) {
            var sign = timezoneOffsetMinutes >= 0 ? "-" : "+";
            var abs = Math.abs(timezoneOffsetMinutes);
            var hours = abs / 60;
            var minutes = abs % 60;
            return String.format("UTC%s%02d%02d", sign, hours, minutes);
        }

        private long getaddrinfo(long[] args) {
            try {
                if (args.length < 4) {
                    return -1;
                }
                var nodePtr = (int) args[0];
                var servicePtr = (int) args[1];
                var hintPtr = (int) args[2];
                var outPtr = (int) args[3];

                var flags = 0;
                var family = 0;
                var type = 0;
                var proto = 0;
                if (hintPtr != 0) {
                    flags = (int) this.mod.instance.memory().readI32(hintPtr);
                    family = (int) this.mod.instance.memory().readI32(hintPtr + 4);
                    type = (int) this.mod.instance.memory().readI32(hintPtr + 8);
                    proto = (int) this.mod.instance.memory().readI32(hintPtr + 12);
                }
                recordHostNote(
                    "getaddrinfo node=\"" +
                    (nodePtr == 0 ? "<null>" : readCString(nodePtr)) +
                    "\" service=\"" +
                    (servicePtr == 0 ? "<null>" : readCString(servicePtr)) +
                    "\" flags=" +
                    flags +
                    " family=" +
                    family +
                    " type=" +
                    type +
                    " proto=" +
                    proto
                );

                if (type != 0 && proto == 0) {
                    proto = type == 2 ? 17 : 6;
                }
                if (type == 0 && proto != 0) {
                    type = proto == 17 ? 2 : 1;
                }
                if (proto == 0) {
                    proto = 6;
                }
                if (type == 0) {
                    type = 1;
                }

                if (nodePtr == 0 && servicePtr == 0) {
                    return -2;
                }
                if ((flags & ~(1 | 2 | 4 | 1024 | 8 | 16 | 32)) != 0) {
                    return -1;
                }
                if (hintPtr != 0 && ((int) this.mod.instance.memory().readI32(hintPtr) & 2) != 0 && nodePtr == 0) {
                    return -1;
                }
                if ((flags & 32) != 0) {
                    return -2;
                }
                if (type != 0 && type != 1 && type != 2) {
                    return -7;
                }
                if (family != 0 && family != AF_INET && family != AF_INET6) {
                    recordHostNote("getaddrinfo reject-family=" + family);
                    return -6;
                }

                var port = 0;
                if (servicePtr != 0) {
                    var service = readCString(servicePtr);
                    try {
                        port = Integer.parseInt(service);
                    } catch (NumberFormatException e) {
                        recordHostNote("getaddrinfo invalid-service");
                        if ((flags & 1024) != 0) {
                            return -2;
                        }
                        return -8;
                    }
                }

                if (nodePtr == 0) {
                    var resolvedFamily = family == 0 ? AF_INET : family;
                    if ((flags & 1) == 0) {
                        if (resolvedFamily == AF_INET) {
                            var loopback = packIpv4(127, 0, 0, 1);
                            var ai = allocAddrInfo4(type, proto, loopback, port);
                            this.mod.instance.memory().writeI32(outPtr, ai);
                            return 0;
                        }
                        if (resolvedFamily == AF_INET6) {
                            var loopback = parseIpv6("::1");
                            if (loopback == null) {
                                return -2;
                            }
                            var ai = allocAddrInfo6(type, proto, loopback, port);
                            this.mod.instance.memory().writeI32(outPtr, ai);
                            return 0;
                        }
                        recordHostNote("getaddrinfo unsupported-any-family=" + resolvedFamily);
                        return -2;
                    }
                    if (resolvedFamily == AF_INET) {
                        var any = packIpv4(0, 0, 0, 0);
                        var ai = allocAddrInfo4(type, proto, any, port);
                        this.mod.instance.memory().writeI32(outPtr, ai);
                        return 0;
                    }
                    if (resolvedFamily == AF_INET6) {
                        var any6 = new int[] { 0, 0, 0, 0 };
                        var ai = allocAddrInfo6(type, proto, any6, port);
                        this.mod.instance.memory().writeI32(outPtr, ai);
                        return 0;
                    }
                    return -2;
                }

                var node = readCString(nodePtr);
                var parsed = parseIpv4(node);
                if (parsed != null) {
                    var resolvedFamily = family;
                    if (resolvedFamily == 0 || resolvedFamily == AF_INET) {
                        resolvedFamily = AF_INET;
                        var ai = allocAddrInfo4(type, proto, parsed, port);
                        this.mod.instance.memory().writeI32(outPtr, ai);
                        return 0;
                    }
                    if (resolvedFamily == AF_INET6 && (flags & 8) != 0) {
                        var mapped = ipv4MappedIpv6(parsed);
                        var ai = allocAddrInfo6(type, proto, mapped, port);
                        this.mod.instance.memory().writeI32(outPtr, ai);
                        return 0;
                    } else {
                        recordHostNote("getaddrinfo unsupported-literal-family=" + resolvedFamily);
                        return -2;
                    }
                }

                var parsedV6 = parseIpv6(node);
                if (parsedV6 != null) {
                    var resolvedFamily = family;
                    if (resolvedFamily == 0 || resolvedFamily == AF_INET6) {
                        resolvedFamily = AF_INET6;
                    } else {
                        recordHostNote("getaddrinfo unsupported-v6-family=" + resolvedFamily);
                        return -2;
                    }
                    var ai = allocAddrInfo6(type, proto, parsedV6, port);
                    this.mod.instance.memory().writeI32(outPtr, ai);
                    return 0;
                }

                if ((flags & 4) != 0) {
                    return -2;
                }

                var mapped = lookupName(node);
                var mappedIpv4 = parseIpv4(mapped);
                if (mappedIpv4 == null) {
                    recordHostNote("getaddrinfo map-failed node=" + mapped);
                    return -2;
                }
                var resolvedFamily = family == 0 ? AF_INET : family;
                if (resolvedFamily != AF_INET) {
                    recordHostNote("getaddrinfo unsupported-mapped-family=" + resolvedFamily);
                    return -2;
                }
                var ai = allocAddrInfo4(type, proto, mappedIpv4, port);
                this.mod.instance.memory().writeI32(outPtr, ai);
                return 0;
            } catch (RuntimeBridgeException e) {
                return -2;
            } catch (Exception e) {
                return -2;
            }
        }

        private long getnameinfo(long[] args) {
            try {
                if (args.length < 7) {
                    return -1;
                }
                var sa = (int) args[0];
                var salen = (int) args[1];
                var nodePtr = (int) args[2];
                var nodeLen = (int) args[3];
                var servPtr = (int) args[4];
                var servLen = (int) args[5];
                var flags = (int) args[6];

                var sockaddr = readSockaddr(sa, salen);
                if (sockaddr == null) {
                    return -6;
                }

                var overflowed = false;
                if (nodePtr != 0 && nodeLen > 0) {
                    var addr = sockaddr.address;
                    if ((flags & 1) != 0) {
                        // NI_NUMERICHOST
                    } else {
                        var lookup = lookupAddr(addr);
                        if (lookup == null) {
                            if ((flags & 8) != 0) { // NI_NAMEREQD
                                return -2;
                            }
                        } else {
                            addr = lookup;
                        }
                    }
                    var written = writeUtf8Bounded(addr, nodePtr, nodeLen);
                    if (written + 1 >= nodeLen) {
                        overflowed = true;
                    }
                }
                if (servPtr != 0 && servLen > 0) {
                    var service = Integer.toString(sockaddr.port);
                    var written = writeUtf8Bounded(service, servPtr, servLen);
                    if (written + 1 >= servLen) {
                        overflowed = true;
                    }
                }
                return overflowed ? -12 : 0;
            } catch (Exception e) {
                return -6;
            }
        }

        private int allocAddrInfo4(int type, int proto, int ipv4, int port) {
            var saLen = 16;
            var sa = mallocFromWasm(saLen);
            writeSockaddr4(sa, ipv4, port, saLen);
            var ai = mallocFromWasm(32);
            this.mod.instance.memory().writeI32(ai, 0);
            this.mod.instance.memory().writeI32(ai + 4, AF_INET);
            this.mod.instance.memory().writeI32(ai + 8, type);
            this.mod.instance.memory().writeI32(ai + 12, proto);
            this.mod.instance.memory().writeI32(ai + 16, saLen);
            this.mod.instance.memory().writeI32(ai + 20, sa);
            this.mod.instance.memory().writeI32(ai + 24, 0);
            this.mod.instance.memory().writeI32(ai + 28, 0);
            return ai;
        }

        private int allocAddrInfo6(int type, int proto, int[] ipv6, int port) {
            var saLen = 28;
            var sa = mallocFromWasm(saLen);
            writeSockaddr6(sa, ipv6, port, saLen);
            var ai = mallocFromWasm(32);
            this.mod.instance.memory().writeI32(ai, 0);
            this.mod.instance.memory().writeI32(ai + 4, AF_INET6);
            this.mod.instance.memory().writeI32(ai + 8, type);
            this.mod.instance.memory().writeI32(ai + 12, proto);
            this.mod.instance.memory().writeI32(ai + 16, saLen);
            this.mod.instance.memory().writeI32(ai + 20, sa);
            this.mod.instance.memory().writeI32(ai + 24, 0);
            this.mod.instance.memory().writeI32(ai + 28, 0);
            return ai;
        }

        private int mallocFromWasm(int size) {
            var malloc = this.mod.instance.export("malloc");
            var ret = malloc.apply(size);
            if (ret.length == 0) {
                throw new RuntimeBridgeException("getaddrinfo", "malloc returned no value");
            }
            return (int) ret[0];
        }

        private void writeSockaddr4(int saPtr, int ipv4, int port, int addrLen) {
            for (var i = 0; i < addrLen; i++) {
                this.mod.instance.memory().writeByte(saPtr + i, (byte) 0);
            }
            this.mod.instance.memory().writeShort(saPtr, (short) AF_INET);
            this.mod.instance.memory().writeShort(saPtr + 2, htons(port));
            this.mod.instance.memory().writeI32(saPtr + 4, ipv4);
        }

        private void writeSockaddr6(int saPtr, int[] ipv6, int port, int addrLen) {
            if (ipv6 == null || ipv6.length != 4) {
                throw new RuntimeBridgeException("getaddrinfo", "Invalid ipv6 address words");
            }
            for (var i = 0; i < addrLen; i++) {
                this.mod.instance.memory().writeByte(saPtr + i, (byte) 0);
            }
            this.mod.instance.memory().writeI32(saPtr, AF_INET6);
            this.mod.instance.memory().writeShort(saPtr + 2, htons(port));
            this.mod.instance.memory().writeI32(saPtr + 8, ipv6[0]);
            this.mod.instance.memory().writeI32(saPtr + 12, ipv6[1]);
            this.mod.instance.memory().writeI32(saPtr + 16, ipv6[2]);
            this.mod.instance.memory().writeI32(saPtr + 20, ipv6[3]);
        }

        private SockAddr readSockaddr(int saPtr, int saLen) {
            var family = Short.toUnsignedInt(this.mod.instance.memory().readShort(saPtr));
            if (family == AF_INET) {
                if (saLen != 16) {
                    return null;
                }
                var port = ntohs(this.mod.instance.memory().readShort(saPtr + 2));
                var addr = (int) this.mod.instance.memory().readI32(saPtr + 4);
                return new SockAddr(AF_INET, formatIpv4(addr), port);
            }
            if (family == AF_INET6) {
                if (saLen != 28) {
                    return null;
                }
                var port = ntohs(this.mod.instance.memory().readShort(saPtr + 2));
                var words = new int[] {
                    (int) this.mod.instance.memory().readI32(saPtr + 8),
                    (int) this.mod.instance.memory().readI32(saPtr + 12),
                    (int) this.mod.instance.memory().readI32(saPtr + 16),
                    (int) this.mod.instance.memory().readI32(saPtr + 20),
                };
                return new SockAddr(AF_INET6, formatIpv6(words), port);
            }
            return null;
        }

        private static short htons(int value) {
            return Short.reverseBytes((short) (value & 0xFFFF));
        }

        private static int ntohs(short value) {
            return Short.toUnsignedInt(Short.reverseBytes(value));
        }

        private static int packIpv4(int b0, int b1, int b2, int b3) {
            return (b0 & 0xFF) | ((b1 & 0xFF) << 8) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 24);
        }

        private static Integer parseIpv4(String value) {
            if (value == null) {
                return null;
            }
            var parts = value.split("\\.");
            if (parts.length != 4) {
                return null;
            }
            var nums = new int[4];
            for (var i = 0; i < 4; i++) {
                try {
                    nums[i] = Integer.parseInt(parts[i]);
                } catch (NumberFormatException e) {
                    return null;
                }
                if (nums[i] < 0 || nums[i] > 255) {
                    return null;
                }
            }
            return packIpv4(nums[0], nums[1], nums[2], nums[3]);
        }

        private static int[] parseIpv6(String value) {
            if (value == null || !value.contains(":")) {
                return null;
            }
            try {
                var addr = InetAddress.getByName(value);
                if (!(addr instanceof Inet6Address)) {
                    return null;
                }
                var bytes = addr.getAddress();
                if (bytes.length != 16) {
                    return null;
                }
                var out = new int[4];
                for (var i = 0; i < 4; i++) {
                    var base = i * 4;
                    out[i] =
                        (bytes[base] & 0xFF) |
                        ((bytes[base + 1] & 0xFF) << 8) |
                        ((bytes[base + 2] & 0xFF) << 16) |
                        ((bytes[base + 3] & 0xFF) << 24);
                }
                return out;
            } catch (Exception e) {
                return null;
            }
        }

        private static int[] ipv4MappedIpv6(int ipv4) {
            return new int[] { 0, 0, 0xFFFF0000, ipv4 };
        }

        private static String formatIpv4(int packed) {
            return (
                (packed & 0xFF) +
                "." +
                ((packed >>> 8) & 0xFF) +
                "." +
                ((packed >>> 16) & 0xFF) +
                "." +
                ((packed >>> 24) & 0xFF)
            );
        }

        private static String formatIpv6(int[] words) {
            if (words == null || words.length != 4) {
                return "";
            }
            var bytes = new byte[16];
            for (var i = 0; i < 4; i++) {
                var value = words[i];
                var base = i * 4;
                bytes[base] = (byte) (value & 0xFF);
                bytes[base + 1] = (byte) ((value >>> 8) & 0xFF);
                bytes[base + 2] = (byte) ((value >>> 16) & 0xFF);
                bytes[base + 3] = (byte) ((value >>> 24) & 0xFF);
            }
            try {
                return InetAddress.getByAddress(bytes).getHostAddress();
            } catch (Exception e) {
                return "";
            }
        }

        private String lookupName(String name) {
            var parsed = parseIpv4(name);
            if (parsed != null) {
                return name;
            }
            var existing = this.dnsNameToAddr.get(name);
            if (existing != null) {
                return existing;
            }
            var id = this.dnsAddressId.getAndIncrement();
            var mapped = "172.29." + (id & 0xFF) + "." + (id & 0xFF00);
            this.dnsNameToAddr.put(name, mapped);
            this.dnsAddrToName.put(mapped, name);
            return mapped;
        }

        private String lookupAddr(String addr) {
            return this.dnsAddrToName.get(addr);
        }

        private int writeUtf8Bounded(String text, int ptr, int maxBytes) {
            if (maxBytes <= 0) {
                return 0;
            }
            var bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            var writeLen = Math.min(bytes.length, Math.max(0, maxBytes - 1));
            if (writeLen > 0) {
                this.mod.instance.memory().write(ptr, bytes, 0, writeLen);
            }
            this.mod.instance.memory().writeByte(ptr + writeLen, (byte) 0);
            return writeLen;
        }

        private static Long toI53(long value) {
            if (value < INT53_MIN || value > INT53_MAX) {
                return null;
            }
            return value;
        }

        private static final class SockAddr {
            private final int family;
            private final String address;
            private final int port;

            private SockAddr(int family, String address, int port) {
                this.family = family;
                this.address = address;
                this.port = port;
            }
        }

        private void bootstrapStandardTree() {
            this.fs.mkdirTree("/");
            this.fs.mkdirTree("/tmp");
            this.fs.mkdirTree("/tmp/pglite");
            this.fs.mkdirTree("/tmp/pglite/base");
            this.fs.mkdirTree("/tmp/pglite/bin");
            this.fs.mkdirTree("/tmp/pglite/share");
            this.fs.mkdirTree("/tmp/pglite/share/postgresql");
            this.fs.mkdirTree("/home");
            this.fs.mkdirTree("/dev");
        }

        private static String normalize(String path) {
            if (path == null || path.isBlank() || "/".equals(path)) {
                return "/";
            }
            var value = path.replace('\\', '/');
            if (!value.startsWith("/")) {
                value = "/" + value;
            }
            var parts = value.split("/");
            var stack = new ArrayDeque<String>();
            for (var part : parts) {
                if (part == null || part.isEmpty() || ".".equals(part)) {
                    continue;
                }
                if ("..".equals(part)) {
                    if (!stack.isEmpty()) {
                        stack.removeLast();
                    }
                    continue;
                }
                stack.addLast(part);
            }
            if (stack.isEmpty()) {
                return "/";
            }
            return "/" + String.join("/", stack);
        }

        private void preloadPgliteData() {
            var data = getPreloadedPackage("pglite.data", 0);
            var manifest = loadPgliteDataManifest();
            reconcileManifestSkew(manifest, data);
            addRunDependency("datafile_pglite.data");
            try {
                for (var entry : manifest) {
                    if (entry.end < entry.start || entry.end > data.length) {
                        throw new RuntimeBridgeException(
                            "preloadPgliteData",
                            "Invalid manifest range for " + entry.filename
                        );
                    }
                    var fileBytes = Arrays.copyOfRange(data, entry.start, entry.end);
                    if ("/tmp/pglite/share/postgresql/postgres.bki".equals(entry.filename)) {
                        fileBytes = normalizeBkiVersion(
                            fileBytes,
                            toMajorVersion(this.wasmPostgresVersion)
                        );
                    }
                    this.fs.createDataFile(entry.filename, null, fileBytes, true, true, true);
                }
                ensurePreloadedFile("/tmp/pglite/bin/initdb", true);
                ensurePreloadedFile("/tmp/pglite/bin/postgres", true);
                ensurePreloadedFile("/tmp/pglite/share/postgresql/postgres.bki", false);
            } finally {
                removeRunDependency("datafile_pglite.data");
            }
        }

        private String detectWasmPostgresVersion() {
            try {
                var wasmBytes = utils.readFile("pglite.wasm");
                var text = new String(wasmBytes, java.nio.charset.StandardCharsets.ISO_8859_1);
                var matcher = Pattern.compile("PostgreSQL\\s+(\\d+\\.\\d+)").matcher(text);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            } catch (Exception ignored) {
            }
            return "17.5";
        }

        private static byte[] normalizeBkiVersion(byte[] bkiBytes, String version) {
            var text = new String(bkiBytes, java.nio.charset.StandardCharsets.UTF_8);
            var marker = "# PostgreSQL ";
            var markerIdx = text.indexOf(marker);
            if (markerIdx < 0) {
                return bkiBytes;
            }
            var valueStart = markerIdx + marker.length();
            var lineEnd = text.indexOf('\n', valueStart);
            if (lineEnd < 0) {
                lineEnd = text.length();
            }
            var current = text.substring(valueStart, lineEnd).trim();
            if (current.equals(version)) {
                return bkiBytes;
            }
            var patched = text.substring(0, valueStart) + version + text.substring(lineEnd);
            return patched.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }

        private static String toMajorVersion(String version) {
            if (version == null || version.isBlank()) {
                return "17";
            }
            var dot = version.indexOf('.');
            if (dot <= 0) {
                return version;
            }
            return version.substring(0, dot);
        }

        private void reconcileManifestSkew(java.util.List<ManifestEntry> manifest, byte[] data) {
            if (manifest.isEmpty()) {
                return;
            }
            var declaredSize = manifest.get(manifest.size() - 1).end;
            if (declaredSize > data.length) {
                throw new RuntimeBridgeException(
                    "reconcileManifestSkew",
                    "Manifest range exceeds package size: " + declaredSize + " > " + data.length
                );
            }
            var trailingDelta = data.length - declaredSize;
            if (trailingDelta != 0) {
                var plpgsqlIdx = findManifestEntryIndex(
                    manifest,
                    "/tmp/pglite/share/postgresql/extension/plpgsql--1.0.sql"
                );
                if (plpgsqlIdx >= 0) {
                    var plpgsqlSignature =
                        "/* src/pl/plpgsql/src/plpgsql--1.0.sql */"
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    var declaredPlpgsqlStart = manifest.get(plpgsqlIdx).start;
                    var detectedPlpgsqlStart = indexOf(
                        data,
                        plpgsqlSignature,
                        Math.max(0, declaredPlpgsqlStart - 512),
                        Math.min(data.length, declaredPlpgsqlStart + 512)
                    );
                    if (detectedPlpgsqlStart >= 0) {
                        var shift = detectedPlpgsqlStart - declaredPlpgsqlStart;
                        shiftManifestEntries(manifest, plpgsqlIdx, manifest.size(), shift);
                    }
                    var remainder = data.length - manifest.get(manifest.size() - 1).end;
                    shiftManifestEntries(manifest, plpgsqlIdx, manifest.size(), remainder);
                }
            }
            validateManifestRanges(manifest, data.length);
        }

        private static int findManifestEntryIndex(
            java.util.List<ManifestEntry> manifest,
            String filename
        ) {
            for (var i = 0; i < manifest.size(); i++) {
                if (filename.equals(manifest.get(i).filename)) {
                    return i;
                }
            }
            return -1;
        }

        private static void shiftManifestEntries(
            java.util.List<ManifestEntry> manifest,
            int fromInclusive,
            int toExclusive,
            int delta
        ) {
            if (delta == 0) {
                return;
            }
            for (var i = fromInclusive; i < toExclusive; i++) {
                manifest.get(i).start += delta;
                manifest.get(i).end += delta;
            }
        }

        private static int indexOf(byte[] haystack, byte[] needle, int from, int toExclusive) {
            if (needle.length == 0) {
                return from;
            }
            var start = Math.max(0, from);
            var end = Math.min(haystack.length, toExclusive);
            var last = end - needle.length;
            for (var i = start; i <= last; i++) {
                var found = true;
                for (var j = 0; j < needle.length; j++) {
                    if (haystack[i + j] != needle[j]) {
                        found = false;
                        break;
                    }
                }
                if (found) {
                    return i;
                }
            }
            return -1;
        }

        private static void validateManifestRanges(
            java.util.List<ManifestEntry> manifest,
            int dataLength
        ) {
            var prevEnd = 0;
            for (var i = 0; i < manifest.size(); i++) {
                var entry = manifest.get(i);
                if (entry.start < 0 || entry.end < entry.start || entry.end > dataLength) {
                    throw new RuntimeBridgeException(
                        "reconcileManifestSkew",
                        "Invalid range for " + entry.filename + ": " + entry.start + "-" + entry.end
                    );
                }
                if (i > 0 && entry.start < prevEnd) {
                    throw new RuntimeBridgeException(
                        "reconcileManifestSkew",
                        "Overlapped range at " + entry.filename
                    );
                }
                prevEnd = entry.end;
            }
        }

        private void ensurePreloadedFile(String path, boolean executable) {
            var filePath = resolve(path);
            if (!Files.exists(filePath)) {
                throw new RuntimeBridgeException(
                    "preloadPgliteData",
                    "Required preload file not found: " + path
                );
            }
            if (executable) {
                try {
                    filePath.toFile().setExecutable(true, false);
                } catch (Exception ignored) {
                }
            }
        }

        private java.util.List<ManifestEntry> loadPgliteDataManifest() {
            var classpathManifest = loadManifestFromClasspath();
            if (!classpathManifest.isEmpty()) {
                return classpathManifest;
            }
            if (!MANIFEST_FALLBACK) {
                throw new RuntimeBridgeException(
                    "loadPgliteDataManifest",
                    "Classpath manifest not found: " +
                    MANIFEST_RESOURCE +
                    " (set -Dpglite.manifest.fallback=true to parse pglite.js)"
                );
            }
            return loadPgliteDataManifestFromSource();
        }

        private java.util.List<ManifestEntry> loadManifestFromClasspath() {
            try (var input = pglite.class.getClassLoader().getResourceAsStream(MANIFEST_RESOURCE)) {
                if (input == null) {
                    return Collections.emptyList();
                }
                var payload = OBJECT_MAPPER.readValue(input, ManifestFile.class);
                if (payload == null || payload.files == null || payload.files.isEmpty()) {
                    throw new RuntimeBridgeException(
                        "loadPgliteDataManifest",
                        "Manifest has no file entries: " + MANIFEST_RESOURCE
                    );
                }
                var out = new ArrayList<ManifestEntry>(payload.files.size());
                for (var entry : payload.files) {
                    if (entry == null || entry.filename == null) {
                        continue;
                    }
                    out.add(entry);
                }
                if (out.isEmpty()) {
                    throw new RuntimeBridgeException(
                        "loadPgliteDataManifest",
                        "Manifest has no valid entries: " + MANIFEST_RESOURCE
                    );
                }
                return out;
            } catch (RuntimeBridgeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeBridgeException("loadPgliteDataManifest", e);
            }
        }

        private java.util.List<ManifestEntry> loadPgliteDataManifestFromSource() {
            try {
                var sourcePath = resolveManifestSource();
                var js = Files.readString(sourcePath);
                var listStart = js.indexOf("loadPackage({");
                if (listStart < 0) {
                    throw new RuntimeBridgeException("loadPgliteDataManifest", "loadPackage block not found");
                }
                var filesStart = js.indexOf("files: [", listStart);
                if (filesStart < 0) {
                    throw new RuntimeBridgeException("loadPgliteDataManifest", "files array not found");
                }
                var filesEnd = js.indexOf("],", filesStart);
                if (filesEnd < 0) {
                    throw new RuntimeBridgeException("loadPgliteDataManifest", "files array end not found");
                }
                var block = js.substring(filesStart, filesEnd);
                var pattern = Pattern.compile(
                    "filename:\\s*\"([^\"]+)\"\\s*,\\s*start:\\s*(\\d+)\\s*,\\s*end:\\s*(\\d+)",
                    Pattern.MULTILINE
                );
                var matcher = pattern.matcher(block);
                var out = new ArrayList<ManifestEntry>();
                while (matcher.find()) {
                    var entry = new ManifestEntry();
                    entry.filename = matcher.group(1);
                    entry.start = Integer.parseInt(matcher.group(2));
                    entry.end = Integer.parseInt(matcher.group(3));
                    out.add(entry);
                }
                if (out.isEmpty()) {
                    throw new RuntimeBridgeException("loadPgliteDataManifest", "No file entries found");
                }
                return out;
            } catch (RuntimeBridgeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeBridgeException("loadPgliteDataManifest", e);
            }
        }

        private Path resolveManifestSource() {
            var candidates = new String[] {
                "pglite/src/pglite/release/pglite.js",
                "../pglite/src/pglite/release/pglite.js",
                "../../pglite/src/pglite/release/pglite.js",
            };
            for (var candidate : candidates) {
                var path = Path.of(candidate).normalize();
                if (Files.exists(path)) {
                    return path;
                }
            }
            throw new RuntimeBridgeException(
                "resolveManifestSource",
                "Unable to locate pglite/src/pglite/release/pglite.js"
            );
        }

        private static final class ManifestEntry {
            public String filename;
            public int start;
            public int end;
        }

        private static final class ManifestFile {
            public java.util.List<ManifestEntry> files = new ArrayList<>();
        }

        private long syscallOpenAt(long[] args) {
            try {
                if (args.length < 4) {
                    return err(EINVAL);
                }
                var dirfd = (int) args[0];
                var path = calculateAt(dirfd, readCString((int) args[1]), false);
                var flags = (int) args[2];
                var varArgs = (int) args[3];
                // Emscripten passes the varargs pointer as the 4th argument for openat.
                var mode = varArgs != 0 ? this.mod.instance.memory().readI32(varArgs) : 0;
                var target = resolve(path);
                recordHostNote(
                    "__syscall_openat path=\"" +
                    path +
                    "\" resolved=\"" +
                    toVirtualPath(target) +
                    "\" flags=" +
                    flags +
                    " mode=" +
                    mode
                );
                var normalizedPath = normalize(path);
                var devId = this.deviceRegistry.deviceNode(normalizedPath);
                if (devId != null && this.deviceRegistry.runtimeDeviceOps(devId) != null) {
                    var devFd = allocateFd(0);
                    this.fdDeviceTable.put(devFd, devId);
                    this.fdDevicePosition.put(devFd, 0);
                    this.fdPathTable.put(devFd, target);
                    this.fdFlagsTable.put(devFd, flags);
                    recordHostNote("__syscall_openat devfd=" + devFd);
                    return devFd;
                }
                var fd = allocateFd(3);
                this.fdPathTable.put(fd, target);
                if (Files.exists(target) && Files.isDirectory(target)) {
                    var entries = new ArrayList<String>();
                    entries.add(".");
                    entries.add("..");
                    try (var stream = Files.list(target)) {
                        stream.forEach(p -> entries.add(p.getFileName().toString()));
                    }
                    this.dirEntries.put(fd, entries);
                    this.dirCursor.put(fd, 0);
                    recordHostNote("__syscall_openat dirfd=" + fd);
                    return fd;
                }
                var opts = decodeOpenOptions(flags);
                var accessMode = flags & 0x3;
                if (accessMode != 0x1 && this.fs instanceof EmscriptenFsImpl fsImpl) {
                    fsImpl.ensureLazyFileMaterialized(normalizedPath);
                }
                if ((flags & 0x40) != 0) { // O_CREAT
                    var parent = target.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                }
                var channel = Files.newByteChannel(target, opts);
                this.fdTable.put(fd, channel);
                this.fdFlagsTable.put(fd, flags);
                if ((mode & 0111) != 0) {
                    try {
                        target.toFile().setExecutable(true, false);
                    } catch (Exception ignored) {
                    }
                }
                recordHostNote("__syscall_openat filefd=" + fd);
                return fd;
            } catch (ErrnoException e) {
                return err(e.errno);
            } catch (java.nio.file.NoSuchFileException e) {
                return err(ENOENT);
            } catch (java.nio.file.AccessDeniedException e) {
                return err(EACCES);
            } catch (java.nio.file.FileSystemException e) {
                return err(EIO);
            } catch (Exception e) {
                if (isMemoryAccessFault(e)) {
                    return err(EFAULT);
                }
                return err(EIO);
            }
        }

        private long syscallClose(long[] args) {
            try {
                if (args.length < 1) {
                    return err(EINVAL);
                }
                var fd = (int) args[0];
                if (!descriptorExists(fd)) {
                    return err(EBADF);
                }
                closeDescriptor(fd);
                return 0;
            } catch (Exception e) {
                if (isMemoryAccessFault(e)) {
                    return err(EFAULT);
                }
                return err(EIO);
            }
        }

        private long syscallDup(long[] args) {
            try {
                if (args.length < 1) {
                    return err(EINVAL);
                }
                var oldFd = (int) args[0];
                if (!descriptorExists(oldFd)) {
                    return err(EBADF);
                }
                var newFd = allocateFd(0);
                duplicateDescriptor(oldFd, newFd);
                return newFd;
            } catch (Exception e) {
                return err(EIO);
            }
        }

        private long syscallDup3(long[] args) {
            try {
                if (args.length < 3) {
                    return err(EINVAL);
                }
                var oldFd = (int) args[0];
                var newFd = (int) args[1];
                var flags = (int) args[2];
                if (flags != 0) {
                    return err(EINVAL);
                }
                if (oldFd == newFd) {
                    return err(EINVAL);
                }
                if (newFd < 0 || newFd >= MAX_OPEN_FDS) {
                    return err(EBADF);
                }
                if (!descriptorExists(oldFd)) {
                    return err(EBADF);
                }
                if (descriptorExists(newFd)) {
                    closeDescriptor(newFd);
                }
                duplicateDescriptor(oldFd, newFd);
                return newFd;
            } catch (Exception e) {
                return err(EIO);
            }
        }

        private long syscallPipe(long[] args) {
            try {
                if (args.length < 1) {
                    return err(EINVAL);
                }
                var fdPtr = (int) args[0];
                if (fdPtr == 0) {
                    return err(EFAULT);
                }
                var readFd = allocateFd(0);
                var pipe = new PipeState();
                this.pipeReadTable.put(readFd, pipe);
                int writeFd;
                try {
                    writeFd = allocateFd(0);
                } catch (ErrnoException e) {
                    this.pipeReadTable.remove(readFd);
                    return err(e.errno);
                }
                this.pipeWriteTable.put(writeFd, pipe);
                this.mod.instance.memory().writeI32(fdPtr, readFd);
                this.mod.instance.memory().writeI32(fdPtr + 4, writeFd);
                return 0;
            } catch (Exception e) {
                return err(EIO);
            }
        }

        private long syscallPipe2(long[] args) {
            if (args.length < 2) {
                return err(EINVAL);
            }
            var flags = (int) args[1];
            if (flags != 0) {
                return err(EINVAL);
            }
            return syscallPipe(new long[] { args[0] });
        }

        private long syscallSocket(long[] args) {
            try {
                if (args.length < 3) {
                    return err(EINVAL);
                }
                var family = (int) args[0];
                var type = (int) args[1];
                // Align with Emscripten SOCKFS.createSocket: mask CLOEXEC/NONBLOCK.
                var normalizedType = type & ~0x80800;
                var protocol = (int) args[2];
                if (normalizedType == SOCK_STREAM && protocol != 0 && protocol != 6) {
                    return err(EPROTONOSUPPORT);
                }
                var fd = allocateFd(3);
                this.socketTypeTable.put(fd, normalizedType);
                this.socketStateTable.put(
                    fd,
                    new RuntimeSockState(family, normalizedType, protocol)
                );
                this.fdFlagsTable.put(fd, 0);
                return fd;
            } catch (Exception e) {
                return err(EIO);
            }
        }

        private long syscallBind(long[] args) {
            try {
                if (args.length < 3) {
                    return err(EINVAL);
                }
                var fd = (int) args[0];
                var state = this.socketStateTable.get(fd);
                if (state == null) {
                    return err(EBADF);
                }
                var sockaddr = validateSockaddrForSyscall((int) args[1], (int) args[2]);
                if (sockaddr == null) {
                    return err(EINVAL);
                }
                if (state.boundAddress != null || state.boundPort != 0) {
                    return err(EINVAL);
                }
                if (sockaddr.port != 0) {
                    var bindingKey = socketBindingKey(sockaddr.address, sockaddr.port);
                    var existingFd = this.socketBindings.putIfAbsent(bindingKey, fd);
                    if (existingFd != null && existingFd != fd) {
                        return err(EINVAL);
                    }
                    state.bindingKey = bindingKey;
                }
                state.boundFamily = sockaddr.family;
                state.boundAddress = sockaddr.address;
                state.boundPort = sockaddr.port;
                return 0;
            } catch (ErrnoException e) {
                return err(e.errno);
            } catch (Exception e) {
                return err(EIO);
            }
        }

        private long syscallSendto(long[] args) {
            try {
                if (args.length < 3) {
                    return err(EINVAL);
                }
                var fd = (int) args[0];
                var state = this.socketStateTable.get(fd);
                if (state == null) {
                    return err(EBADF);
                }
                var len = (int) args[2];
                if (len < 0) {
                    return err(EINVAL);
                }
                SockAddr destination = null;
                if (state.type == SOCK_DGRAM) {
                    var hasSockAddr = args.length >= 6 && args[4] != 0L;
                    if (hasSockAddr) {
                        destination = validateSockaddrForSyscall((int) args[4], (int) args[5]);
                        if (destination == null) {
                            return err(EINVAL);
                        }
                    } else if (state.connectedAddress != null && state.connectedPort > 0) {
                        destination = new SockAddr(
                            state.connectedFamily,
                            state.connectedAddress,
                            state.connectedPort
                        );
                    } else {
                        return err(EDESTADDRREQ);
                    }
                } else {
                    if (state.connectedAddress == null || state.connectedPort <= 0) {
                        return err(ENOTCONN);
                    }
                    destination = new SockAddr(
                        state.connectedFamily,
                        state.connectedAddress,
                        state.connectedPort
                    );
                }
                if (len == 0) {
                    return 0;
                }
                var payload = this.mod.instance.memory().readBytes((int) args[1], len);
                var sourceAddress = state.boundAddress != null
                    ? state.boundAddress
                    : state.family == AF_INET6
                        ? "::"
                        : "0.0.0.0";
                var sourcePort = Math.max(0, state.boundPort);
                var targetFd = this.socketBindings.get(
                    socketBindingKey(destination.address, destination.port)
                );
                if (targetFd != null) {
                    var target = this.socketStateTable.get(targetFd);
                    if (target != null) {
                        target.recvQueue.addLast(
                            new RuntimeDatagram(payload, sourceAddress, sourcePort)
                        );
                    }
                }
                return len;
            } catch (ErrnoException e) {
                return err(e.errno);
            } catch (RuntimeException e) {
                if (isMemoryAccessFault(e)) {
                    return err(EFAULT);
                }
                return err(EIO);
            } catch (Exception e) {
                return err(EIO);
            }
        }

        private long syscallRecvfrom(long[] args) {
            try {
                if (args.length < 3) {
                    return err(EINVAL);
                }
                var fd = (int) args[0];
                var state = this.socketStateTable.get(fd);
                if (state == null) {
                    return err(EBADF);
                }
                var len = (int) args[2];
                if (len < 0) {
                    return err(EINVAL);
                }
                if (len == 0) {
                    return 0;
                }
                var queued = state.recvQueue.pollFirst();
                if (queued == null) {
                    if (
                        state.type == SOCK_STREAM &&
                        (state.connectedAddress == null || state.connectedPort <= 0)
                    ) {
                        return err(ENOTCONN);
                    }
                    return err(EAGAIN);
                }
                var readLen = Math.min(len, queued.buffer.length);
                this.mod.instance.memory().write((int) args[1], queued.buffer, 0, readLen);
                if (state.type == SOCK_STREAM && readLen < queued.buffer.length) {
                    var remaining = Arrays.copyOfRange(queued.buffer, readLen, queued.buffer.length);
                    state.recvQueue.addFirst(new RuntimeDatagram(remaining, queued.address, queued.port));
                }

                if (args.length >= 6 && args[4] != 0L) {
                    var sockaddrPtr = (int) args[4];
                    var addrLenPtr = (int) args[5];
                    var family = state.family == AF_INET6 ? AF_INET6 : AF_INET;
                    var resolvedAddr = lookupName(queued.address);
                    var writeErrno = writeSockaddrForRecv(
                        sockaddrPtr,
                        addrLenPtr,
                        family,
                        resolvedAddr,
                        queued.port
                    );
                    if (writeErrno != 0) {
                        return err(writeErrno);
                    }
                }
                return readLen;
            } catch (RuntimeException e) {
                if (isMemoryAccessFault(e)) {
                    return err(EFAULT);
                }
                return err(EIO);
            } catch (Exception e) {
                return err(EIO);
            }
        }

        private SockAddr validateSockaddrForSyscall(int sockaddrPtr, int sockaddrLen)
            throws ErrnoException {
            var family = Short.toUnsignedInt(this.mod.instance.memory().readShort(sockaddrPtr));
            if (family != AF_INET && family != AF_INET6) {
                throw new ErrnoException(EAFNOSUPPORT);
            }
            return readSockaddr(sockaddrPtr, sockaddrLen);
        }

        private int writeSockaddrForRecv(
            int sockaddrPtr,
            int addrLenPtr,
            int family,
            String addr,
            int port
        ) {
            if (family == AF_INET) {
                var ipv4 = parseIpv4(addr);
                if (ipv4 == null) {
                    return EAFNOSUPPORT;
                }
                writeSockaddr4(sockaddrPtr, ipv4, port, 16);
                if (addrLenPtr != 0) {
                    this.mod.instance.memory().writeI32(addrLenPtr, 16);
                }
                return 0;
            }
            if (family == AF_INET6) {
                var ipv6 = parseIpv6(addr);
                if (ipv6 == null) {
                    var ipv4 = parseIpv4(addr);
                    if (ipv4 == null) {
                        return EAFNOSUPPORT;
                    }
                    ipv6 = ipv4MappedIpv6(ipv4);
                }
                writeSockaddr6(sockaddrPtr, ipv6, port, 28);
                if (addrLenPtr != 0) {
                    this.mod.instance.memory().writeI32(addrLenPtr, 28);
                }
                return 0;
            }
            return EAFNOSUPPORT;
        }

        private static String socketBindingKey(String address, int port) {
            return address + ":" + port;
        }

        private long syscallRead(long[] args) {
            try {
                if (args.length < 3) {
                    return err(EINVAL);
                }
                var fd = (int) args[0];
                var bufPtr = (int) args[1];
                var len = (int) args[2];
                var pipe = this.pipeReadTable.get(fd);
                if (pipe != null) {
                    if (len <= 0) {
                        return 0;
                    }
                    var dst = new byte[len];
                    var read = readPipe(pipe, dst, 0, len);
                    if (read < 0) {
                        return err(EAGAIN);
                    }
                    this.mod.instance.memory().write(bufPtr, dst, 0, read);
                    return read;
                }
                if (this.fdDeviceTable.containsKey(fd)) {
                    if (len <= 0) {
                        return 0;
                    }
                    var dst = new byte[len];
                    var read = readDevice(fd, dst, 0, len);
                    if (read > 0) {
                        this.mod.instance.memory().write(bufPtr, dst, 0, read);
                    }
                    return read;
                }
                var stdioFd = resolveStdioFd(fd);
                recordHostNote("__syscall_read fd=" + fd + " len=" + len);
                var ch = this.fdTable.get(fd);
                if (ch == null && stdioFd == 0) {
                    return 0;
                }
                if (ch == null) {
                    recordHostNote("__syscall_read missing-fd=" + fd);
                    return err(EBADF);
                }
                var buf = java.nio.ByteBuffer.allocate(len);
                var read = ch.read(buf);
                if (read <= 0) {
                    return 0;
                }
                this.mod.instance.memory().write(bufPtr, buf.array(), 0, read);
                return read;
            } catch (ErrnoException e) {
                return err(e.errno);
            } catch (RuntimeException e) {
                return err(EFAULT);
            } catch (Exception e) {
                return err(EIO);
            }
        }

        private long wasiEnvironSizesGet(long[] args) {
            try {
                if (args.length < 2) {
                    return EINVAL;
                }
                var countPtr = (int) args[0];
                var sizePtr = (int) args[1];
                var totalBytes = 0;
                for (var entry : this.environmentEntries) {
                    totalBytes += entry.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 1;
                }
                if (countPtr != 0) {
                    this.mod.instance.memory().writeI32(countPtr, this.environmentEntries.size());
                }
                if (sizePtr != 0) {
                    this.mod.instance.memory().writeI32(sizePtr, totalBytes);
                }
                return 0;
            } catch (Exception e) {
                return EIO;
            }
        }

        private long wasiEnvironGet(long[] args) {
            try {
                if (args.length < 2) {
                    return EINVAL;
                }
                var environPtr = (int) args[0];
                var environBufPtr = (int) args[1];
                var offset = 0;
                for (var i = 0; i < this.environmentEntries.size(); i++) {
                    var bytes = this.environmentEntries
                        .get(i)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    var ptr = environBufPtr + offset;
                    this.mod.instance.memory().writeI32(environPtr + (i * 4), ptr);
                    this.mod.instance.memory().write(ptr, bytes, 0, bytes.length);
                    this.mod.instance.memory().writeByte(ptr + bytes.length, (byte) 0);
                    offset += bytes.length + 1;
                }
                return 0;
            } catch (Exception e) {
                return EIO;
            }
        }

        private long wasiClockTimeGet(long[] args) {
            try {
                if (args.length < 3) {
                    return EINVAL;
                }
                var clockId = (int) args[0];
                var timePtr = (int) args[2];
                if (timePtr == 0) {
                    return EFAULT;
                }
                long now;
                switch (clockId) {
                    case 0:
                        now = System.currentTimeMillis() * 1_000_000L;
                        break;
                    case 1:
                    case 2:
                    case 3:
                        now = System.nanoTime();
                        break;
                    default:
                        return EINVAL;
                }
                this.mod.instance.memory().writeLong(timePtr, now);
                return 0;
            } catch (Exception e) {
                return EIO;
            }
        }

        private long wasiProcExit(long[] args) {
            var code = args.length > 0 ? (int) args[0] : 0;
            throw new ExitStatusException("proc_exit", code);
        }

        private long wasiFdClose(long[] args) {
            try {
                if (args.length < 1) {
                    return EINVAL;
                }
                var fd = (int) args[0];
                if (!descriptorExists(fd)) {
                    return EBADF;
                }
                closeDescriptor(fd);
                return 0;
            } catch (Exception e) {
                return EIO;
            }
        }

        private long wasiFdFdstatGet(long[] args) {
            try {
                if (args.length < 2) {
                    return EINVAL;
                }
                var fd = (int) args[0];
                var ptr = (int) args[1];
                var knownFd = descriptorExists(fd);
                if (!knownFd) {
                    return EBADF;
                }
                for (var i = 0; i < 24; i++) {
                    this.mod.instance.memory().writeByte(ptr + i, (byte) 0);
                }
                byte filetype = 0;
                if (this.fdTable.containsKey(fd)) {
                    filetype = 4;
                } else if (this.fdDeviceTable.containsKey(fd)) {
                    filetype = 2;
                } else if (resolveStdioFd(fd) >= 0) {
                    filetype = 2;
                } else if (this.dirEntries.containsKey(fd)) {
                    filetype = 3;
                } else {
                    filetype = 4;
                }
                this.mod.instance.memory().writeByte(ptr, filetype);
                this.mod.instance.memory().writeShort(ptr + 2, (short) 0);
                this.mod.instance.memory().writeLong(ptr + 8, 0L);
                this.mod.instance.memory().writeLong(ptr + 16, 0L);
                return 0;
            } catch (Exception e) {
                return EIO;
            }
        }

        private long wasiFdRead(long[] args) {
            try {
                if (args.length < 4) {
                    return EINVAL;
                }
                var fd = (int) args[0];
                var iovsPtr = (int) args[1];
                var iovsLen = (int) args[2];
                var nreadPtr = (int) args[3];
                var pipe = this.pipeReadTable.get(fd);
                if (pipe != null) {
                    var total = 0;
                    for (var i = 0; i < iovsLen; i++) {
                        var base = iovsPtr + (i * 8);
                        var bufPtr = (int) this.mod.instance.memory().readI32(base);
                        var len = (int) this.mod.instance.memory().readI32(base + 4);
                        if (len <= 0) {
                            continue;
                        }
                        var dst = new byte[len];
                        var read = readPipe(pipe, dst, 0, len);
                        if (read < 0) {
                            if (total == 0) {
                                return EAGAIN;
                            }
                            break;
                        }
                        if (read > 0) {
                            this.mod.instance.memory().write(bufPtr, dst, 0, read);
                            total += read;
                        }
                        if (read < len) {
                            break;
                        }
                    }
                    this.mod.instance.memory().writeI32(nreadPtr, total);
                    return 0;
                }
                if (this.fdDeviceTable.containsKey(fd)) {
                    var total = 0;
                    for (var i = 0; i < iovsLen; i++) {
                        var base = iovsPtr + (i * 8);
                        var bufPtr = (int) this.mod.instance.memory().readI32(base);
                        var len = (int) this.mod.instance.memory().readI32(base + 4);
                        if (len <= 0) {
                            continue;
                        }
                        var dst = new byte[len];
                        var read = readDevice(fd, dst, 0, len);
                        if (read > 0) {
                            this.mod.instance.memory().write(bufPtr, dst, 0, read);
                            total += read;
                        }
                        if (read < len) {
                            break;
                        }
                    }
                    this.mod.instance.memory().writeI32(nreadPtr, total);
                    return 0;
                }
                var stdioFd = resolveStdioFd(fd);
                var total = 0;
                for (var i = 0; i < iovsLen; i++) {
                    var base = iovsPtr + (i * 8);
                    var bufPtr = (int) this.mod.instance.memory().readI32(base);
                    var len = (int) this.mod.instance.memory().readI32(base + 4);
                    if (len <= 0) {
                        continue;
                    }
                    var ch = this.fdTable.get(fd);
                    if (ch == null && stdioFd == 0) {
                        break;
                    }
                    if (ch == null) {
                        return EBADF;
                    }
                    var buf = java.nio.ByteBuffer.allocate(len);
                    var read = ch.read(buf);
                    if (read <= 0) {
                        break;
                    }
                    this.mod.instance.memory().write(bufPtr, buf.array(), 0, read);
                    total += read;
                    if (read < len) {
                        break;
                    }
                }
                this.mod.instance.memory().writeI32(nreadPtr, total);
                return 0;
            } catch (ErrnoException e) {
                return e.errno;
            } catch (Exception e) {
                return EIO;
            }
        }

        private long wasiFdPread(long[] args) {
            try {
                if (args.length < 5) {
                    return EINVAL;
                }
                var fd = (int) args[0];
                var iovsPtr = (int) args[1];
                var iovsLen = (int) args[2];
                var offset = args[3];
                var nreadPtr = (int) args[4];
                if (this.pipeReadTable.containsKey(fd) || this.pipeWriteTable.containsKey(fd)) {
                    return ESPIPE;
                }
                var ch = this.fdTable.get(fd);
                if (ch == null) {
                    return EBADF;
                }
                var oldPos = ch.position();
                ch.position(offset);
                var total = 0;
                for (var i = 0; i < iovsLen; i++) {
                    var base = iovsPtr + (i * 8);
                    var bufPtr = (int) this.mod.instance.memory().readI32(base);
                    var len = (int) this.mod.instance.memory().readI32(base + 4);
                    if (len <= 0) {
                        continue;
                    }
                    var buf = java.nio.ByteBuffer.allocate(len);
                    var read = ch.read(buf);
                    if (read <= 0) {
                        break;
                    }
                    this.mod.instance.memory().write(bufPtr, buf.array(), 0, read);
                    total += read;
                    if (read < len) {
                        break;
                    }
                }
                ch.position(oldPos);
                this.mod.instance.memory().writeI32(nreadPtr, total);
                return 0;
            } catch (Exception e) {
                return EIO;
            }
        }

        private long wasiFdWrite(long[] args) {
            try {
                if (args.length < 4) {
                    return EINVAL;
                }
                var fd = (int) args[0];
                var iovsPtr = (int) args[1];
                var iovsLen = (int) args[2];
                var nwrittenPtr = (int) args[3];
                var pipe = this.pipeWriteTable.get(fd);
                if (pipe != null) {
                    var total = 0;
                    for (var i = 0; i < iovsLen; i++) {
                        var base = iovsPtr + (i * 8);
                        var bufPtr = (int) this.mod.instance.memory().readI32(base);
                        var len = (int) this.mod.instance.memory().readI32(base + 4);
                        if (len <= 0) {
                            continue;
                        }
                        var bytes = this.mod.instance.memory().readBytes(bufPtr, len);
                        total += writePipe(pipe, bytes, 0, bytes.length);
                    }
                    this.mod.instance.memory().writeI32(nwrittenPtr, total);
                    return 0;
                }
                if (this.fdDeviceTable.containsKey(fd)) {
                    var total = 0;
                    for (var i = 0; i < iovsLen; i++) {
                        var base = iovsPtr + (i * 8);
                        var bufPtr = (int) this.mod.instance.memory().readI32(base);
                        var len = (int) this.mod.instance.memory().readI32(base + 4);
                        if (len <= 0) {
                            continue;
                        }
                        var bytes = this.mod.instance.memory().readBytes(bufPtr, len);
                        total += writeDevice(fd, bytes, 0, bytes.length);
                    }
                    this.mod.instance.memory().writeI32(nwrittenPtr, total);
                    return 0;
                }
                var stdioFd = resolveStdioFd(fd);
                var ch = this.fdTable.get(fd);
                var total = 0;
                for (var i = 0; i < iovsLen; i++) {
                    var base = iovsPtr + (i * 8);
                    var bufPtr = (int) this.mod.instance.memory().readI32(base);
                    var len = (int) this.mod.instance.memory().readI32(base + 4);
                    if (len <= 0) {
                        continue;
                    }
                    var bytes = this.mod.instance.memory().readBytes(bufPtr, len);
                    if (ch != null) {
                        total += writeFully(ch, bytes);
                        continue;
                    }
                    if (stdioFd == 1) {
                        appendStdout(bytes);
                        total += len;
                        continue;
                    }
                    if (stdioFd == 2) {
                        appendStderr(bytes);
                        total += len;
                        continue;
                    }
                    return EBADF;
                }
                this.mod.instance.memory().writeI32(nwrittenPtr, total);
                return 0;
            } catch (ErrnoException e) {
                return e.errno;
            } catch (Exception e) {
                return EIO;
            }
        }

        private long wasiFdPwrite(long[] args) {
            try {
                if (args.length < 5) {
                    return EINVAL;
                }
                var fd = (int) args[0];
                var iovsPtr = (int) args[1];
                var iovsLen = (int) args[2];
                var offset = args[3];
                var nwrittenPtr = (int) args[4];
                if (this.pipeReadTable.containsKey(fd) || this.pipeWriteTable.containsKey(fd)) {
                    return ESPIPE;
                }
                var ch = this.fdTable.get(fd);
                if (ch == null) {
                    return EBADF;
                }
                var oldPos = ch.position();
                ch.position(offset);
                var total = 0;
                for (var i = 0; i < iovsLen; i++) {
                    var base = iovsPtr + (i * 8);
                    var bufPtr = (int) this.mod.instance.memory().readI32(base);
                    var len = (int) this.mod.instance.memory().readI32(base + 4);
                    if (len <= 0) {
                        continue;
                    }
                    var bytes = this.mod.instance.memory().readBytes(bufPtr, len);
                    total += writeFully(ch, bytes);
                }
                ch.position(oldPos);
                this.mod.instance.memory().writeI32(nwrittenPtr, total);
                return 0;
            } catch (Exception e) {
                return EIO;
            }
        }

        private long wasiFdSeek(long[] args) {
            try {
                if (args.length < 4) {
                    return EINVAL;
                }
                var fd = (int) args[0];
                var offset = args[1];
                var whence = (int) args[2];
                var newOffsetPtr = (int) args[3];
                if (this.pipeReadTable.containsKey(fd) || this.pipeWriteTable.containsKey(fd)) {
                    return ESPIPE;
                }
                if (this.fdDeviceTable.containsKey(fd)) {
                    var next = llseekDevice(fd, offset, whence);
                    this.mod.instance.memory().writeLong(newOffsetPtr, next);
                    return 0;
                }
                if (this.dirEntries.containsKey(fd)) {
                    var size = this.dirEntries.get(fd).size() * 280L;
                    var current = this.dirCursor.getOrDefault(fd, 0);
                    var next = switch (whence) {
                        case 0 -> offset;
                        case 1 -> current + offset;
                        case 2 -> size + offset;
                        default -> Long.MIN_VALUE;
                    };
                    if (next < 0) {
                        return EINVAL;
                    }
                    this.dirCursor.put(fd, (int) next);
                    this.mod.instance.memory().writeLong(newOffsetPtr, next);
                    return 0;
                }
                var ch = this.fdTable.get(fd);
                if (ch == null) {
                    return EBADF;
                }
                var next = switch (whence) {
                    case 0 -> offset;
                    case 1 -> ch.position() + offset;
                    case 2 -> ch.size() + offset;
                    default -> Long.MIN_VALUE;
                };
                if (next < 0) {
                    return EINVAL;
                }
                ch.position(next);
                this.mod.instance.memory().writeLong(newOffsetPtr, next);
                return 0;
            } catch (ErrnoException e) {
                return e.errno;
            } catch (Exception e) {
                return EIO;
            }
        }

        private long wasiFdSync(long[] args) {
            try {
                if (args.length < 1) {
                    return EINVAL;
                }
                var fd = (int) args[0];
                if (!descriptorExists(fd)) {
                    return EBADF;
                }
                var ch = this.fdTable.get(fd);
                if (ch == null) {
                    return 0;
                }
                if (ch instanceof java.nio.channels.FileChannel) {
                    ((java.nio.channels.FileChannel) ch).force(true);
                }
                return 0;
            } catch (Exception e) {
                return EIO;
            }
        }

        private long syscallWrite(long[] args) {
            try {
                if (args.length < 3) {
                    return err(EINVAL);
                }
                var fd = (int) args[0];
                var bufPtr = (int) args[1];
                var len = (int) args[2];
                var bytes = this.mod.instance.memory().readBytes(bufPtr, len);
                var pipe = this.pipeWriteTable.get(fd);
                if (pipe != null) {
                    return writePipe(pipe, bytes, 0, bytes.length);
                }
                if (this.fdDeviceTable.containsKey(fd)) {
                    return writeDevice(fd, bytes, 0, bytes.length);
                }
                var ch = this.fdTable.get(fd);
                var stdioFd = resolveStdioFd(fd);
                if (ch != null) {
                    return writeFully(ch, bytes);
                }
                if (stdioFd == 1) {
                    appendStdout(bytes);
                    return len;
                }
                if (stdioFd == 2) {
                    appendStderr(bytes);
                    return len;
                }
                return err(EBADF);
            } catch (ErrnoException e) {
                return err(e.errno);
            } catch (RuntimeException e) {
                return err(EFAULT);
            } catch (Exception e) {
                return err(EIO);
            }
        }

        private long syscallLseek(long[] args) {
            try {
                if (args.length < 3) {
                    return err(EINVAL);
                }
                var fd = (int) args[0];
                var offset = args[1];
                var whence = (int) args[2];
                if (this.pipeReadTable.containsKey(fd) || this.pipeWriteTable.containsKey(fd)) {
                    return err(ESPIPE);
                }
                if (this.fdDeviceTable.containsKey(fd)) {
                    return llseekDevice(fd, offset, whence);
                }
                if (this.dirEntries.containsKey(fd)) {
                    var size = this.dirEntries.get(fd).size() * 280L;
                    var current = this.dirCursor.getOrDefault(fd, 0);
                    var next = switch (whence) {
                        case 0 -> offset;
                        case 1 -> current + offset;
                        case 2 -> size + offset;
                        default -> -1;
                    };
                    if (next < 0) {
                        return err(ESPIPE);
                    }
                    this.dirCursor.put(fd, (int) next);
                    return next;
                }
                var ch = this.fdTable.get(fd);
                if (ch == null) {
                    return err(EBADF);
                }
                var next = switch (whence) {
                    case 0 -> offset;
                    case 1 -> ch.position() + offset;
                    case 2 -> ch.size() + offset;
                    default -> -1;
                };
                if (next < 0) {
                    return err(ESPIPE);
                }
                ch.position(next);
                return next;
            } catch (ErrnoException e) {
                return err(e.errno);
            } catch (Exception e) {
                return err(EIO);
            }
        }

        private long syscallFstat64(long[] args) {
            try {
                if (args.length < 2) {
                    return err(EINVAL);
                }
                var fd = (int) args[0];
                var buf = (int) args[1];
                if (this.pipeReadTable.containsKey(fd) || this.pipeWriteTable.containsKey(fd)) {
                    writePipeStat(buf);
                    return 0;
                }
                if (this.fdDeviceTable.containsKey(fd)) {
                    writePseudoStat(buf, fd);
                    return 0;
                }
                var stdioFd = resolveStdioFd(fd);
                if (stdioFd >= 0 && !this.fdTable.containsKey(fd)) {
                    writePseudoStat(buf, stdioFd);
                    return 0;
                }
                var path = this.fdPathTable.get(fd);
                if (path == null) {
                    return err(EBADF);
                }
                return doStat(path, buf, true);
            } catch (ErrnoException e) {
                return err(e.errno);
            }
        }

        private long syscallStat64(long[] args) {
            try {
                if (args.length < 2) {
                    return err(EINVAL);
                }
                var path = resolve(readCString((int) args[0]));
                var buf = (int) args[1];
                return doStat(path, buf, true);
            } catch (ErrnoException e) {
                return err(e.errno);
            }
        }

        private long syscallLstat64(long[] args) {
            try {
                if (args.length < 2) {
                    return err(EINVAL);
                }
                var path = resolve(readCString((int) args[0]));
                var buf = (int) args[1];
                return doStat(path, buf, false);
            } catch (ErrnoException e) {
                return err(e.errno);
            }
        }

        private long syscallNewfstatat(long[] args) {
            try {
                if (args.length < 4) {
                    return err(EINVAL);
                }
                var dirfd = (int) args[0];
                var path = readCString((int) args[1]);
                var buf = (int) args[2];
                var flags = (int) args[3];
                var nofollow = (flags & 256) != 0;
                var allowEmpty = (flags & 4096) != 0;
                var resolved = resolve(calculateAt(dirfd, path, allowEmpty));
                return doStat(resolved, buf, !nofollow);
            } catch (ErrnoException e) {
                return err(e.errno);
            }
        }

        private long syscallGetCwd(long[] args) {
            try {
                if (args.length < 2) {
                    return err(EINVAL);
                }
                var bufPtr = (int) args[0];
                var size = (int) args[1];
                if (size == 0) {
                    return err(EINVAL);
                }
                var data = (this.cwd + "\0").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                if (size < data.length) {
                    return err(ERANGE);
                }
                this.mod.instance.memory().write(bufPtr, data, 0, data.length);
                return data.length;
            } catch (Exception e) {
                return err(EIO);
            }
        }

        private long syscallMkdirAt(long[] args) {
            try {
                if (args.length < 2) {
                    return err(EINVAL);
                }
                var dirfd = (int) args[0];
                var path = calculateAt(dirfd, readCString((int) args[1]), false);
                var resolved = resolve(path);
                recordHostNote(
                    "__syscall_mkdirat path=\"" +
                    path +
                    "\" resolved=\"" +
                    toVirtualPath(resolved) +
                    "\" cwd=\"" +
                    this.cwd +
                    "\""
                );
                Files.createDirectories(resolved);
                return 0;
            } catch (ErrnoException e) {
                return err(e.errno);
            } catch (Exception e) {
                return err(EIO);
            }
        }

        private long syscallSymlinkAt(long[] args) {
            try {
                if (args.length < 3) {
                    return err(EINVAL);
                }
                var target = readCString((int) args[0]);
                var newdirfd = (int) args[1];
                var linkPath = calculateAt(newdirfd, readCString((int) args[2]), false);
                var resolvedLink = resolve(linkPath);
                var parent = resolvedLink.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.deleteIfExists(resolvedLink);
                var targetPath = target.startsWith("/")
                    ? resolve(target)
                    : java.nio.file.Path.of(target);
                Files.createSymbolicLink(resolvedLink, targetPath);
                return 0;
            } catch (ErrnoException e) {
                return err(e.errno);
            } catch (java.nio.file.FileAlreadyExistsException e) {
                return err(EEXIST);
            } catch (java.nio.file.AccessDeniedException e) {
                return err(EACCES);
            } catch (java.nio.file.NoSuchFileException e) {
                return err(ENOENT);
            } catch (Exception e) {
                return err(EIO);
            }
        }

        private long syscallUnlinkAt(long[] args) {
            try {
                if (args.length < 1) {
                    return err(EINVAL);
                }
                var dirfd = AT_FDCWD;
                var pathPtr = 0;
                var flags = 0;
                if (args.length == 1) {
                    // __syscall_rmdir compatibility path: only path pointer is supplied.
                    pathPtr = (int) args[0];
                    flags = AT_REMOVEDIR;
                } else if (args.length == 2) {
                    dirfd = (int) args[0];
                    pathPtr = (int) args[1];
                } else {
                    dirfd = (int) args[0];
                    pathPtr = (int) args[1];
                    flags = (int) args[2];
                }
                if ((flags & ~AT_REMOVEDIR) != 0) {
                    return err(EINVAL);
                }
                var path = calculateAt(dirfd, readCString(pathPtr), false);
                var resolved = resolve(path);
                var removeDir = (flags & AT_REMOVEDIR) != 0;
                if (!Files.exists(resolved, LinkOption.NOFOLLOW_LINKS)) {
                    return err(ENOENT);
                }
                if (removeDir) {
                    if (!Files.isDirectory(resolved, LinkOption.NOFOLLOW_LINKS)) {
                        return err(ENOTDIR);
                    }
                } else if (Files.isDirectory(resolved, LinkOption.NOFOLLOW_LINKS)) {
                    return err(EISDIR);
                }
                Files.delete(resolved);
                return 0;
            } catch (ErrnoException e) {
                return err(e.errno);
            } catch (java.nio.file.NoSuchFileException e) {
                return err(ENOENT);
            } catch (java.nio.file.NotDirectoryException e) {
                return err(ENOTDIR);
            } catch (java.nio.file.AccessDeniedException e) {
                return err(EACCES);
            } catch (Exception e) {
                return err(EIO);
            }
        }

        private long syscallRenameAt(long[] args) {
            try {
                if (args.length < 4) {
                    return err(EINVAL);
                }
                var oldPath = resolve(calculateAt((int) args[0], readCString((int) args[1]), false));
                var newPath = resolve(calculateAt((int) args[2], readCString((int) args[3]), false));
                var parent = newPath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
                return 0;
            } catch (ErrnoException e) {
                return err(e.errno);
            } catch (Exception e) {
                return err(EIO);
            }
        }

        private long syscallFadvise64(long[] args) {
            try {
                if (args.length < 2) {
                    return err(EINVAL);
                }
                var fd = (int) args[0];
                if (!descriptorExists(fd)) {
                    return err(EBADF);
                }
                // posix_fadvise is advisory only; success without side effects is acceptable.
                return 0;
            } catch (Exception e) {
                return err(EIO);
            }
        }

        private long syscallFallocate(long[] args) {
            try {
                if (args.length < 4) {
                    return err(EINVAL);
                }
                var fd = (int) args[0];
                if (!descriptorExists(fd)) {
                    return err(EBADF);
                }
                var mode = (int) args[1];
                var offset = toI53(args[2]);
                var len = toI53(args[3]);
                if (offset == null || len == null) {
                    return EOVERFLOW;
                }
                if (mode != 0 || offset < 0 || len < 0) {
                    return err(EINVAL);
                }
                if (len == 0) {
                    return 0;
                }
                if (this.pipeReadTable.containsKey(fd) || this.pipeWriteTable.containsKey(fd)) {
                    return err(ENODEV);
                }
                if (this.fdDeviceTable.containsKey(fd) || this.dirEntries.containsKey(fd)) {
                    return err(ENODEV);
                }
                var channel = this.fdTable.get(fd);
                if (channel == null) {
                    return err(EBADF);
                }
                if (!(channel instanceof java.nio.channels.FileChannel fileChannel)) {
                    return 0;
                }
                var requiredSize = offset + len;
                if (requiredSize < 0 || requiredSize < offset) {
                    return err(EINVAL);
                }
                var originalPosition = fileChannel.position();
                try {
                    if (fileChannel.size() < requiredSize) {
                        fileChannel.position(requiredSize - 1);
                        fileChannel.write(java.nio.ByteBuffer.wrap(new byte[] { 0 }));
                    }
                } finally {
                    fileChannel.position(originalPosition);
                }
                return 0;
            } catch (Exception e) {
                return err(EIO);
            }
        }

        private long syscallNewselect(long[] args) {
            try {
                if (args.length < 5) {
                    return err(EINVAL);
                }
                var nfds = (int) args[0];
                if (nfds < 0) {
                    return err(EINVAL);
                }
                var memory = this.mod.instance.memory();
                var readSet = new RuntimeSelectState(memory, (int) args[1], nfds);
                var writeSet = new RuntimeSelectState(memory, (int) args[2], nfds);
                var exceptSet = new RuntimeSelectState(memory, (int) args[3], nfds);
                var timeoutPtr = (int) args[4];
                if (timeoutPtr != 0) {
                    var timeoutMs = selectTimeoutMillis(timeoutPtr);
                    if (timeoutMs < 0) {
                        return err(EINVAL);
                    }
                }
                return computeSelectReady(nfds, readSet, writeSet, exceptSet);
            } catch (ErrnoException e) {
                return err(e.errno);
            } catch (RuntimeException e) {
                return err(EFAULT);
            } catch (Exception e) {
                return err(EIO);
            }
        }

        private int computeSelectReady(
            int nfds,
            RuntimeSelectState readSet,
            RuntimeSelectState writeSet,
            RuntimeSelectState exceptSet
        ) throws ErrnoException {
            readSet.clearOutput();
            writeSet.clearOutput();
            exceptSet.clearOutput();
            var total = 0;
            for (var fd = 0; fd < nfds; fd++) {
                if (!readSet.isSelected(fd) && !writeSet.isSelected(fd) && !exceptSet.isSelected(fd)) {
                    continue;
                }
                var pollMask = pollFdMask(fd);
                if ((pollMask & RuntimeSelectState.READ_READY) != 0 && readSet.isSelected(fd)) {
                    readSet.markReady(fd);
                    total++;
                }
                if ((pollMask & RuntimeSelectState.WRITE_READY) != 0 && writeSet.isSelected(fd)) {
                    writeSet.markReady(fd);
                    total++;
                }
                if ((pollMask & RuntimeSelectState.EXCEPT_READY) != 0 && exceptSet.isSelected(fd)) {
                    exceptSet.markReady(fd);
                    total++;
                }
            }
            return total;
        }

        private long selectTimeoutMillis(int timeoutPtr) {
            if (timeoutPtr == 0) {
                return 0;
            }
            var sec = this.mod.instance.memory().readI32(timeoutPtr);
            var usec = this.mod.instance.memory().readI32(timeoutPtr + 4);
            if (sec < 0 || usec < 0 || usec >= 1_000_000) {
                return -1;
            }
            var millisFromSec = sec * 1000L;
            var millisFromUsec = usec / 1000L;
            return Math.max(0L, millisFromSec + millisFromUsec);
        }

        private int pollFdMask(int fd) throws ErrnoException {
            if (!descriptorExists(fd)) {
                throw new ErrnoException(EBADF);
            }
            var pipeRead = this.pipeReadTable.get(fd);
            if (pipeRead != null) {
                synchronized (pipeRead) {
                    return pipeRead.buffers.isEmpty() ? 0 : 1;
                }
            }
            if (this.pipeWriteTable.containsKey(fd)) {
                return 4;
            }
            if (this.dirEntries.containsKey(fd)) {
                return 1;
            }
            var socket = this.socketStateTable.get(fd);
            if (socket != null) {
                var mask = RuntimeSelectState.WRITE_READY;
                if (!socket.recvQueue.isEmpty()) {
                    mask |= RuntimeSelectState.READ_READY;
                } else if (
                    socket.type == SOCK_STREAM &&
                    (socket.connectedAddress == null || socket.connectedPort <= 0)
                ) {
                    // Mirror SOCKFS poll semantics: disconnected streams are readable with EOF/error.
                    mask |= RuntimeSelectState.READ_READY;
                }
                return mask;
            }
            if (
                this.fdTable.containsKey(fd) ||
                this.fdDeviceTable.containsKey(fd) ||
                resolveStdioFd(fd) >= 0
            ) {
                return 5;
            }
            return 0;
        }

        private long syscallFcntl64(long[] args) {
            try {
                if (args.length < 2) {
                    return err(EINVAL);
                }
                var fd = (int) args[0];
                var cmd = (int) args[1];
                if (!descriptorExists(fd)) {
                    return err(EBADF);
                }
                return switch (cmd) {
                    case 0 -> {
                        if (args.length < 3) {
                            yield err(EINVAL);
                        }
                        var minFd = (int) args[2];
                        if (minFd < 0) {
                            yield err(EINVAL);
                        }
                        var newFd = allocateFd(minFd);
                        duplicateDescriptor(fd, newFd);
                        yield newFd;
                    }
                    case 1, 2 -> 0;
                    case 3 -> this.fdFlagsTable.getOrDefault(fd, 0);
                    case 4 -> {
                        if (args.length < 3) {
                            yield err(EINVAL);
                        }
                        var arg = (int) args[2];
                        this.fdFlagsTable.put(fd, this.fdFlagsTable.getOrDefault(fd, 0) | arg);
                        yield 0;
                    }
                    case 12 -> {
                        if (args.length < 3) {
                            yield err(EINVAL);
                        }
                        var lockPtr = (int) args[2];
                        this.mod.instance.memory().writeShort(lockPtr, (short) 2);
                        yield 0;
                    }
                    case 13, 14 -> 0;
                    default -> err(EINVAL);
                };
            } catch (ErrnoException e) {
                return err(e.errno);
            } catch (Exception e) {
                return err(EIO);
            }
        }

        private long syscallIoctl(long[] args) {
            try {
                if (args.length < 2) {
                    return err(EINVAL);
                }
                var fd = (int) args[0];
                if (!descriptorExists(fd)) {
                    return err(EBADF);
                }
                var op = (int) args[1];
                var hasTty = resolveStdioFd(fd) >= 0 && !this.fdTable.containsKey(fd);
                return switch (op) {
                    case RuntimeIoctlContract.TCSETS,
                        RuntimeIoctlContract.TCSETSW,
                        RuntimeIoctlContract.TCSETSF,
                        RuntimeIoctlContract.TIOCSPGRP,
                        RuntimeIoctlContract.TCFLSH -> {
                            if (!hasTty) {
                                yield err(ENOTTY);
                            }
                            resolveIoctlArgPointer(args);
                            yield 0;
                        }
                    case RuntimeIoctlContract.TCGETS -> {
                        if (!hasTty) {
                            yield err(ENOTTY);
                        }
                        var argp = resolveIoctlArgPointer(args);
                        if (argp != 0) {
                            this.mod.instance.memory().writeI32(argp, 0);
                            this.mod.instance.memory().writeI32(argp + 4, 0);
                            this.mod.instance.memory().writeI32(argp + 8, 0);
                            this.mod.instance.memory().writeI32(argp + 12, 0);
                            for (var i = 0; i < 32; i++) {
                                this.mod.instance.memory().writeByte(argp + 17 + i, (byte) 0);
                            }
                        }
                        yield 0;
                    }
                    case RuntimeIoctlContract.TCSETA,
                        RuntimeIoctlContract.TCSETAW,
                        RuntimeIoctlContract.TCSETAF -> {
                            if (!hasTty) {
                                yield err(ENOTTY);
                            }
                            resolveIoctlArgPointer(args);
                            yield 0;
                        }
                    case RuntimeIoctlContract.TIOCGPGRP -> {
                        if (!hasTty) {
                            yield err(ENOTTY);
                        }
                        var argp = resolveIoctlArgPointer(args);
                        if (argp != 0) {
                            this.mod.instance.memory().writeI32(argp, 0);
                        }
                        yield 0;
                    }
                    case RuntimeIoctlContract.TIOCSPGRP_ALT -> hasTty ? err(EINVAL) : err(ENOTTY);
                    case RuntimeIoctlContract.TIOCGPTPEER -> {
                        var argp = resolveIoctlArgPointer(args);
                        var socket = this.socketStateTable.get(fd);
                        if (socket == null) {
                            yield err(ENOTTY);
                        }
                        if (argp != 0) {
                            var bytes = socket.recvQueue.peekFirst();
                            this.mod.instance.memory().writeI32(
                                argp,
                                bytes != null ? bytes.buffer.length : 0
                            );
                        }
                        yield 0;
                    }
                    case RuntimeIoctlContract.TIOCGWINSZ -> {
                        if (!hasTty) {
                            yield err(ENOTTY);
                        }
                        var argp = resolveIoctlArgPointer(args);
                        if (argp != 0) {
                            this.mod.instance.memory().writeShort(argp, (short) 24);
                            this.mod.instance.memory().writeShort(argp + 2, (short) 80);
                            this.mod.instance.memory().writeShort(argp + 4, (short) 0);
                            this.mod.instance.memory().writeShort(argp + 6, (short) 0);
                        }
                        yield 0;
                    }
                    default -> RuntimeIoctlContract.defaultErrno(op, EINVAL);
                };
            } catch (ErrnoException e) {
                return err(e.errno);
            } catch (RuntimeException e) {
                return err(EFAULT);
            }
        }

        private int resolveIoctlArgPointer(long[] args) throws ErrnoException {
            if (args.length < 3) {
                return 0;
            }
            var varargsPtr = (int) args[2];
            if (varargsPtr == 0) {
                return 0;
            }
            try {
                return (int) this.mod.instance.memory().readI32(varargsPtr);
            } catch (RuntimeException e) {
                throw new ErrnoException(EFAULT);
            }
        }

        private long syscallGetdents64(long[] args) {
            try {
                if (args.length < 3) {
                    return err(EINVAL);
                }
                var fd = (int) args[0];
                var dirp = (int) args[1];
                var count = (int) args[2];
                var entries = this.dirEntries.get(fd);
                if (entries == null) {
                    return err(EBADF);
                }
                var structSize = 280;
                if (count < structSize) {
                    return 0;
                }
                var off = this.dirCursor.getOrDefault(fd, 0);
                var startIdx = Math.max(0, off / structSize);
                var maxEntries = Math.max(0, count / structSize);
                var endIdx = Math.min(entries.size(), startIdx + maxEntries);
                var pos = 0;
                var idx = startIdx;
                var basePath = this.fdPathTable.get(fd);
                for (; idx < endIdx; idx++) {
                    var name = entries.get(idx);
                    byte type;
                    long id;
                    if (".".equals(name) || "..".equals(name)) {
                        type = 4;
                        var target = ".".equals(name)
                            ? basePath
                            : (basePath != null ? basePath.getParent() : null);
                        if (target == null) {
                            target = this.rootDir;
                        }
                        id = this.fsNodeIdIndex.idFor(target);
                    } else {
                        var child = basePath != null ? basePath.resolve(name) : null;
                        if (child == null || !Files.exists(child, LinkOption.NOFOLLOW_LINKS)) {
                            continue;
                        }
                        if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                            type = 4;
                        } else if (Files.isSymbolicLink(child)) {
                            type = 10;
                        } else if (isDeviceNode(child)) {
                            type = 2;
                        } else {
                            type = 8;
                        }
                        id = this.fsNodeIdIndex.idFor(child);
                    }
                    var p = dirp + pos;
                    this.mod.instance.memory().writeLong(p, id);
                    this.mod.instance.memory().writeLong(p + 8, (idx + 1L) * structSize);
                    this.mod.instance.memory().writeShort(p + 16, (short) structSize);
                    this.mod.instance.memory().writeByte(p + 18, type);
                    var nameBytes = name.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    var writeLen = Math.min(nameBytes.length, 255);
                    this.mod.instance.memory().write(p + 19, nameBytes, 0, writeLen);
                    this.mod.instance.memory().writeByte(p + 19 + writeLen, (byte) 0);
                    pos += structSize;
                }
                this.dirCursor.put(fd, idx * structSize);
                return pos;
            } catch (Exception e) {
                return err(EIO);
            }
        }

        private long syscallReadlinkAt(long[] args) {
            try {
                if (args.length < 4) {
                    return err(EINVAL);
                }
                var dirfd = (int) args[0];
                var path = resolve(calculateAt(dirfd, readCString((int) args[1]), false));
                var bufPtr = (int) args[2];
                var bufSize = (int) args[3];
                if (bufSize <= 0) {
                    return err(EINVAL);
                }
                if (!Files.isSymbolicLink(path)) {
                    return err(EINVAL);
                }
                var link = Files.readSymbolicLink(path).toString()
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                var writeLen = Math.min(link.length, bufSize);
                var endChar = this.mod.instance.memory().read(bufPtr + writeLen);
                this.mod.instance.memory().write(bufPtr, link, 0, writeLen);
                this.mod.instance.memory().writeByte(bufPtr + writeLen, endChar);
                return writeLen;
            } catch (ErrnoException e) {
                return err(e.errno);
            } catch (RuntimeException e) {
                return err(EFAULT);
            } catch (java.nio.file.NotLinkException e) {
                return err(EINVAL);
            } catch (java.nio.file.NoSuchFileException e) {
                return err(ENOENT);
            } catch (java.nio.file.NotDirectoryException e) {
                return err(ENOTDIR);
            } catch (java.nio.file.AccessDeniedException e) {
                return err(EACCES);
            } catch (Exception e) {
                return err(EIO);
            }
        }

        private long syscallTruncate64(long[] args) {
            try {
                if (args.length < 2) {
                    return err(EINVAL);
                }
                var length = toI53(args[1]);
                if (length == null) {
                    return EOVERFLOW;
                }
                if (length < 0) {
                    return err(EINVAL);
                }
                if (args.length == 2) {
                    var fd = (int) args[0];
                    var ch = this.fdTable.get(fd);
                    if (ch == null) {
                        return err(EBADF);
                    }
                    ch.truncate(length);
                    return 0;
                }
                var path = resolve(readCString((int) args[0]));
                try (var ch = Files.newByteChannel(
                    path,
                    EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE)
                )) {
                    ch.truncate(length);
                }
                return 0;
            } catch (java.nio.file.NoSuchFileException e) {
                return err(ENOENT);
            } catch (java.nio.file.AccessDeniedException e) {
                return err(EACCES);
            } catch (Exception e) {
                return err(EIO);
            }
        }

        private long syscallFdatasync(long[] args) {
            try {
                if (args.length < 1) {
                    return err(EINVAL);
                }
                var fd = (int) args[0];
                if (!descriptorExists(fd)) {
                    return err(EBADF);
                }
                var ch = this.fdTable.get(fd);
                if (ch == null) {
                    return 0;
                }
                if (ch instanceof java.nio.channels.FileChannel) {
                    ((java.nio.channels.FileChannel) ch).force(true);
                }
                return 0;
            } catch (Exception e) {
                return err(EIO);
            }
        }

        private long syscallChdir(long[] args) {
            try {
                if (args.length < 1) {
                    return err(EINVAL);
                }
                var rawPath = readCString((int) args[0]);
                recordHostNote("__syscall_chdir path=\"" + rawPath + "\" cwd=\"" + this.cwd + "\"");
                if (rawPath.isEmpty()) {
                    return err(ENOENT);
                }
                var path = resolve(rawPath);
                recordHostNote(
                    "__syscall_chdir resolved=\"" +
                    toVirtualPath(path) +
                    "\" exists=" +
                    Files.exists(path) +
                    " isDir=" +
                    Files.isDirectory(path)
                );
                if (!Files.exists(path)) {
                    return err(ENOENT);
                }
                if (!Files.isDirectory(path)) {
                    return err(ENOTDIR);
                }
                this.cwd = toVirtualPath(path);
                return 0;
            } catch (Exception e) {
                return err(ENOENT);
            }
        }

        private long syscallFaccessAt(long[] args) {
            try {
                if (args.length < 4) {
                    return err(EINVAL);
                }
                var dirfd = (int) args[0];
                var rawPath = readCString((int) args[1]);
                var flags = (int) args[3];
                var supportedFlags = AT_SYMLINK_NOFOLLOW | AT_EACCESS | AT_EMPTY_PATH;
                if ((flags & ~supportedFlags) != 0) {
                    return err(EINVAL);
                }
                var allowEmpty = (flags & AT_EMPTY_PATH) != 0;
                var path = resolve(calculateAt(dirfd, rawPath, allowEmpty));
                recordHostNote(
                    "__syscall_faccessat rawPath=\"" +
                    rawPath +
                    "\" resolved=\"" +
                    toVirtualPath(path) +
                    "\" cwd=\"" +
                    this.cwd +
                    "\""
                );
                var amode = (int) args[2];
                if ((amode & ~7) != 0) {
                    return err(EINVAL);
                }
                var noFollow = (flags & AT_SYMLINK_NOFOLLOW) != 0;
                var exists = noFollow
                    ? Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                    : Files.exists(path);
                if (!exists) {
                    return err(ENOENT);
                }
                if ((amode & 4) != 0 && !Files.isReadable(path)) {
                    return err(EACCES);
                }
                if ((amode & 2) != 0 && !Files.isWritable(path)) {
                    return err(EACCES);
                }
                if ((amode & 1) != 0 && !Files.isExecutable(path)) {
                    return err(EACCES);
                }
                return 0;
            } catch (ErrnoException e) {
                return err(e.errno);
            } catch (Exception e) {
                return err(ENOENT);
            }
        }

        private long syscallChmod(long[] args) {
            try {
                if (args.length < 2) {
                    return err(EINVAL);
                }
                var path = resolve(readCString((int) args[0]));
                var mode = (int) args[1];
                if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                    return err(ENOENT);
                }
                try {
                    Files.setPosixFilePermissions(path, buildPosixPermissions(mode));
                    return 0;
                } catch (UnsupportedOperationException unsupported) {
                    // Non-POSIX file system fallback.
                    var file = path.toFile();
                    var readOk = file.setReadable((mode & 0444) != 0, false);
                    var writeOk = file.setWritable((mode & 0222) != 0, false);
                    var execOk = file.setExecutable((mode & 0111) != 0, false);
                    return readOk && writeOk && execOk ? 0 : err(EACCES);
                }
            } catch (java.nio.file.NoSuchFileException e) {
                return err(ENOENT);
            } catch (java.nio.file.AccessDeniedException e) {
                return err(EACCES);
            } catch (Exception e) {
                return err(EIO);
            }
        }

        private static Set<java.nio.file.attribute.PosixFilePermission> buildPosixPermissions(
            int mode
        ) {
            var perms = EnumSet.noneOf(java.nio.file.attribute.PosixFilePermission.class);
            if ((mode & 0400) != 0) {
                perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_READ);
            }
            if ((mode & 0200) != 0) {
                perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
            }
            if ((mode & 0100) != 0) {
                perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE);
            }
            if ((mode & 0040) != 0) {
                perms.add(java.nio.file.attribute.PosixFilePermission.GROUP_READ);
            }
            if ((mode & 0020) != 0) {
                perms.add(java.nio.file.attribute.PosixFilePermission.GROUP_WRITE);
            }
            if ((mode & 0010) != 0) {
                perms.add(java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE);
            }
            if ((mode & 0004) != 0) {
                perms.add(java.nio.file.attribute.PosixFilePermission.OTHERS_READ);
            }
            if ((mode & 0002) != 0) {
                perms.add(java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE);
            }
            if ((mode & 0001) != 0) {
                perms.add(java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE);
            }
            return perms;
        }

        private long doStat(Path path, int statPtr, boolean followLinks) throws ErrnoException {
            try {
                if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new ErrnoException(ENOENT);
                }
                var opts = followLinks ? new LinkOption[] {} : new LinkOption[] { LinkOption.NOFOLLOW_LINKS };
                var attrs = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class, opts);
                writeStatStruct(statPtr, path, attrs);
                return 0;
            } catch (java.nio.file.NoSuchFileException e) {
                throw new ErrnoException(ENOENT);
            } catch (java.nio.file.NotDirectoryException e) {
                throw new ErrnoException(ENOTDIR);
            } catch (ErrnoException e) {
                throw e;
            } catch (Exception e) {
                throw new ErrnoException(EIO);
            }
        }

        private static int writeFully(SeekableByteChannel ch, byte[] bytes) throws java.io.IOException {
            if (bytes.length == 0) {
                return 0;
            }
            var buffer = java.nio.ByteBuffer.wrap(bytes);
            var written = 0;
            while (buffer.hasRemaining()) {
                var n = ch.write(buffer);
                if (n < 0) {
                    break;
                }
                if (n == 0) {
                    throw new java.io.IOException("write returned 0 with remaining bytes");
                }
                written += n;
            }
            return written;
        }

        private int readPipe(PipeState pipe, byte[] dest, int offset, int length) {
            if (length <= 0) {
                return 0;
            }
            synchronized (pipe) {
                if (pipe.buffers.isEmpty()) {
                    return -1;
                }
                var remaining = length;
                var written = 0;
                while (remaining > 0 && !pipe.buffers.isEmpty()) {
                    var head = pipe.buffers.peekFirst();
                    var available = head.length - pipe.headOffset;
                    var take = Math.min(remaining, available);
                    System.arraycopy(head, pipe.headOffset, dest, offset + written, take);
                    written += take;
                    remaining -= take;
                    pipe.headOffset += take;
                    if (pipe.headOffset >= head.length) {
                        pipe.buffers.removeFirst();
                        pipe.headOffset = 0;
                    }
                }
                return written;
            }
        }

        private int writePipe(PipeState pipe, byte[] src, int offset, int length) {
            if (length <= 0) {
                return 0;
            }
            synchronized (pipe) {
                var copy = Arrays.copyOfRange(src, offset, offset + length);
                pipe.buffers.addLast(copy);
                return length;
            }
        }

        private static void retainPipe(PipeState pipe) {
            synchronized (pipe) {
                pipe.refCount++;
            }
        }

        private static void releasePipe(PipeState pipe) {
            synchronized (pipe) {
                pipe.refCount--;
                if (pipe.refCount <= 0) {
                    pipe.buffers.clear();
                    pipe.headOffset = 0;
                }
            }
        }

        private void writePseudoStat(int statPtr, int fd) {
            this.mod.instance.memory().writeI32(statPtr, 0);
            this.mod.instance.memory().writeI32(statPtr + 4, 020000 | 0666);
            this.mod.instance.memory().writeI32(statPtr + 8, 1);
            this.mod.instance.memory().writeI32(statPtr + 12, 0);
            this.mod.instance.memory().writeI32(statPtr + 16, 0);
            this.mod.instance.memory().writeI32(statPtr + 20, fd);
            this.mod.instance.memory().writeLong(statPtr + 24, 0);
            this.mod.instance.memory().writeI32(statPtr + 32, 4096);
            this.mod.instance.memory().writeI32(statPtr + 36, 0);
            this.mod.instance.memory().writeLong(statPtr + 40, 0);
            this.mod.instance.memory().writeI32(statPtr + 48, 0);
            this.mod.instance.memory().writeLong(statPtr + 56, 0);
            this.mod.instance.memory().writeI32(statPtr + 64, 0);
            this.mod.instance.memory().writeLong(statPtr + 72, 0);
            this.mod.instance.memory().writeI32(statPtr + 80, 0);
            this.mod.instance.memory().writeLong(statPtr + 88, fd + 1L);
        }

        private void writePipeStat(int statPtr) {
            this.mod.instance.memory().writeI32(statPtr, 0);
            this.mod.instance.memory().writeI32(statPtr + 4, 0010000 | 0666);
            this.mod.instance.memory().writeI32(statPtr + 8, 1);
            this.mod.instance.memory().writeI32(statPtr + 12, 0);
            this.mod.instance.memory().writeI32(statPtr + 16, 0);
            this.mod.instance.memory().writeI32(statPtr + 20, 0);
            this.mod.instance.memory().writeLong(statPtr + 24, 0);
            this.mod.instance.memory().writeI32(statPtr + 32, 4096);
            this.mod.instance.memory().writeI32(statPtr + 36, 0);
            this.mod.instance.memory().writeLong(statPtr + 40, 0);
            this.mod.instance.memory().writeI32(statPtr + 48, 0);
            this.mod.instance.memory().writeLong(statPtr + 56, 0);
            this.mod.instance.memory().writeI32(statPtr + 64, 0);
            this.mod.instance.memory().writeLong(statPtr + 72, 0);
            this.mod.instance.memory().writeI32(statPtr + 80, 0);
            this.mod.instance.memory().writeLong(statPtr + 88, 0);
        }

        private boolean isDeviceNode(Path path) {
            var virtualPath = toVirtualPath(path);
            return this.deviceRegistry.deviceNode(virtualPath) != null;
        }

        private void writeStatStruct(
            int statPtr,
            Path path,
            java.nio.file.attribute.BasicFileAttributes attrs
        ) {
            var mode = attrs.isDirectory() ? 0040000 : attrs.isSymbolicLink() ? 0120000 : 0100000;
            mode |= attrs.isDirectory() ? 0755 : 0644;
            var size = attrs.size();
            var atime = attrs.lastAccessTime().toMillis();
            var mtime = attrs.lastModifiedTime().toMillis();
            var ctime = attrs.creationTime().toMillis();
            var blocks = (int) ((size + 511) / 512);
            var fileKey = attrs.fileKey();
            var ino = fileKey != null ? Math.abs(fileKey.hashCode()) : Math.abs(path.toString().hashCode());

            this.mod.instance.memory().writeI32(statPtr, 1);
            this.mod.instance.memory().writeI32(statPtr + 4, mode);
            this.mod.instance.memory().writeI32(statPtr + 8, 1);
            this.mod.instance.memory().writeI32(statPtr + 12, 0);
            this.mod.instance.memory().writeI32(statPtr + 16, 0);
            this.mod.instance.memory().writeI32(statPtr + 20, 0);
            this.mod.instance.memory().writeLong(statPtr + 24, size);
            this.mod.instance.memory().writeI32(statPtr + 32, 4096);
            this.mod.instance.memory().writeI32(statPtr + 36, blocks);
            this.mod.instance.memory().writeLong(statPtr + 40, atime / 1000);
            this.mod.instance.memory().writeI32(statPtr + 48, (int) ((atime % 1000) * 1_000_000));
            this.mod.instance.memory().writeLong(statPtr + 56, mtime / 1000);
            this.mod.instance.memory().writeI32(statPtr + 64, (int) ((mtime % 1000) * 1_000_000));
            this.mod.instance.memory().writeLong(statPtr + 72, ctime / 1000);
            this.mod.instance.memory().writeI32(statPtr + 80, (int) ((ctime % 1000) * 1_000_000));
            this.mod.instance.memory().writeLong(statPtr + 88, ino);
        }

        private int allocateFd(int minFd) throws ErrnoException {
            var start = Math.max(0, minFd);
            for (var fd = start; fd <= MAX_OPEN_FDS; fd++) {
                if (!descriptorExists(fd)) {
                    return fd;
                }
            }
            throw new ErrnoException(EMFILE);
        }

        private boolean descriptorExists(int fd) {
            return (
                fd >= 0 &&
                this.stdioAliases.containsKey(fd)
            ) ||
            this.fdTable.containsKey(fd) ||
            this.fdPathTable.containsKey(fd) ||
            this.fdDeviceTable.containsKey(fd) ||
            this.dirEntries.containsKey(fd) ||
            this.pipeReadTable.containsKey(fd) ||
            this.pipeWriteTable.containsKey(fd) ||
            this.socketTypeTable.containsKey(fd);
        }

        private int resolveStdioFd(int fd) {
            return this.stdioAliases.getOrDefault(fd, -1);
        }

        private void duplicateDescriptor(int oldFd, int newFd) throws ErrnoException {
            if (!descriptorExists(oldFd)) {
                throw new ErrnoException(EBADF);
            }
            var channel = this.fdTable.get(oldFd);
            if (channel != null) {
                this.fdTable.put(newFd, channel);
            } else {
                this.fdTable.remove(newFd);
            }
            if (this.fdPathTable.containsKey(oldFd)) {
                this.fdPathTable.put(newFd, this.fdPathTable.get(oldFd));
            } else {
                this.fdPathTable.remove(newFd);
            }
            if (this.fdFlagsTable.containsKey(oldFd)) {
                this.fdFlagsTable.put(newFd, this.fdFlagsTable.get(oldFd));
            } else {
                this.fdFlagsTable.remove(newFd);
            }
            if (this.fdDeviceTable.containsKey(oldFd)) {
                this.fdDeviceTable.put(newFd, this.fdDeviceTable.get(oldFd));
                this.fdDevicePosition.put(newFd, this.fdDevicePosition.getOrDefault(oldFd, 0));
            } else {
                this.fdDeviceTable.remove(newFd);
                this.fdDevicePosition.remove(newFd);
            }
            if (this.dirEntries.containsKey(oldFd)) {
                this.dirEntries.put(newFd, this.dirEntries.get(oldFd));
                this.dirCursor.put(newFd, this.dirCursor.getOrDefault(oldFd, 0));
            } else {
                this.dirEntries.remove(newFd);
                this.dirCursor.remove(newFd);
            }
            if (this.socketTypeTable.containsKey(oldFd)) {
                this.socketTypeTable.put(newFd, this.socketTypeTable.get(oldFd));
            } else {
                this.socketTypeTable.remove(newFd);
            }
            if (this.socketStateTable.containsKey(oldFd)) {
                this.socketStateTable.put(newFd, this.socketStateTable.get(oldFd));
            } else {
                this.socketStateTable.remove(newFd);
            }
            var readPipe = this.pipeReadTable.get(oldFd);
            if (readPipe != null) {
                retainPipe(readPipe);
                this.pipeReadTable.put(newFd, readPipe);
                this.pipeWriteTable.remove(newFd);
            } else {
                var writePipe = this.pipeWriteTable.get(oldFd);
                if (writePipe != null) {
                    retainPipe(writePipe);
                    this.pipeWriteTable.put(newFd, writePipe);
                    this.pipeReadTable.remove(newFd);
                } else {
                    this.pipeReadTable.remove(newFd);
                    this.pipeWriteTable.remove(newFd);
                }
            }
            var stdioFd = resolveStdioFd(oldFd);
            if (stdioFd >= 0 && channel == null) {
                this.stdioAliases.put(newFd, stdioFd);
            } else {
                this.stdioAliases.remove(newFd);
            }
        }

        private void closeDescriptor(int fd) throws Exception {
            var channel = this.fdTable.remove(fd);
            if (
                channel != null &&
                this.fdTable.values().stream().noneMatch(existing -> existing == channel)
            ) {
                channel.close();
            }
            this.fdPathTable.remove(fd);
            this.fdFlagsTable.remove(fd);
            this.fdDeviceTable.remove(fd);
            this.fdDevicePosition.remove(fd);
            this.dirEntries.remove(fd);
            this.dirCursor.remove(fd);
            var readPipe = this.pipeReadTable.remove(fd);
            if (readPipe != null) {
                releasePipe(readPipe);
            }
            var writePipe = this.pipeWriteTable.remove(fd);
            if (writePipe != null) {
                releasePipe(writePipe);
            }
            this.socketTypeTable.remove(fd);
            var socketState = this.socketStateTable.remove(fd);
            if (socketState != null && socketState.bindingKey != null) {
                var hasAlias = false;
                Integer replacementFd = null;
                for (var entry : this.socketStateTable.entrySet()) {
                    if (entry.getValue() == socketState) {
                        hasAlias = true;
                        replacementFd = entry.getKey();
                        break;
                    }
                }
                if (hasAlias) {
                    if (replacementFd != null) {
                        this.socketBindings.put(socketState.bindingKey, replacementFd);
                    }
                } else {
                    this.socketBindings.remove(socketState.bindingKey);
                }
            }
            this.stdioAliases.remove(fd);
        }

        private String readCString(int ptr) {
            if (ptr == 0) {
                return "";
            }
            return this.mod.instance.memory().readCString(ptr);
        }

        private String calculateAt(int dirfd, String path, boolean allowEmpty) throws ErrnoException {
            if (path != null && !path.isEmpty() && (path.charAt(0) == '/')) {
                return path;
            }
            var dir = resolveDirFd(dirfd);
            if (path == null || path.isEmpty()) {
                if (!allowEmpty) {
                    throw new ErrnoException(ENOENT);
                }
                return dir;
            }
            return dir + "/" + path;
        }

        private String resolveDirFd(int dirfd) throws ErrnoException {
            if (dirfd == AT_FDCWD) {
                return this.cwd;
            }
            var path = this.fdPathTable.get(dirfd);
            if (path == null) {
                throw new ErrnoException(EBADF);
            }
            return toVirtualPath(path);
        }

        private static final class RuntimeSelectState {
            private static final int READ_READY = 1;
            private static final int EXCEPT_READY = 2;
            private static final int WRITE_READY = 4;
            private final Memory memory;
            private final int ptr;
            private final boolean[] selected;

            private RuntimeSelectState(Memory memory, int ptr, int nfds) {
                this.memory = memory;
                this.ptr = ptr;
                var count = Math.max(0, nfds);
                this.selected = new boolean[count];
                if (ptr == 0) {
                    return;
                }
                for (var fd = 0; fd < count; fd++) {
                    this.selected[fd] = readBit(fd);
                }
            }

            private boolean isSelected(int fd) {
                return fd >= 0 && fd < this.selected.length && this.selected[fd];
            }

            private void clearOutput() {
                if (this.ptr == 0) {
                    return;
                }
                var words = (this.selected.length + 31) / 32;
                for (var i = 0; i < words; i++) {
                    this.memory.writeI32(this.ptr + (i * 4), 0);
                }
            }

            private void markReady(int fd) {
                if (this.ptr == 0 || fd < 0 || fd >= this.selected.length) {
                    return;
                }
                var addr = this.ptr + ((fd / 32) * 4);
                var mask = 1 << (fd % 32);
                var current = (int) this.memory.readI32(addr);
                this.memory.writeI32(addr, current | mask);
            }

            private boolean readBit(int fd) {
                var addr = this.ptr + ((fd / 32) * 4);
                var mask = 1 << (fd % 32);
                return (this.memory.readI32(addr) & mask) != 0;
            }
        }

        private static final class ErrnoException extends Exception {
            private final int errno;

            private ErrnoException(int errno) {
                this.errno = errno;
            }
        }

        private static final class DylinkMetadata {
            private int memorySize = 0;
            private int memoryAlign = 0;
            private int tableSize = 0;
            private int tableAlign = 0;
            private final java.util.List<String> neededDynlibs = new ArrayList<>();
        }

        private static final class UlebReader {
            private final byte[] data;
            private int index;

            private UlebReader(byte[] data) {
                this.data = data;
                this.index = 0;
            }

            private boolean hasRemaining() {
                return this.index < this.data.length;
            }

            private int position() {
                return this.index;
            }

            private void seek(int nextIndex) {
                this.index = Math.min(this.data.length, Math.max(0, nextIndex));
            }

            private int readU8() {
                return this.data[this.index++] & 0xFF;
            }

            private int readUleb() {
                var shift = 0;
                var result = 0;
                while (this.index < this.data.length) {
                    var b = this.data[this.index++] & 0xFF;
                    result |= (b & 0x7F) << shift;
                    if ((b & 0x80) == 0) {
                        return result;
                    }
                    shift += 7;
                }
                return result;
            }

            private String readString() {
                var len = readUleb();
                if (len <= 0) {
                    return "";
                }
                var available = Math.max(0, Math.min(len, this.data.length - this.index));
                var out = new String(
                    this.data,
                    this.index,
                    available,
                    java.nio.charset.StandardCharsets.UTF_8
                );
                this.index += available;
                return out;
            }
        }

        private static final class DynamicLibrary {
            private final String name;
            private final int handlePtr;
            private final boolean global;
            private final boolean nodelete;
            private final Instance instance;
            private final int tableBase;
            private final int tableSize;
            private final Map<String, Export> exports;
            private final java.util.List<String> exportOrder;
            private final Map<String, Integer> functionPointers = new ConcurrentHashMap<>();

            private DynamicLibrary(
                String name,
                int handlePtr,
                boolean global,
                boolean nodelete,
                Instance instance,
                int tableBase,
                int tableSize,
                Map<String, Export> exports,
                java.util.List<String> exportOrder
            ) {
                this.name = name;
                this.handlePtr = handlePtr;
                this.global = global;
                this.nodelete = nodelete;
                this.instance = instance;
                this.tableBase = tableBase;
                this.tableSize = tableSize;
                this.exports = exports;
                this.exportOrder = exportOrder;
            }
        }

        private static final class RuntimeSockState {
            private final int family;
            private final int type;
            private final int protocol;
            private final java.util.ArrayDeque<RuntimeDatagram> recvQueue =
                new java.util.ArrayDeque<>();
            private int boundFamily;
            private String boundAddress;
            private int boundPort;
            private int connectedFamily;
            private String connectedAddress;
            private int connectedPort;
            private String bindingKey;

            private RuntimeSockState(int family, int type, int protocol) {
                this.family = family;
                this.type = type;
                this.protocol = protocol;
                this.boundFamily = family;
                this.connectedFamily = family;
            }
        }

        private static final class RuntimeDatagram {
            private final byte[] buffer;
            private final String address;
            private final int port;

            private RuntimeDatagram(byte[] buffer, String address, int port) {
                this.buffer = Arrays.copyOf(buffer, buffer.length);
                this.address = address;
                this.port = port;
            }
        }

        private static final class PipeState {
            private final java.util.ArrayDeque<byte[]> buffers = new java.util.ArrayDeque<>();
            private int headOffset = 0;
            private int refCount = 2;
        }

        private static final class MmapRegion {
            private final int fd;
            private final long offset;
            private final int length;
            private final boolean allocated;
            private final int prot;
            private final int flags;

            private MmapRegion(
                int fd,
                long offset,
                int length,
                boolean allocated,
                int prot,
                int flags
            ) {
                this.fd = fd;
                this.offset = offset;
                this.length = length;
                this.allocated = allocated;
                this.prot = prot;
                this.flags = flags;
            }
        }

        private Path resolve(String path) {
            var normalized = normalizeResolvedPath(path);
            if ("/".equals(normalized)) {
                return this.rootDir;
            }
            var mapped = this.rootDir.resolve(normalized.substring(1)).normalize();
            if (!mapped.startsWith(this.rootDir)) {
                throw new RuntimeBridgeException("resolve", "path escapes runtime fs root: " + path);
            }
            return mapped;
        }

        private String normalizeResolvedPath(String path) {
            if (path == null || path.isBlank() || "/".equals(path)) {
                return "/";
            }
            var p = path.replace('\\', '/');
            if ("PG_PREFIX".equals(p)) {
                p = this.mod.WASM_PREFIX();
            } else if (p.startsWith("PG_PREFIX/")) {
                p = this.mod.WASM_PREFIX() + p.substring("PG_PREFIX".length());
            }
            if (!p.startsWith("/")) {
                var base = this.cwd == null || this.cwd.isBlank() ? "/" : this.cwd;
                p = (base.endsWith("/") ? base : base + "/") + p;
            }
            var parts = p.split("/");
            var stack = new ArrayDeque<String>();
            for (var part : parts) {
                if (part == null || part.isEmpty() || ".".equals(part)) {
                    continue;
                }
                if ("..".equals(part)) {
                    if (!stack.isEmpty()) {
                        stack.removeLast();
                    }
                    continue;
                }
                stack.addLast(part);
            }
            if (stack.isEmpty()) {
                return "/";
            }
            return "/" + String.join("/", stack);
        }

        private String toVirtualPath(Path target) {
            var normalized = target.normalize();
            if (normalized.equals(this.rootDir)) {
                return "/";
            }
            var rel = this.rootDir.relativize(normalized).toString().replace('\\', '/');
            return "/" + rel;
        }

        private static Set<StandardOpenOption> decodeOpenOptions(int flags) {
            var set = EnumSet.of(StandardOpenOption.READ);
            var accessMode = flags & 3;
            if (accessMode == 1) {
                set.clear();
                set.add(StandardOpenOption.WRITE);
            } else if (accessMode == 2) {
                set.clear();
                set.add(StandardOpenOption.READ);
                set.add(StandardOpenOption.WRITE);
            }
            if ((flags & 0x40) != 0) {
                set.add(StandardOpenOption.CREATE);
            }
            if ((flags & 0x200) != 0) {
                set.add(StandardOpenOption.TRUNCATE_EXISTING);
            }
            if ((flags & 0x400) != 0) {
                set.add(StandardOpenOption.APPEND);
            }
            return set;
        }
    }

    private static final class RuntimeDlErrorState {
        private volatile String lastError = "";

        private void set(String message) {
            this.lastError = message != null ? message : "";
        }

        private void clear() {
            this.lastError = "";
        }

        private String get() {
            return this.lastError;
        }
    }

    private static final class RuntimeFsNodeIdIndex {
        private final AtomicInteger nextId = new AtomicInteger(1);
        private final Map<String, Long> ids = new ConcurrentHashMap<>();

        private long idFor(Path path) {
            var key = path.toAbsolutePath().normalize().toString();
            return this.ids.computeIfAbsent(
                key,
                ignored -> (long) this.nextId.getAndIncrement()
            );
        }
    }

    private static final class RuntimeAsmConstRegistry {
        private final Map<Long, java.util.function.BiConsumer<EmscriptenRuntimeImpl, java.util.List<Object>>> handlers =
            new HashMap<>();

        private static RuntimeAsmConstRegistry create() {
            var registry = new RuntimeAsmConstRegistry();
            registry.register(2_537_480L, EmscriptenRuntimeImpl::applyFdBufferMaxAsmConst);
            registry.register(2_537_652L, EmscriptenRuntimeImpl::markPostMessageAsmConst);
            registry.register(
                2_537_781L,
                EmscriptenRuntimeImpl::markCustomMessageHandlerAsmConst
            );
            return registry;
        }

        private void register(
            long code,
            java.util.function.BiConsumer<EmscriptenRuntimeImpl, java.util.List<Object>> handler
        ) {
            this.handlers.put(code, handler);
        }

        private boolean dispatch(
            EmscriptenRuntimeImpl runtime,
            long code,
            java.util.List<Object> args
        ) {
            var handler = this.handlers.get(code);
            if (handler == null) {
                return false;
            }
            handler.accept(runtime, args);
            return true;
        }
    }

    private static final class RuntimeWasiContract {
        private static final Set<String> SUPPORTED = Set.of(
            "environ_get",
            "environ_sizes_get",
            "clock_time_get",
            "proc_exit",
            "fd_close",
            "fd_fdstat_get",
            "fd_pread",
            "fd_pwrite",
            "fd_read",
            "fd_seek",
            "fd_sync",
            "fd_datasync",
            "fd_write"
        );

        private static boolean isSupported(String name) {
            return SUPPORTED.contains(name);
        }
    }

    private static final class RuntimeIoctlContract {
        private static final int TCGETS = 21505;
        private static final int TCSETS = 21506;
        private static final int TCSETSW = 21507;
        private static final int TCSETSF = 21508;
        private static final int TCSETA = 21509;
        private static final int TCSETAW = 21510;
        private static final int TCSETAF = 21511;
        private static final int TCFLSH = 21515;
        private static final int TIOCGPGRP = 21519;
        private static final int TIOCSPGRP_ALT = 21520;
        private static final int TIOCGWINSZ = 21523;
        private static final int TIOCSPGRP = 21524;
        private static final int TIOCGPTPEER = 21531;

        private static long defaultErrno(int opcode, int invalid) {
            return -invalid;
        }
    }

    private static final class RuntimeDeviceRegistry {
        private final Map<Integer, Object> devices = new ConcurrentHashMap<>();
        private final Map<String, Integer> deviceNodes = new ConcurrentHashMap<>();
        private final AtomicInteger nextDynamicDevId = new AtomicInteger((64 << 8) | 1);

        private void registerDevice(int devId, Object ops, String source) {
            if (ops == null) {
                throw new RuntimeBridgeException(source, "device ops is null");
            }
            var previous = this.devices.putIfAbsent(devId, ops);
            if (previous != null) {
                throw new RuntimeBridgeException(
                    source,
                    "device id already registered: " + devId
                );
            }
        }

        private void mkdev(String path, int devId, String source) {
            if (!this.devices.containsKey(devId)) {
                throw new RuntimeBridgeException(
                    source,
                    "device id is not registered: " + devId
                );
            }
            var normalized = normalize(path);
            var previous = this.deviceNodes.putIfAbsent(normalized, devId);
            if (previous != null) {
                throw new RuntimeBridgeException(
                    source,
                    "device node already exists: " + normalized
                );
            }
        }

        private Integer deviceNode(String path) {
            return this.deviceNodes.get(normalize(path));
        }

        private int allocateDeviceId() {
            while (true) {
                var next = this.nextDynamicDevId.getAndIncrement();
                if (!this.devices.containsKey(next)) {
                    return next;
                }
            }
        }

        private postgresMod.DeviceOps runtimeDeviceOps(int devId) {
            var ops = this.devices.get(devId);
            if (ops instanceof postgresMod.DeviceOps) {
                return (postgresMod.DeviceOps) ops;
            }
            return null;
        }

        private static String normalize(String path) {
            if (path == null || path.isBlank() || "/".equals(path)) {
                return "/";
            }
            var value = path.replace('\\', '/');
            if (!value.startsWith("/")) {
                value = "/" + value;
            }
            var parts = value.split("/");
            var stack = new ArrayDeque<String>();
            for (var part : parts) {
                if (part == null || part.isEmpty() || ".".equals(part)) {
                    continue;
                }
                if ("..".equals(part)) {
                    if (!stack.isEmpty()) {
                        stack.removeLast();
                    }
                    continue;
                }
                stack.addLast(part);
            }
            if (stack.isEmpty()) {
                return "/";
            }
            return "/" + String.join("/", stack);
        }
    }

    private static final class RuntimeTimerRegistry {
        private final Map<Integer, TimerHandle> timers = new ConcurrentHashMap<>();
        private final AtomicInteger keepaliveCounter = new AtomicInteger(0);
        private final java.util.function.Consumer<String> debugLog;

        private RuntimeTimerRegistry(java.util.function.Consumer<String> debugLog) {
            this.debugLog = debugLog;
        }

        private void setTimeout(int which, long timeoutMs, Runnable callback) {
            clear(which);
            if (timeoutMs <= 0L) {
                return;
            }
            var handle = new TimerHandle();
            this.keepaliveCounter.incrementAndGet();
            handle.future = TIMER_EXECUTOR.schedule(
                () -> {
                    try {
                        callback.run();
                    } finally {
                        if (this.timers.remove(which, handle)) {
                            release(handle);
                        }
                    }
                },
                timeoutMs,
                TimeUnit.MILLISECONDS
            );
            this.timers.put(which, handle);
            this.debugLog.accept(
                "setitimer which=" + which + " timeoutMs=" + timeoutMs + " keepalive=" + keepaliveCounter.get()
            );
        }

        private void clear(int which) {
            var handle = this.timers.remove(which);
            if (handle == null) {
                return;
            }
            if (handle.future != null) {
                handle.future.cancel(false);
            }
            release(handle);
        }

        private void clearAll() {
            for (var which : new ArrayList<>(this.timers.keySet())) {
                clear(which);
            }
            this.keepaliveCounter.set(0);
        }

        private int activeTimerCount() {
            return this.timers.size();
        }

        private int keepaliveCount() {
            return this.keepaliveCounter.get();
        }

        private void release(TimerHandle handle) {
            if (handle.released.compareAndSet(false, true)) {
                this.keepaliveCounter.updateAndGet(current -> Math.max(0, current - 1));
            }
        }

        private static final class TimerHandle {
            private ScheduledFuture<?> future;
            private final java.util.concurrent.atomic.AtomicBoolean released =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        }
    }

    private static final class EmscriptenFsImpl implements extensionUtils.EmscriptenFS {
        private final Path rootDir;
        private final RuntimeDeviceRegistry deviceRegistry;
        private final Map<String, MountEntry> mounts = new ConcurrentHashMap<>();
        private final Map<String, LazyFileEntry> lazyFiles = new ConcurrentHashMap<>();

        private EmscriptenFsImpl(Path rootDir, RuntimeDeviceRegistry deviceRegistry) {
            this.rootDir = rootDir;
            this.deviceRegistry = deviceRegistry;
            this.mounts.put("/", new MountEntry("/", MountBackend.MEMFS, "MEMFS", null, null, null));
        }

        @Override
        public void createPath(String parent, String path, boolean canRead, boolean canWrite) {
            var base = resolve(parent);
            var dir = path == null ? base : base.resolve(path);
            try {
                Files.createDirectories(dir);
            } catch (Exception e) {
                throw new RuntimeBridgeException("createPath", e);
            }
        }

        @Override
        public void createDataFile(
            String path,
            String name,
            Object data,
            boolean canRead,
            boolean canWrite,
            boolean canOwn
        ) {
            var parent = path != null ? path : "/";
            var fileName = name != null ? name : "";
            var targetPath = parent.endsWith("/") || fileName.isEmpty()
                ? parent + fileName
                : parent + "/" + fileName;
            writeFile(targetPath, toBytes(data));
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
                var base = resolve(parent);
                Files.createDirectories(base);
                if (!dontCreateFile) {
                    var target = base.resolve(name);
                    var bytes = toBytes(data);
                    Files.write(
                        target,
                        bytes,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE
                    );
                }
                if (onload != null) {
                    onload.apply(name);
                }
            } catch (Exception e) {
                if (onerror != null) {
                    onerror.apply(name, e.getMessage());
                    return;
                }
                throw new RuntimeBridgeException("createPreloadedFile", e);
            }
        }

        @Override
        public extensionUtils.AnalyzePathResult analyzePath(String path) {
            var result = new extensionUtils.AnalyzePathResult();
            result.exists = Files.exists(resolve(path));
            return result;
        }

        @Override
        public void mkdirTree(String path) {
            try {
                Files.createDirectories(resolve(path));
            } catch (Exception e) {
                throw new RuntimeBridgeException("mkdirTree", e);
            }
        }

        @Override
        public void writeFile(String path, byte[] data) {
            try {
                var target = resolve(path);
                var parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.write(
                    target,
                    data,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
                );
                this.lazyFiles.remove(normalize(path));
            } catch (Exception e) {
                throw new RuntimeBridgeException("writeFile", e);
            }
        }

        @Override
        public byte[] readFile(String path) {
            try {
                ensureLazyFileMaterialized(path);
                return Files.readAllBytes(resolve(path));
            } catch (Exception e) {
                throw new RuntimeBridgeException("readFile", e);
            }
        }

        @Override
        public void unlink(String path) {
            try {
                Files.delete(resolve(path));
                this.lazyFiles.remove(normalize(path));
            } catch (Exception e) {
                throw new RuntimeBridgeException("unlink", e);
            }
        }

        @Override
        public void createLazyFile(
            String parent,
            String name,
            Object data,
            boolean canRead,
            boolean canWrite
        ) {
            try {
                var normalizedParent = normalize(parent);
                var normalizedPath = normalize(
                    normalizedParent + ((name == null || name.isEmpty()) ? "" : "/" + name)
                );
                var base = resolve(normalizedParent);
                Files.createDirectories(base);
                var target = resolve(normalizedPath);
                Files.write(
                    target,
                    new byte[0],
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
                );
                this.lazyFiles.put(normalizedPath, new LazyFileEntry(data));
            } catch (Exception e) {
                throw new RuntimeBridgeException("createLazyFile", e);
            }
        }

        @Override
        public void createDevice(String parent, String name, Object input, Object output) {
            var fullPath = normalize(
                (parent == null ? "/" : parent) +
                ((name == null || name.isEmpty()) ? "" : "/" + name)
            );
            var devId = this.deviceRegistry.allocateDeviceId();
            this.deviceRegistry.registerDevice(
                devId,
                toDeviceOps(input, output),
                "fs.createDevice"
            );
            this.deviceRegistry.mkdev(fullPath, devId, "fs.createDevice");
            writeFile(fullPath, new byte[0]);
        }

        @Override
        public void mount(Object type, Object opts, String mountpoint) {
            var normalized = normalize(mountpoint);
            if (this.mounts.containsKey(normalized)) {
                throw new RuntimeBridgeException(
                    "mount",
                    "mountpoint already mounted: " + normalized
                );
            }
            mkdirTree(normalized);
            var backend = resolveMountBackend(type);
            var hostRoot = resolveHostRoot(type, opts, backend);
            var syncRoot = resolveSyncRoot(normalized, backend);
            this.mounts.put(
                normalized,
                new MountEntry(normalized, backend, type, opts, hostRoot, syncRoot)
            );
        }

        @Override
        public void unmount(String mountpoint) {
            var normalized = normalize(mountpoint);
            if ("/".equals(normalized)) {
                throw new RuntimeBridgeException("unmount", "cannot unmount root");
            }
            var removed = this.mounts.remove(normalized);
            if (removed == null) {
                throw new RuntimeBridgeException(
                    "unmount",
                    "mountpoint is not mounted: " + normalized
                );
            }
        }

        @Override
        public void symlink(String target, String path) {
            try {
                var linkPath = resolve(path);
                var parent = linkPath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                var targetPath = resolve(target);
                try {
                    Files.deleteIfExists(linkPath);
                } catch (Exception ignored) {
                }
                Files.createSymbolicLink(linkPath, targetPath);
            } catch (Exception e) {
                try {
                    var linkPath = resolve(path);
                    writeFile(path, target.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    if (!Files.exists(linkPath, LinkOption.NOFOLLOW_LINKS)) {
                        throw e;
                    }
                } catch (Exception nested) {
                    throw new RuntimeBridgeException("symlink", nested);
                }
            }
        }

        @Override
        public extensionUtils.FsStat stat(String path) {
            try {
                var p = resolve(path);
                var attrs = Files.readAttributes(
                    p,
                    java.nio.file.attribute.BasicFileAttributes.class
                );
                var stat = new extensionUtils.FsStat();
                stat.size = attrs.size();
                stat.mtimeMs = attrs.lastModifiedTime().toMillis();
                stat.directory = attrs.isDirectory();
                return stat;
            } catch (Exception e) {
                throw new RuntimeBridgeException("stat", e);
            }
        }

        @Override
        public String[] readdir(String path) {
            try (var stream = Files.list(resolve(path))) {
                return stream
                    .map(p -> p.getFileName().toString())
                    .toArray(String[]::new);
            } catch (Exception e) {
                throw new RuntimeBridgeException("readdir", e);
            }
        }

        @Override
        public void syncfs(boolean populate, extensionUtils.SyncfsCallback done) {
            if (done == null) {
                return;
            }
            Exception error = null;
            try {
                // Keep callback contract with Emscripten: complete once after all mounts.
                for (var entry : this.mounts.values()) {
                    if (entry == null) {
                        continue;
                    }
                    switch (entry.backend) {
                        case NODEFS -> {
                            if (entry.hostRoot != null) {
                                Files.createDirectories(entry.hostRoot);
                            }
                        }
                        case IDBFS -> {
                            var mountRoot = resolve(entry.mountpoint);
                            Files.createDirectories(mountRoot);
                            if (entry.syncRoot != null) {
                                Files.createDirectories(entry.syncRoot);
                                if (populate) {
                                    copyDirectoryTree(entry.syncRoot, mountRoot);
                                } else {
                                    copyDirectoryTree(mountRoot, entry.syncRoot);
                                }
                            }
                        }
                        case MEMFS -> Files.createDirectories(resolve(entry.mountpoint));
                    }
                }
            } catch (Exception e) {
                error = e;
            }
            done.apply(error);
        }

        @Override
        public void registerDevice(int devId, Object ops) {
            this.deviceRegistry.registerDevice(devId, ops, "fs.registerDevice");
        }

        @Override
        public int makedev(int major, int minor) {
            return (major << 8) | (minor & 0xFF);
        }

        @Override
        public void mkdev(String path, int dev) {
            var normalized = normalize(path);
            this.deviceRegistry.mkdev(normalized, dev, "fs.mkdev");
            writeFile(normalized, new byte[0]);
        }

        private void ensureLazyFileMaterialized(String path) {
            var normalized = normalize(path);
            var lazyEntry = this.lazyFiles.get(normalized);
            if (lazyEntry == null || lazyEntry.materialized) {
                return;
            }
            synchronized (lazyEntry) {
                if (lazyEntry.materialized) {
                    return;
                }
                var target = resolve(normalized);
                var parent = target.getParent();
                if (parent != null) {
                    try {
                        Files.createDirectories(parent);
                    } catch (Exception e) {
                        throw new RuntimeBridgeException("createLazyFile", e);
                    }
                }
                var payload = readLazyFilePayload(lazyEntry.source);
                try {
                    Files.write(
                        target,
                        payload,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE
                    );
                } catch (Exception e) {
                    throw new RuntimeBridgeException("createLazyFile", e);
                }
                lazyEntry.materialized = true;
            }
        }

        private static final class LazyFileEntry {
            private final Object source;
            private volatile boolean materialized;

            private LazyFileEntry(Object source) {
                this.source = source;
                this.materialized = false;
            }
        }

        private static final class MountEntry {
            private final MountBackend backend;
            private final String mountpoint;
            private final Object type;
            private final Object opts;
            private final Path hostRoot;
            private final Path syncRoot;

            private MountEntry(
                String mountpoint,
                MountBackend backend,
                Object type,
                Object opts,
                Path hostRoot,
                Path syncRoot
            ) {
                this.mountpoint = mountpoint;
                this.backend = backend;
                this.type = type;
                this.opts = opts;
                this.hostRoot = hostRoot;
                this.syncRoot = syncRoot;
            }
        }

        private enum MountBackend {
            MEMFS,
            NODEFS,
            IDBFS
        }

        private Path resolve(String path) {
            var normalized = normalize(path);
            var mount = resolveMount(normalized);
            if (mount != null && mount.hostRoot != null) {
                if (normalized.equals(mount.mountpoint)) {
                    return mount.hostRoot;
                }
                var relative = normalized.substring(mount.mountpoint.length());
                if (relative.startsWith("/")) {
                    relative = relative.substring(1);
                }
                var mapped = mount.hostRoot.resolve(relative).normalize();
                if (!mapped.startsWith(mount.hostRoot)) {
                    throw new RuntimeBridgeException("resolve", "path escapes mount root: " + path);
                }
                return mapped;
            }
            if ("/".equals(normalized)) {
                return this.rootDir;
            }
            var mapped = this.rootDir.resolve(normalized.substring(1)).normalize();
            if (!mapped.startsWith(this.rootDir)) {
                throw new RuntimeBridgeException("resolve", "path escapes fs root: " + path);
            }
            return mapped;
        }

        private MountEntry resolveMount(String normalizedPath) {
            var best = this.mounts.get("/");
            var bestLength = 1;
            for (var entry : this.mounts.values()) {
                if (entry == null || "/".equals(entry.mountpoint)) {
                    continue;
                }
                var mountpoint = entry.mountpoint;
                if (
                    normalizedPath.equals(mountpoint) ||
                    normalizedPath.startsWith(mountpoint + "/")
                ) {
                    if (mountpoint.length() > bestLength) {
                        best = entry;
                        bestLength = mountpoint.length();
                    }
                }
            }
            return best;
        }

        private MountBackend resolveMountBackend(Object type) {
            if (isNodeFs(type)) {
                return MountBackend.NODEFS;
            }
            if (isIdbFs(type)) {
                return MountBackend.IDBFS;
            }
            return MountBackend.MEMFS;
        }

        private Path resolveHostRoot(Object type, Object opts, MountBackend backend) {
            if (backend != MountBackend.NODEFS) {
                return null;
            }
            if (!(opts instanceof Map<?, ?> map)) {
                throw new RuntimeBridgeException("mount", "NODEFS mount requires opts.root");
            }
            var root = map.get("root");
            if (!(root instanceof String rootPath) || rootPath.isBlank()) {
                throw new RuntimeBridgeException("mount", "NODEFS mount requires non-empty opts.root");
            }
            try {
                var resolved = Path.of(rootPath).toAbsolutePath().normalize();
                Files.createDirectories(resolved);
                return resolved;
            } catch (Exception e) {
                throw new RuntimeBridgeException("mount", e);
            }
        }

        private Path resolveSyncRoot(String mountpoint, MountBackend backend) {
            if (backend != MountBackend.IDBFS) {
                return null;
            }
            var sanitized = mountpoint.equals("/")
                ? "_root"
                : mountpoint.substring(1).replace('/', '_');
            var syncRoot = this.rootDir.resolve(".idbfs").resolve(sanitized).normalize();
            try {
                Files.createDirectories(syncRoot);
            } catch (Exception e) {
                throw new RuntimeBridgeException("mount", e);
            }
            return syncRoot;
        }

        private void copyDirectoryTree(Path from, Path to) {
            try {
                if (!Files.exists(from)) {
                    return;
                }
                Files.createDirectories(to);
                try (var stream = Files.walk(from)) {
                    stream.forEach(sourcePath -> {
                        try {
                            var relative = from.relativize(sourcePath);
                            var targetPath = to.resolve(relative.toString()).normalize();
                            if (Files.isDirectory(sourcePath)) {
                                Files.createDirectories(targetPath);
                            } else if (Files.isSymbolicLink(sourcePath)) {
                                var linkTarget = Files.readSymbolicLink(sourcePath);
                                Files.deleteIfExists(targetPath);
                                Files.createSymbolicLink(targetPath, linkTarget);
                            } else {
                                var parent = targetPath.getParent();
                                if (parent != null) {
                                    Files.createDirectories(parent);
                                }
                                Files.copy(
                                    sourcePath,
                                    targetPath,
                                    StandardCopyOption.REPLACE_EXISTING,
                                    StandardCopyOption.COPY_ATTRIBUTES
                                );
                            }
                        } catch (Exception e) {
                            throw new RuntimeBridgeException("syncfs", e);
                        }
                    });
                }
            } catch (RuntimeBridgeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeBridgeException("syncfs", e);
            }
        }

        private static boolean isNodeFs(Object type) {
            if (type == null) {
                return false;
            }
            if (type instanceof String text) {
                return "NODEFS".equalsIgnoreCase(text);
            }
            var simpleName = type.getClass().getSimpleName();
            return simpleName != null && simpleName.toUpperCase().contains("NODEFS");
        }

        private static boolean isIdbFs(Object type) {
            if (type == null) {
                return false;
            }
            if (type instanceof String text) {
                return "IDBFS".equalsIgnoreCase(text);
            }
            var simpleName = type.getClass().getSimpleName();
            return simpleName != null && simpleName.toUpperCase().contains("IDBFS");
        }

        private static String normalize(String path) {
            if (path == null || path.isBlank() || "/".equals(path)) {
                return "/";
            }
            var value = path.replace('\\', '/');
            if (!value.startsWith("/")) {
                value = "/" + value;
            }
            var parts = value.split("/");
            var stack = new ArrayDeque<String>();
            for (var part : parts) {
                if (part == null || part.isEmpty() || ".".equals(part)) {
                    continue;
                }
                if ("..".equals(part)) {
                    if (!stack.isEmpty()) {
                        stack.removeLast();
                    }
                    continue;
                }
                stack.addLast(part);
            }
            if (stack.isEmpty()) {
                return "/";
            }
            return "/" + String.join("/", stack);
        }

        private static byte[] toBytes(Object data) {
            if (data == null) {
                return new byte[0];
            }
            if (data instanceof byte[]) {
                return (byte[]) data;
            }
            if (data instanceof Uint8Array) {
                return ((Uint8Array) data).toByteArray();
            }
            throw new RuntimeBridgeException(
                "toBytes",
                "Unsupported file payload type: " + data.getClass().getName()
            );
        }

        private static byte[] readLazyFilePayload(Object data) {
            if (data == null) {
                return new byte[0];
            }
            if (data instanceof byte[] || data instanceof Uint8Array) {
                return toBytes(data);
            }
            if (data instanceof java.net.URL url) {
                return utils.readFile(url);
            }
            if (data instanceof String text) {
                var normalizedResource = text.startsWith("/") ? text.substring(1) : text;
                try {
                    return utils.readFile(normalizedResource);
                } catch (RuntimeException classpathMissing) {
                    try {
                        return Files.readAllBytes(Path.of(text));
                    } catch (Exception fileMissing) {
                        throw new RuntimeBridgeException(
                            "createLazyFile",
                            "Resource not found: " + text
                        );
                    }
                }
            }
            throw new RuntimeBridgeException(
                "createLazyFile",
                "Unsupported lazy file source: " + data.getClass().getName()
            );
        }

        private static postgresMod.DeviceOps toDeviceOps(Object input, Object output) {
            if (input instanceof postgresMod.DeviceOps ops && output == null) {
                return ops;
            }
            return new postgresMod.DeviceOps() {
                @Override
                public int read(byte[] buffer, int offset, int length, int position) {
                    if (input instanceof java.util.function.IntSupplier supplier) {
                        var count = 0;
                        for (var i = 0; i < length; i++) {
                            var value = supplier.getAsInt();
                            if (value < 0) {
                                break;
                            }
                            buffer[offset + i] = (byte) (value & 0xFF);
                            count++;
                        }
                        return count;
                    }
                    return 0;
                }

                @Override
                public int write(byte[] buffer, int offset, int length, int position) {
                    if (output instanceof java.util.function.IntConsumer consumer) {
                        for (var i = 0; i < length; i++) {
                            consumer.accept(buffer[offset + i] & 0xFF);
                        }
                    }
                    return Math.max(0, length);
                }

                @Override
                public int llseek(int offset, int whence, int position) {
                    return Math.max(0, offset);
                }
            };
        }
    }

    private static final class RuntimeLongjmpException extends RuntimeException {
        private RuntimeLongjmpException(String api, String message) {
            super("EmscriptenRuntime[" + api + "]: " + message);
        }
    }

    private static final class ExitStatusException extends RuntimeException {
        private final int code;

        private ExitStatusException(String source, int code) {
            super(source + "(" + code + ")");
            this.code = code;
        }
    }

    private static final class RuntimeBridgeException extends RuntimeException {
        private RuntimeBridgeException(String api, String message) {
            super("EmscriptenRuntime[" + api + "]: " + message);
        }

        private RuntimeBridgeException(String api, Throwable cause) {
            super("EmscriptenRuntime[" + api + "]: " + cause.getMessage(), cause);
        }
    }

    private pglite() {
    }
}
