import fs from "node:fs";
import path from "node:path";
import vm from "node:vm";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const releaseDir = path.resolve(__dirname, "..");
const srcDir = path.resolve(releaseDir, "../..");
const repoRoot = path.resolve(srcDir, "..");
const originalReleasePath = path.resolve(srcDir, "pglite/release/pglite.js");

function readText(filePath) {
	return fs.readFileSync(filePath, "utf8");
}

function findBalancedIndex(source, openIndex, openChar, closeChar) {
	let depth = 0;
	let inSingle = false;
	let inDouble = false;
	let inTemplate = false;
	let inLineComment = false;
	let inBlockComment = false;
	let escaped = false;

	for (let i = openIndex; i < source.length; i += 1) {
		const ch = source[i];
		const next = source[i + 1] ?? "";

		if (inLineComment) {
			if (ch === "\n") {
				inLineComment = false;
			}
			continue;
		}
		if (inBlockComment) {
			if (ch === "*" && next === "/") {
				inBlockComment = false;
				i += 1;
			}
			continue;
		}

		if (escaped) {
			escaped = false;
			continue;
		}

		if (inSingle) {
			if (ch === "\\") {
				escaped = true;
			} else if (ch === "'") {
				inSingle = false;
			}
			continue;
		}
		if (inDouble) {
			if (ch === "\\") {
				escaped = true;
			} else if (ch === '"') {
				inDouble = false;
			}
			continue;
		}
		if (inTemplate) {
			if (ch === "\\") {
				escaped = true;
				continue;
			}
			if (ch === "`") {
				inTemplate = false;
			}
			continue;
		}

		if (ch === "/" && next === "/") {
			inLineComment = true;
			i += 1;
			continue;
		}
		if (ch === "/" && next === "*") {
			inBlockComment = true;
			i += 1;
			continue;
		}

		if (ch === "'") {
			inSingle = true;
			continue;
		}
		if (ch === '"') {
			inDouble = true;
			continue;
		}
		if (ch === "`") {
			inTemplate = true;
			continue;
		}

		if (ch === openChar) {
			depth += 1;
		}
		if (ch === closeChar) {
			depth -= 1;
			if (depth === 0) {
				return i;
			}
		}
	}

	throw new Error(`No balanced close found for ${openChar} at index ${openIndex}`);
}

function extractFsBootstrapPaths(source) {
	const pattern =
		/Module\["FS_createPath"\]\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*(true|false)\s*,\s*(true|false)\s*,?\s*\);/gs;
	const entries = [];
	for (const match of source.matchAll(pattern)) {
		entries.push({
			parent: match[1],
			name: match[2],
			canRead: match[3] === "true",
			canWrite: match[4] === "true",
		});
	}
	if (entries.length !== 37) {
		throw new Error(`Expected 37 FS_createPath entries, found ${entries.length}`);
	}
	return entries;
}

function extractLoadPackageMetadata(source) {
	const anchor = source.indexOf("loadPackage({");
	if (anchor < 0) {
		throw new Error("loadPackage({ not found");
	}
	const open = source.indexOf("{", anchor);
	const close = findBalancedIndex(source, open, "{", "}");
	const literal = source.slice(open, close + 1);
	const metadata = vm.runInNewContext(`(${literal})`, Object.create(null), {
		timeout: 1000,
	});
	if (!Array.isArray(metadata.files)) {
		throw new Error("metadata.files is not an array");
	}
	if (typeof metadata.remote_package_size !== "number") {
		throw new Error("metadata.remote_package_size is not a number");
	}
	return {
		files: metadata.files.map((file) => ({
			filename: file.filename,
			start: file.start,
			end: file.end,
			audio: file.audio,
		})),
		remotePackageSize: metadata.remote_package_size,
	};
}

function extractAsmConstKeys(source) {
	const anchor = source.indexOf("var ASM_CONSTS = {");
	if (anchor < 0) {
		throw new Error("ASM_CONSTS definition not found");
	}
	const open = source.indexOf("{", anchor);
	const close = findBalancedIndex(source, open, "{", "}");
	const literal = source.slice(open, close + 1);
	const objectValue = vm.runInNewContext(`(${literal})`, Object.create(null), {
		timeout: 1000,
	});
	const keys = Object.keys(objectValue)
		.map((key) => Number(key))
		.sort((a, b) => a - b);
	return keys;
}

function collectEmJsSymbols() {
	const candidates = [
		path.resolve(srcDir, "pglite/release/pglite.wasm"),
		path.resolve(repoRoot, "lib/src/main/resources/pglite.wasm"),
	];
	for (const candidate of candidates) {
		if (!fs.existsSync(candidate)) {
			continue;
		}
		const text = fs.readFileSync(candidate).toString("latin1");
		const symbols = [
			...new Set(text.match(/__em_js__[A-Za-z0-9_]+/g) ?? []),
		].map((name) => name.replace(/^__em_js__/, ""));
		symbols.sort();
		return symbols;
	}
	return [];
}

function toTs(value) {
	if (Array.isArray(value)) {
		return `[${value.map((entry) => toTs(entry)).join(", ")}]`;
	}
	if (value && typeof value === "object") {
		const pairs = Object.entries(value)
			.filter(([, entry]) => entry !== undefined)
			.map(([key, entry]) => `${key}: ${toTs(entry)}`);
		return `{ ${pairs.join(", ")} }`;
	}
	if (typeof value === "string") {
		return JSON.stringify(value);
	}
	return String(value);
}

