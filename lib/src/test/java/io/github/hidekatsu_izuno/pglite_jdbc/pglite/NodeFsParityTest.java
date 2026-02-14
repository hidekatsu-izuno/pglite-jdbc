package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.nodefs;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

public class NodeFsParityTest {
    @Test
    void shouldInvokeFsQuitWhenAvailable() throws Exception {
        var fs = new nodefs.NodeFS("/tmp/nodefs-parity-test");
        var pg = new StubPGlite();
        var modField = pglite.class.getDeclaredField("mod");
        modField.setAccessible(true);
        var quitCalled = new AtomicBoolean(false);
        modField.set(pg, new FakeMod(new FakeRuntimeFs(quitCalled)));

        fs.init(pg, Map.of()).join();
        fs.closeFs().join();

        assertTrue(quitCalled.get());
    }

    public static class FakeRuntimeFs implements extensionUtils.EmscriptenFS {
        private final AtomicBoolean quitCalled;

        FakeRuntimeFs(AtomicBoolean quitCalled) {
            this.quitCalled = quitCalled;
        }

        @SuppressWarnings("unused")
        public void quit() {
            quitCalled.set(true);
        }

        @Override
        public void createPath(String parent, String path, boolean canRead, boolean canWrite) {}

        @Override
        public void createDataFile(
            String path,
            String name,
            Object data,
            boolean canRead,
            boolean canWrite,
            boolean canOwn
        ) {}

        @Override
        public void createPreloadedFile(
            String parent,
            String name,
            Object data,
            boolean canRead,
            boolean canWrite,
            extensionUtils.Log onload,
            extensionUtils.Log onerror,
            boolean dontCreateFile
        ) {}

        @Override
        public extensionUtils.AnalyzePathResult analyzePath(String path) {
            return new extensionUtils.AnalyzePathResult(false);
        }

        @Override
        public void mkdirTree(String path) {}

        @Override
        public void writeFile(String path, byte[] data) {}

        @Override
        public byte[] readFile(String path) {
            return new byte[0];
        }

        @Override
        public void unlink(String path) {}

        @Override
        public void createLazyFile(String parent, String name, Object data, boolean canRead, boolean canWrite) {}

        @Override
        public void createDevice(String parent, String name, Object input, Object output) {}

        @Override
        public void mount(Object type, Object opts, String mountpoint) {}

        @Override
        public void unmount(String mountpoint) {}

        @Override
        public void symlink(String target, String path) {}

        @Override
        public extensionUtils.FsStat stat(String path) {
            return new extensionUtils.FsStat();
        }

        @Override
        public String[] readdir(String path) {
            return new String[0];
        }

        @Override
        public void syncfs(boolean populate, extensionUtils.SyncfsCallback done) {
            if (done != null) {
                done.apply(null);
            }
        }

        @Override
        public void registerDevice(int devId, Object ops) {}

        @Override
        public int makedev(int major, int minor) {
            return 0;
        }

        @Override
        public void mkdev(String path, int dev) {}
    }

    public static class FakeMod implements postgresMod.PostgresMod {
        private final FakeRuntimeFs fs;

        FakeMod(FakeRuntimeFs fs) {
            this.fs = fs;
        }

        @Override
        public String WASM_PREFIX() {
            return "";
        }

        @Override
        public Integer INITIAL_MEMORY() {
            return 0;
        }

        @Override
        public Integer FD_BUFFER_MAX() {
            return 0;
        }

        @Override
        public void setFD_BUFFER_MAX(Integer value) {}

        @Override
        public Uint8Array HEAP8() {
            return new Uint8Array(0);
        }

        @Override
        public Uint8Array HEAPU8() {
            return new Uint8Array(0);
        }

        @Override
        public Map<String, CompletableFuture<extensionUtils.ExtensionBlob>> pg_extensions() {
            return Map.of();
        }

        @Override
        public int _pgl_initdb() {
            return 0;
        }

        @Override
        public void _pgl_backend() {}

        @Override
        public void _pgl_shutdown() {}

