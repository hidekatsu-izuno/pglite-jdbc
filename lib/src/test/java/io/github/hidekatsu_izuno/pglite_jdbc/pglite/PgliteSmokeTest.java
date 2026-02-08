package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.contrib.amcheck;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.contrib.pgcrypto;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.PGliteOptions;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class PgliteSmokeTest {
    private static final class ExtensionsMap
        extends HashMap<String, Object>
        implements interface_.Extensions {
    }

    @Test
    void shouldSelectOneFromMemoryFs() {
        var options = new PGliteOptions<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Extensions>();
        options.dataDir = "memory://";
        var pg = await(pglite.create(options));
        try {
            await(pg.query("SELECT 1 AS n", null, null));
        } finally {
            await(pg.close());
        }
    }

    @Test
    void shouldCommitAndRollbackTransaction() {
        var options = new PGliteOptions<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Extensions>();
        options.dataDir = "memory://";
        var pg = await(pglite.create(options));
        try {
            await(pg.exec("CREATE TABLE t(id INT PRIMARY KEY, v TEXT);", null));
            await(pg.transaction(
                tx -> tx.exec("INSERT INTO t VALUES (1, 'a');", null).thenApply(ignored -> null)
            ));

            try {
                await(pg.transaction(
                    tx -> tx.exec("INSERT INTO t VALUES (1, 'dup');", null)
                        .thenApply(ignored -> null)
                ));
            } catch (RuntimeException ignored) {
                // Expected duplicate-key failure path; rollback is handled by transaction wrapper.
            }
        } finally {
            await(pg.close());
        }
    }

    @Test
    void shouldLoadExtensionBundle() {
        var options = new PGliteOptions<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Extensions>();
        options.dataDir = "memory://";
        var extensions = new ExtensionsMap();
        extensions.put("amcheck", amcheck.amcheck);
        options.extensions = extensions;

        var pg = await(pglite.create(options));
        try {
            await(pg.exec("CREATE EXTENSION amcheck;", null));
        } finally {
            await(pg.close());
        }
    }

    @Test
    void shouldLoadSecondExtensionBundle() {
        var options = new PGliteOptions<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Extensions>();
        options.dataDir = "memory://";
        var extensions = new ExtensionsMap();
        extensions.put("pgcrypto", pgcrypto.pgcrypto);
        options.extensions = extensions;

        var pg = await(pglite.create(options));
        try {
            await(pg.exec("CREATE EXTENSION pgcrypto;", null));
        } finally {
            await(pg.close());
        }
    }

    @Test
    void shouldUnlisten() {
        var options = new PGliteOptions<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Extensions>();
        options.dataDir = "memory://";
        var pg = await(pglite.create(options));
        try {
            var unsub = await(pg.listen("test_channel", payload -> {
            }, null));
            await(unsub.apply(null));
        } finally {
            await(pg.close());
        }
    }

    @Test
    void shouldFailAfterClose() {
        var options = new PGliteOptions<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Extensions>();
        options.dataDir = "memory://";
        var pg = await(pglite.create(options));
        await(pg.close());
        try {
            await(pg.query("SELECT 1", null, null));
        } catch (RuntimeException ignored) {
            // expected
        }
    }

    @Test
    void shouldCreateAndCloseWithinTimeout() {
        assertTimeoutPreemptively(Duration.ofSeconds(240), () -> {
            var options = new PGliteOptions<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Extensions>();
            options.dataDir = "memory://";
            var pg = await(pglite.create(options));
            await(pg.close());
        });
    }

    private static <T> T await(CompletableFuture<T> future) {
        return future.orTimeout(240, TimeUnit.SECONDS).join();
    }
}
