package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.core.runtimeTypes;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.preload.packageLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class RuntimePackageLoaderParityTest {
    @Test
    void shouldInstallLoaderHookWhenRuntimeNotStarted() {
        var module = new CapturingRuntimeModule(false);
        module.preloadedPackage = "data".getBytes();
        var manifest = new runtimeTypes.DataManifest(
            List.of(new runtimeTypes.DataFileEntry("/tmp/pglite/share/postgresql/sample.txt", 0, 4, 0)),
            4
        );
        var options = new packageLoader.PackageLoaderOptions(
            manifest,
            List.of(
                new runtimeTypes.FsPathEntry("/", "tmp", true, true),
                new runtimeTypes.FsPathEntry("/tmp", "pglite", true, true)
            ),
            "pglite.data",
            "pglite.data"
        );

        packageLoader.installPackageLoader(module, options);
        assertEquals(1, module.preRun().size());

        module.preRun().getFirst().run();

        assertTrue(module.paths.contains("/tmp"));
        assertTrue(module.paths.contains("/tmp/pglite"));
        assertArrayEquals(
            "data".getBytes(),
            module.dataFiles.get("/tmp/pglite/share/postgresql/sample.txt")
        );
        assertTrue(module.removedDependencies.contains("datafile_pglite.data"));
        assertTrue(module.preloadResults().containsKey("pglite.data"));
        assertFalse(module.dataFileDownloads().containsKey("pglite.data"));
    }

    @Test
    void shouldRunImmediatelyWhenRuntimeAlreadyStarted() {
        var module = new CapturingRuntimeModule(true);
        module.preloadedPackage = "data".getBytes();
        var manifest = new runtimeTypes.DataManifest(
            List.of(new runtimeTypes.DataFileEntry("/tmp/pglite/share/postgresql/sample.txt", 0, 4, 0)),
            4
        );
        var options = new packageLoader.PackageLoaderOptions(
            manifest,
            List.of(new runtimeTypes.FsPathEntry("/", "tmp", true, true)),
            "pglite.data",
            "pglite.data"
        );

        packageLoader.installPackageLoader(module, options);

        assertTrue(module.preRun().isEmpty());
        assertArrayEquals(
            "data".getBytes(),
            module.dataFiles.get("/tmp/pglite/share/postgresql/sample.txt")
        );
    }

    private static class CapturingRuntimeModule implements runtimeTypes.RuntimeModule {
        final boolean calledRun;
        final List<String> paths = new ArrayList<>();
        final List<String> addedDependencies = new ArrayList<>();
        final List<String> removedDependencies = new ArrayList<>();
        final Map<String, byte[]> dataFiles = new HashMap<>();
        final List<Runnable> preRun = new ArrayList<>();
        final Map<String, Map<String, Integer>> dataFileDownloads = new HashMap<>();
        final Map<String, Map<String, Boolean>> preloadResults = new HashMap<>();
        byte[] preloadedPackage;
        String status;

        CapturingRuntimeModule(boolean calledRun) {
            this.calledRun = calledRun;
        }

        @Override
        public void FS_createPath(String parent, String name, boolean canRead, boolean canWrite) {
            var path = ("/".equals(parent) ? "" : parent) + "/" + name;
            paths.add(path);
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
            dataFiles.put(name, data);
        }

        @Override
        public void addRunDependency(String name) {
            addedDependencies.add(name);
        }

        @Override
        public void removeRunDependency(String name) {
            removedDependencies.add(name);
        }

        @Override
        public byte[] getPreloadedPackage(String remotePackageName, int remotePackageSize) {
            return preloadedPackage;
        }

        @Override
        public void setStatus(String status) {
            this.status = status;
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
        public boolean calledRun() {
            return calledRun;
        }

        @Override
        public Map<String, Map<String, Integer>> dataFileDownloads() {
            return dataFileDownloads;
        }

        @Override
        public void setDataFileDownloads(Map<String, Map<String, Integer>> downloads) {
            dataFileDownloads.clear();
            dataFileDownloads.putAll(downloads);
        }

        @Override
        public Map<String, Map<String, Boolean>> preloadResults() {
            return preloadResults;
        }

        @Override
        public void setPreloadResults(Map<String, Map<String, Boolean>> preloadResults) {
            this.preloadResults.clear();
            this.preloadResults.putAll(preloadResults);
        }
    }
}
