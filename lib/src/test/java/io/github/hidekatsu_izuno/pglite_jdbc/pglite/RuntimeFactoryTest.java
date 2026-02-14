package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class RuntimeFactoryTest {
    @Test
    void shouldCreateRuntimeAndDispatchInteractiveCallbacks() {
        var overrides = new postgresMod.PartialPostgresMod();
        overrides.WASM_PREFIX = "/tmp/pglite";

        var mod = io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.pglite
            .PostgresModFactory(overrides)
            .join();
        assertNotNull(mod);
        assertEquals("/tmp/pglite", mod.WASM_PREFIX());

        var read = mod.addFunction((ptr, len) -> 0, "iii");
        var write = mod.addFunction((ptr, len) -> 0, "iii");
        mod._set_read_write_cbs(read, write);
        mod.removeFunction(read);
        mod.removeFunction(write);

        assertTrue(read >= 0);
        assertTrue(write >= 0);
    }
}
