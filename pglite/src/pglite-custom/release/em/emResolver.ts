import type {
	EmAsmHandler,
	EmJsHandler,
	RuntimeModule,
	RuntimeState,
} from "../core/runtimeTypes.js";

const DYNAMIC_EVAL_PATTERN = /\beval\s*\(|\bnew\s+Function\s*\(/;

export function assertNoDynamicEval(sourceText: string): void {
	if (DYNAMIC_EVAL_PATTERN.test(sourceText)) {
		throw new Error("Dynamic code evaluation is forbidden in pglite-custom outputs");
	}
}

export function attachEmAsmHandlers(
	state: RuntimeState,
	registry: Record<number, EmAsmHandler>,
): void {
	state.asmHandlers.clear();
	for (const [key, handler] of Object.entries(registry)) {
		state.asmHandlers.set(Number(key), handler);
	}
}

export function attachEmJsHandlers(
	state: RuntimeState,
	registry: Record<string, EmJsHandler>,
): void {
	state.emJsHandlers.clear();
	for (const [key, handler] of Object.entries(registry)) {
		state.emJsHandlers.set(key, handler);
	}
}

export function runEmAsmFunction(
	state: RuntimeState,
	module: RuntimeModule,
	code: number,
	args: unknown[],
): unknown {
	const handler = state.asmHandlers.get(code);
	if (!handler) {
		throw new Error(`Unresolved EM_ASM handler code=${code}`);
	}
	return handler({ state, module }, ...args);
}

export function resolveEmJsHandler(
	state: RuntimeState,
	module: RuntimeModule,
	symbol: string,
): (...args: unknown[]) => unknown {
	const handler = state.emJsHandlers.get(symbol);
	if (!handler) {
		throw new Error(`Unresolved EM_JS symbol=${symbol}`);
	}
	return (...args: unknown[]) => handler({ state, module }, ...args);
}
