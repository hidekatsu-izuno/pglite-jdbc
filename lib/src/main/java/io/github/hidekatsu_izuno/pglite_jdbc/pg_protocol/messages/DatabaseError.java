package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;

public class DatabaseError extends RuntimeException implements BackendMessage, NoticeOrError {
    public final int length;
    public final String name;
    public String severity;
    public String code;
    public String detail;
    public String hint;
    public String position;
    public String internalPosition;
    public String internalQuery;
    public String where;
    public String schema;
    public String table;
    public String column;
    public String dataType;
    public String constraint;
    public String file;
    public String line;
    public String routine;

    public DatabaseError(String message, int length, String name) {
        super(message);
        this.length = length;
        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public int getLength() {
        return this.length;
    }
}
