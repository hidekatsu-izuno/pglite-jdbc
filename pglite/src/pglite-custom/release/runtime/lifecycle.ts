import type { RuntimeModule } from "../core/runtimeTypes.js";

function asArray(
	hooks: RuntimeModule["preRun"] | RuntimeModule["postRun"] | undefined,
): Array<() => void> {
	if (!hooks) {
		return [];
	}
	if (Array.isArray(hooks)) {
		return hooks;
	}
	return [hooks as unknown as () => void];
}

export function mergePreRunHooks(
	moduleArg: Partial<RuntimeModule>,
	hook: () => void,
): void {
	const hooks = asArray(moduleArg.preRun);
	hooks.push(hook);
	moduleArg.preRun = hooks;
}

export function mergePostRunHooks(
	moduleArg: Partial<RuntimeModule>,
	hook: () => void,
): void {
	const hooks = asArray(moduleArg.postRun);
	hooks.push(hook);
	moduleArg.postRun = hooks;
}
