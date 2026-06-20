package io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.core;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.em.emAsmRegistry_generated;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.em.emJsRegistry_generated;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.em.emResolver;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.preload.dataManifest_generated;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.preload.fsBootstrapPaths_generated;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.preload.packageLoader;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class moduleFactory {
    public interface CustomModuleFactoryOptions {
        default runtimeTypes.WasmLoader baseFactory() {
            return null;
        }

        default boolean installCustomPackageLoader() {
            return false;
        }

        default List<String> sourceTextsToValidate() {
            return List.of();
        }

        default Consumer<runtimeTypes.RuntimeState> onStateCreated() {
            return null;
        }
    }

    private moduleFactory() {}

    public static runtimeTypes.WasmLoader createModuleFactory(CustomModuleFactoryOptions options) {
        return (moduleArg, ignored) -> {
            var resolvedArg = moduleArg != null ? moduleArg : Map.<String, Object>of();
            var resolvedOptions = options != null
                ? options
                : new CustomModuleFactoryOptions() {};
            var baseFactory = resolvedOptions.baseFactory();
            if (baseFactory == null) {
                baseFactory = (arg, ignoredFactoryArg) -> Promise.resolve(new runtimeTypes.MutableRuntimeModule());
            }

            for (var sourceText : resolvedOptions.sourceTextsToValidate()) {
                emResolver.assertNoDynamicEval(sourceText);
            }

            var state = runtimeState.createRuntimeState(
                resolvedArg,
                dataManifest_generated.dataManifest
            );
            emResolver.attachEmAsmHandlers(state, emAsmRegistry_generated.emAsmRegistry);
            emResolver.attachEmJsHandlers(state, emJsRegistry_generated.emJsRegistry);
            if (resolvedOptions.onStateCreated() != null) {
                resolvedOptions.onStateCreated().accept(state);
            }

            return baseFactory
                .apply(resolvedArg, ignored)
                .then(moduleObj -> {
                    var module = (runtimeTypes.RuntimeModule) moduleObj;
                    if (resolvedOptions.installCustomPackageLoader()) {
                        packageLoader.installPackageLoader(
                            module,
                            new packageLoader.PackageLoaderOptions(
                                dataManifest_generated.dataManifest,
                                fsBootstrapPaths_generated.fsBootstrapPaths,
                                "pglite.data",
                                "pglite.data"
                            )
                        );
                    }
                    return runtimeState.bindRuntimeModule(state, module);
                });
        };
    }
}
