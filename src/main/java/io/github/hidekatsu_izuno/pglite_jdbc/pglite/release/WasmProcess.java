package io.github.hidekatsu_izuno.pglite_jdbc.pglite.release;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.initdbModFactory;

/** An isolated WASM process with its own memory and file descriptors. */
public interface WasmProcess extends initdbModFactory.InitdbMod, AutoCloseable {
    WasmProcess createProcess(String program);
    @Override
    void close();
}
