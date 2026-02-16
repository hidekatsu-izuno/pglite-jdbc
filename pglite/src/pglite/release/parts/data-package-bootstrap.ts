export const runDataPackageBootstrap = ({
	Module,
	PGLITE_DATA_METADATA,
	require,
}: {
	Module: Record<string, any>;
	PGLITE_DATA_METADATA: {
		files: Array<Record<string, any>>;
		remote_package_size: number;
	};
	require?: (id: string) => any;
}) => {
	const isPthread = Boolean((globalThis as any).ENVIRONMENT_IS_PTHREAD);
	const isWasmWorker = Boolean((globalThis as any).ENVIRONMENT_IS_WASM_WORKER);
	if (isPthread || isWasmWorker) return;
	const processRef = (globalThis as any).process;
	const isNode =
		typeof processRef === "object" &&
		typeof processRef?.versions === "object" &&
		typeof processRef?.versions?.node === "string";

	type DownloadProgress = { loaded: number; total: number };
	type MetadataFile = {
		filename: string;
		start: number;
		end: number;
		audio?: number;
	};
	type Metadata = {
		files: MetadataFile[];
		remote_package_size: number;
	};
	type FetchCallback = (data: ArrayBufferLike) => void;
	type DataRequestRecord = {
		start: number;
		end: number;
		audio: number;
		name: string;
		byteArray: Uint8Array;
		finish: (byteArray: Uint8Array) => void;
	};

	function loadPackage(metadata: Metadata) {
		let PACKAGE_PATH = "";
		if (typeof window === "object") {
			PACKAGE_PATH = window["encodeURIComponent"](
				window.location.pathname.substring(
					0,
					window.location.pathname.lastIndexOf("/"),
				) + "/",
			);
		} else if (
			typeof processRef === "undefined" &&
			typeof location !== "undefined"
		) {
			PACKAGE_PATH = encodeURIComponent(
				location.pathname.substring(0, location.pathname.lastIndexOf("/")) +
					"/",
			);
		}
		const PACKAGE_NAME = "pglite.data";
		const REMOTE_PACKAGE_BASE = "pglite.data";
		const REMOTE_PACKAGE_NAME = Module["locateFile"]
			? Module["locateFile"](REMOTE_PACKAGE_BASE, "")
			: REMOTE_PACKAGE_BASE;
		const REMOTE_PACKAGE_SIZE = metadata["remote_package_size"];
		void PACKAGE_PATH;

		function fetchRemotePackage(
			packageName: string,
			packageSize: number,
			callback: FetchCallback,
			errback: (error: unknown) => void,
		) {
			if (isNode) {
				if (!require) {
					errback(new Error("require is unavailable in Node mode"));
					return;
				}
				require("fs").readFile(
					packageName,
					(
						err: unknown,
						contents: ArrayBufferView & {
							buffer: ArrayBufferLike;
							byteOffset: number;
							byteLength: number;
						},
					) => {
						if (err) {
							errback(err);
						} else {
							callback(
								contents.buffer.slice(
									contents.byteOffset,
									contents.byteOffset + contents.byteLength,
								),
							);
						}
					},
				);
				return;
			}
			Module["dataFileDownloads"] ??= {};
			fetch(packageName)
				.catch((cause: unknown) =>
					Promise.reject(new Error(`Network Error: ${packageName}`, { cause })),
				)
				.then((response: Response) => {
					if (!response.ok) {
						return Promise.reject(
							new Error(`${response.status}: ${response.url}`),
						);
					}
					if (!response.body && response.arrayBuffer) {
						return response.arrayBuffer().then(callback);
					}
					const reader = response.body!.getReader();
					const chunks: Uint8Array[] = [];
					const headers = response.headers;
					const total = Number(headers.get("Content-Length") ?? packageSize);
					let loaded = 0;
					const iterate = () =>
						reader
							.read()
							.then(handleChunk)
							.catch((cause: unknown) =>
								Promise.reject(
									new Error(
										`Unexpected error while handling : ${response.url}${cause}`,
										{ cause },
									),
								),
							);
					const handleChunk = ({
						done,
						value,
					}: ReadableStreamReadResult<Uint8Array>) => {
						if (!done && value) {
							chunks.push(value);
							loaded += value.length;
							Module["dataFileDownloads"][packageName] = { loaded, total };
							let totalLoaded = 0;
							let totalSize = 0;
							for (const download of Object.values(
								Module["dataFileDownloads"],
							) as DownloadProgress[]) {
								totalLoaded += download.loaded;
								totalSize += download.total;
							}
							Module["setStatus"]?.(
								`Downloading data... (${totalLoaded}/${totalSize})`,
							);
							return iterate();
						}
						const packageData = new Uint8Array(
							chunks.map((c) => c.length).reduce((a, b) => a + b, 0),
						);
						let offset = 0;
						for (const chunk of chunks) {
							packageData.set(chunk, offset);
							offset += chunk.length;
						}
						callback(packageData.buffer);
					};
					Module["setStatus"]?.("Downloading data...");
					return iterate();
				});
		}

		function handleError(error: unknown) {
			console.error("package error:", error);
		}

		let fetchedCallback: FetchCallback | null = null;
		let fetched = Module["getPreloadedPackage"]
			? (Module["getPreloadedPackage"](
					REMOTE_PACKAGE_NAME,
					REMOTE_PACKAGE_SIZE,
				) as ArrayBufferLike | null)
			: null;
		if (!fetched) {
			fetchRemotePackage(
				REMOTE_PACKAGE_NAME,
				REMOTE_PACKAGE_SIZE,
				(data: ArrayBufferLike) => {
					if (fetchedCallback) {
						fetchedCallback(data);
						fetchedCallback = null;
					} else {
						fetched = data;
					}
				},
				handleError,
			);
		}

		function runWithFS(module: Record<string, any>) {
			function assert(check: unknown, msg: string) {
				if (!check) throw msg + new Error().stack;
			}
			module["FS_createPath"]("/", "home", true, true);
			module["FS_createPath"]("/home", "web_user", true, true);
			module["FS_createPath"]("/", "tmp", true, true);
			module["FS_createPath"]("/tmp", "pglite", true, true);
			module["FS_createPath"]("/tmp/pglite", "bin", true, true);
			module["FS_createPath"]("/tmp/pglite", "lib", true, true);
			module["FS_createPath"]("/tmp/pglite/lib", "postgresql", true, true);
			module["FS_createPath"]("/tmp/pglite/lib/postgresql", "pgxs", true, true);
			module["FS_createPath"](
				"/tmp/pglite/lib/postgresql/pgxs",
				"config",
				true,
				true,
			);
			module["FS_createPath"](
				"/tmp/pglite/lib/postgresql/pgxs",
				"src",
				true,
				true,
			);
			module["FS_createPath"](
				"/tmp/pglite/lib/postgresql/pgxs/src",
				"makefiles",
				true,
				true,
			);
			module["FS_createPath"]("/tmp/pglite", "share", true, true);
			module["FS_createPath"]("/tmp/pglite/share", "postgresql", true, true);
			module["FS_createPath"](
				"/tmp/pglite/share/postgresql",
				"extension",
				true,
				true,
			);
			module["FS_createPath"](
				"/tmp/pglite/share/postgresql",
				"timezone",
				true,
				true,
			);
			module["FS_createPath"](
				"/tmp/pglite/share/postgresql/timezone",
				"Africa",
				true,
				true,
			);
			module["FS_createPath"](
				"/tmp/pglite/share/postgresql/timezone",
				"America",
				true,
				true,
			);
			module["FS_createPath"](
				"/tmp/pglite/share/postgresql/timezone/America",
				"Argentina",
				true,
				true,
			);
			module["FS_createPath"](
				"/tmp/pglite/share/postgresql/timezone/America",
				"Indiana",
				true,
				true,
			);
			module["FS_createPath"](
				"/tmp/pglite/share/postgresql/timezone/America",
				"Kentucky",
				true,
				true,
			);
			module["FS_createPath"](
				"/tmp/pglite/share/postgresql/timezone/America",
				"North_Dakota",
				true,
				true,
			);
			module["FS_createPath"](
				"/tmp/pglite/share/postgresql/timezone",
				"Antarctica",
				true,
				true,
			);
			module["FS_createPath"](
				"/tmp/pglite/share/postgresql/timezone",
				"Arctic",
				true,
				true,
			);
			module["FS_createPath"](
				"/tmp/pglite/share/postgresql/timezone",
				"Asia",
				true,
				true,
			);
			module["FS_createPath"](
				"/tmp/pglite/share/postgresql/timezone",
				"Atlantic",
				true,
				true,
			);
			module["FS_createPath"](
				"/tmp/pglite/share/postgresql/timezone",
				"Australia",
				true,
				true,
			);
			module["FS_createPath"](
				"/tmp/pglite/share/postgresql/timezone",
				"Brazil",
				true,
				true,
			);
			module["FS_createPath"](
				"/tmp/pglite/share/postgresql/timezone",
				"Canada",
				true,
				true,
			);
			module["FS_createPath"](
				"/tmp/pglite/share/postgresql/timezone",
				"Chile",
				true,
				true,
			);
			module["FS_createPath"](
				"/tmp/pglite/share/postgresql/timezone",
				"Etc",
				true,
				true,
			);
			module["FS_createPath"](
				"/tmp/pglite/share/postgresql/timezone",
				"Europe",
				true,
				true,
			);
			module["FS_createPath"](
				"/tmp/pglite/share/postgresql/timezone",
				"Indian",
				true,
				true,
			);
			module["FS_createPath"](
				"/tmp/pglite/share/postgresql/timezone",
				"Mexico",
				true,
				true,
			);
			module["FS_createPath"](
				"/tmp/pglite/share/postgresql/timezone",
				"Pacific",
				true,
				true,
			);
			module["FS_createPath"](
				"/tmp/pglite/share/postgresql/timezone",
				"US",
				true,
				true,
			);
			module["FS_createPath"](
				"/tmp/pglite/share/postgresql",
				"timezonesets",
				true,
				true,
			);
			module["FS_createPath"](
				"/tmp/pglite/share/postgresql",
				"tsearch_data",
				true,
				true,
			);

			function DataRequest(
				this: DataRequestRecord,
				start: number,
				end: number,
				audio: number,
			) {
				this.start = start;
				this.end = end;
				this.audio = audio;
			}
			(DataRequest as any).prototype = {
				requests: {} as Record<string, DataRequestRecord | null>,
				open: function (this: DataRequestRecord, _mode: string, name: string) {
					this.name = name;
					(
						(DataRequest as any).prototype.requests as Record<
							string,
							DataRequestRecord | null
						>
					)[name] = this;
					module["addRunDependency"](`fp ${this.name}`);
				},
				send: function (this: DataRequestRecord) {
					void this;
				},
				onload: function (this: DataRequestRecord) {
					const byteArray = this.byteArray.subarray(this.start, this.end);
					this.finish(byteArray);
				},
				finish: function (this: DataRequestRecord, byteArray: Uint8Array) {
					module["FS_createDataFile"](
						this.name,
						null,
						byteArray,
						true,
						true,
						true,
					);
					module["removeRunDependency"](`fp ${this.name}`);
					(
						(DataRequest as any).prototype.requests as Record<
							string,
							DataRequestRecord | null
						>
					)[this.name] = null;
				},
			};

			const files = metadata["files"];
			for (let i = 0; i < files.length; ++i) {
				new (DataRequest as any)(
					files[i]["start"],
					files[i]["end"],
					files[i]["audio"] || 0,
				).open("GET", files[i]["filename"]);
			}

			function processPackageData(arrayBuffer: ArrayBufferLike) {
				assert(arrayBuffer, "Loading data file failed.");
				assert(
					arrayBuffer.constructor.name === ArrayBuffer.name,
					"bad input to processPackageData",
				);
				const byteArray = new Uint8Array(arrayBuffer);
				(DataRequest as any).prototype.byteArray = byteArray;
				const dataFiles = metadata["files"];
				for (let i = 0; i < dataFiles.length; ++i) {
					(
						(DataRequest as any).prototype.requests as Record<
							string,
							{ onload: () => void } | null
						>
					)[dataFiles[i].filename]?.onload();
				}
				module["removeRunDependency"]("datafile_pglite.data");
			}
			module["addRunDependency"]("datafile_pglite.data");
			module["preloadResults"] ??= {};
			module["preloadResults"][PACKAGE_NAME] = { fromCache: false };
			if (fetched) {
				processPackageData(fetched);
				fetched = null;
			} else {
				fetchedCallback = processPackageData;
			}
		}
		if (Module["calledRun"]) {
			runWithFS(Module);
		} else {
			(Module["preRun"] ??= []).push(runWithFS);
		}
	}
	loadPackage(PGLITE_DATA_METADATA as Metadata);
};
