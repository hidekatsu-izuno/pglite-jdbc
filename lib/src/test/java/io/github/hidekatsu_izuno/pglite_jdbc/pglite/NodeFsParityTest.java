package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.nodefs;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import java.util.List;
import java.util.Map;
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

    public static class FakeRuntimeFs implements postgresMod.FS {
        private final AtomicBoolean quitCalled;

        FakeRuntimeFs(AtomicBoolean quitCalled) {
            this.quitCalled = quitCalled;
        }

        @Override
        public void createPreloadedFile(
            String parent,
            String name,
            byte[] data,
            boolean canRead,
            boolean canWrite,
            Runnable onLoad,
            Runnable onError,
            boolean dontCreateFile
        ) {}

        @Override
        public postgresMod.PathInfo analyzePath(String path) {
            return new postgresMod.PathInfo(false);
        }

        @Override
        public void mkdirTree(String path) {}

        @Override
        public void writeFile(String path, byte[] data) {}

        @SuppressWarnings("unused")
        public void quit() {
            quitCalled.set(true);
        }
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
        public postgresMod.FS FS() {
            return fs;
        }

        @Override
        public Map<String, Promise<byte[]>> pg_extensions() {
            return Map.of();
        }

        @Override
        public List<Consumer<postgresMod.PostgresMod>> preInit() {
            return List.of();
        }

        @Override
        public List<Consumer<postgresMod.PostgresMod>> preRun() {
            return List.of();
        }

        @Override
        public List<Consumer<postgresMod.PostgresMod>> postRun() {
            return List.of();
        }

        @Override
        public int INITIAL_MEMORY() {
            return 0;
        }

        @Override
        public int FD_BUFFER_MAX() {
            return 0;
        }

        @Override
        public int addFunction(BiConsumer<Integer, Integer> cb, String signature) {
            return 0;
        }

        @Override
        public void removeFunction(int f) {}

        @Override
        public void _queue_message(byte[] message) {}

        @Override
        public void _set_read_write_cbs(int readCb, int writeCb) {}

        @Override
        public void _interactive_write(int msgLength) {}

        @Override
        public void _interactive_one(int length, int peek) {}

        @Override
        public int _pgl_initdb() {
            return 0;
        }

        @Override
        public void _pgl_backend() {}

        @Override
        public void _pgl_shutdown() {}
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
