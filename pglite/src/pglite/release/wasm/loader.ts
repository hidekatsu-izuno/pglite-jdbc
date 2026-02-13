import type { RuntimeModule, WasmLoader } from "../core/runtimeTypes.js";

export async function loadWasmModule(
	factory: WasmLoader,
	moduleOverrides: Partial<RuntimeModule>,
): Promise<RuntimeModule> {
	return factory(moduleOverrides);
}
