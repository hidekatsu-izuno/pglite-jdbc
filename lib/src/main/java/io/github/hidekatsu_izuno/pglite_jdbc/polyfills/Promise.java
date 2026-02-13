package io.github.hidekatsu_izuno.pglite_jdbc.polyfills;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Function;
import java.util.function.Supplier;

public final class Promise<T> {
    private static final ThreadLocal<ExecutorService> EXECUTOR = ThreadLocal.withInitial(
        Executors::newVirtualThreadPerTaskExecutor
    );
    private final CompletableFuture<T> future;

    @FunctionalInterface
    public interface Executor<T> {
        void run(Resolve<T> resolve, Reject reject);
    }

    @FunctionalInterface
    public interface Resolve<T> {
        void run(T value);
    }

    @FunctionalInterface
    public interface Reject {
        void run(Throwable reason);
    }

    public Promise(Executor<T> executor) {
        this.future = new CompletableFuture<>();
        var settled = new AtomicBoolean(false);
        var resolve = (Resolve<T>) value -> {
            if (!settled.compareAndSet(false, true)) {
                return;
            }
            completeFromValue(value, this.future);
        };
        var reject = (Reject) reason -> {
            if (!settled.compareAndSet(false, true)) {
                return;
            }
            this.future.completeExceptionally(normalizeReason(reason));
        };
        try {
            executor.run(resolve, reject);
        } catch (Throwable e) {
            reject.run(e);
        }
    }

    private Promise(CompletableFuture<T> future) {
        this.future = future;
    }

    public static ExecutorService executor() {
        return EXECUTOR.get();
    }

    public <U> Promise<U> then(Function<? super T, ?> onFulfilled) {
        return this.then(onFulfilled, null);
    }

    public <U> Promise<U> then(
        Function<? super T, ?> onFulfilled,
        Function<? super Throwable, ?> onRejected
    ) {
        var nextFuture = new CompletableFuture<U>();
        this.future.whenCompleteAsync(
            (value, error) -> {
                if (error == null) {
                    if (onFulfilled == null) {
                        completeFromValue(value, nextFuture);
                        return;
                    }
                    try {
                        var handled = onFulfilled.apply(value);
                        completeFromValue(handled, nextFuture);
                    } catch (Throwable e) {
                        nextFuture.completeExceptionally(e);
                    }
                    return;
                }
                var cause = unwrap(error);
                if (onRejected == null) {
                    nextFuture.completeExceptionally(cause);
                    return;
                }
                try {
                    var handled = onRejected.apply(cause);
                    completeFromValue(handled, nextFuture);
                } catch (Throwable e) {
                    nextFuture.completeExceptionally(e);
                }
            },
            executor()
        );
        return new Promise<>(nextFuture);
    }

    public Promise<T> catch_(Function<? super Throwable, ?> onRejected) {
        return this.then(value -> value, onRejected);
    }

    public Promise<T> finally_(Supplier<?> onFinally) {
        var nextFuture = new CompletableFuture<T>();
        this.future.whenCompleteAsync(
            (value, error) -> {
                Object callbackResult;
                try {
                    callbackResult = onFinally.get();
                } catch (Throwable callbackError) {
                    nextFuture.completeExceptionally(callbackError);
                    return;
                }
                var callbackFuture = new CompletableFuture<Object>();
                completeFromValue(callbackResult, callbackFuture);
                callbackFuture.whenCompleteAsync(
                    (ignored, callbackError) -> {
                        if (callbackError != null) {
                            nextFuture.completeExceptionally(unwrap(callbackError));
                            return;
                        }
                        if (error == null) {
                            nextFuture.complete(value);
                            return;
                        }
                        nextFuture.completeExceptionally(unwrap(error));
                    },
                    executor()
                );
            },
            executor()
        );
        return new Promise<>(nextFuture);
    }

    @SuppressWarnings("unchecked")
    public static <T> Promise<T> resolve(T value) {
        if (value instanceof Promise<?> promise) {
            return (Promise<T>) promise;
        }
        var future = new CompletableFuture<T>();
        completeFromValue(value, future);
        return new Promise<>(future);
    }

    public static <T> Promise<T> reject(Throwable reason) {
        var future = new CompletableFuture<T>();
        future.completeExceptionally(normalizeReason(reason));
        return new Promise<>(future);
    }

    @SuppressWarnings("unchecked")
    public static <T> Promise<List<T>> all(List<?> values) {
        Objects.requireNonNull(values, "values is null");
        if (values.isEmpty()) {
            return Promise.resolve(List.of());
        }
        var resultFuture = new CompletableFuture<List<T>>();
        var results = new AtomicReferenceArray<Object>(values.size());
        var remaining = new AtomicInteger(values.size());
        var settled = new AtomicBoolean(false);
        for (var i = 0; i < values.size(); i++) {
            var index = i;
            var itemFuture = new CompletableFuture<Object>();
            completeFromValue(values.get(i), itemFuture);
            itemFuture.whenCompleteAsync(
                (value, error) -> {
                    if (settled.get()) {
                        return;
                    }
                    if (error != null) {
                        if (settled.compareAndSet(false, true)) {
                            resultFuture.completeExceptionally(unwrap(error));
                        }
                        return;
                    }
                    results.set(index, value);
                    if (remaining.decrementAndGet() == 0 && settled.compareAndSet(false, true)) {
                        var resolvedValues = new ArrayList<T>(values.size());
                        for (var j = 0; j < values.size(); j++) {
                            resolvedValues.add((T) results.get(j));
                        }
                        resultFuture.complete(resolvedValues);
                    }
                },
                executor()
            );
        }
        return new Promise<>(resultFuture);
    }

    public CompletableFuture<T> toCompletableFuture() {
        return this.future;
    }

    public T join() {
        try {
            return this.future.join();
        } catch (CompletionException e) {
            var cause = unwrap(e);
            if (cause == e) {
                throw e;
            }
            throw new CompletionException(cause);
        }
    }

    private static Throwable normalizeReason(Throwable reason) {
        if (reason == null) {
            return new NullPointerException("reason is null");
        }
        return reason;
    }

    private static Throwable unwrap(Throwable error) {
        var cause = error;
        while (
            (
                cause instanceof CompletionException ||
                cause instanceof ExecutionException
            ) &&
            cause.getCause() != null
        ) {
            cause = cause.getCause();
        }
        return cause;
    }

    @SuppressWarnings("unchecked")
    private static <T> void completeFromValue(
        Object value,
        CompletableFuture<T> targetFuture
    ) {
        if (value instanceof Promise<?> promise) {
            if (promise.future == targetFuture) {
                targetFuture.completeExceptionally(
                    new IllegalStateException("Cannot resolve promise with itself")
                );
                return;
            }
            promise.future.whenCompleteAsync(
                (resolved, error) -> {
                    if (error != null) {
                        targetFuture.completeExceptionally(unwrap(error));
                        return;
                    }
                    completeFromValue(resolved, targetFuture);
                },
                executor()
            );
            return;
        }
        if (value instanceof CompletionStage<?> stage) {
            var stageFuture = stage.toCompletableFuture();
            if (stageFuture == targetFuture) {
                targetFuture.completeExceptionally(
                    new IllegalStateException("Cannot resolve promise with itself")
                );
                return;
            }
            stageFuture.whenCompleteAsync(
                (resolved, error) -> {
                    if (error != null) {
                        targetFuture.completeExceptionally(unwrap(error));
                        return;
                    }
                    completeFromValue(resolved, targetFuture);
                },
                executor()
            );
            return;
        }
        targetFuture.complete((T) value);
    }
}
