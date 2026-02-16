export const createEnviron = ({
	thisProgram,
	ENV,
	HEAP8,
	HEAPU32,
}: {
	thisProgram: string;
	ENV: Record<string, string | undefined>;
	HEAP8: Int8Array | Uint8Array;
	HEAPU32: Uint32Array;
}) => {
	var getExecutableName = () => thisProgram || "./this.program";
	var getEnvStrings: any = () => {
		if (!getEnvStrings.strings) {
			var lang =
				(
					(typeof navigator == "object" &&
						navigator.languages &&
						navigator.languages[0]) ||
					"C"
				).replace("-", "_") + ".UTF-8";
			var env = {
				USER: "web_user",
				LOGNAME: "web_user",
				PATH: "/",
				PWD: "/",
				HOME: "/home/web_user",
				LANG: lang,
				_: getExecutableName(),
			};
			for (var x in ENV) {
				if (ENV[x] === undefined) delete env[x];
				else env[x] = ENV[x];
			}
			var strings = [];
			for (var x in env) {
				strings.push(`${x}=${env[x]}`);
			}
			getEnvStrings.strings = strings;
		}
		return getEnvStrings.strings;
	};
	var stringToAscii = (str: any, buffer: any) => {
		for (var i = 0; i < str.length; ++i) {
			HEAP8[buffer++] = str.charCodeAt(i);
		}
		HEAP8[buffer] = 0;
	};
	var _environ_get: any = (__environ: any, environ_buf: any) => {
		var bufSize = 0;
		getEnvStrings().forEach((string: any, i: any) => {
			var ptr = environ_buf + bufSize;
			HEAPU32[(__environ + i * 4) >> 2] = ptr;
			stringToAscii(string, ptr);
			bufSize += string.length + 1;
		});
		return 0;
	};
	_environ_get.sig = "ipp";
	var _environ_sizes_get: any = (
		penviron_count: any,
		penviron_buf_size: any,
	) => {
		var strings = getEnvStrings();
		HEAPU32[penviron_count >> 2] = strings.length;
		var bufSize = 0;
		strings.forEach((string: any) => (bufSize += string.length + 1));
		HEAPU32[penviron_buf_size >> 2] = bufSize;
		return 0;
	};
	_environ_sizes_get.sig = "ipp";

	return {
		getExecutableName,
		getEnvStrings,
		stringToAscii,
		_environ_get,
		_environ_sizes_get,
	};
};
