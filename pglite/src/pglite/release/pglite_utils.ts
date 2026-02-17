import fs from "node:fs";

export function loadPackage(Module: any, metadata: any) {
    var PACKAGE_NAME = "pglite.data";
    var REMOTE_PACKAGE_BASE = "pglite.data";
    var REMOTE_PACKAGE_NAME = Module["locateFile"] ? Module["locateFile"](REMOTE_PACKAGE_BASE, "") : REMOTE_PACKAGE_BASE;
    var REMOTE_PACKAGE_SIZE = metadata["remote_package_size"];

    function fetchRemotePackage(packageName: string, callback: any, errback: any) {
        fs.readFile(packageName, (err: any, contents: any) => {
            if (err) {
                errback(err)
            } else {
                callback(contents.buffer)
            }
        });
    }

    function handleError(error: any) {
        console.error("package error:", error)
    }
    var fetchedCallback: any = null;
    var fetched = Module["getPreloadedPackage"] ? Module["getPreloadedPackage"](REMOTE_PACKAGE_NAME, REMOTE_PACKAGE_SIZE) : null;
    if (!fetched) fetchRemotePackage(REMOTE_PACKAGE_NAME, (data: any) => {
        if (fetchedCallback) {
            fetchedCallback(data);
            fetchedCallback = null
        } else {
            fetched = data
        }
    }, handleError);

    function runWithFS(Module: any) {
        function assert(check: any, msg: string) {
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

        class DataRequest {
            static requests: Record<string, any> = {};
            static byteArray: any;

            name?: string;
            start: number;
            end: number;
            audio: number;

            constructor(start: number, end: number, audio: number) {
                this.start = start;
                this.end = end;
                this.audio = audio;
            }

            open(mode: any, name: any) {
                this.name = name;
                DataRequest.requests[name] = this;
                Module["addRunDependency"](`fp ${this.name}`)
            }
            send() {

            }

            onload() {
                var byteArray = DataRequest.byteArray.subarray(this.start, this.end);
                this.finish(byteArray)
            }

            finish(byteArray: any) {
                Module["FS_createDataFile"](this.name, null, byteArray, true, true, true);
                Module["removeRunDependency"](`fp ${this.name}`);
                DataRequest.requests[this.name ?? ""] = null;
            }
        };
        var files = metadata["files"];
        for (var i = 0; i < files.length; ++i) {
            new DataRequest(files[i]["start"], files[i]["end"], files[i]["audio"] || 0).open("GET", files[i]["filename"])
        }

        function processPackageData(arrayBuffer: any) {
            assert(arrayBuffer, "Loading data file failed.");
            assert(arrayBuffer.constructor.name === ArrayBuffer.name, "bad input to processPackageData");
            var byteArray = new Uint8Array(arrayBuffer);
            DataRequest.byteArray = byteArray;
            var files = metadata["files"];
            for (var i = 0; i < files.length; ++i) {
                DataRequest.requests[files[i].filename].onload()
            }
            Module["removeRunDependency"]("datafile_pglite.data")
        }
        Module["addRunDependency"]("datafile_pglite.data");
        Module["preloadResults"] ??= {};
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
};