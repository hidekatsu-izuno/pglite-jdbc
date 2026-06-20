package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.BroadcastChannel;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Event;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.EventTarget;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Navigator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class WebStandardPolyfillTest {
    @Test
    void shouldDispatchBroadcastMessagesByChannelName() {
        var chanA = new BroadcastChannel("chan");
        var chanB = new BroadcastChannel("chan");
        var received = new AtomicReference<Object>();

        chanB.addEventListener("message", event -> received.set(event.data()));
        chanA.postMessage("payload");

        assertEquals("payload", received.get());

        chanB.close();
        received.set(null);
        chanA.postMessage("after-close");
        assertEquals(null, received.get());
    }

    @Test
    void shouldSupportBasicEventTargetDispatch() {
        var target = new EventTarget();
        var called = new AtomicBoolean(false);
        target.addEventListener("connected", event -> called.set(true));

        target.dispatchEvent(new Event("connected"));

        assertTrue(called.get());
    }

    @Test
    void shouldProvideJvmLockManager() {
        var lockManager = Navigator.locks();
        var firstRelease = lockManager.acquire("lock-a").join();
        var secondAcquired = new AtomicBoolean(false);
        var secondError = new AtomicReference<Throwable>();
        var secondThread = new Thread(() -> {
            try {
                var release = lockManager.acquire("lock-a").join();
                secondAcquired.set(true);
                release.run();
            } catch (Throwable e) {
                secondError.set(e);
            }
        });
        secondThread.start();
        try {
            Thread.sleep(50L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertEquals(null, secondError.get());
        assertFalse(secondAcquired.get());
        firstRelease.run();
        try {
            secondThread.join(500L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertEquals(null, secondError.get());
        assertTrue(secondAcquired.get());
    }
}
