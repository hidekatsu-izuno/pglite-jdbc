package io.github.hidekatsu_izuno.pglite_jdbc.pglite.live;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import java.util.List;
import java.util.function.Consumer;

public class interface_ {
    private interface_() {}

    public record LiveQueryOptions<T>(
        String query,
        Object[] params,
        Integer offset,
        Integer limit,
        Consumer<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<T>> callback,
        Object signal
    ) {}

    public record LiveChangesOptions<T>(
        String query,
        Object[] params,
        String key,
        Consumer<List<Change<T>>> callback,
        Object signal
    ) {}

    public record LiveIncrementalQueryOptions<T>(
        String query,
        Object[] params,
        String key,
        Consumer<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<T>> callback,
        Object signal
    ) {}

    public record LiveQueryResults<T>(
        List<T> rows,
        Integer affectedRows,
        List<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Field> fields,
        byte[] blob,
        Integer totalCount,
        Integer offset,
        Integer limit
    ) {}

    public interface LiveQuery<T> {
        LiveQueryResults<T> initialResults();
        void subscribe(Consumer<LiveQueryResults<T>> callback);
        Promise<Void> unsubscribe(Consumer<LiveQueryResults<T>> callback);
        Promise<Void> refresh(Integer offset, Integer limit);
    }

    public sealed interface Change<T> permits ChangeInsert, ChangeDelete, ChangeUpdate, ChangeReset {}

    public record ChangeInsert<T>(List<String> changedColumns, Object after, T row) implements Change<T> {}

    public record ChangeDelete<T>(List<String> changedColumns, T row) implements Change<T> {}

    public record ChangeUpdate<T>(List<String> changedColumns, Object after, T row) implements Change<T> {}

    public record ChangeReset<T>(T row) implements Change<T> {}

    public interface LiveChanges<T> {
        List<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Field> fields();
        List<Change<T>> initialChanges();
        void subscribe(Consumer<List<Change<T>>> callback);
        Promise<Void> unsubscribe(Consumer<List<Change<T>>> callback);
        Promise<Void> refresh();
    }

    public interface LiveNamespace {
        <T> Promise<LiveQuery<T>> query(
            String query,
            Object[] params,
            Consumer<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<T>> callback
        );

        <T> Promise<LiveQuery<T>> query(LiveQueryOptions<T> options);

        <T> Promise<LiveChanges<T>> changes(
            String query,
            Object[] params,
            String key,
            Consumer<List<Change<T>>> callback
        );

        <T> Promise<LiveChanges<T>> changes(LiveChangesOptions<T> options);

        <T> Promise<LiveQuery<T>> incrementalQuery(
            String query,
            Object[] params,
            String key,
            Consumer<io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results<T>> callback
        );

        <T> Promise<LiveQuery<T>> incrementalQuery(LiveIncrementalQueryOptions<T> options);
    }
}
