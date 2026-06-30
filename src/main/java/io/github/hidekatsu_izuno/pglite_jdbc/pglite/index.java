package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

public class index {
    private index() {}

    public static io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.parser.Parser createProtocolParser() {
        return io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.index.createParser();
    }

    public static class MemoryFS extends io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.memoryfs.MemoryFS {}

    public static pglite create(pglite.PGliteOptions options) {
        return new pglite(options);
    }

    public static interface_.Extension extension(String name) {
        return extensionCatalog.get(name);
    }

    public static java.util.Map<String, interface_.Extension> extensions() {
        return extensionCatalog.getAll();
    }

    public static String uuid() {
        return utils.uuid();
    }

    public static String toPostgresName(String input) {
        return utils.toPostgresName(input);
    }
}
