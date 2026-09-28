package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.WasmProcess;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.postgresMod.ReadWriteCallback;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class initdb {
    public static final String PG_ROOT = "/pglite";
    public static final String PGDATA = "/data";
    public static final String ICU_DATA_PATH = PG_ROOT + "/icu";
    public static final String INITDB_EXE_PATH = PG_ROOT + "/bin/initdb";
    public static final String POSTGRES_EXE_PATH = PG_ROOT + "/bin/postgres";

    private static final String PGSTDOUT_PATH = PG_ROOT + "/pgstdout";
    private static final String PGSTDIN_PATH = PG_ROOT + "/pgstdin";

    public static final Object K_EXIT_CODE = new Object();

    public interface WasiRuntime {
        Integer exitCode();
    }

    public interface PGliteForInitdb {
        interface Module {
            Uint8Array HEAPU8();

            int stringToUTF8OnStack(String str);

            void _pgl_freopen(int path, int mode, int fd);

            default Integer _close(int fd) {
                return null;
            }

            extensionUtils.EmscriptenFS FS();

            default Object __wasi() {
                return null;
            }

            default String __wasiDataRoot() {
                return null;
            }

            default Object __pgliteEhProvider() {
                return null;
            }

            default Integer INITIAL_MEMORY() {
                return null;
            }

            default Integer _pgl_chdir(int path) {
                return null;
            }

            default String UTF8ToString(int ptr) {
                return null;
            }

            default int addFunction(ReadWriteCallback cb, String signature) {
                return 0;
            }

            default int _fopen(int path, int mode) {
                return -1;
            }

            default int _fclose(int stream) {
                return -1;
            }

            default void _pgl_set_popen_fn(int popenFn) {}

            default void _pgl_set_pclose_fn(int pcloseFn) {}
        }

        Module Module();

        int callMain(String[] args);

        int callInitdbMain(String[] args);

        initdbModFactory.InitdbMod initdbMod();
    }

    public record ExecResult(int exitCode, String stderr, String stdout, String dataFolder) {}

    public record InitdbOptions(
        PGliteForInitdb pg,
        Integer debug,
        String[] args,
        byte[] wasmModule
    ) {}

    private initdb() {}

    private static void assertCondition(boolean condition, String message) {
        if (!condition) {
            throw new Error(message != null ? message : "Assertion failed");
        }
    }

    private static void log(Integer debug, Object... args) {
        if (debug != null && debug > 0) {
            System.out.print("initdb: ");
            for (var arg : args) {
                System.out.print(String.valueOf(arg) + " ");
            }
            System.out.println();
        }
    }

    private static int toWaitStatus(int exitCode) {
        return exitCode << 8;
    }

    private static void copyFile(
        extensionUtils.EmscriptenFS fromFs,
        extensionUtils.EmscriptenFS toFs,
        String path
    ) {
        var data = fromFs.readFile(path);
        toFs.writeFile(path, data);
    }

    private static Integer getWasiExitCode(Object err, Object wasi) {
        if (err != K_EXIT_CODE) {
            return null;
        }
        if (!(wasi instanceof WasiRuntime runtime)) {
            return null;
        }
        return runtime.exitCode();
    }

    private static boolean isWasi(Object wasi) {
        return wasi != null;
    }

    private static Promise<ExecResult> execInitdb(
        PGliteForInitdb pg,
        Integer debug,
        String[] args,
        byte[] wasmModule
    ) {
        var systemFn = new int[] {0};
        var popenFn = new int[] {0};
        var pcloseFn = new int[] {0};

        var needToCallPGmain = new boolean[] {false};
        var postgresArgs = new ArrayList<String>();

        var pgMainResult = new int[] {0};

        var initdbStdinFd = new int[] {-1};
        var initdbStdoutFd = new int[] {-1};
        var pgLocaleAFd = new int[] {-1};
        var stderrOutput = new StringBuilder();
        var stdoutOutput = new StringBuilder();

        var reopenPgStreams = new Runnable[] {null};
        var callPgMainHolder = new java.util.concurrent.atomic.AtomicReference<java.util.function.Function<String[], Integer>>();

        var origHeapU8 = new byte[][] {null};
        // Isolated WASM processes own their descriptors as well as linear memory.
        var memorySwap = isWasi(pg.Module().__wasi()) && !(pg.initdbMod() instanceof WasmProcess);

        reopenPgStreams[0] = () -> {
            var pgliteStdinPath = pg.Module().stringToUTF8OnStack(PGSTDIN_PATH);
            var rmode = pg.Module().stringToUTF8OnStack("r");
            pg.Module()._pgl_freopen(pgliteStdinPath, rmode, 0);
            var pgliteStdoutPath = pg.Module().stringToUTF8OnStack(PGSTDOUT_PATH);
            var wmode = pg.Module().stringToUTF8OnStack("w");
            pg.Module()._pgl_freopen(pgliteStdoutPath, wmode, 1);
        };

        callPgMainHolder.set(callArgs -> {
            var argsList = new ArrayList<>(Arrays.asList(callArgs));
            var firstArg = argsList.isEmpty() ? null : argsList.remove(0);
            log(debug, "firstArg", firstArg);
            assertCondition(
                POSTGRES_EXE_PATH.equals(firstArg),
                "trying to execute " + firstArg
            );

            if (pg.initdbMod() instanceof WasmProcess processMod) {
                try (var child = processMod.createProcess(POSTGRES_EXE_PATH)) {
                    child._pgl_chdir(child.stringToUTF8OnStack(PGDATA));
                    child._pgl_freopen(child.stringToUTF8OnStack(PGSTDIN_PATH), child.stringToUTF8OnStack("r"), 0);
                    child._pgl_freopen(child.stringToUTF8OnStack(PGSTDOUT_PATH), child.stringToUTF8OnStack("w"), 1);
                    child._pgl_freopen(child.stringToUTF8OnStack("/pglite/pgstderr"), child.stringToUTF8OnStack("w"), 2);
                    var status = child.callMain(argsList.toArray(String[]::new));
                    var error = new String(child.FS().readFile("/pglite/pgstderr"), java.nio.charset.StandardCharsets.UTF_8);
                    stderrOutput.append(error);
                    log(debug, "child status", status, "stderr", error);
                    return status;
                }
            }

            // initdb and the backend now live in one linear memory.  Preserve
            // initdb's state while its child postgres process uses the pristine
            // main-module state.
            var initdbHeap = memorySwap
                ? pg.Module().HEAPU8().toByteArray()
                : null;
            if (origHeapU8[0] != null) {
                pg.Module().HEAPU8().set(origHeapU8[0]);
                // These callback pointers are C globals in linear memory, so
                // re-install them after restoring the backend baseline.
                var backend = pg.initdbMod();
                backend._pgl_set_system_fn(systemFn[0]);
                backend._pgl_set_popen_fn(popenFn[0]);
                backend._pgl_set_pclose_fn(pcloseFn[0]);
            }
            if (isWasi(pg.Module().__wasi())) {
                var chdirResult = pg.Module()._pgl_chdir(pg.Module().stringToUTF8OnStack(PGDATA));
                if (chdirResult != null) {
                    chdirResult.intValue();
                }
                reopenPgStreams[0].run();
            }

            log(debug, "executing pg main with", argsList);
            int result;
            try {
                result = pg.callMain(argsList.toArray(String[]::new));
            } catch (Throwable err) {
                var wasiExitCode = getWasiExitCode(err, pg.Module().__wasi());
                if (wasiExitCode == null) {
                    if (err instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    if (err instanceof Error error) {
                        throw error;
                    }
                    throw new RuntimeException(err);
                }
                result = wasiExitCode;
            }
            if (initdbHeap != null) pg.Module().HEAPU8().set(initdbHeap);
            log(debug, "pg main result", result);

            postgresArgs.clear();

            return result;
        });

        var initdbMod = pg.initdbMod() instanceof WasmProcess processMod
            ? processMod.createProcess(INITDB_EXE_PATH) : pg.initdbMod();
        try {
        var env = modEnv(initdbMod);
        env.put("PGDATA", PGDATA);
        env.put("HOME", "/home/postgres");
        env.put("USER", "postgres");
        env.put("LOGNAME", "postgres");
        env.put("ICU_DATA", ICU_DATA_PATH);
        {
                    systemFn[0] = initdbMod.addFunction(
                        (ReadWriteCallback) (cmdPtr, ignored) -> {
                            if (cmdPtr == 0) {
                                return 1;
                            }
                            var command = initdbMod.UTF8ToString(cmdPtr);
                            log(debug, "system raw", command);
                            postgresArgs.clear();
                            postgresArgs.addAll(getArgs(command));
                            if (postgresArgs.isEmpty()) {
                                return 1;
                            }
                            log(debug, "system", postgresArgs);
                            return toWaitStatus(
                                callPgMainHolder.get().apply(postgresArgs.toArray(String[]::new))
                            );
                        },
                        "pi"
                    );
                    initdbMod._pgl_set_system_fn(systemFn[0]);

                    popenFn[0] = initdbMod.addFunction(
                        (ReadWriteCallback) (cmdPtr, modePtr) -> {
                            var smode = initdbMod.UTF8ToString(modePtr);
                            var command = initdbMod.UTF8ToString(cmdPtr);
                            log(debug, "popen raw", command, smode);
                            if (isWasi(pg.Module().__wasi()) && "locale -a".equals(command) && "r".equals(smode)) {
                                var localePath = "/pglite/locale-a";
                                pg.Module().FS().writeFile(localePath,
                                    "C\nC.UTF-8\nPOSIX\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                                var path = initdbMod.stringToUTF8OnStack(localePath);
                                var rmode = initdbMod.stringToUTF8OnStack("r");
                                pgLocaleAFd[0] = initdbMod._fopen(path, rmode);
                                return pgLocaleAFd[0];
                            }
                            postgresArgs.clear();
                            postgresArgs.addAll(getArgs(command));
                            log(debug, "popen", smode, postgresArgs);

                            if ("r".equals(smode)) {
                                pgMainResult[0] = callPgMainHolder.get().apply(postgresArgs.toArray(String[]::new));
                                if (isWasi(pg.Module().__wasi())) {
                                    copyFile(pg.Module().FS(), initdbMod.FS(), PGSTDOUT_PATH);
                                    if (initdbStdinFd[0] != -1) {
                                        initdbMod._fclose(initdbStdinFd[0]);
                                    }
                                    var path = initdbMod.stringToUTF8OnStack(PGSTDOUT_PATH);
                                    var rmode = initdbMod.stringToUTF8OnStack("r");
                                    initdbStdinFd[0] = initdbMod._fopen(path, rmode);
                                }
                                return initdbStdinFd[0];
                            }
                            if ("w".equals(smode)) {
                                if (isWasi(pg.Module().__wasi())) {
                                    var path = initdbMod.stringToUTF8OnStack(PGSTDIN_PATH);
                                    var wmode = initdbMod.stringToUTF8OnStack("w");
                                    initdbStdoutFd[0] = initdbMod._fopen(path, wmode);
                                }
                                needToCallPGmain[0] = true;
                                return initdbStdoutFd[0];
                            }
                            throw new RuntimeException("Unexpected popen mode value " + smode);
                        },
                        "ppi"
                    );
                    initdbMod._pgl_set_popen_fn(popenFn[0]);

                    pcloseFn[0] = initdbMod.addFunction(
                        (ReadWriteCallback) (stream, ignored) -> {
                            log(
                                debug,
                                "pclose",
                                stream,
                                Map.of(
                                    "initdb_stdin_fd", initdbStdinFd[0],
                                    "initdb_stdout_fd", initdbStdoutFd[0]
                                )
                            );
                            if (stream == pgLocaleAFd[0]) {
                                pgLocaleAFd[0] = -1;
                                return initdbMod._fclose(stream);
                            }
                            if (stream == initdbStdinFd[0] || stream == initdbStdoutFd[0]) {
                                if (isWasi(pg.Module().__wasi()) && stream == initdbStdoutFd[0]) {
                                    initdbMod._fflush(stream);
                                    initdbMod._fclose(stream);
                                    copyFile(initdbMod.FS(), pg.Module().FS(), PGSTDIN_PATH);
                                    log(debug, "pgstdin bytes", pg.Module().FS().readFile(PGSTDIN_PATH).length);
                                    initdbStdoutFd[0] = -1;
                                }
                                if (needToCallPGmain[0]) {
                                    needToCallPGmain[0] = false;
                                    pgMainResult[0] = callPgMainHolder.get().apply(postgresArgs.toArray(String[]::new));
                                }
                                return toWaitStatus(pgMainResult[0]);
                            }
                            return initdbMod._pclose(stream);
                        },
                        "pi"
                    );
                    initdbMod._pgl_set_pclose_fn(pcloseFn[0]);

                    if (isWasi(pg.Module().__wasi())) {
                        pg.Module().FS().writeFile(PGSTDIN_PATH, new byte[0]);
                        pg.Module().FS().writeFile(PGSTDOUT_PATH, new byte[0]);
                    }
                    reopenPgStreams[0].run();

                    var initdbPath = initdbMod.stringToUTF8OnStack(PGSTDOUT_PATH);
                    var rmode = initdbMod.stringToUTF8OnStack("r");
                    initdbStdinFd[0] = initdbMod._fopen(initdbPath, rmode);

                    if (isWasi(pg.Module().__wasi())) {
                        initdbStdoutFd[0] = -1;
                    } else {
                        var path = initdbMod.stringToUTF8OnStack(PGSTDIN_PATH);
                        var wmode = initdbMod.stringToUTF8OnStack("w");
                        initdbStdoutFd[0] = initdbMod._fopen(path, wmode);
                    }

                    if (memorySwap) {
                        origHeapU8[0] = pg.Module().HEAPU8().toByteArray();
                    }
        }
        // Keep the initial backend image for initdb's child backend calls.
        if (memorySwap) origHeapU8[0] = pg.Module().HEAPU8().toByteArray();
        log(debug, "calling initdb main with", Arrays.toString(args));
        var result = initdbMod.callInitdbMain(args);
        // initdb and its emulated children must not leave their allocator and
        // process-exit globals behind for the long-lived backend.
        if (memorySwap && origHeapU8[0] != null) pg.Module().HEAPU8().set(origHeapU8[0]);
        initdbMod.resetAfterProcExit();
        return Promise.resolve(new ExecResult(result, stderrOutput.toString(), stdoutOutput.toString(), PGDATA));
        } finally {
            if (initdbMod != pg.initdbMod() && initdbMod instanceof WasmProcess processMod) {
                processMod.close();
            }
        }
    }

    private static Map<String, String> modEnv(postgresMod.PostgresMod mod) {
        if (mod instanceof initdbModFactory.InitdbMod initdbMod) {
            return initdbMod.ENV();
        }
        throw new UnsupportedOperationException("Expected InitdbMod with ENV map");
    }

    private static List<String> getArgs(String cmd) {
        var parsed = argsParser.parse(cmd);
        var args = new ArrayList<String>();
        for (var token : parsed) {
            if (token instanceof argsParser.OpToken) {
                break;
            }
            if (token instanceof String text) {
                args.add(text);
            }
        }
        return args;
    }

    public static Promise<ExecResult> getInitdb(InitdbOptions options) {
        var resolved = options != null ? options : new InitdbOptions(null, null, null, null);
        var extraArgs = resolved.args() != null ? resolved.args() : new String[0];
        var args = new ArrayList<String>();
        args.addAll(
            List.of(
                "-D",
                PGDATA,
                "--allow-group-access",
                "--encoding",
                "UTF8",
                "--locale=C.UTF-8",
                "--locale-provider=libc",
                "--auth=trust",
                "--no-sync"
            )
        );
        args.addAll(Arrays.asList(extraArgs));

        return execInitdb(
            resolved.pg(),
            resolved.debug(),
            args.toArray(String[]::new),
            resolved.wasmModule()
        );
    }
}
