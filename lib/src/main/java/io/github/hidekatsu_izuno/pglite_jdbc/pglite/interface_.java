package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.BackendMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.NoticeMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.templating.TemplateStringsArray;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class interface_ {
    public static final class FilesystemType {
        public static final String nodefs = "nodefs";
        public static final String idbfs = "idbfs";
        public static final String memoryfs = "memoryfs";

        private FilesystemType() {
        }
    }

    public static final class DebugLevel {
        public static final int LEVEL_0 = 0;
        public static final int LEVEL_1 = 1;
        public static final int LEVEL_2 = 2;
        public static final int LEVEL_3 = 3;
        public static final int LEVEL_4 = 4;
        public static final int LEVEL_5 = 5;

        private DebugLevel() {
        }
    }

    public static final class RowMode {
        public static final String array = "array";
        public static final String object = "object";

        private RowMode() {
        }
    }

    public interface ParserOptions extends Map<Integer, types.Parser> {
    }

    public interface SerializerOptions extends Map<Integer, types.Serializer> {
    }

    public static final class QueryOptions {
        public String rowMode;
        public ParserOptions parsers;
        public SerializerOptions serializers;
        public Blob blob;
        public Consumer<NoticeMessage> onNotice;
        public int[] paramTypes;
    }

    public static final class ExecProtocolOptions {
        public Boolean syncToFs;
        public Boolean throwOnError;
        public Consumer<NoticeMessage> onNotice;
    }

    public static final class ExtensionSetupResult<TNamespace> {
        public Object emscriptenOpts;
        public TNamespace namespaceObj;
        public URL bundlePath;
        public Supplier<CompletableFuture<Void>> init;
        public Supplier<CompletableFuture<Void>> close;
    }

    @FunctionalInterface
    public interface ExtensionSetup<TNamespace> {
        CompletableFuture<ExtensionSetupResult<TNamespace>> apply(
            PGliteInterface<?> pg,
            Object emscriptenOpts,
            Boolean clientOnly
        );
    }

    public static final class Extension<TNamespace> {
        public String name;
        public ExtensionSetup<TNamespace> setup;
    }

    public interface ExtensionNamespace<T> {
    }

    public interface Extensions extends Map<String, Object> {
    }

    public interface InitializedExtensions<TExtensions extends Extensions>
        extends Map<String, Object> {
    }

    public static final class ExecProtocolResult {
        public List<BackendMessage> messages;
        public Uint8Array data;
    }

    public static final class DumpDataDirResult {
        public Uint8Array tarball;
        public String extension;
        public String filename;
    }

    public static final class PGliteOptions<TExtensions extends Extensions> {
        public String dataDir;
        public String username;
        public String database;
        public Filesystem fs;
        public Integer debug;
        public Boolean relaxedDurability;
        public TExtensions extensions;
        public Blob loadDataDir;
        public Integer initialMemory;
        public com.dylibso.chicory.wasm.WasmModule wasmModule;
        public Blob fsBundle;
        public ParserOptions parsers;
        public SerializerOptions serializers;
    }

    public interface PGliteInterface<TExtensions extends Extensions>
        extends InitializedExtensions<TExtensions> {
        CompletableFuture<Void> waitReady();
        int debug();
        boolean ready();
        boolean closed();

        CompletableFuture<Void> close();
        <T> CompletableFuture<Results> query(
            String query,
            Object[] params,
            QueryOptions options
        );
        <T> CompletableFuture<Results> sql(
            TemplateStringsArray sqlStrings,
            Object... params
        );
        CompletableFuture<List<Results>> exec(String query, QueryOptions options);
        CompletableFuture<DescribeQueryResult> describeQuery(String query);
        <T> CompletableFuture<T> transaction(
            Function<Transaction, CompletableFuture<T>> callback
        );
        CompletableFuture<Uint8Array> execProtocolRaw(
            Uint8Array message,
            ExecProtocolOptions options
        );
        CompletableFuture<ExecProtocolResult> execProtocol(
            Uint8Array message,
            ExecProtocolOptions options
        );
        <T> CompletableFuture<T> runExclusive(
            Supplier<CompletableFuture<T>> fn
        );
        CompletableFuture<Function<Transaction, CompletableFuture<Void>>> listen(
            String channel,
            Consumer<String> callback,
            Transaction tx
        );
        CompletableFuture<Void> unlisten(
            String channel,
            Consumer<String> callback,
            Transaction tx
        );
        Runnable onNotification(
            BiConsumer<String, String> callback
        );
        void offNotification(BiConsumer<String, String> callback);
        CompletableFuture<Blob> dumpDataDir(DumpTarCompressionOptions compression);
        CompletableFuture<Void> refreshArrayTypes();
    }

    public interface PGliteInterfaceExtensions<E extends Extensions>
        extends Map<String, Object> {
    }

    public static final class Results {
        public List<Object> rows;
        public Integer affectedRows;
        public List<Field> fields;
        public Blob blob; // Only set when a file is returned, such as from a COPY command

        public static final class Field {
            public String name;
            public int dataTypeID;
        }
    }

    public interface Transaction {
        <T> CompletableFuture<Results> query(
            String query,
            Object[] params,
            QueryOptions options
        );
        <T> CompletableFuture<Results> sql(
            TemplateStringsArray sqlStrings,
            Object... params
        );
        CompletableFuture<List<Results>> exec(String query, QueryOptions options);
        CompletableFuture<Void> rollback();
        CompletableFuture<Function<Transaction, CompletableFuture<Void>>> listen(
            String channel,
            Consumer<String> callback
        );
        boolean closed();
    }

    public static final class DescribeQueryResult {
        public List<QueryParam> queryParams;
        public List<ResultField> resultFields;

        public static final class QueryParam {
            public int dataTypeID;
            public types.Serializer serializer;
        }

        public static final class ResultField {
            public String name;
            public int dataTypeID;
            public types.Parser parser;
        }
    }

    public interface Blob {
    }

    public interface File extends Blob {
    }

    public interface Filesystem {
    }

    public static final class DumpTarCompressionOptions {
    }

    private interface_() {
    }
}
