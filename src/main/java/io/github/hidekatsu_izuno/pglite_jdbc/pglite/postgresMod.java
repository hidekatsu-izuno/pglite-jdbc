package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class postgresMod {
    public interface PostgresMod {
        String WASM_PREFIX();
        Integer INITIAL_MEMORY();
        Integer FD_BUFFER_MAX();
        void setFD_BUFFER_MAX(Integer value);
        Uint8Array HEAP8();
        Uint8Array HEAPU8();
        Map<String, CompletableFuture<extensionUtils.ExtensionBlob>> pg_extensions();
        int _pgl_initdb();
        void _pgl_backend();
        void _pgl_shutdown();
        void _interactive_write(int msgLength);
        void _interactive_one(int length, int peek);
        default void _queue_message(byte[] message) {}
        void _set_read_write_cbs(int read_cb, int write_cb);
        int addFunction(ReadWriteCallback cb, String signature);
        void removeFunction(int f);
        void copyFromHeap(int ptr, byte[] dest, int destOffset, int length);
        void copyToHeap(int ptr, byte[] src, int srcOffset, int length);
        EmscriptenRuntime runtime();
        extensionUtils.EmscriptenFS FS();
    }

    public interface EmscriptenRuntime {
        extensionUtils.EmscriptenFS FS();
        byte[] getPreloadedPackage(String name, int size);
        void addRunDependency(String key);
        void removeRunDependency(String key);
        void preRun();
        void postRun();
        int makedev(int major, int minor);
        void registerDevice(int devId, DeviceOps ops);
        void mkdev(String path, int devId);
    }

    public interface DeviceOps {
        int read(byte[] buffer, int offset, int length, int position);
        int write(byte[] buffer, int offset, int length, int position);
        int llseek(int offset, int whence, int position);
    }

    @FunctionalInterface
    public interface ReadWriteCallback {
        int apply(int ptr, int length);
    }

    public static final class PartialPostgresMod {
        public String WASM_PREFIX;
        public String[] arguments;
        public Integer INITIAL_MEMORY;
        public Boolean noExitRuntime;
        public Consumer<Object[]> print;
        public Consumer<Object[]> printErr;
        public Object instantiateWasm;
        public Object getPreloadedPackage;
        public java.util.List<Consumer<PostgresMod>> preInit;
        public java.util.List<Consumer<PostgresMod>> preRun;
        public java.util.List<Consumer<PostgresMod>> postRun;
        public Map<String, CompletableFuture<extensionUtils.ExtensionBlob>> pg_extensions;
        public extensionUtils.EmscriptenFS FS;
        public Integer FD_BUFFER_MAX;
        public URL wasmModuleUrl;
    }

    private postgresMod() {
    }
}
