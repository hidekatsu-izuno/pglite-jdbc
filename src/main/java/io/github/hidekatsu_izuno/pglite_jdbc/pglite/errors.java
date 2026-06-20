package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;

public class errors {
    public static final class PGliteError extends RuntimeException {
        public final messages.DatabaseError error;
        public final String query;
        public final Object[] params;
        public final interface_.QueryOptions queryOptions;

        public PGliteError(
            messages.DatabaseError error,
            String query,
            Object[] params,
            interface_.QueryOptions queryOptions
        ) {
            super(error.getMessage(), error);
            this.error = error;
            this.query = query;
            this.params = params;
            this.queryOptions = queryOptions;
        }
    }

    private errors() {}

    public static RuntimeException makePGliteError(
        messages.DatabaseError e,
        String query,
        Object[] params,
        interface_.QueryOptions options
    ) {
        return new PGliteError(e, query, params, options);
    }
}
