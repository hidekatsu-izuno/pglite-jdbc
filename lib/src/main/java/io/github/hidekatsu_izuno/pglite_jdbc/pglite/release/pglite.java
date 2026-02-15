package io.github.hidekatsu_izuno.pglite_jdbc.pglite.release;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.postgresMod.PostgresMod;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.postgresMod.ReadWriteCallback;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.extensionUtils.EmscriptenFS;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.extensionUtils.ExtensionBlob;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.postgresMod.EmscriptenRuntime;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.postgresMod.PartialPostgresMod;

public final class pglite {
    public static class PostgresModFactory {
        public static PostgresMod create(PartialPostgresMod moduleOverrides) {
            return new PostgresMod() {
                @Override
                public String WASM_PREFIX() {
                    // TODO Auto-generated method stub
                    throw new UnsupportedOperationException("Unimplemented method 'WASM_PREFIX'");
                }

                @Override
                public Integer INITIAL_MEMORY() {
                    // TODO Auto-generated method stub
                    throw new UnsupportedOperationException("Unimplemented method 'INITIAL_MEMORY'");
                }

                @Override
                public Integer FD_BUFFER_MAX() {
                    // TODO Auto-generated method stub
                    throw new UnsupportedOperationException("Unimplemented method 'FD_BUFFER_MAX'");
                }

                @Override
                public void setFD_BUFFER_MAX(Integer value) {
                    // TODO Auto-generated method stub
                    throw new UnsupportedOperationException("Unimplemented method 'setFD_BUFFER_MAX'");
                }

                @Override
                public Uint8Array HEAP8() {
                    // TODO Auto-generated method stub
                    throw new UnsupportedOperationException("Unimplemented method 'HEAP8'");
                }

                @Override
                public Uint8Array HEAPU8() {
                    // TODO Auto-generated method stub
                    throw new UnsupportedOperationException("Unimplemented method 'HEAPU8'");
                }

                @Override
                public Map<String, CompletableFuture<ExtensionBlob>> pg_extensions() {
                    // TODO Auto-generated method stub
                    throw new UnsupportedOperationException("Unimplemented method 'pg_extensions'");
                }

                @Override
                public int _pgl_initdb() {
                    // TODO Auto-generated method stub
                    throw new UnsupportedOperationException("Unimplemented method '_pgl_initdb'");
                }

                @Override
                public void _pgl_backend() {
                    // TODO Auto-generated method stub
                    throw new UnsupportedOperationException("Unimplemented method '_pgl_backend'");
                }

                @Override
                public void _pgl_shutdown() {
                    // TODO Auto-generated method stub
                    throw new UnsupportedOperationException("Unimplemented method '_pgl_shutdown'");
                }

                @Override
                public void _interactive_write(int msgLength) {
                    // TODO Auto-generated method stub
                    throw new UnsupportedOperationException("Unimplemented method '_interactive_write'");
                }

                @Override
                public void _interactive_one(int length, int peek) {
                    // TODO Auto-generated method stub
                    throw new UnsupportedOperationException("Unimplemented method '_interactive_one'");
                }

                @Override
                public void _set_read_write_cbs(int read_cb, int write_cb) {
                    // TODO Auto-generated method stub
                    throw new UnsupportedOperationException("Unimplemented method '_set_read_write_cbs'");
                }

                @Override
                public int addFunction(ReadWriteCallback cb, String signature) {
                    // TODO Auto-generated method stub
                    throw new UnsupportedOperationException("Unimplemented method 'addFunction'");
                }

                @Override
                public void removeFunction(int f) {
                    // TODO Auto-generated method stub
                    throw new UnsupportedOperationException("Unimplemented method 'removeFunction'");
                }

                @Override
                public void copyFromHeap(int ptr, byte[] dest, int destOffset, int length) {
                    // TODO Auto-generated method stub
                    throw new UnsupportedOperationException("Unimplemented method 'copyFromHeap'");
                }

                @Override
                public void copyToHeap(int ptr, byte[] src, int srcOffset, int length) {
                    // TODO Auto-generated method stub
                    throw new UnsupportedOperationException("Unimplemented method 'copyToHeap'");
                }

                @Override
                public EmscriptenRuntime runtime() {
                    // TODO Auto-generated method stub
                    throw new UnsupportedOperationException("Unimplemented method 'runtime'");
                }

                @Override
                public EmscriptenFS FS() {
                    // TODO Auto-generated method stub
                    throw new UnsupportedOperationException("Unimplemented method 'FS'");
                }

            };
        }
    }

    private pglite() {
    }
}
