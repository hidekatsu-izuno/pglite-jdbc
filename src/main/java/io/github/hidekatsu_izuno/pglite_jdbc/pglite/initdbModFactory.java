package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import java.net.URL;
import java.util.Map;

public class initdbModFactory {
    @FunctionalInterface
    public interface InitdbCallback {
        int apply(int ptr, int length);
    }

    public interface InitdbMod extends postgresMod.PostgresMod {
        int callMain(String[] args);

        Map<String, String> ENV();

        extensionUtils.EmscriptenFS FS();

        Object PROXYFS();

        String UTF8ToString(int ptr);

        int stringToUTF8OnStack(String s);

        void _pgl_set_system_fn(int systemFn);

        void _pgl_set_popen_fn(int popenFn);

        void _pgl_set_pclose_fn(int pcloseFn);

        int _fopen(int path, int mode);

        int _fclose(int stream);

        void _fflush(int stream);

        int _pclose(int stream);

        int ___errno_location();

        int _strerror(int errno);

        int _pipe(int fd);

        Boolean __wasi();

        default void onExit(int status) {}

        default void print(String text) {}

        default void printErr(String text) {}

        default void onRuntimeInitialized() {}
    }

    @FunctionalInterface
    public interface InitdbFactory {
        Promise<InitdbMod> create(postgresMod.PartialPostgresMod moduleOverrides);
    }

    private static final URL INITDB_WASM_URL = resolveInitdbWasmUrl();

    private initdbModFactory() {}

    public static Promise<InitdbMod> create(postgresMod.PartialPostgresMod moduleOverrides) {
        return createWasiModule(moduleOverrides);
    }

    public static Promise<InitdbMod> createWasiModule(postgresMod.PartialPostgresMod moduleOverrides) {
        var overrides = moduleOverrides != null ? moduleOverrides : new postgresMod.PartialPostgresMod();
        return postgresMod.createWasiModule(overrides, INITDB_WASM_URL);
    }

    private static URL resolveInitdbWasmUrl() {
        var url = initdbModFactory.class.getClassLoader().getResource(
            extensionCatalog.RELEASE_RESOURCE_ROOT + "initdb.wasm"
        );
        if (url == null) {
            url = initdbModFactory.class.getResource("/initdb.wasm");
        }
        if (url == null) {
            url = initdbModFactory.class.getResource("initdb.wasm");
        }
        return url;
    }
}
