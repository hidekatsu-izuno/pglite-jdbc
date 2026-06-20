package io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.preload;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.core.runtimeTypes;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.fs.fsBridge;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.utils;
import java.util.HashMap;
import java.util.Map;

public class packageLoader {
    public record PackageLoaderOptions(
        runtimeTypes.DataManifest manifest,
        java.util.List<runtimeTypes.FsPathEntry> fsPaths,
        String packageName,
        String remotePackageBase
    ) {}

    private packageLoader() {}

    public static void installPackageLoader(runtimeTypes.RuntimeModule module, PackageLoaderOptions options) {
        var packageName = options.packageName() != null ? options.packageName() : "pglite.data";
        var base = options.remotePackageBase() != null ? options.remotePackageBase() : packageName;
        Runnable runWithFs = () -> {
            fsBridge.applyFsBootstrapPaths(module, options.fsPaths());
            module.addRunDependency("datafile_pglite.data");

            var preloadResults = module.preloadResults();
            if (preloadResults == null) {
                preloadResults = new HashMap<>();
                module.setPreloadResults(preloadResults);
            }
            preloadResults.put(packageName, Map.of("fromCache", Boolean.FALSE));

            var remotePackageName = module.locateFile(base, "");
            var fetched = module.getPreloadedPackage(remotePackageName, options.manifest().remotePackageSize());
            if (fetched == null) {
                fetched = fetchRemotePackage(module, remotePackageName, options.manifest().remotePackageSize());
            }
            processPackageData(module, options.manifest(), fetched);
        };

        if (module.calledRun()) {
            runWithFs.run();
            return;
        }
        var hooks = new java.util.ArrayList<>(module.preRun());
        hooks.add(runWithFs);
        module.setPreRun(hooks);
    }

    private static void processPackageData(
        runtimeTypes.RuntimeModule module,
        runtimeTypes.DataManifest manifest,
        byte[] bytes
    ) {
        if (bytes == null) {
            throw new IllegalStateException("Loading data file failed.");
        }
        for (var file : manifest.files()) {
            var request = new dataRequest(module, bytes, file);
            request.open(file.filename());
            request.onload();
        }
        module.removeRunDependency("datafile_pglite.data");
    }

    private static byte[] fetchRemotePackage(
        runtimeTypes.RuntimeModule module,
        String packageName,
        int packageSize
    ) {
        var downloads = module.dataFileDownloads();
        if (downloads == null) {
            downloads = new HashMap<>();
            module.setDataFileDownloads(downloads);
        }
        var bytes = utils.readFile(packageName);
        var total = bytes.length > 0 ? bytes.length : packageSize;
        downloads.put(packageName, Map.of("loaded", bytes.length, "total", total));
        module.setStatus("Downloading data... (" + bytes.length + "/" + total + ")");
        return bytes;
    }
}
