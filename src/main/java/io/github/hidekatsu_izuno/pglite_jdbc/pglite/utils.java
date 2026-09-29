package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.serializer;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class utils {
    public static final boolean IN_NODE = true;
    public static final String WASM_PREFIX = "/pglite";
    private static final Pattern PARAM_PATTERN = Pattern.compile("\\$([0-9]+)");

    private utils() {}

    public interface MinimalFS {
        String[] readdir(String path);

        void unlink(String path);

        void rmdir(String path);
    }

    public static void rmdirRecursive(MinimalFS fs, String path) {
        try {
            var entries = fs.readdir(path);
            for (var name : entries) {
                if (".".equals(name) || "..".equals(name)) {
                    continue;
                }
                var child = path + "/" + name;
                try {
                    fs.readdir(child);
                    rmdirRecursive(fs, child);
                } catch (RuntimeException e) {
                    fs.unlink(child);
                }
            }
            fs.rmdir(path);
        } catch (RuntimeException e) {
            try {
                fs.unlink(path);
            } catch (RuntimeException ignored) {
                // ignore if already gone
            }
        }
    }

    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    public static String toPostgresName(String input) {
        if (input != null && input.length() >= 2 && input.startsWith("\"") && input.endsWith("\"")) {
            return input.substring(1, input.length() - 1);
        }
        return input == null ? null : input.toLowerCase();
    }

    public static byte[] readFile(String pathOrClasspath) {
        var path = Path.of(pathOrClasspath);
        if (Files.exists(path)) {
            try {
                return Files.readAllBytes(path);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        var resourcePath = pathOrClasspath.startsWith("/")
            ? pathOrClasspath.substring(1)
            : pathOrClasspath;
        var resourceStream = utils.class.getClassLoader().getResourceAsStream(resourcePath);
        if (resourceStream == null && !resourcePath.startsWith(extensionCatalog.RELEASE_RESOURCE_ROOT)) {
            resourceStream = utils.class.getClassLoader().getResourceAsStream(
                extensionCatalog.RELEASE_RESOURCE_ROOT + resourcePath
            );
        }
        try (var in = resourceStream) {
            if (in == null) {
                throw new IllegalArgumentException("Resource not found: " + pathOrClasspath);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] readFile(URL url) {
        if (url == null) {
            throw new IllegalArgumentException("url is null");
        }
        try (InputStream in = url.openStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Promise<byte[]> getFsBundle() {
        return Promise.resolve(readFile("pglite.data"));
    }

    public static Promise<Void> startWasmDownload() {
        return Promise.resolve(null);
    }

    public static Promise<byte[]> instantiateWasm(byte[] imports, byte[] module) {
        if (module != null) {
            return Promise.resolve(module);
        }
        return Promise.resolve(readFile("pglite.wasm"));
    }

    public static Promise<String> formatQuery(
        interface_.PGliteInterface pg,
        String query,
        Object[] params,
        interface_.Transaction tx
    ) {
        if (params == null || params.length == 0) {
            return Promise.resolve(query);
        }
        return base.asPromise(() -> {
            var received = new ArrayList<messages.BackendMessage>();
            var protocolOptions = new interface_.ExecProtocolOptions(false, true, null);
            try {
                var parseOptions = new serializer.ParseOpts();
                parseOptions.text = query;
                pg.execProtocol(serializer.serialize.parse(parseOptions).toByteArray(), protocolOptions).join();
                var describeOptions = new serializer.PortalOpts();
                describeOptions.type = "S";
                received.addAll(pg.execProtocol(
                    serializer.serialize.describe(describeOptions).toByteArray(), protocolOptions).join().messages());
            } finally {
                received.addAll(pg.execProtocol(
                    serializer.serialize.sync().toByteArray(), protocolOptions).join().messages());
            }
            var dataTypeIDs = parse.parseDescribeStatementResults(received);
            var substituted = new StringBuffer();
            var matcher = PARAM_PATTERN.matcher(query);
            while (matcher.find()) {
                matcher.appendReplacement(substituted, Matcher.quoteReplacement("%" + matcher.group(1) + "$L"));
            }
            matcher.appendTail(substituted);
            var placeholders = new ArrayList<String>();
            var values = new Object[params.length + 1];
            values[0] = substituted.toString();
            System.arraycopy(params, 0, values, 1, params.length);
            for (var i = 0; i < params.length; i++) placeholders.add("$" + (i + 2));
            var parameterTypes = new int[dataTypeIDs.length + 1];
            parameterTypes[0] = types.TEXT;
            System.arraycopy(dataTypeIDs, 0, parameterTypes, 1, dataTypeIDs.length);
            var options = new interface_.QueryOptions(null, null, null, null, null, parameterTypes);
            var formatSql = "SELECT format($1, " + String.join(", ", placeholders) + ") AS query";
            var result = tx != null
                ? tx.<Map<String, Object>>query(formatSql, values, options).join()
                : pg.<Map<String, Object>>query(formatSql, values, options).join();
            return (String) result.rows().getFirst().get("query");
        });
    }

    @FunctionalInterface
    public interface DebouncedPromiseFn<A, R> {
        Promise<R> call(List<A> args);
    }

    public static <A, R> DebouncedPromiseFn<A, R> debounceMutex(
        DebouncedPromiseFn<A, R> fn
    ) {
        var next = new java.util.concurrent.atomic.AtomicReference<PendingCall<A, R>>();
        var isRunning = new AtomicBoolean(false);

        Runnable processNext = new Runnable() {
            @Override
            public void run() {
                if (next.get() == null) {
                    isRunning.set(false);
                    return;
                }
                isRunning.set(true);
                var current = next.get();
                next.set(null);
                fn.call(current.args()).then(value -> {
                    current.resolve().run(value);
                    return null;
                }, error -> {
                    current.reject().run(
                        error instanceof Throwable throwable
                            ? throwable
                            : new RuntimeException(String.valueOf(error))
                    );
                    return null;
                }).then(ignored -> {
                    this.run();
                    return null;
                }, ignored -> {
                    this.run();
                    return null;
                });
            }
        };

        return args ->
            new Promise<>((resolve, reject) -> {
                if (next.get() != null) {
                    next.get().resolve().run(null);
                }
                next.set(new PendingCall<>(args, resolve, reject));
                if (!isRunning.get()) {
                    processNext.run();
                }
            });
    }

    private record PendingCall<A, R>(
        List<A> args,
        Promise.Resolve<R> resolve,
        Promise.Reject reject
    ) {}

}
