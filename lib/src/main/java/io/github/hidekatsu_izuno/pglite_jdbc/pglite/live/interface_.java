package io.github.hidekatsu_izuno.pglite_jdbc.pglite.live;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class interface_ {
    public static final class LiveQueryOptions<T> {
        public String query;
        public Object[] params;
        public Integer offset;
        public Integer limit;
        public LiveQueryCallback<T> callback;
        public AbortSignal signal;
    }

    public static final class LiveChangesOptions<T> {
        public String query;
        public Object[] params;
        public String key;
        public LiveChangesCallback<T> callback;
        public AbortSignal signal;
    }

    public static final class LiveIncrementalQueryOptions<T> {
        public String query;
        public Object[] params;
        public String key;
        public LiveQueryCallback<T> callback;
        public AbortSignal signal;
    }

    public interface LiveNamespace {
        /**
         * Create a live query
         * @param query - The query to run
         * @param params - The parameters to pass to the query
         * @param callback - A callback to run when the query is updated
         * @returns A promise that resolves to an object with the initial results,
         * an unsubscribe function, and a refresh function
         */
        <T> CompletableFuture<LiveQuery<T>> query(
            String query,
            Object[] params,
            LiveQueryCallback<T> callback
        );

        /**
         * Create a live query
         * @param options - The options to pass to the query
         * @returns A promise that resolves to an object with the initial results,
         * an unsubscribe function, and a refresh function
         */
        <T> CompletableFuture<LiveQuery<T>> query(
            LiveQueryOptions<T> options
        );

        /**
         * Create a live query that returns the changes to the query results
         * @param query - The query to run
         * @param params - The parameters to pass to the query
         * @param callback - A callback to run when the query is updated
         * @returns A promise that resolves to an object with the initial changes,
         * an unsubscribe function, and a refresh function
         */
        <T> CompletableFuture<LiveChanges<T>> changes(
            String query,
            Object[] params,
            String key,
            LiveChangesCallback<T> callback
        );

        /**
         * Create a live query that returns the changes to the query results
         * @param options - The options to pass to the query
         * @returns A promise that resolves to an object with the initial changes,
         * an unsubscribe function, and a refresh function
         */
        <T> CompletableFuture<LiveChanges<T>> changes(
            LiveChangesOptions<T> options
        );

        /**
         * Create a live query with incremental updates
         * @param query - The query to run
         * @param params - The parameters to pass to the query
         * @param callback - A callback to run when the query is updated
         * @returns A promise that resolves to an object with the initial results,
         * an unsubscribe function, and a refresh function
         */
        <T> CompletableFuture<LiveQuery<T>> incrementalQuery(
            String query,
            Object[] params,
            String key,
            LiveQueryCallback<T> callback
        );

        /**
         * Create a live query with incremental updates
         * @param options - The options to pass to the query
         * @returns A promise that resolves to an object with the initial results,
         * an unsubscribe function, and a refresh function
         */
        <T> CompletableFuture<LiveQuery<T>> incrementalQuery(
            LiveIncrementalQueryOptions<T> options
        );
    }

    public static class LiveQueryResults<T> extends Results {
        public Integer totalCount;
        public Integer offset;
        public Integer limit;
    }

    public static final class LiveQuery<T> {
        public LiveQueryResults<T> initialResults;
        public LiveQuerySubscribe<T> subscribe;
        public LiveQueryUnsubscribe<T> unsubscribe;
        public LiveQueryRefresh refresh;
    }

    public static final class LiveChanges<T> {
        public List<Results.Field> fields;
        public List<Change<T>> initialChanges;
        public LiveChangesSubscribe<T> subscribe;
        public LiveChangesUnsubscribe<T> unsubscribe;
        public LiveChangesRefresh refresh;
    }

    public static final class ChangeInsert<T> {
        public List<String> __changed_columns__;
        public String __op__;
        public Integer __after__;
        public T value;
    }

    public static final class ChangeDelete<T> {
        public List<String> __changed_columns__;
        public String __op__;
        public Integer __after__;
        public T value;
    }

    public static final class ChangeUpdate<T> {
        public List<String> __changed_columns__;
        public String __op__;
        public Integer __after__;
        public T value;
    }

    public static final class ChangeReset<T> {
        public String __op__;
        public T value;
    }

    public static final class Change<T> extends HashMap<String, Object> {
    }

    @FunctionalInterface
    public interface LiveQueryCallback<T> {
        void apply(Results results);
    }

    @FunctionalInterface
    public interface LiveChangesCallback<T> {
        void apply(List<Change<T>> changes);
    }

    @FunctionalInterface
    public interface LiveQuerySubscribe<T> {
        void apply(LiveQueryCallback<T> callback);
    }

    @FunctionalInterface
    public interface LiveQueryUnsubscribe<T> {
        CompletableFuture<Void> apply(LiveQueryCallback<T> callback);
    }

    @FunctionalInterface
    public interface LiveQueryRefresh {
        CompletableFuture<Void> apply(RefreshOptions options);
    }

    @FunctionalInterface
    public interface LiveChangesSubscribe<T> {
        void apply(LiveChangesCallback<T> callback);
    }

    @FunctionalInterface
    public interface LiveChangesUnsubscribe<T> {
        CompletableFuture<Void> apply(LiveChangesCallback<T> callback);
    }

    @FunctionalInterface
    public interface LiveChangesRefresh {
        CompletableFuture<Void> apply();
    }

    public static final class RefreshOptions {
        public Integer offset;
        public Integer limit;
    }

    public interface AbortSignal {
        boolean aborted();
        void addEventListener(
            String type,
            AbortListener listener,
            AddEventListenerOptions options
        );
    }

    @FunctionalInterface
    public interface AbortListener {
        void handleEvent();
    }

    public static final class AddEventListenerOptions {
        public Boolean once;
    }

    private interface_() {
    }
}
