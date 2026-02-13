package io.github.hidekatsu_izuno.pglite_jdbc.polyfills;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromiseTest {
    @Test
    void thenShouldRunAsynchronously() {
        var callerThread = Thread.currentThread();
        var callbackThread = new AtomicReference<Thread>();
        var result = Promise.resolve(1).then(
            value -> {
                callbackThread.set(Thread.currentThread());
                return value;
            }
        ).join();
        assertEquals(1, result);
        assertNotNull(callbackThread.get());
        assertNotSame(callerThread, callbackThread.get());
    }

    @Test
    void shouldResolveAndChainWithThen() {
        var result = Promise.resolve(2).then(value -> value + 3).join();
        assertEquals(5, result);
    }

    @Test
    void shouldRecoverWithCatch() {
        var result = Promise.<Integer>reject(new IllegalStateException("boom"))
            .catch_(error -> 7)
            .join();
        assertEquals(7, result);
    }

    @Test
    void shouldRecoverWithThenRejectedHandler() {
        var result = Promise.<Integer>reject(new IllegalArgumentException("bad"))
            .then(value -> value, error -> 9)
            .join();
        assertEquals(9, result);
    }

    @Test
    void finallyShouldPassThroughFulfilledResult() {
        var called = new AtomicBoolean(false);
        var result = Promise.resolve("ok")
            .finally_(
                () -> {
                    called.set(true);
                    return null;
                }
            )
            .join();
        assertTrue(called.get());
        assertEquals("ok", result);
    }

    @Test
    void finallyShouldPassThroughRejectedError() {
        var called = new AtomicBoolean(false);
        var error = assertThrows(
            CompletionException.class,
            () -> Promise.<String>reject(new IllegalStateException("bad"))
                .finally_(
                    () -> {
                        called.set(true);
                        return null;
                    }
                )
                .join()
        );
        assertTrue(called.get());
        assertInstanceOf(IllegalStateException.class, error.getCause());
        assertEquals("bad", error.getCause().getMessage());
    }

    @Test
    void finallyShouldReplaceResultWhenCallbackFails() {
        var error = assertThrows(
            CompletionException.class,
            () -> Promise.resolve("ok")
                .finally_(
                    () -> {
                        throw new RuntimeException("cleanup");
                    }
                )
                .join()
        );
        assertInstanceOf(RuntimeException.class, error.getCause());
        assertEquals("cleanup", error.getCause().getMessage());
    }

    @Test
    void thenShouldFlattenPromiseReturnValue() {
        var result = Promise.resolve(1).then(value -> Promise.resolve(value + 1)).join();
        assertEquals(2, result);
    }

    @Test
    void thenShouldFlattenCompletionStageReturnValue() {
        var result = Promise.resolve(1)
            .then(value -> CompletableFuture.completedFuture(value + 2))
            .join();
        assertEquals(3, result);
    }

    @Test
    void allShouldResolveInInputOrder() {
        var result = Promise.<Integer>all(
            List.of(
                Promise.resolve(1),
                CompletableFuture.completedFuture(2),
                3
            )
        ).join();
        assertEquals(List.of(1, 2, 3), result);
    }

    @Test
    void allShouldRejectWhenAnyInputRejects() {
        var error = assertThrows(
            CompletionException.class,
            () -> Promise.<Integer>all(
                List.of(
                    Promise.resolve(1),
                    Promise.reject(new IllegalArgumentException("all-fail")),
                    Promise.resolve(3)
                )
            ).join()
        );
        assertInstanceOf(IllegalArgumentException.class, error.getCause());
        assertEquals("all-fail", error.getCause().getMessage());
    }

    @Test
    void allShouldResolveEmptyInput() {
        var result = Promise.<Integer>all(List.of()).join();
        assertEquals(List.of(), result);
    }
}
