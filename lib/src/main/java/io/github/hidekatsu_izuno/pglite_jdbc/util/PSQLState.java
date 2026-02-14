package io.github.hidekatsu_izuno.pglite_jdbc.util;

public final class PSQLState {
    public static final String UNKNOWN_STATE = "99999";
    public static final String UNEXPECTED_ERROR = "99999";
    public static final String CONNECTION_FAILURE = "08006";
    public static final String NOT_IMPLEMENTED = "0A000";
    public static final String INVALID_PARAMETER_VALUE = "22023";
    public static final String NO_DATA = "02000";

    private PSQLState() {
    }
}
