package io.github.hidekatsu_izuno.pglite_jdbc.pglite.release;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.postgresMod;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class pglite {
    private pglite() {}

    @FunctionalInterface
    public interface ModuleFactory {
        Promise<postgresMod.PostgresMod> create(postgresMod.PartialPostgresMod moduleOverrides);
    }

    public static ModuleFactory createModuleFactory() {
        return pglite::PostgresModFactory;
    }

    public static Promise<postgresMod.PostgresMod> PostgresModFactory(
        postgresMod.PartialPostgresMod moduleOverrides
    ) {
        var overrides = moduleOverrides != null ? moduleOverrides : new postgresMod.PartialPostgresMod();
        var mod = new RuntimePostgresMod(overrides);
        for (var hook : mod.preInit()) {
            hook.accept(mod);
        }
        for (var hook : mod.preRun()) {
            hook.accept(mod);
        }
        for (var hook : mod.postRun()) {
            hook.accept(mod);
        }
        return Promise.resolve(mod);
    }

    private static final class RuntimePostgresMod implements postgresMod.PostgresMod {
        private final String wasmPrefix;
        private final int initialMemory;
        private final int fdBufferMax;
        private final RuntimeFs fs;
        private final Map<String, Promise<byte[]>> extensions;
        private final List<Consumer<postgresMod.PostgresMod>> preInit;
        private final List<Consumer<postgresMod.PostgresMod>> preRun;
        private final List<Consumer<postgresMod.PostgresMod>> postRun;
        private final Map<Integer, BiConsumer<Integer, Integer>> functions = new ConcurrentHashMap<>();
        private final AtomicInteger nextFunctionId = new AtomicInteger(1);
        private final ArrayDeque<byte[]> inboundMessages = new ArrayDeque<>();
        private volatile int readCallbackId = -1;
        private volatile int writeCallbackId = -1;

        private RuntimePostgresMod(postgresMod.PartialPostgresMod moduleOverrides) {
            this.wasmPrefix = moduleOverrides.WASM_PREFIX != null
                ? moduleOverrides.WASM_PREFIX
                : "/tmp/pglite";
            this.initialMemory = moduleOverrides.INITIAL_MEMORY != null
                ? moduleOverrides.INITIAL_MEMORY
                : 64 * 1024 * 1024;
            this.fdBufferMax = moduleOverrides.FD_BUFFER_MAX != null
                ? moduleOverrides.FD_BUFFER_MAX
                : 64 * 1024;
            this.fs = new RuntimeFs();
            this.extensions = new ConcurrentHashMap<>();
            if (moduleOverrides.pg_extensions != null) {
                this.extensions.putAll(moduleOverrides.pg_extensions);
            }
            this.preInit = new ArrayList<>(moduleOverrides.preInit != null ? moduleOverrides.preInit : List.of());
            this.preRun = new ArrayList<>(moduleOverrides.preRun != null ? moduleOverrides.preRun : List.of());
            this.postRun = new ArrayList<>(moduleOverrides.postRun != null ? moduleOverrides.postRun : List.of());
        }

        @Override
        public String WASM_PREFIX() {
            return wasmPrefix;
        }

        @Override
        public postgresMod.FS FS() {
            return fs;
        }

        @Override
        public Map<String, Promise<byte[]>> pg_extensions() {
            return extensions;
        }

        @Override
        public List<Consumer<postgresMod.PostgresMod>> preInit() {
            return preInit;
        }

        @Override
        public List<Consumer<postgresMod.PostgresMod>> preRun() {
            return preRun;
        }

        @Override
        public List<Consumer<postgresMod.PostgresMod>> postRun() {
            return postRun;
        }

        @Override
        public int INITIAL_MEMORY() {
            return initialMemory;
        }

        @Override
        public int FD_BUFFER_MAX() {
            return fdBufferMax;
        }

        @Override
        public int addFunction(BiConsumer<Integer, Integer> cb, String signature) {
            var id = nextFunctionId.getAndIncrement();
            functions.put(id, cb);
            return id;
        }

        @Override
        public void removeFunction(int f) {
            functions.remove(f);
        }

        @Override
        public void _queue_message(byte[] message) {
            queueInboundMessage(message);
        }

        @Override
        public void _set_read_write_cbs(int readCb, int writeCb) {
            this.readCallbackId = readCb;
            this.writeCallbackId = writeCb;
        }

        @Override
        public void _interactive_write(int msgLength) {
            interactiveDispatch(msgLength, 0);
        }

        @Override
        public void _interactive_one(int length, int peek) {
            interactiveDispatch(length, peek);
        }

        @Override
        public int _pgl_initdb() {
            return 0;
        }

        @Override
        public void _pgl_backend() {
        }

        @Override
        public void _pgl_shutdown() {
        }

        void queueInboundMessage(byte[] message) {
            inboundMessages.addLast(message != null ? message : new byte[0]);
        }

        private void interactiveDispatch(int length, int peek) {
            var readFn = functions.get(readCallbackId);
            var writeFn = functions.get(writeCallbackId);
            if (readFn == null || writeFn == null) {
                return;
            }

            var payload = inboundMessages.pollFirst();
            if (payload == null) {
                payload = new byte[0];
            }

            readFn.accept(0, payload.length);
            writeFn.accept(0, payload.length);

            if (peek != 0) {
                inboundMessages.addFirst(payload);
            }
        }
    }

    private static final class RuntimeFs implements postgresMod.FS {
        private final Map<String, byte[]> files = new ConcurrentHashMap<>();

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
        ) {
            files.put(parent + "/" + name, data != null ? data : new byte[0]);
            if (onLoad != null) {
                onLoad.run();
            }
        }

        @Override
        public postgresMod.PathInfo analyzePath(String path) {
            return new postgresMod.PathInfo(files.containsKey(path));
        }

        @Override
        public void mkdirTree(String path) {
            files.putIfAbsent(path, new byte[0]);
        }

        @Override
        public void writeFile(String path, byte[] data) {
            files.put(path, data != null ? data : new byte[0]);
        }
    }
}
