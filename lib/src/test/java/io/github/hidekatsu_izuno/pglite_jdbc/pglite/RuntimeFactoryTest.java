package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
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

        var readCount = new AtomicInteger();
        var writeCount = new AtomicInteger();
        var read = mod.addFunction((ptr, len) -> readCount.incrementAndGet(), "vii");
        var write = mod.addFunction((ptr, len) -> writeCount.incrementAndGet(), "vii");
        mod._set_read_write_cbs(read, write);
        mod._queue_message(new byte[] { 'Q', 0, 0, 0, 5, 0 });
        mod._interactive_one(6, 0);
        mod.removeFunction(read);
        mod.removeFunction(write);

        assertTrue(readCount.get() > 0);
        assertTrue(writeCount.get() > 0);
    }
}

