package io.github.hidekatsu_izuno.pglite_jdbc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PGliteWasmEngineTest {
    @Test
    void testWasmModuleLoading() {
        var engine = new PGliteWasmEngine();
        var instance = engine.getInstance();
        System.out.println("WASM instance loaded successfully!");
        assertNotNull(instance);
        assertNotNull(instance.export("malloc"));
        assertNotNull(instance.export("free"));
    }
}
