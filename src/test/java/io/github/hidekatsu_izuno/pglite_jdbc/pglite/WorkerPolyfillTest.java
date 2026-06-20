package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Worker;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class WorkerPolyfillTest {
    @Test
    void shouldDeliverMessagesBetweenLinkedWorkers() {
        var pair = Worker.createLinkedPair();
        var received = new AtomicReference<Object>();
        pair.workerSide().addEventListener("message", event -> received.set(event.data()));

        pair.mainSide().postMessage("hello");

        assertEquals("hello", received.get());
    }

    @Test
    void shouldMarkWorkerTerminated() {
        var pair = Worker.createLinkedPair();

        pair.mainSide().terminate();

        assertTrue(pair.mainSide().terminated());
    }
}
