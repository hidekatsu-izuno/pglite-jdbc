package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.DatabaseError;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.utils.QueryOptions;

public final class errors {
    public static class PGliteError extends DatabaseError {
        public String query;
        public Object[] params;
        public QueryOptions queryOptions;

        public PGliteError(String message, int length, String name) {
            super(message, length, name);
        }

        public PGliteError(DatabaseError error) {
            super(error.getMessage(), error.length, error.name);
            this.severity = error.severity;
            this.code = error.code;
            this.detail = error.detail;
            this.hint = error.hint;
            this.position = error.position;
            this.internalPosition = error.internalPosition;
            this.internalQuery = error.internalQuery;
            this.where = error.where;
            this.schema = error.schema;
            this.table = error.table;
            this.column = error.column;
            this.dataType = error.dataType;
            this.constraint = error.constraint;
            this.file = error.file;
            this.line = error.line;
            this.routine = error.routine;
            var cause = error.getCause();
            if (cause != null) {
                initCause(cause);
            }
            setStackTrace(error.getStackTrace());
        }
    }

    public static final class MakePGliteErrorData {
        public DatabaseError e;
        public String query;
        public Object[] params;
        public QueryOptions options;
    }

    public static PGliteError makePGliteError(MakePGliteErrorData data) {
        var pgError = data.e instanceof PGliteError
            ? (PGliteError) data.e
            : new PGliteError(data.e);
        pgError.query = data.query;
        pgError.params = data.params;
        pgError.queryOptions = data.options;
        return pgError;
    }

    private errors() {
    }
}
