package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

public class PgliteSmokeTest {
    @Test
    void shouldCreateAndExecuteSimpleSelect() {
        var pg = new pglite();
        pg.waitReady().join();

        var result = pg.query("SELECT 1", null, null).join();
        assertNotNull(result);
        assertEquals(1, result.rows().size());
        @SuppressWarnings("unchecked")
        var row = (Map<String, Object>) result.rows().getFirst();
        assertEquals("1", row.get("result"));
        assertFalse(pg.closed());

        pg.close().join();
        assertTrue(pg.closed());
    }
}

