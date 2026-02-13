import type {
	DataManifest,
	RuntimeModule,
	RuntimeState,
} from "./runtimeTypes.js";

const EMPTY_MANIFEST: DataManifest = {
	files: [],
	remotePackageSize: 0,
};

export function createRuntimeState(
	moduleArg: Partial<RuntimeModule>,
	manifest: DataManifest = EMPTY_MANIFEST,
): RuntimeState {
	return {
		moduleArg,
		module: null,
		asmHandlers: new Map(),
		emJsHandlers: new Map(),
		dataManifest: manifest,
		diagnostics: [],
	};
}

export function bindRuntimeModule(
	state: RuntimeState,
	module: RuntimeModule,
): RuntimeModule {
	state.module = module;
	return module;
}
