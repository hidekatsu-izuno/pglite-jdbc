package io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.preload;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.core.runtimeTypes;

public class dataRequest {
    private static final java.util.Map<String, dataRequest> requests = new java.util.concurrent.ConcurrentHashMap<>();
    private final runtimeTypes.RuntimeModule module;
    private final byte[] byteArray;
    private final runtimeTypes.DataFileEntry entry;
    private String name;

    public dataRequest(runtimeTypes.RuntimeModule module, byte[] byteArray, runtimeTypes.DataFileEntry entry) {
        this.module = module;
        this.byteArray = byteArray;
        this.entry = entry;
    }

    public void open(String fileName) {
        this.name = fileName;
        requests.put(fileName, this);
        this.module.addRunDependency("fp " + fileName);
    }

    public void onload() {
        if (name == null) {
            throw new IllegalStateException("DataRequest has no open file name");
        }
        var size = Math.max(0, entry.end() - entry.start());
        var bytes = new byte[size];
        System.arraycopy(byteArray, entry.start(), bytes, 0, size);
        finish(bytes);
    }

    private void finish(byte[] bytes) {
        this.module.FS_createDataFile(name, null, bytes, true, true, true);
        this.module.removeRunDependency("fp " + name);
        requests.remove(name);
    }
}
