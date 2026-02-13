export interface SyscallRecord {
	name: string;
	args: unknown[];
	result: unknown;
}

export interface SyscallRecorder {
	record(name: string, args: unknown[], result: unknown): void;
	recent(): SyscallRecord[];
}

export function createSyscallRecorder(maxEntries = 256): SyscallRecorder {
	const entries: SyscallRecord[] = [];
	return {
		record(name: string, args: unknown[], result: unknown): void {
			entries.push({ name, args, result });
			if (entries.length > maxEntries) {
				entries.shift();
			}
		},
		recent(): SyscallRecord[] {
			return entries.slice();
		},
	};
}
