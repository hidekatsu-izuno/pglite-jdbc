export const runDataPackageBootstrap = ({
  Module,
  PGLITE_DATA_METADATA,
  require,
}: {
  Module: Record<string, any>;
  PGLITE_DATA_METADATA: { files: Array<Record<string, any>>; remote_package_size: number };
  require?: (id: string) => any;
}) => {

    var isPthread = typeof ENVIRONMENT_IS_PTHREAD != "undefined" && ENVIRONMENT_IS_PTHREAD;
    var isWasmWorker = typeof ENVIRONMENT_IS_WASM_WORKER != "undefined" && ENVIRONMENT_IS_WASM_WORKER;
    if (isPthread || isWasmWorker) return;
    var isNode = typeof process === "object" && typeof process.versions === "object" && typeof process.versions.node === "string";
    function loadPackage(metadata) {
      var PACKAGE_PATH = "";
      if (typeof window === "object") {
        PACKAGE_PATH = window["encodeURIComponent"](window.location.pathname.substring(0, window.location.pathname.lastIndexOf("/")) + "/")
      } else if (typeof process === "undefined" && typeof location !== "undefined") {
        PACKAGE_PATH = encodeURIComponent(location.pathname.substring(0, location.pathname.lastIndexOf("/")) + "/")
      }
      var PACKAGE_NAME = "pglite.data";
      var REMOTE_PACKAGE_BASE = "pglite.data";
      var REMOTE_PACKAGE_NAME = Module["locateFile"] ? Module["locateFile"](REMOTE_PACKAGE_BASE, "") : REMOTE_PACKAGE_BASE;
      var REMOTE_PACKAGE_SIZE = metadata["remote_package_size"];
      function fetchRemotePackage(packageName, packageSize, callback, errback) {
        if (isNode) {
          require("fs").readFile(packageName, (err, contents) => {
            if (err) {
              errback(err)
            } else {
              callback(contents.buffer)
            }

          });
          return
        }
        Module["dataFileDownloads"] ??= {};
        fetch(packageName).catch(cause => Promise.reject(new Error(`Network Error: ${packageName}`, { cause }))).then(response => {
          if (!response.ok) {
            return Promise.reject(new Error(`${response.status}: ${response.url}`))
          }
          if (!response.body && response.arrayBuffer) {
            return response.arrayBuffer().then(callback)
          }
          const reader = response.body.getReader();
          const iterate = () => reader.read().then(handleChunk).catch(cause => Promise.reject(new Error(`Unexpected error while handling : ${response.url}${cause}`, { cause })));
          const chunks = [];
          const headers = response.headers;
          const total = Number(headers.get("Content-Length") ?? packageSize);
          let loaded = 0;
          const handleChunk = ({
            done, value
          }) => {
            if (!done) {
              chunks.push(value);
              loaded += value.length;
              Module["dataFileDownloads"][packageName] = {
                loaded, total
              };
              let totalLoaded = 0;
              let totalSize = 0;
              for (const download of Object.values(Module["dataFileDownloads"])) {
                totalLoaded += download.loaded;
                totalSize += download.total
              }
              Module["setStatus"]?.(`Downloading data... (${totalLoaded}/${totalSize})`);
              return iterate()
            } else {
              const packageData = new Uint8Array(chunks.map(c => c.length).reduce((a, b) => a + b, 0));
              let offset = 0;
              for (const chunk of chunks) {
                packageData.set(chunk, offset);
                offset += chunk.length
              }
              callback(packageData.buffer)
            }
          };
          Module["setStatus"]?.("Downloading data...");
          return iterate()
        })
      }
      function handleError(error) {
        console.error("package error:", error)
      }
      var fetchedCallback = null;
      var fetched = Module["getPreloadedPackage"] ? Module["getPreloadedPackage"](REMOTE_PACKAGE_NAME, REMOTE_PACKAGE_SIZE) : null;
      if (!fetched) fetchRemotePackage(REMOTE_PACKAGE_NAME, REMOTE_PACKAGE_SIZE, data => {
        if (fetchedCallback) {
          fetchedCallback(data);
          fetchedCallback = null
        } else {
          fetched = data
        }
      }, handleError);
      function runWithFS(Module) {
        function assert(check, msg) {
          if (!check) throw msg + (new Error).stack
        }
        Module["FS_createPath"]("/", "home", true, true);
        Module["FS_createPath"]("/home", "web_user", true, true);
        Module["FS_createPath"]("/", "tmp", true, true);
        Module["FS_createPath"]("/tmp", "pglite", true, true);
        Module["FS_createPath"]("/tmp/pglite", "bin", true, true);
        Module["FS_createPath"]("/tmp/pglite", "lib", true, true);
        Module["FS_createPath"]("/tmp/pglite/lib", "postgresql", true, true);
        Module["FS_createPath"]("/tmp/pglite/lib/postgresql", "pgxs", true, true);
        Module["FS_createPath"]("/tmp/pglite/lib/postgresql/pgxs", "config", true, true);
        Module["FS_createPath"]("/tmp/pglite/lib/postgresql/pgxs", "src", true, true);
        Module["FS_createPath"]("/tmp/pglite/lib/postgresql/pgxs/src", "makefiles", true, true);
        Module["FS_createPath"]("/tmp/pglite", "share", true, true);
        Module["FS_createPath"]("/tmp/pglite/share", "postgresql", true, true);
        Module["FS_createPath"]("/tmp/pglite/share/postgresql", "extension", true, true);
        Module["FS_createPath"]("/tmp/pglite/share/postgresql", "timezone", true, true);
        Module["FS_createPath"]("/tmp/pglite/share/postgresql/timezone", "Africa", true, true);
        Module["FS_createPath"]("/tmp/pglite/share/postgresql/timezone", "America", true, true);
        Module["FS_createPath"]("/tmp/pglite/share/postgresql/timezone/America", "Argentina", true, true);
        Module["FS_createPath"]("/tmp/pglite/share/postgresql/timezone/America", "Indiana", true, true);
        Module["FS_createPath"]("/tmp/pglite/share/postgresql/timezone/America", "Kentucky", true, true);
        Module["FS_createPath"]("/tmp/pglite/share/postgresql/timezone/America", "North_Dakota", true, true);
        Module["FS_createPath"]("/tmp/pglite/share/postgresql/timezone", "Antarctica", true, true);
        Module["FS_createPath"]("/tmp/pglite/share/postgresql/timezone", "Arctic", true, true);
        Module["FS_createPath"]("/tmp/pglite/share/postgresql/timezone", "Asia", true, true);
        Module["FS_createPath"]("/tmp/pglite/share/postgresql/timezone", "Atlantic", true, true);
        Module["FS_createPath"]("/tmp/pglite/share/postgresql/timezone", "Australia", true, true);
        Module["FS_createPath"]("/tmp/pglite/share/postgresql/timezone", "Brazil", true, true);
        Module["FS_createPath"]("/tmp/pglite/share/postgresql/timezone", "Canada", true, true);
        Module["FS_createPath"]("/tmp/pglite/share/postgresql/timezone", "Chile", true, true);
        Module["FS_createPath"]("/tmp/pglite/share/postgresql/timezone", "Etc", true, true);
        Module["FS_createPath"]("/tmp/pglite/share/postgresql/timezone", "Europe", true, true);
        Module["FS_createPath"]("/tmp/pglite/share/postgresql/timezone", "Indian", true, true);
        Module["FS_createPath"]("/tmp/pglite/share/postgresql/timezone", "Mexico", true, true);
        Module["FS_createPath"]("/tmp/pglite/share/postgresql/timezone", "Pacific", true, true);
        Module["FS_createPath"]("/tmp/pglite/share/postgresql/timezone", "US", true, true);
        Module["FS_createPath"]("/tmp/pglite/share/postgresql", "timezonesets", true, true);
        Module["FS_createPath"]("/tmp/pglite/share/postgresql", "tsearch_data", true, true);
        function DataRequest(start, end, audio) {
          this.start = start;
          this.end = end;
          this.audio = audio
        }
        DataRequest.prototype = {
          requests: {

          }, open: function (mode, name) {
            this.name = name;
            this.requests[name] = this;
            Module["addRunDependency"](`fp ${this.name}`)
          }, send: function () {

          }, onload: function () {
            var byteArray = this.byteArray.subarray(this.start, this.end);
            this.finish(byteArray)
          }, finish: function (byteArray) {
            var that = this;
            Module["FS_createDataFile"](this.name, null, byteArray, true, true, true);
            Module["removeRunDependency"](`fp ${that.name}`);
            this.requests[this.name] = null
          }
        };
        var files = metadata["files"];
        for (var i = 0;
          i < files.length;
          ++i) {
          new DataRequest(files[i]["start"], files[i]["end"], files[i]["audio"] || 0).open("GET", files[i]["filename"])
        }
        function processPackageData(arrayBuffer) {
          assert(arrayBuffer, "Loading data file failed.");
          assert(arrayBuffer.constructor.name === ArrayBuffer.name, "bad input to processPackageData");
          var byteArray = new Uint8Array(arrayBuffer);
          DataRequest.prototype.byteArray = byteArray;
          var files = metadata["files"];
          for (var i = 0;
            i < files.length;
            ++i) {
            DataRequest.prototype.requests[files[i].filename].onload()
          }
          Module["removeRunDependency"]("datafile_pglite.data")
        }
        Module["addRunDependency"]("datafile_pglite.data");
        Module["preloadResults"] ??= {

        };
        Module["preloadResults"][PACKAGE_NAME] = {
          fromCache: false
        };
        if (fetched) {
          processPackageData(fetched);
          fetched = null
        } else {
          fetchedCallback = processPackageData
        }
      }
      if (Module["calledRun"]) {
        runWithFS(Module)
      } else {
        (Module["preRun"] ??= []).push(runWithFS)
      }
    }
    loadPackage(PGLITE_DATA_METADATA);
  
};
