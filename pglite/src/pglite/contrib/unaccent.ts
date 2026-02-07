import type {
	Extension,
	ExtensionSetupResult,
	PGliteInterface,
} from "../interface";

const setup = async (_pg: PGliteInterface, _emscriptenOpts: any) => {
	return {
		bundlePath: new URL("unaccent.tar.gz", import.meta.url),
	} satisfies ExtensionSetupResult;
};

export const unaccent = {
	name: "unaccent",
	setup,
} satisfies Extension;
