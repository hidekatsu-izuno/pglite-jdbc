package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Worker;
import org.junit.jupiter.api.Test;

public class WorkerPolyfillTest {
    @Test
    void shouldRejectWorkerPolyfillInJvmOnlyMode() {
        assertThrows(UnsupportedOperationException.class, Worker::createLinkedPair);
    }
}
