package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class utils {
    public static final boolean IN_NODE = true;
    private static final Pattern PARAM_PATTERN = Pattern.compile("\\$([0-9]+)");

    private utils() {}

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
        try (InputStream in = utils.class.getClassLoader().getResourceAsStream(pathOrClasspath)) {
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
        var out = new StringBuffer();
        Matcher matcher = PARAM_PATTERN.matcher(query);
        while (matcher.find()) {
            var index = Integer.parseInt(matcher.group(1)) - 1;
            var value = index >= 0 && index < params.length ? params[index] : null;
            matcher.appendReplacement(out, Matcher.quoteReplacement(toSqlLiteral(value)));
        }
        matcher.appendTail(out);
        return Promise.resolve(out.toString());
    }

    public interface DebouncedPromiseFn<A, R> {
        Promise<R> call(A args);
    }

    public static <A, R> DebouncedPromiseFn<A, R> debounceMutex(
        DebouncedPromiseFn<A, R> fn
    ) {
        var queue = new ArrayDeque<PendingCall<A, R>>();
        var running = new AtomicBoolean(false);

        return args ->
            new Promise<>((resolve, reject) -> {
                queue.addLast(new PendingCall<>(args, resolve, reject));
                if (running.compareAndSet(false, true)) {
                    runQueued(fn, queue, running);
                }
            });
    }

    private static <A, R> void runQueued(
        DebouncedPromiseFn<A, R> fn,
        ArrayDeque<PendingCall<A, R>> queue,
        AtomicBoolean running
    ) {
        var call = queue.pollLast();
        queue.clear();
        if (call == null) {
            running.set(false);
            return;
        }
        fn.call(call.args()).then(value -> {
            call.resolve().run(value);
            runQueued(fn, queue, running);
            return null;
        }, error -> {
            call.reject().run(error instanceof Throwable ? (Throwable) error : new RuntimeException(String.valueOf(error)));
            runQueued(fn, queue, running);
            return null;
        });
    }

    private record PendingCall<A, R>(
        A args,
        Promise.Resolve<R> resolve,
        Promise.Reject reject
    ) {}

    private static String toSqlLiteral(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        var text = String.valueOf(value).replace("'", "''");
        return "'" + text + "'";
    }
}
