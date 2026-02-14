package io.github.hidekatsu_izuno.pglite_jdbc.util;

import java.sql.SQLException;

public class PSQLException extends SQLException {
    public PSQLException(String message, String sqlState) {
        super(message, sqlState);
    }

    public PSQLException(String message, String sqlState, Throwable cause) {
        super(message, sqlState, cause);
    }

    public PSQLException(ServerErrorMessage serverErrorMessage, Throwable cause) {
        super(
            serverErrorMessage != null ? serverErrorMessage.toString() : null,
            serverErrorMessage != null ? serverErrorMessage.getSQLState() : PSQLState.UNKNOWN_STATE,
            cause
        );
    }
}
