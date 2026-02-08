package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.pglite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeTableManagerTest {
    @Test
    void shouldReuseReleasedCallbackTableSlot() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var slot1 = mod.addFunction((ptr, len) -> 0, "iii");
        var slot2 = mod.addFunction((ptr, len) -> len, "iii");
        mod.removeFunction(slot1);
        var slot3 = mod.addFunction((ptr, len) -> ptr + len, "iii");

        assertTrue(slot1 >= 0);
        assertTrue(slot2 >= 0);
        assertEquals(slot1, slot3);

        mod.removeFunction(slot2);
        mod.removeFunction(slot3);
    }
}
