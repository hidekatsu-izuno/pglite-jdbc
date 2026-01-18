package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;

public class NoticeMessage implements BackendMessage, NoticeOrError {
    public final int length;
    public final String message;
    public final String name = "notice";
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

    public NoticeMessage(int length, String message) {
        this.length = length;
        this.message = message;
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
