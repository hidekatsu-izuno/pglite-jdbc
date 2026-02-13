import type {
	DataManifest,
	RuntimeModule,
} from "../core/runtimeTypes.js";
import { applyFsBootstrapPaths } from "../fs/fsBridge.js";
import type { FsPathEntry } from "../core/runtimeTypes.js";
import { DataRequest } from "./dataRequest.js";

function assert(condition: unknown, message: string): asserts condition {
	if (!condition) {
		throw new Error(message);
	}
}

async function fetchRemotePackage(
	module: RuntimeModule,
	packageName: string,
	packageSize: number,
): Promise<ArrayBuffer> {
	module.dataFileDownloads ??= {};
	const response = await fetch(packageName);
	if (!response.ok) {
		throw new Error(`${response.status}: ${response.url}`);
	}
	if (!response.body && response.arrayBuffer) {
		return response.arrayBuffer();
	}
	assert(response.body, `response body is missing: ${packageName}`);
	const reader = response.body.getReader();
	const chunks: Uint8Array[] = [];
	const total = Number(response.headers.get("Content-Length") ?? packageSize);
	let loaded = 0;
	for (;;) {
		const { done, value } = await reader.read();
		if (done) {
			break;
		}
		chunks.push(value);
		loaded += value.length;
		module.dataFileDownloads[packageName] = { loaded, total };
		let totalLoaded = 0;
		let totalSize = 0;
		for (const download of Object.values(module.dataFileDownloads)) {
			totalLoaded += download.loaded;
			totalSize += download.total;
		}
		module.setStatus?.(`Downloading data... (${totalLoaded}/${totalSize})`);
	}
	const packageData = new Uint8Array(chunks.reduce((sum, c) => sum + c.length, 0));
	let offset = 0;
	for (const chunk of chunks) {
		packageData.set(chunk, offset);
		offset += chunk.length;
	}
	return packageData.buffer;
}

function processPackageData(
	module: RuntimeModule,
	manifest: DataManifest,
	arrayBuffer: ArrayBuffer,
): void {
	assert(arrayBuffer, "Loading data file failed.");
	const byteArray = new Uint8Array(arrayBuffer);
	for (const file of manifest.files) {
		const request = new DataRequest(module, byteArray, file);
		request.open(file.filename);
		request.onload();
	}
	module.removeRunDependency?.("datafile_pglite.data");
}

export interface PackageLoaderOptions {
	manifest: DataManifest;
	fsPaths: FsPathEntry[];
	packageName?: string;
	remotePackageBase?: string;
}

export function installPackageLoader(
	module: RuntimeModule,
	options: PackageLoaderOptions,
): void {
	const packageName = options.packageName ?? "pglite.data";
	const remotePackageBase = options.remotePackageBase ?? "pglite.data";
	const runWithFs = async (mod: RuntimeModule): Promise<void> => {
		applyFsBootstrapPaths(mod, options.fsPaths);
		mod.addRunDependency?.("datafile_pglite.data");
		mod.preloadResults ??= {};
		mod.preloadResults[packageName] = { fromCache: false };
		const remotePackageName = mod.locateFile
			? mod.locateFile(remotePackageBase, "")
			: remotePackageBase;
		let fetched = mod.getPreloadedPackage
			? mod.getPreloadedPackage(remotePackageName, options.manifest.remotePackageSize)
			: null;
		if (!fetched) {
			fetched = await fetchRemotePackage(
				mod,
				remotePackageName,
				options.manifest.remotePackageSize,
			);
		}
		processPackageData(mod, options.manifest, fetched);
	};

	const preRun = (mod: RuntimeModule) => {
		void runWithFs(mod).catch((error) => {
			throw error;
		});
	};

	if (module.calledRun) {
		preRun(module);
		return;
	}
	const hooks = (module.preRun ??= []);
	hooks.push(() => preRun(module));
}
