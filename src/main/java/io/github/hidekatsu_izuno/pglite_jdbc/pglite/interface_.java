package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.base;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.tarUtils.DumpTarCompressionOptions;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class interface_ {
    private interface_() {}

    public enum DebugLevel {
        LEVEL_0(0),
        LEVEL_1(1),
        LEVEL_2(2),
        LEVEL_3(3),
        LEVEL_4(4),
        LEVEL_5(5);

        private final int value;

        DebugLevel(int value) {
            this.value = value;
        }

        public int value() {
            return this.value;
        }

        public static DebugLevel of(int value) {
            return switch (value) {
                case 1 -> LEVEL_1;
                case 2 -> LEVEL_2;
                case 3 -> LEVEL_3;
                case 4 -> LEVEL_4;
                case 5 -> LEVEL_5;
                default -> LEVEL_0;
            };
        }
    }

    public enum RowMode {
        array,
        object,
    }

    public interface Parser {
        Object parse(String value, Integer typeId);
    }

    public interface Serializer {
        String serialize(Object value);
    }

    public record QueryOptions(
        RowMode rowMode,
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

    public record ExecProtocolOptionsStream(
        boolean syncToFs,
        Consumer<byte[]> onRawData
    ) {}

    public record Field(String name, int dataTypeID, int dataTypeModifier) {
        public Field(String name, int dataTypeID) {
            this(name, dataTypeID, -1);
        }
    }

    public record QueryParamField(int dataTypeID, Serializer serializer) {}

    public record ResultField(String name, int dataTypeID, int dataTypeModifier, Parser parser) {
        public ResultField(String name, int dataTypeID, Parser parser) {
            this(name, dataTypeID, -1, parser);
        }
    }

    public record DescribeQueryResult(
        List<QueryParamField> queryParams,
        List<ResultField> resultFields
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

    public record DumpDataDirResult(
        byte[] tarball,
        String extension,
        String filename
    ) {}

    public record PGliteOptions(
        Boolean noInitDb,
        String dataDir,
        String username,
        String database,
        base.Filesystem fs,
        DebugLevel debug,
        Boolean relaxedDurability,
        Map<String, Object> extensions,
        byte[] loadDataDir,
        byte[] icuDataDir,
        Integer initialMemory,
        byte[] pgliteWasmModule,
        byte[] initdbWasmModule,
        byte[] fsBundle,
        Map<Integer, Parser> parsers,
        Map<Integer, Serializer> serializers,
        String[] startParams,
        String[] initDbStartParams,
        Object postgresqlconf
    ) {
        public PGliteOptions() {
            this(
                null,
                null,
                null,
                null,
                null,
                DebugLevel.LEVEL_0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            );
        }
    }

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

        Promise<Void> execProtocolRawStream(
            byte[] message,
            ExecProtocolOptionsStream options
        );

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

        Promise<DumpDataDirResult> dumpDataDir(DumpTarCompressionOptions compression);

        Promise<Void> refreshArrayTypes();
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
        URL bundlePath,
        List<String> sharedPreloadLibraries,
        java.util.function.Supplier<Promise<Void>> init,
        java.util.function.Supplier<Promise<Void>> close
    ) {}

    public interface PGliteInterfaceExtensions {
        Map<String, Object> namespaces();
    }
}
