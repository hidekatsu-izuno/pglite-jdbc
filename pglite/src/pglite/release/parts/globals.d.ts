declare var process: any;
declare var Buffer: any;
declare var Browser: any;
declare var WorkerGlobalScope: any;

interface Function {
	[key: string]: any;
	sig?: string;
	current?: number;
	strings?: string[];
	major?: number;
}

interface Window {
	mozIndexedDB?: any;
	webkitIndexedDB?: any;
	msIndexedDB?: any;
}

declare module "module" {
	export function createRequire(path: string): any;
}
