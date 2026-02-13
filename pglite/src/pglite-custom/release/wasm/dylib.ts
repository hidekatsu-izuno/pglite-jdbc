export interface DynamicLibrarySymbolTable {
	register(name: string, value: unknown): void;
	resolve(name: string): unknown;
	has(name: string): boolean;
}

export function createDynamicLibrarySymbolTable(): DynamicLibrarySymbolTable {
	const symbols = new Map<string, unknown>();
	return {
		register(name: string, value: unknown): void {
			symbols.set(name, value);
		},
		resolve(name: string): unknown {
			if (!symbols.has(name)) {
				throw new Error(`Undefined dynamic library symbol: ${name}`);
			}
			return symbols.get(name);
		},
		has(name: string): boolean {
			return symbols.has(name);
		},
	};
}
