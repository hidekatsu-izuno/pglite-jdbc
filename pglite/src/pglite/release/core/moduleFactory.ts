import OriginalModuleFactory from "../../../pglite/release/pglite.js";
import { bindRuntimeModule, createRuntimeState } from "./runtimeState.js";
import type {
	RuntimeModule,
	RuntimeState,
	WasmLoader,
} from "./runtimeTypes.js";
import { dataManifest } from "../preload/dataManifest.generated.js";
import { fsBootstrapPaths } from "../preload/fsBootstrapPaths.generated.js";
import { installPackageLoader } from "../preload/packageLoader.js";
import { emAsmRegistry } from "../em/emAsmRegistry.generated.js";
import { emJsRegistry } from "../em/emJsRegistry.generated.js";
import {
	assertNoDynamicEval,
	attachEmAsmHandlers,
	attachEmJsHandlers,
} from "../em/emResolver.js";

export interface CustomModuleFactoryOptions {
	baseFactory?: WasmLoader;
	installCustomPackageLoader?: boolean;
	sourceTextsToValidate?: string[];
	onStateCreated?: (state: RuntimeState) => void;
}

export function createModuleFactory(
	options: CustomModuleFactoryOptions = {},
): WasmLoader {
	const baseFactory = options.baseFactory ?? (OriginalModuleFactory as WasmLoader);
	const installCustomPackageLoader = options.installCustomPackageLoader ?? false;

	return async function Module(
		moduleArg: Partial<RuntimeModule> = {},
	): Promise<RuntimeModule> {
		if (options.sourceTextsToValidate) {
			for (const sourceText of options.sourceTextsToValidate) {
				assertNoDynamicEval(sourceText);
			}
		}

		const state = createRuntimeState(moduleArg, dataManifest);
		attachEmAsmHandlers(state, emAsmRegistry);
		attachEmJsHandlers(state, emJsRegistry);
		options.onStateCreated?.(state);

		if (installCustomPackageLoader) {
			installPackageLoader(moduleArg as RuntimeModule, {
				manifest: dataManifest,
				fsPaths: fsBootstrapPaths,
			});
		}

		const module = await baseFactory(moduleArg);
		return bindRuntimeModule(state, module);
	};
}
