package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

    @Test
    void shouldPerformCrudOperations() {
        var pg = new pglite();
        pg.waitReady().join();
        try {
            assertDoesNotThrow(() -> pg.exec("CREATE TABLE IF NOT EXISTS crud_test (id INT PRIMARY KEY, name TEXT)", null).join());
            assertDoesNotThrow(() -> pg.exec("DELETE FROM crud_test", null).join());
            assertDoesNotThrow(() -> pg.exec("INSERT INTO crud_test (id, name) VALUES (1, 'alice')", null).join());
            assertDoesNotThrow(() -> pg.query("SELECT * FROM crud_test WHERE id = 1", null, null).join());
            assertDoesNotThrow(() -> pg.exec("UPDATE crud_test SET name = 'carol' WHERE id = 1", null).join());
            assertDoesNotThrow(() -> pg.exec("DELETE FROM crud_test WHERE id = 1", null).join());
        } finally {
            pg.close().join();
        }
    }
}
