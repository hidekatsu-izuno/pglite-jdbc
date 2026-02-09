package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.pglite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeRunDependencyContractTest {
    @Test
    void shouldFailPreRunWhenManualDependencyRemains() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        runtime.addRunDependency("manual-check");

        var error = assertThrows(RuntimeException.class, runtime::preRun);
        assertTrue(error.getMessage().contains("pending run dependencies"));

        runtime.removeRunDependency("manual-check");
        assertDoesNotThrow(runtime::preRun);
    }

    @Test
    void shouldTreatDuplicateDependencyKeyAsSinglePendingEntry() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        runtime.addRunDependency("duplicate-key");
        runtime.addRunDependency("duplicate-key");
        runtime.removeRunDependency("duplicate-key");

        assertDoesNotThrow(runtime::preRun);
        assertDoesNotThrow(runtime::postRun);
    }
}
