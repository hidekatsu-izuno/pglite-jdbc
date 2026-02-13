import type { FsPathEntry, RuntimeModule } from "../core/runtimeTypes.js";

export function applyFsBootstrapPaths(
	module: RuntimeModule,
	paths: FsPathEntry[],
): void {
	const createPath = module.FS_createPath;
	if (!createPath) {
		throw new Error("FS_createPath is not available on module");
	}
	for (const entry of paths) {
		createPath(entry.parent, entry.name, entry.canRead, entry.canWrite);
	}
}
