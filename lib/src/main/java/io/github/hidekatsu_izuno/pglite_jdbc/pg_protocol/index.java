package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol;

public final class index {
    public static final class serialize {
        public static io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array startup(
            java.util.Map<String, String> opts
        ) {
            return serializer.startup(opts);
        }

        public static io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array requestSsl() {
            return serializer.requestSsl();
        }

        public static io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array password(
            String password
        ) {
            return serializer.password(password);
        }

        public static io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array sendSASLInitialResponseMessage(
            String mechanism,
            String initialResponse
        ) {
            return serializer.sendSASLInitialResponseMessage(mechanism, initialResponse);
        }

        public static io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array sendSCRAMClientFinalMessage(
            String additionalData
        ) {
            return serializer.sendSCRAMClientFinalMessage(additionalData);
        }

        public static io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array query(
            String text
        ) {
            return serializer.query(text);
        }

        public static io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array parse(
            serializer.ParseOpts query
        ) {
            return serializer.parse(query);
        }

        public static io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array bind(
            serializer.BindOpts config
        ) {
            return serializer.bind(config);
        }

        public static io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array execute(
            serializer.ExecOpts config
        ) {
            return serializer.execute(config);
        }

        public static io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array describe(
            serializer.PortalOpts msg
        ) {
            return serializer.describe(msg);
        }

        public static io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array close(
            serializer.PortalOpts msg
        ) {
            return serializer.close(msg);
        }

        public static io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array flush() {
            return serializer.flush();
        }

        public static io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array sync() {
            return serializer.sync();
        }

        public static io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array end() {
            return serializer.end();
        }

        public static io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array copyData(
            io.github.hidekatsu_izuno.pglite_jdbc.polyfills.ArrayBuffer chunk
        ) {
            return serializer.copyData(chunk);
        }

        public static io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array copyDone() {
            return serializer.copyDone();
        }

        public static io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array copyFail(
            String message
        ) {
            return serializer.copyFail(message);
        }

        public static io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array cancel(
            int processID,
            int secretKey
        ) {
            return serializer.cancel(processID, secretKey);
        }

        private serialize() {
        }
    }

    public static class Parser extends parser.Parser {
    }

    private index() {
    }
}
