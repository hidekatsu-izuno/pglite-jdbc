package io.github.hidekatsu_izuno.pglite_jdbc.util;

import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;

public class ServerErrorMessage {
    private final String message;
    private final String sqlState;
    private final String severity;

    public ServerErrorMessage(String message, String sqlState, String severity) {
        this.message = message;
        this.sqlState = sqlState;
        this.severity = severity;
    }

    public static ServerErrorMessage fromDatabaseError(messages.DatabaseError error) {
        if (error == null) {
            return new ServerErrorMessage(null, PSQLState.UNKNOWN_STATE, null);
        }
        return new ServerErrorMessage(error.message(), error.code(), error.severity());
    }

    public String getSQLState() {
        return sqlState != null ? sqlState : PSQLState.UNKNOWN_STATE;
    }

    public String getMessage() {
        return message;
    }

    public String getSeverity() {
        return severity;
    }

    @Override
    public String toString() {
        if (severity == null || severity.isBlank()) {
            return message;
        }
        return severity + ": " + message;
    }
}
