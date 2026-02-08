package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.pglite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeFactoryTest {
    @Test
    void shouldCreateRuntimeAndExposeFs() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        assertNotNull(mod.runtime());
        assertNotNull(mod.FS());
    }

    @Test
    void shouldLoadPgliteDataFromClasspath() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var expected = utils.readFile("pglite.data");
        var loaded = mod.runtime().getPreloadedPackage("pglite.data", expected.length);
        assertTrue(loaded.length == expected.length);
    }

    @Test
    void shouldRejectUnknownPackage() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        assertThrows(RuntimeException.class, () -> mod.runtime().getPreloadedPackage("unknown", 0));
    }

    @Test
    void shouldRejectPackageSizeMismatch() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var expected = utils.readFile("pglite.data");
        assertThrows(
            RuntimeException.class,
            () -> mod.runtime().getPreloadedPackage("pglite.data", expected.length + 1)
        );
    }
}
