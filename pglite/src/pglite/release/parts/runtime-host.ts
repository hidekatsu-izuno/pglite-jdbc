import { PATH } from "./path.ts";
import { createRandomFill } from "./random-fill.ts";
import { createTTY } from "./tty.ts";
import { UTF8ArrayToString, intArrayFromString } from "./text-codec.ts";

export const initializeRuntimeEnvironment = async ({
  importMetaUrl,
}: {
  importMetaUrl: string;
}) => {
  var ENVIRONMENT_IS_WEB = typeof window == "object";
  var ENVIRONMENT_IS_WORKER = typeof WorkerGlobalScope != "undefined";
  var ENVIRONMENT_IS_NODE = typeof process == "object" && typeof process.versions == "object" && typeof process.versions.node == "string" && process.type != "renderer";
  var require;
  if (ENVIRONMENT_IS_NODE) {
    const { createRequire } = await import("module");
    let dirname = importMetaUrl;
    if (dirname.startsWith("data:")) {
      dirname = "/";
    }
    require = createRequire(dirname);
  }
  return {
    ENVIRONMENT_IS_WEB,
    ENVIRONMENT_IS_WORKER,
    ENVIRONMENT_IS_NODE,
    require,
  };
};

export const initializeRuntimeHost = async ({
  Module,
  moduleOverrides,
  scriptName,
  importMetaUrl,
  environment,
}: {
  Module: Record<string, any>;
  moduleOverrides: Record<string, any> | null;
  scriptName?: string;
  importMetaUrl: string;
  environment: {
    ENVIRONMENT_IS_WEB: boolean;
    ENVIRONMENT_IS_WORKER: boolean;
    ENVIRONMENT_IS_NODE: boolean;
    require?: (id: string) => any;
  };
}) => {
  var { ENVIRONMENT_IS_WEB, ENVIRONMENT_IS_WORKER, ENVIRONMENT_IS_NODE, require } = environment;
  var arguments_ = [];
  var thisProgram = "./this.program";
  var quit_ = (status: any, toThrow: any) => {
    throw toThrow;
  };
  var scriptDirectory = "";
  var locateFile = (path: any) => {
    if (Module["locateFile"]) {
      return Module["locateFile"](path, scriptDirectory);
    }
    return scriptDirectory + path;
  };
  var dataURIPrefix = "data:application/octet-stream;base64,";
  var isDataURI = (filename: any) => filename.startsWith(dataURIPrefix);
  var isFileURI = (filename: any) => filename.startsWith("file://");
  var readAsync;
  var readBinary;
  var fs;
  if (ENVIRONMENT_IS_NODE) {
    fs = require("fs");
    var nodePath = require("path");
    if (!importMetaUrl.startsWith("data:")) {
      scriptDirectory = nodePath.dirname(require("url").fileURLToPath(importMetaUrl)) + "/";
    }
    readBinary = (filename: any) => {
      filename = isFileURI(filename) ? new URL(filename) : filename;
      var ret = fs.readFileSync(filename);
      return ret;
    };
    readAsync = async (filename, binary = true) => {
      filename = isFileURI(filename) ? new URL(filename) : filename;
      var ret = fs.readFileSync(filename, binary ? undefined : "utf8");
      return ret;
    };
    if (!Module["thisProgram"] && process.argv.length > 1) {
      thisProgram = process.argv[1].replace(/\\/g, "/");
    }
    arguments_ = process.argv.slice(2);
    quit_ = (status: any, toThrow: any) => {
      process.exitCode = status;
      throw toThrow;
    };
  } else if (ENVIRONMENT_IS_WEB || ENVIRONMENT_IS_WORKER) {
    if (ENVIRONMENT_IS_WORKER) {
      scriptDirectory = self.location.href;
    } else if (typeof document != "undefined" && document.currentScript) {
      scriptDirectory = (document.currentScript as any).src;
    }
    if (scriptName) {
      scriptDirectory = scriptName;
    }
    if (scriptDirectory.startsWith("blob:")) {
      scriptDirectory = "";
    } else {
      scriptDirectory = scriptDirectory.substr(0, scriptDirectory.replace(/[?#].*/, "").lastIndexOf("/") + 1);
    }
    if (ENVIRONMENT_IS_WORKER) {
      readBinary = (url: any) => {
        var xhr = new XMLHttpRequest;
        xhr.open("GET", url, false);
        xhr.responseType = "arraybuffer";
        xhr.send(null);
        return new Uint8Array(xhr.response);
      };
    }
    readAsync = async (url: any) => {
      var response = await fetch(url, { credentials: "same-origin" });
      if (response.ok) {
        return response.arrayBuffer();
      }
      throw new Error(response.status + " : " + response.url);
    };
  }
  var out = Module["print"] || console.log.bind(console);
  var err = Module["printErr"] || console.error.bind(console);
  Object.assign(Module, moduleOverrides);
  moduleOverrides = null;
  if (Module["arguments"]) arguments_ = Module["arguments"];
  if (Module["thisProgram"]) thisProgram = Module["thisProgram"];
  var findWasmBinary = () => {
    if (Module["locateFile"]) {
      var f = "pglite.wasm";
      if (!isDataURI(f)) {
        return locateFile(f);
      }
      return f;
    }
    return new URL("pglite.wasm", importMetaUrl).href;
  };
  return {
    ENVIRONMENT_IS_WEB,
    ENVIRONMENT_IS_WORKER,
    ENVIRONMENT_IS_NODE,
    require,
    fs,
    arguments_,
    thisProgram,
    quit_,
    locateFile,
    readAsync,
    readBinary,
    out,
    err,
    findWasmBinary,
    isDataURI,
    isFileURI,
  };
};

export const createRuntimeHostFsSupport = ({
  getFS,
  Module,
  ENVIRONMENT_IS_NODE,
  require,
  abort,
  fs,
  out,
  err,
  HEAPU8,
  alignMemory,
  emscriptenBuiltinMemalign,
  addRunDependency,
  removeRunDependency,
  getUniqueRunDependency,
  preloadPlugins,
  asyncLoad,
}: {
  getFS: () => any;
  Module: Record<string, any>;
  ENVIRONMENT_IS_NODE: boolean;
  require?: (id: string) => any;
  abort: (message?: string) => never;
  fs: any;
  out: (...args: any[]) => void;
  err: (...args: any[]) => void;
  HEAPU8: Uint8Array;
  alignMemory: (size: number, alignment: number) => number;
  emscriptenBuiltinMemalign: (alignment: number, size: number) => number;
  addRunDependency: (id: string) => void;
  removeRunDependency: (id: string) => void;
  getUniqueRunDependency: (id: string) => string;
  preloadPlugins: Array<Record<string, any>>;
  asyncLoad: (url: string) => Promise<Uint8Array>;
}) => {
  var { randomFill } = createRandomFill({ ENVIRONMENT_IS_NODE, require, abort });
  var PATH_FS = {
    resolve: (...args) => {
      var resolvedPath = "", resolvedAbsolute = false;
      for (var i = args.length - 1; i >= -1 && !resolvedAbsolute; i--) {
        var path = i >= 0 ? args[i] : getFS().cwd();
        if (typeof path != "string") {
          throw new TypeError("Arguments to path.resolve must be strings");
        } else if (!path) {
          return "";
        }
        resolvedPath = path + "/" + resolvedPath;
        resolvedAbsolute = PATH.isAbs(path);
      }
      resolvedPath = PATH.normalizeArray(resolvedPath.split("/").filter((p: any) => !!p), !resolvedAbsolute).join("/");
      return (resolvedAbsolute ? "/" : "") + resolvedPath || ".";
    },
    relative: (from: any, to: any) => {
      from = PATH_FS.resolve(from).substr(1);
      to = PATH_FS.resolve(to).substr(1);
      function trim(arr: any) {
        var start = 0;
        for (; start < arr.length; start++) {
          if (arr[start] !== "") break;
        }
        var end = arr.length - 1;
        for (; end >= 0; end--) {
          if (arr[end] !== "") break;
        }
        if (start > end) return [];
        return arr.slice(start, end - start + 1);
      }
      var fromParts = trim(from.split("/"));
      var toParts = trim(to.split("/"));
      var length = Math.min(fromParts.length, toParts.length);
      var samePartsLength = length;
      for (var i = 0; i < length; i++) {
        if (fromParts[i] !== toParts[i]) {
          samePartsLength = i;
          break;
        }
      }
      var outputParts = [];
      for (var i = samePartsLength; i < fromParts.length; i++) {
        outputParts.push("..");
      }
      outputParts = outputParts.concat(toParts.slice(samePartsLength));
      return outputParts.join("/");
    },
  };
  var FS_stdin_getChar_buffer = [];
  var FS_stdin_getChar = () => {
    if (!FS_stdin_getChar_buffer.length) {
      var result = null;
      if (ENVIRONMENT_IS_NODE) {
        var BUFSIZE = 256;
        var buf = Buffer.alloc(BUFSIZE);
        var bytesRead = 0;
        var fd = process.stdin.fd;
        try {
          bytesRead = fs.readSync(fd, buf, 0, BUFSIZE);
        } catch (e) {
          if (e.toString().includes("EOF")) bytesRead = 0;
          else throw e;
        }
        if (bytesRead > 0) {
          result = buf.slice(0, bytesRead).toString("utf-8");
        }
      } else if (typeof window != "undefined" && typeof window.prompt == "function") {
        result = window.prompt("Input: ");
        if (result !== null) {
          result += "\n";
        }
      }
      if (!result) {
        return null;
      }
      FS_stdin_getChar_buffer = intArrayFromString(result, true);
    }
    return FS_stdin_getChar_buffer.shift();
  };
  var TTY = createTTY({ getFS, FS_stdin_getChar, UTF8ArrayToString, out, err });
  var zeroMemory = (address: any, size: any) => {
    HEAPU8.fill(0, address, address + size);
  };
  var mmapAlloc = (size: any) => {
    size = alignMemory(size, 65536);
    var ptr = emscriptenBuiltinMemalign(65536, size);
    if (ptr) zeroMemory(ptr, size);
    return ptr;
  };
  var FS_createDataFile = (parent: any, name: any, fileData: any, canRead: any, canWrite: any, canOwn: any) => {
    getFS().createDataFile(parent, name, fileData, canRead, canWrite, canOwn);
  };
  var FS_handledByPreloadPlugin = (byteArray: any, fullname: any, finish: any, onerror: any) => {
    if (typeof Browser != "undefined") Browser.init();
    var handled = false;
    preloadPlugins.forEach((plugin: any) => {
      if (handled) return;
      if (plugin["canHandle"](fullname)) {
        plugin["handle"](byteArray, fullname, finish, onerror);
        handled = true;
      }
    });
    return handled;
  };
  var FS_createPreloadedFile = (parent: any, name: any, url: any, canRead: any, canWrite: any, onload: any, onerror: any, dontCreateFile: any, canOwn: any, preFinish: any) => {
    var fullname = name ? PATH_FS.resolve(PATH.join2(parent, name)) : parent;
    var dep = getUniqueRunDependency(`cp ${fullname}`);
    function processData(byteArray: any) {
      function finish(byteArray: any) {
        preFinish?.();
        if (!dontCreateFile) {
          FS_createDataFile(parent, name, byteArray, canRead, canWrite, canOwn);
        }
        onload?.();
        removeRunDependency(dep);
      }
      if (FS_handledByPreloadPlugin(byteArray, fullname, finish, () => {
        onerror?.();
        removeRunDependency(dep);
      })) {
        return;
      }
      finish(byteArray);
    }
    addRunDependency(dep);
    if (typeof url == "string") {
      asyncLoad(url).then(processData, onerror);
    } else {
      processData(url);
    }
  };
  var FS_modeStringToFlags = (str: any) => {
    var flagModes = { r: 0, "r+": 2, w: 512 | 64 | 1, "w+": 512 | 64 | 2, a: 1024 | 64 | 1, "a+": 1024 | 64 | 2 };
    var flags = flagModes[str];
    if (typeof flags == "undefined") {
      throw new Error(`Unknown file open mode: ${str}`);
    }
    return flags;
  };
  var FS_getMode = (canRead: any, canWrite: any) => {
    var mode = 0;
    if (canRead) mode |= 292 | 73;
    if (canWrite) mode |= 146;
    return mode;
  };
  return {
    randomFill,
    PATH_FS,
    TTY,
    zeroMemory,
    mmapAlloc,
    FS_createPreloadedFile,
    FS_modeStringToFlags,
    FS_getMode,
  };
};
