/// <reference path="../../../pglite/emscripten.d.ts" />

export type EmAsmHandler = (
	context: EmResolverContext,
	...args: unknown[]
) => unknown;

export type EmJsHandler = (
	context: EmResolverContext,
	...args: unknown[]
) => unknown;

export interface FsPathEntry {
	parent: string;
	name: string;
	canRead: boolean;
	canWrite: boolean;
}

export interface DataFileEntry {
	filename: string;
	start: number;
	end: number;
	audio?: number;
}

export interface DataManifest {
	files: DataFileEntry[];
	remotePackageSize: number;
}

export interface RuntimeModule extends EmscriptenModule {
	calledRun?: boolean;
	setStatus?: (status: string) => void;
	FS_createPath?: (
		parent: string,
		name: string,
		canRead: boolean,
		canWrite: boolean,
	) => unknown;
	FS_createDataFile?: (
		name: string,
		path: string | null,
		data: Uint8Array,
		canRead: boolean,
		canWrite: boolean,
		canOwn: boolean,
	) => unknown;
	addRunDependency?: (name: string) => void;
	removeRunDependency?: (name: string) => void;
	locateFile?: (path: string, scriptDirectory: string) => string;
	dataFileDownloads?: Record<string, { loaded: number; total: number }>;
	preloadResults?: Record<string, { fromCache: boolean }>;
	[key: string]: unknown;
}

export interface RuntimeState {
	moduleArg: Partial<RuntimeModule>;
	module: RuntimeModule | null;
	asmHandlers: Map<number, EmAsmHandler>;
	emJsHandlers: Map<string, EmJsHandler>;
	dataManifest: DataManifest;
	diagnostics: string[];
}

export interface EmResolverContext {
	state: RuntimeState;
	module: RuntimeModule;
	stringToUTF8?: (text: string, ptr: number, maxBytes: number) => number;
}

export interface WasmLoader {
	(moduleOverrides?: Partial<RuntimeModule>): Promise<RuntimeModule>;
}