function formatTsFile(body) {
	return `// Auto-generated by tools/generateCustomRelease.mjs\n${body.trim()}\n`;
}

function writeGeneratedFsPaths(fsPaths) {
	const target = path.resolve(releaseDir, "preload/fsBootstrapPaths.generated.ts");
	const rows = fsPaths
		.map(
			(entry) =>
				`\t{ parent: ${toTs(entry.parent)}, name: ${toTs(entry.name)}, canRead: ${entry.canRead}, canWrite: ${entry.canWrite} },`,
		)
		.join("\n");
	const content = formatTsFile(`
import type { FsPathEntry } from "../core/runtimeTypes.js";

export const fsBootstrapPaths: FsPathEntry[] = [
${rows}
];
`);
	fs.writeFileSync(target, content);
}

function writeGeneratedManifest(manifest) {
	const target = path.resolve(releaseDir, "preload/dataManifest.generated.ts");
	const fileRows = manifest.files
		.map(
			(file) =>
				`\t\t{ filename: ${toTs(file.filename)}, start: ${file.start}, end: ${file.end}${file.audio === undefined ? "" : `, audio: ${file.audio}`} },`,
		)
		.join("\n");
	const content = formatTsFile(`
import type { DataManifest } from "../core/runtimeTypes.js";

export const dataManifest: DataManifest = {
	files: [
${fileRows}
	],
	remotePackageSize: ${manifest.remotePackageSize},
};
`);
	fs.writeFileSync(target, content);
}

function writeGeneratedEmAsm(keys) {
	const known = new Set([2537480, 2537652, 2537781]);
	const unknown = keys.filter((key) => !known.has(key));
	if (unknown.length > 0) {
		throw new Error(`Unhandled ASM_CONST keys: ${unknown.join(", ")}`);
	}
	const target = path.resolve(releaseDir, "em/emAsmRegistry.generated.ts");
	const content = formatTsFile(`
import type { EmAsmHandler } from "../core/runtimeTypes.js";

export const emAsmRegistry: Record<number, EmAsmHandler> = {
	2537480: (context, fdBufferMax) => {
		const moduleAny = context.module as Record<string, unknown>;
		moduleAny.is_worker =
			typeof WorkerGlobalScope !== "undefined" && self instanceof WorkerGlobalScope;
		moduleAny.FD_BUFFER_MAX = Number(fdBufferMax ?? 0);
		moduleAny.emscripten_copy_to = console.warn;
	},
	2537652: (context) => {
		context.module.postMessage = function custom_postMessage(event: unknown) {
			console.log("# pg_main_emsdk.c:544: onCustomMessage:", event);
		};
	},
	2537781: (context) => {
		const moduleAny = context.module as Record<string, unknown>;
		if (moduleAny.is_worker) {
			moduleAny.onCustomMessage = function onCustomMessage(event: unknown) {
				console.log("onCustomMessage:", event);
			};
			return;
		}
		context.module.postMessage = function custom_postMessage(event: unknown) {
			if (
				typeof event === "object" &&
				event !== null &&
				"type" in event &&
				"data" in event &&
				(event as { type: string }).type === "stdin"
			) {
				if (!context.stringToUTF8) {
					throw new Error("stringToUTF8 is required for stdin EM_ASM handler");
				}
				const data = String((event as { data: unknown }).data ?? "");
				const fdBufferMax = Number(moduleAny.FD_BUFFER_MAX ?? 0);
				context.stringToUTF8(data, 1, fdBufferMax);
				return;
			}
			if (
				typeof event === "object" &&
				event !== null &&
				"type" in event &&
				((event as { type: string }).type === "raw" ||
					(event as { type: string }).type === "rcon")
			) {
				return;
			}
			console.warn("custom_postMessage?", event);
		};
	},
};
`);
	fs.writeFileSync(target, content);
}

function writeGeneratedEmJs(symbols) {
	const knownImplementations = new Map();
	const unknownSymbols = symbols.filter((symbol) => !knownImplementations.has(symbol));
	if (unknownSymbols.length > 0) {
		throw new Error(
			`Unhandled EM_JS symbols: ${unknownSymbols.join(", ")} (add static implementations)`
		);
	}
	const entries = symbols
		.map((symbol) => `${JSON.stringify(symbol)}: ${knownImplementations.get(symbol)}`)
		.join(",\n\t");
	const target = path.resolve(releaseDir, "em/emJsRegistry.generated.ts");
	const content = formatTsFile(`
import type { EmJsHandler } from "../core/runtimeTypes.js";

export const emJsRegistry: Record<string, EmJsHandler> = {
	${entries}
};

export const emJsSymbolNames = ${toTs(symbols)};
`);
	fs.writeFileSync(target, content);
}

function main() {
	const source = readText(originalReleasePath);
	const fsPaths = extractFsBootstrapPaths(source);
	const manifest = extractLoadPackageMetadata(source);
	const asmKeys = extractAsmConstKeys(source);
	const emJsSymbols = collectEmJsSymbols();

	writeGeneratedFsPaths(fsPaths);
	writeGeneratedManifest(manifest);
	writeGeneratedEmAsm(asmKeys);
	writeGeneratedEmJs(emJsSymbols);

	console.log("Generated files:");
	console.log("- preload/fsBootstrapPaths.generated.ts");
	console.log("- preload/dataManifest.generated.ts");
	console.log("- em/emAsmRegistry.generated.ts");
	console.log("- em/emJsRegistry.generated.ts");
}

main();
