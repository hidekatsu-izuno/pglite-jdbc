export const createEnviron = ({ thisProgram, ENV, HEAP8, HEAPU32 }) => {
var getExecutableName = () => thisProgram || "./this.program";
      var getEnvStrings = () => {
        if (!getEnvStrings.strings) {
          var lang = (typeof navigator == "object" && navigator.languages && navigator.languages[0] || "C").replace("-", "_") + ".UTF-8";
          var env = { USER: "web_user", LOGNAME: "web_user", PATH: "/", PWD: "/", HOME: "/home/web_user", LANG: lang, _: getExecutableName() };
          for (var x in ENV) {
            if (ENV[x] === undefined) delete env[x];
            else env[x] = ENV[x]
          } var strings = [];
          for (var x in env) { strings.push(`${x}=${env[x]}`) } getEnvStrings.strings = strings
        } return getEnvStrings.strings
      };
      var stringToAscii = (str, buffer) => {
        for (var i = 0;
          i < str.length;
          ++i) { HEAP8[buffer++] = str.charCodeAt(i) } HEAP8[buffer] = 0
      };
      var _environ_get = (__environ, environ_buf) => {
        var bufSize = 0;
        getEnvStrings().forEach((string, i) => {
          var ptr = environ_buf + bufSize;
          HEAPU32[__environ + i * 4 >> 2] = ptr;
          stringToAscii(string, ptr);
          bufSize += string.length + 1
        });
        return 0
      };
      _environ_get.sig = "ipp";
      var _environ_sizes_get = (penviron_count, penviron_buf_size) => {
        var strings = getEnvStrings();
        HEAPU32[penviron_count >> 2] = strings.length;
        var bufSize = 0;
        strings.forEach(string => bufSize += string.length + 1);
        HEAPU32[penviron_buf_size >> 2] = bufSize;
        return 0
      };
      _environ_sizes_get.sig = "ipp";
      
return { getExecutableName, getEnvStrings, stringToAscii, _environ_get, _environ_sizes_get };
};
