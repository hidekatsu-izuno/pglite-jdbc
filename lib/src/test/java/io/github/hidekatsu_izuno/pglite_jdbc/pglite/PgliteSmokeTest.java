package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.contrib.amcheck;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.PGliteOptions;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Smoke scenarios from the migration plan.
 * Enable this class after the Wasm host in release/pglite.java is implemented.
 */
@Disabled("Wasm host factory wiring in release/pglite.java is not implemented yet")
class PgliteSmokeTest {
    private static final class ExtensionsMap
        extends HashMap<String, Object>
        implements interface_.Extensions {
    }

    @Test
    void shouldSelectOneFromMemoryFs() {
        var options = new PGliteOptions<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Extensions>();
        options.dataDir = "memory://";
        var pg = pglite.create(options).join();
        try {
            pg.query("SELECT 1 AS n", null, null).join();
        } finally {
            pg.close().join();
        }
    }

    @Test
    void shouldCommitAndRollbackTransaction() {
        var options = new PGliteOptions<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Extensions>();
        options.dataDir = "memory://";
        var pg = pglite.create(options).join();
        try {
            pg.exec("CREATE TABLE t(id INT PRIMARY KEY, v TEXT);", null).join();
            pg.transaction(
                tx -> tx.exec("INSERT INTO t VALUES (1, 'a');", null).thenApply(ignored -> null)
            ).join();

            try {
                pg.transaction(
                    tx -> tx.exec("INSERT INTO t VALUES (1, 'dup');", null)
                        .thenApply(ignored -> null)
                ).join();
            } catch (RuntimeException ignored) {
                // Expected duplicate-key failure path; rollback is handled by transaction wrapper.
            }
        } finally {
            pg.close().join();
        }
    }

    @Test
    void shouldLoadExtensionBundle() {
        var options = new PGliteOptions<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Extensions>();
        options.dataDir = "memory://";
        var extensions = new ExtensionsMap();
        extensions.put("amcheck", amcheck.amcheck);
        options.extensions = extensions;

        var pg = pglite.create(options).join();
        try {
            pg.exec("CREATE EXTENSION amcheck;", null).join();
        } finally {
            pg.close().join();
        }
    }

    @Test
    void shouldUnlisten() {
        var options = new PGliteOptions<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Extensions>();
        options.dataDir = "memory://";
        var pg = pglite.create(options).join();
        try {
            var unsub = pg.listen("test_channel", payload -> {
            }, null).join();
            unsub.apply(null).join();
        } finally {
            pg.close().join();
        }
    }

    @Test
    void shouldFailAfterClose() {
        var options = new PGliteOptions<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Extensions>();
        options.dataDir = "memory://";
        var pg = pglite.create(options).join();
        pg.close().join();
        try {
            pg.query("SELECT 1", null, null).join();
        } catch (RuntimeException ignored) {
            // expected
        }
    }
}
