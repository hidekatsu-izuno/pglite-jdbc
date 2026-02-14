package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class postgresMod {
    private postgresMod() {}

    public interface FS {
        void createPreloadedFile(
            String parent,
            String name,
            byte[] data,
            boolean canRead,
            boolean canWrite,
            Runnable onLoad,
            Runnable onError,
            boolean dontCreateFile
        );

        PathInfo analyzePath(String path);

        void mkdirTree(String path);

        void writeFile(String path, byte[] data);
    }

    public record PathInfo(boolean exists) {}

    public interface PostgresMod {
        String WASM_PREFIX();
        FS FS();
        Map<String, Promise<byte[]>> pg_extensions();
        List<Consumer<PostgresMod>> preInit();
        List<Consumer<PostgresMod>> preRun();
        List<Consumer<PostgresMod>> postRun();
        int INITIAL_MEMORY();
        int FD_BUFFER_MAX();
        int addFunction(BiConsumer<Integer, Integer> cb, String signature);
        void removeFunction(int f);
        void _queue_message(byte[] message);
        void _set_read_write_cbs(int readCb, int writeCb);
        void _interactive_write(int msgLength);
        void _interactive_one(int length, int peek);
        int _pgl_initdb();
        void _pgl_backend();
        void _pgl_shutdown();
    }

    public static class PartialPostgresMod {
        public String WASM_PREFIX;
        public Integer INITIAL_MEMORY;
        public Integer FD_BUFFER_MAX;
        public List<Consumer<PostgresMod>> preInit = new ArrayList<>();
        public List<Consumer<PostgresMod>> preRun = new ArrayList<>();
        public List<Consumer<PostgresMod>> postRun = new ArrayList<>();
        public Map<String, Promise<byte[]>> pg_extensions = Map.of();
        public String[] arguments = new String[0];
        public Boolean noExitRuntime;
    }
}
