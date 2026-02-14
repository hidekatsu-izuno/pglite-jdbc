package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.worker.index;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class WorkerParityTest {
    @Test
    void shouldRejectBrowserWorkerRuntimeInJvmOnlyMode() {
        assertThrows(
            RuntimeException.class,
            () -> index.worker(new index.WorkerOptions(options -> null), Map.of()).join()
        );
    }
}
