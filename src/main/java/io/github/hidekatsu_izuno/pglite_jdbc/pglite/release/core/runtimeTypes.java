package io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import java.util.function.BiFunction;

public class runtimeTypes {
    private runtimeTypes() {}

    @FunctionalInterface
    public interface EmAsmHandler {
        Object apply(EmResolverContext context, Object... args);
    }

    @FunctionalInterface
    public interface EmJsHandler {
        Object apply(EmResolverContext context, Object... args);
    }

    public record FsPathEntry(String parent, String name, boolean canRead, boolean canWrite) {}

    public record DataFileEntry(String filename, int start, int end, int audio) {}

    public record DataManifest(List<DataFileEntry> files, int remotePackageSize) {
        public static DataManifest empty() {
            return new DataManifest(List.of(), 0);
        }
    }

    public interface RuntimeModule {
        default void FS_createPath(String parent, String name, boolean canRead, boolean canWrite) {}

        default void FS_createDataFile(
            String name,
            String path,
            byte[] data,
            boolean canRead,
            boolean canWrite,
            boolean canOwn
        ) {}

        default void addRunDependency(String name) {}

        default void removeRunDependency(String name) {}

        default String locateFile(String path, String scriptDirectory) {
            return path;
        }

        default byte[] getPreloadedPackage(String remotePackageName, int remotePackageSize) {
            return null;
        }

        default void setStatus(String status) {}

        default List<Runnable> preRun() {
            return new ArrayList<>();
        }

        default void setPreRun(List<Runnable> hooks) {}

        default List<Runnable> postRun() {
            return new ArrayList<>();
        }

        default void setPostRun(List<Runnable> hooks) {}

        default boolean calledRun() {
            return false;
        }

        default Map<String, Map<String, Integer>> dataFileDownloads() {
            return null;
        }

        default void setDataFileDownloads(Map<String, Map<String, Integer>> downloads) {}

        default Map<String, Map<String, Boolean>> preloadResults() {
            return null;
        }

        default void setPreloadResults(Map<String, Map<String, Boolean>> preloadResults) {}
    }

    public static class MutableRuntimeModule implements RuntimeModule {
        private final List<Runnable> preRun = new ArrayList<>();
        private final List<Runnable> postRun = new ArrayList<>();
        private final Map<String, Map<String, Integer>> dataFileDownloads = new HashMap<>();
        private final Map<String, Map<String, Boolean>> preloadResults = new HashMap<>();
        private final Map<String, byte[]> files = new HashMap<>();
        private boolean calledRun;

        @Override
        public void FS_createPath(String parent, String name, boolean canRead, boolean canWrite) {
            var key = parent.endsWith("/") ? parent + name : parent + "/" + name;
            files.putIfAbsent(key, new byte[0]);
        }

        @Override
        public void FS_createDataFile(
            String name,
            String path,
            byte[] data,
            boolean canRead,
            boolean canWrite,
            boolean canOwn
        ) {
            files.put(name, data != null ? data : new byte[0]);
        }

        @Override
        public List<Runnable> preRun() {
            return preRun;
        }

        @Override
        public void setPreRun(List<Runnable> hooks) {
            preRun.clear();
            preRun.addAll(hooks);
        }

        @Override
        public List<Runnable> postRun() {
            return postRun;
        }

        @Override
        public void setPostRun(List<Runnable> hooks) {
            postRun.clear();
            postRun.addAll(hooks);
        }

        @Override
        public boolean calledRun() {
            return calledRun;
        }

        public void setCalledRun(boolean calledRun) {
            this.calledRun = calledRun;
        }

        @Override
        public Map<String, Map<String, Integer>> dataFileDownloads() {
            return dataFileDownloads;
        }

        @Override
        public void setDataFileDownloads(Map<String, Map<String, Integer>> downloads) {
            dataFileDownloads.clear();
            if (downloads != null) {
                dataFileDownloads.putAll(downloads);
            }
        }

        @Override
        public Map<String, Map<String, Boolean>> preloadResults() {
            return preloadResults;
        }

        @Override
        public void setPreloadResults(Map<String, Map<String, Boolean>> preload) {
            preloadResults.clear();
            if (preload != null) {
                preloadResults.putAll(preload);
            }
        }
    }

    public static class RuntimeState {
        private final Map<String, Object> moduleArg;
        private RuntimeModule module;
        private final Map<Integer, EmAsmHandler> asmHandlers;
        private final Map<String, EmJsHandler> emJsHandlers;
        private final DataManifest dataManifest;
        private final List<String> diagnostics;

        public RuntimeState(
            Map<String, Object> moduleArg,
            RuntimeModule module,
            Map<Integer, EmAsmHandler> asmHandlers,
            Map<String, EmJsHandler> emJsHandlers,
            DataManifest dataManifest,
            List<String> diagnostics
        ) {
            this.moduleArg = moduleArg;
            this.module = module;
            this.asmHandlers = asmHandlers;
            this.emJsHandlers = emJsHandlers;
            this.dataManifest = dataManifest;
            this.diagnostics = diagnostics;
        }

        public Map<String, Object> moduleArg() {
            return moduleArg;
        }

        public RuntimeModule module() {
            return module;
        }

        public void setModule(RuntimeModule module) {
            this.module = module;
        }

        public Map<Integer, EmAsmHandler> asmHandlers() {
            return asmHandlers;
        }

        public Map<String, EmJsHandler> emJsHandlers() {
            return emJsHandlers;
        }

        public DataManifest dataManifest() {
            return dataManifest;
        }

        public List<String> diagnostics() {
            return diagnostics;
        }
    }

    public record EmResolverContext(RuntimeState state, RuntimeModule module) {}

    @FunctionalInterface
    public interface WasmLoader extends BiFunction<Map<String, Object>, Object, Promise<RuntimeModule>> {}
}
