package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;
import java.util.List;
import java.util.Map;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import java.util.function.Consumer;

public class interface_ {
    private interface_() {}

    public interface Parser {
        Object parse(String value, Integer typeId);
    }

    public interface Serializer {
        String serialize(Object value);
    }

    public record QueryOptions(
        String rowMode,
        Map<Integer, Parser> parsers,
        Map<Integer, Serializer> serializers,
        byte[] blob,
        Consumer<messages.NoticeMessage> onNotice,
        int[] paramTypes
    ) {}

    public record ExecProtocolOptions(
        boolean syncToFs,
        boolean throwOnError,
        Consumer<messages.NoticeMessage> onNotice
    ) {}

    public record Field(String name, int dataTypeID) {}

    public record DescribeQueryResult(
        List<Field> queryParams,
        List<Field> resultFields
    ) {}

    public record Results<T>(
        List<T> rows,
        Integer affectedRows,
        List<Field> fields,
        byte[] blob
    ) {}

    public record ExecProtocolResult(
        List<messages.BackendMessage> messages,
        byte[] data
    ) {}

    public interface Transaction {
        <T> Promise<Results<T>> query(String query, Object[] params, QueryOptions options);
        <T> Promise<Results<T>> sql(List<String> strings, Object... params);
        Promise<List<Results<Map<String, Object>>>> exec(String query, QueryOptions options);
        Promise<Void> rollback();
        Promise<java.util.function.Function<Transaction, Promise<Void>>> listen(
            String channel,
            java.util.function.Consumer<String> callback
        );
        boolean closed();
    }

    public interface PGliteInterface {
        Promise<Void> waitReady();

        int debug();

        boolean ready();

        boolean closed();

        Promise<Void> close();

        <T> Promise<Results<T>> query(String query, Object[] params, QueryOptions options);

        Promise<List<Results<Map<String, Object>>>> exec(
            String query,
            QueryOptions options
        );

        <T> Promise<Results<T>> sql(List<String> strings, Object... params);

        Promise<DescribeQueryResult> describeQuery(String query);

        <T> Promise<T> transaction(java.util.function.Function<Transaction, Promise<T>> callback);

        Promise<ExecProtocolResult> execProtocol(
            byte[] message,
            ExecProtocolOptions options
        );

        Promise<byte[]> execProtocolRaw(byte[] message, ExecProtocolOptions options);

        <T> Promise<T> runExclusive(java.util.function.Supplier<Promise<T>> fn);

        Promise<java.util.function.Function<Transaction, Promise<Void>>> listen(
            String channel,
            java.util.function.Consumer<String> callback,
            Transaction tx
        );

        Promise<Void> unlisten(
            String channel,
            java.util.function.Consumer<String> callback,
            Transaction tx
        );

        java.util.function.Supplier<Void> onNotification(
            java.util.function.BiConsumer<String, String> callback
        );

        void offNotification(java.util.function.BiConsumer<String, String> callback);

        Promise<byte[]> dumpDataDir(String compression);
    }

    @FunctionalInterface
    public interface ExtensionSetup {
        Promise<ExtensionSetupResult> setup(
            PGliteInterface pg,
            Object emscriptenOpts,
            boolean clientOnly
        );
    }

    public interface Extension {
        String name();
        ExtensionSetup setup();
    }

    public record ExtensionSetupResult(
        Object emscriptenOpts,
        Map<String, Object> namespaceObj,
        String bundlePath,
        Runnable init,
        Runnable close
    ) {}
}
