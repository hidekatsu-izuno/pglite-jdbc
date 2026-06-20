package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol;

/**
 * Public entry points equivalent to pg-protocol/index.ts exports.
 */
public final class index {
    private index() {
    }

    public static parser.Parser createParser() {
        return new parser.Parser();
    }
}
