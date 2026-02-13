import type { DataFileEntry, RuntimeModule } from "../core/runtimeTypes.js";

export class DataRequest {
	private static readonly requests = new Map<string, DataRequest>();
	private readonly start: number;
	private readonly end: number;
	private readonly audio: number;
	private readonly module: RuntimeModule;
	private readonly byteArray: Uint8Array;
	private name: string | null = null;

	constructor(
		module: RuntimeModule,
		byteArray: Uint8Array,
		entry: DataFileEntry,
	) {
		this.module = module;
		this.byteArray = byteArray;
		this.start = entry.start;
		this.end = entry.end;
		this.audio = entry.audio ?? 0;
	}

	open(name: string): void {
		this.name = name;
		DataRequest.requests.set(name, this);
		this.module.addRunDependency?.(`fp ${name}`);
	}

	onload(): void {
		const bytes = this.byteArray.subarray(this.start, this.end);
		this.finish(bytes);
	}

	private finish(bytes: Uint8Array): void {
		if (!this.name) {
			throw new Error("DataRequest has no open file name");
		}
		const createDataFile = this.module.FS_createDataFile;
		if (!createDataFile) {
			throw new Error("FS_createDataFile is not available on module");
		}
		createDataFile(this.name, null, bytes, true, true, true);
		this.module.removeRunDependency?.(`fp ${this.name}`);
		DataRequest.requests.delete(this.name);
		void this.audio;
	}
}
