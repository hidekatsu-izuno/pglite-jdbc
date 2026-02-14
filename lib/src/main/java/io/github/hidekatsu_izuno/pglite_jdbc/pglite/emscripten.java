package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class emscripten {
    private emscripten() {}

    public interface FileSystemType {
        Object mount(Object mount);

        void syncfs(Object mount, Runnable populate, Consumer<Integer> done);
    }

    public interface EmscriptenModule {
        default void print(String str) {}

        default void printErr(String str) {}

        default String[] arguments() {
            return new String[0];
        }

        default List<Runnable> preInit() {
            return List.of();
        }

        default List<Runnable> preRun() {
            return List.of();
        }

        default List<Runnable> postRun() {
            return List.of();
        }

        default void onAbort(Object what) {}

        default void onRuntimeInitialized() {}

        default String locateFile(String url, String scriptDirectory) {
            return url;
        }

        default byte[] getPreloadedPackage(String remotePackageName, int remotePackageSize) {
            return null;
        }

        default Object instantiateWasm(
            Map<String, Object> imports,
            Consumer<Object> successCallback
        ) {
            return null;
        }
    }

    @FunctionalInterface
    public interface EmscriptenModuleFactory<T extends EmscriptenModule> {
        Promise<T> create(T moduleOverrides);
    }
}
