import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const releaseDir = path.resolve(__dirname, "..");
const srcDir = path.resolve(releaseDir, "../..");
const repoRoot = path.resolve(srcDir, "..");

const originalReleasePath = path.resolve(srcDir, "pglite/release/pglite.js");
const fsGeneratedPath = path.resolve(releaseDir, "preload/fsBootstrapPaths.generated.ts");
const manifestGeneratedPath = path.resolve(releaseDir, "preload/dataManifest.generated.ts");
const emAsmGeneratedPath = path.resolve(releaseDir, "em/emAsmRegistry.generated.ts");
const emJsGeneratedPath = path.resolve(releaseDir, "em/emJsRegistry.generated.ts");

function listFilesRecursive(dir, suffix) {
	const result = [];
	for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
		const fullPath = path.join(dir, entry.name);
		if (entry.isDirectory()) {
			result.push(...listFilesRecursive(fullPath, suffix));
			continue;
		}
		if (!entry.isFile()) {
			continue;
		}
		if (fullPath.endsWith(suffix)) {
			result.push(fullPath);
		}
	}
	return result;
}

function assert(condition, message) {
	if (!condition) {
		throw new Error(message);
	}
}

function extractFsPathCount(sourceText) {
	const pattern =
		/Module\["FS_createPath"\]\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*(true|false)\s*,\s*(true|false)\s*,?\s*\);/gs;
	return [...sourceText.matchAll(pattern)].length;
}

function extractRemotePackageSize(sourceText) {
	const match = sourceText.match(/remote_package_size:\s*(\d+)/);
	assert(match, "remote_package_size is missing in original release");
	return Number(match[1]);
}

function extractAsmKeys(sourceText) {
	const anchor = sourceText.indexOf("var ASM_CONSTS = {");
	assert(anchor >= 0, "ASM_CONSTS block is missing");
	const open = sourceText.indexOf("{", anchor);
	let depth = 0;
	let close = -1;
	for (let i = open; i < sourceText.length; i += 1) {
		if (sourceText[i] === "{") {
			depth += 1;
		} else if (sourceText[i] === "}") {
			depth -= 1;
			if (depth === 0) {
				close = i;
				break;
			}
		}
	}
	assert(close > open, "ASM_CONSTS block could not be parsed");
	const block = sourceText.slice(open, close + 1);
	const keys = [...block.matchAll(/(\d+)\s*:\s*\(/g)].map((match) => Number(match[1]));
	keys.sort((a, b) => a - b);
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

function parseGeneratedEmJsSymbols(sourceText) {
	const match = sourceText.match(/emJsSymbolNames\s*=\s*(\[[\s\S]*\]);/);
	assert(match, "emJsSymbolNames is missing in emJsRegistry.generated.ts");
	const arrayLiteral = match[1];
	// JSON-compatible because generator emits string arrays with JSON.stringify.
	return JSON.parse(arrayLiteral);
}

function main() {
	const tsFiles = listFilesRecursive(releaseDir, ".ts");
	const dynamicEvalPattern = /\beval\s*\(|\bnew\s+Function\s*\(/;
	for (const filePath of tsFiles) {
		const text = fs.readFileSync(filePath, "utf8");
		assert(
			!dynamicEvalPattern.test(text),
			`Dynamic eval usage detected: ${path.relative(releaseDir, filePath)}`,
		);
	}

	const originalSource = fs.readFileSync(originalReleasePath, "utf8");
	const generatedFsText = fs.readFileSync(fsGeneratedPath, "utf8");
	const generatedManifestText = fs.readFileSync(manifestGeneratedPath, "utf8");
	const generatedAsmText = fs.readFileSync(emAsmGeneratedPath, "utf8");
	const generatedEmJsText = fs.readFileSync(emJsGeneratedPath, "utf8");

	const originalFsCount = extractFsPathCount(originalSource);
	const generatedFsCount = (generatedFsText.match(/\bparent:\s*/g) ?? []).length;
	assert(generatedFsCount === originalFsCount, "FS bootstrap path count mismatch");
	assert(generatedFsCount === 37, "FS bootstrap path count must be 37");

	const originalRemoteSize = extractRemotePackageSize(originalSource);
	const generatedRemoteSizeMatch = generatedManifestText.match(/remotePackageSize:\s*(\d+)/);
	assert(generatedRemoteSizeMatch, "remotePackageSize missing in generated manifest");
	const generatedRemoteSize = Number(generatedRemoteSizeMatch[1]);
	assert(generatedRemoteSize === originalRemoteSize, "remotePackageSize mismatch");
	assert(generatedRemoteSize === 4939130, "remotePackageSize must be 4939130");

	const originalAsmKeys = extractAsmKeys(originalSource);
	const generatedAsmKeys = [...generatedAsmText.matchAll(/\n\s*(\d+)\s*:\s*\(/g)]
		.map((match) => Number(match[1]))
		.sort((a, b) => a - b);
	assert(
		JSON.stringify(generatedAsmKeys) === JSON.stringify(originalAsmKeys),
		"EM_ASM key set mismatch",
	);

	const emJsSymbolsFromWasm = collectEmJsSymbols();
	const generatedEmJsSymbols = parseGeneratedEmJsSymbols(generatedEmJsText);
	assert(
		JSON.stringify(generatedEmJsSymbols) === JSON.stringify(emJsSymbolsFromWasm),
		"EM_JS symbol set mismatch",
	);

	console.log("verifyCustomRelease: OK");
}

main();