        @Override
        public void _interactive_write(int msgLength) {}

        @Override
        public void _interactive_one(int length, int peek) {}

        @Override
        public void _set_read_write_cbs(int read_cb, int write_cb) {}

        @Override
        public int addFunction(postgresMod.ReadWriteCallback cb, String signature) {
            return 0;
        }

        @Override
        public void removeFunction(int f) {}

        @Override
        public void copyFromHeap(int ptr, byte[] dest, int destOffset, int length) {}

        @Override
        public void copyToHeap(int ptr, byte[] src, int srcOffset, int length) {}

        @Override
        public postgresMod.EmscriptenRuntime runtime() {
            return new postgresMod.EmscriptenRuntime() {
                @Override
                public extensionUtils.EmscriptenFS FS() {
                    return fs;
                }

                @Override
                public byte[] getPreloadedPackage(String name, int size) {
                    return new byte[0];
                }

                @Override
                public void addRunDependency(String key) {}

                @Override
                public void removeRunDependency(String key) {}

                @Override
                public void preRun() {}

                @Override
                public void postRun() {}

                @Override
                public int makedev(int major, int minor) {
                    return 0;
                }

                @Override
                public void registerDevice(int devId, postgresMod.DeviceOps ops) {}

                @Override
                public void mkdev(String path, int devId) {}
            };
        }

        @Override
        public extensionUtils.EmscriptenFS FS() {
            return fs;
        }
    }

    private static class StubPGlite extends pglite {
        @Override
        public Promise<Void> waitReady() {
            return Promise.resolve(null);
        }

        @Override
        public int debug() {
            return 0;
        }

        @Override
        public boolean ready() {
            return true;
        }

        @Override
        public boolean closed() {
            return false;
        }

        @Override
        public Promise<Void> close() {
            return Promise.resolve(null);
        }

        @Override
        public <T> Promise<interface_.Results<T>> query(String query, Object[] params, interface_.QueryOptions options) {
            return Promise.resolve(new interface_.Results<>(List.of(), 0, List.of(), null));
        }

        @Override
        public Promise<List<interface_.Results<Map<String, Object>>>> exec(String query, interface_.QueryOptions options) {
            return Promise.resolve(List.of());
        }

        @Override
        public <T> Promise<interface_.Results<T>> sql(List<String> strings, Object... params) {
            return Promise.resolve(new interface_.Results<>(List.of(), 0, List.of(), null));
        }

        @Override
        public Promise<interface_.DescribeQueryResult> describeQuery(String query) {
            return Promise.resolve(new interface_.DescribeQueryResult(List.of(), List.of()));
        }

        @Override
        public <T> Promise<T> transaction(Function<interface_.Transaction, Promise<T>> callback) {
            return Promise.reject(new UnsupportedOperationException("not used"));
        }

        @Override
        public Promise<interface_.ExecProtocolResult> execProtocol(byte[] message, interface_.ExecProtocolOptions options) {
            return Promise.resolve(new interface_.ExecProtocolResult(List.of(), new byte[0]));
        }

        @Override
        public Promise<byte[]> execProtocolRaw(byte[] message, interface_.ExecProtocolOptions options) {
            return Promise.resolve(new byte[0]);
        }

        @Override
        public <T> Promise<T> runExclusive(Supplier<Promise<T>> fn) {
            return fn.get();
        }

        @Override
        public Promise<Function<interface_.Transaction, Promise<Void>>> listen(String channel, Consumer<String> callback, interface_.Transaction tx) {
            return Promise.resolve(ignored -> Promise.resolve(null));
        }

        @Override
        public Promise<Void> unlisten(String channel, Consumer<String> callback, interface_.Transaction tx) {
            return Promise.resolve(null);
        }

        @Override
        public Supplier<Void> onNotification(BiConsumer<String, String> callback) {
            return () -> null;
        }

        @Override
        public void offNotification(BiConsumer<String, String> callback) {}

        @Override
        public Promise<byte[]> dumpDataDir(String compression) {
            return Promise.resolve(new byte[0]);
        }
    }
}
