package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol;

import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.types.Mode;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array;

public final class messages {
    public static interface BackendMessage {
        public String name();
        public int length();
    }

    public static final BackendMessage parseComplete = new BackendMessage() {
        @Override
        public String name() {
            return "parseComplete";
        }

        @Override
        public int length() {
            return 5;
        }
    };

    public static final BackendMessage bindComplete = new BackendMessage() {
        @Override
        public String name() {
            return "bindComplete";
        }

        @Override
        public int length() {
            return 5;
        }
    };

    public static final BackendMessage closeComplete = new BackendMessage() {
        @Override
        public String name() {
            return "closeComplete";
        }

        @Override
        public int length() {
            return 5;
        }
    };

    public static final BackendMessage noData = new BackendMessage() {
        @Override
        public String name() {
            return "noData";
        }

        @Override
        public int length() {
            return 5;
        }
    };

    public static final BackendMessage portalSuspended = new BackendMessage() {
        @Override
        public String name() {
            return "portalSuspended";
        }

        @Override
        public int length() {
            return 5;
        }
    };

    public static final BackendMessage replicationStart = new BackendMessage() {
        @Override
        public String name() {
            return "replicationStart";
        }

        @Override
        public int length() {
            return 4;
        }
    };

    public static final BackendMessage emptyQuery = new BackendMessage() {
        @Override
        public String name() {
            return "emptyQuery";
        }

        @Override
        public int length() {
            return 4;
        }
    };

    public static final BackendMessage copyDone = new BackendMessage() {
        @Override
        public String name() {
            return "copyDone";
        }

        @Override
        public int length() {
            return 4;
        }
    };

    public static class AuthenticationOk implements BackendMessage {
        public final int length;

        public AuthenticationOk(int length) {
            this.length = length;
        }

        @Override
        public String name() {
            return "authenticationOk";
        }

        @Override
        public int length() {
            return this.length;
        }
    }

    public static class AuthenticationCleartextPassword implements BackendMessage {
        public final int length;

        public AuthenticationCleartextPassword(int length) {
            this.length = length;
        }

        @Override
        public String name() {
            return "authenticationCleartextPassword";
        }

        @Override
        public int length() {
            return this.length;
        }
    }

    public static class AuthenticationMD5Password implements BackendMessage {
        public final int length;
        public final Uint8Array salt;

        public AuthenticationMD5Password(int length, Uint8Array salt) {
            this.length = length;
            this.salt = salt;
        }

        @Override
        public String name() {
            return "authenticationMD5Password";
        }

        @Override
        public int length() {
            return this.length;
        }
    }

    public static class AuthenticationSASL implements BackendMessage {
        public final int length;
        public final String[] mechanisms;

        public AuthenticationSASL(int length, String[] mechanisms) {
            this.length = length;
            this.mechanisms = mechanisms;
        }

        @Override
        public String name() {
            return "authenticationSASL";
        }

        @Override
        public int length() {
            return this.length;
        }
    }

    public static class AuthenticationSASLContinue implements BackendMessage {
        public final int length;
        public final String data;

        public AuthenticationSASLContinue(int length, String data) {
            this.length = length;
            this.data = data;
        }

        @Override
        public String name() {
            return "authenticationSASLContinue";
        }

        @Override
        public int length() {
            return this.length;
        }
    }

    public static class AuthenticationSASLFinal implements BackendMessage {
        public final int length;
        public final String data;

        public AuthenticationSASLFinal(int length, String data) {
            this.length = length;
            this.data = data;
        }

        @Override
        public String name() {
            return "authenticationSASLFinal";
        }

        @Override
        public int length() {
            return this.length;
        }
    }

    public static interface NoticeOrError {
        public String message();
        public String severity();
        public String code();
        public String detail();
        public String hint();
        public String position();
        public String internalPosition();
        public String internalQuery();
        public String where();
        public String schema();
        public String table();
        public String column();
        public String dataType();
        public String constraint();
        public String file();
        public String line();
        public String routine();
    }

    public static class DatabaseError extends RuntimeException
        implements BackendMessage, NoticeOrError {
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
        public String name() {
            return this.name;
        }

        @Override
        public int length() {
            return this.length;
        }

        @Override
        public String message() {
            return this.getMessage();
        }

        @Override
        public String severity() {
            return this.severity;
        }

        @Override
        public String code() {
            return this.code;
        }

        @Override
        public String detail() {
            return this.detail;
        }

        @Override
        public String hint() {
            return this.hint;
        }

        @Override
        public String position() {
            return this.position;
        }

        @Override
        public String internalPosition() {
            return this.internalPosition;
        }

        @Override
        public String internalQuery() {
            return this.internalQuery;
        }

        @Override
        public String where() {
            return this.where;
        }

        @Override
        public String schema() {
            return this.schema;
        }

        @Override
        public String table() {
            return this.table;
        }

        @Override
        public String column() {
            return this.column;
        }

        @Override
        public String dataType() {
            return this.dataType;
        }

        @Override
        public String constraint() {
            return this.constraint;
        }

        @Override
        public String file() {
            return this.file;
        }

        @Override
        public String line() {
            return this.line;
        }

        @Override
        public String routine() {
            return this.routine;
        }
    }

    public static class CopyDataMessage implements BackendMessage {
        public final int length;
        public final Uint8Array chunk;

        public CopyDataMessage(int length, Uint8Array chunk) {
            this.length = length;
            this.chunk = chunk;
        }

        @Override
        public String name() {
            return "copyData";
        }

        @Override
        public int length() {
            return this.length;
        }
    }

    public static class CopyResponse implements BackendMessage {
        public final int length;
        public final String name;
        public final boolean binary;
        public final int[] columnTypes;

        public CopyResponse(int length, String name, boolean binary, int columnCount) {
            this.length = length;
            this.name = name;
            this.binary = binary;
            this.columnTypes = new int[columnCount];
        }

        @Override
        public String name() {
            return this.name;
        }

        @Override
        public int length() {
            return this.length;
        }
    }

    public static class Field {
        public final String name;
        public final int tableID;
        public final int columnID;
        public final int dataTypeID;
        public final int dataTypeSize;
        public final int dataTypeModifier;
        public final Mode format;

        public Field(
            String name,
            int tableID,
            int columnID,
            int dataTypeID,
            int dataTypeSize,
            int dataTypeModifier,
            Mode format
        ) {
            this.name = name;
            this.tableID = tableID;
            this.columnID = columnID;
            this.dataTypeID = dataTypeID;
            this.dataTypeSize = dataTypeSize;
            this.dataTypeModifier = dataTypeModifier;
            this.format = format;
        }
    }

    public static class RowDescriptionMessage implements BackendMessage {
        public final int length;
        public final int fieldCount;
        public final String name = "rowDescription";
        public final Field[] fields;

        public RowDescriptionMessage(int length, int fieldCount) {
            this.length = length;
            this.fieldCount = fieldCount;
            this.fields = new Field[this.fieldCount];
        }

        @Override
        public String name() {
            return this.name;
        }

        @Override
        public int length() {
            return this.length;
        }
    }

    public static class ParameterDescriptionMessage implements BackendMessage {
        public final int length;
        public final int parameterCount;
        public final String name = "parameterDescription";
        public final int[] dataTypeIDs;

        public ParameterDescriptionMessage(int length, int parameterCount) {
            this.length = length;
            this.parameterCount = parameterCount;
            this.dataTypeIDs = new int[this.parameterCount];
        }

        @Override
        public String name() {
            return this.name;
        }

        @Override
        public int length() {
            return this.length;
        }
    }

    public static class ParameterStatusMessage implements BackendMessage {
        public final int length;
        public final String parameterName;
        public final String parameterValue;

        public ParameterStatusMessage(int length, String parameterName, String parameterValue) {
            this.length = length;
            this.parameterName = parameterName;
            this.parameterValue = parameterValue;
        }

        @Override
        public String name() {
            return "parameterStatus";
        }

        @Override
        public int length() {
            return this.length;
        }
    }

    public static class BackendKeyDataMessage implements BackendMessage {
        public final int length;
        public final int processID;
        public final int secretKey;

        public BackendKeyDataMessage(int length, int processID, int secretKey) {
            this.length = length;
            this.processID = processID;
            this.secretKey = secretKey;
        }

        @Override
        public String name() {
            return "backendKeyData";
        }

        @Override
        public int length() {
            return this.length;
        }
    }

    public static class NotificationResponseMessage implements BackendMessage {
        public final int length;
        public final int processId;
        public final String channel;
        public final String payload;

        public NotificationResponseMessage(
            int length,
            int processId,
            String channel,
            String payload
        ) {
            this.length = length;
            this.processId = processId;
            this.channel = channel;
            this.payload = payload;
        }

        @Override
        public String name() {
            return "notification";
        }

        @Override
        public int length() {
            return this.length;
        }
    }

    public static class ReadyForQueryMessage implements BackendMessage {
        public final int length;
        public final String status;

        public ReadyForQueryMessage(int length, String status) {
            this.length = length;
            this.status = status;
        }

        @Override
        public String name() {
            return "readyForQuery";
        }

        @Override
        public int length() {
            return this.length;
        }
    }

    public static class CommandCompleteMessage implements BackendMessage {
        public final int length;
        public final String text;

        public CommandCompleteMessage(int length, String text) {
            this.length = length;
            this.text = text;
        }

        @Override
        public String name() {
            return "commandComplete";
        }

        @Override
        public int length() {
            return this.length;
        }
    }

    public static class DataRowMessage implements BackendMessage {
        public int length;
        public String[] fields;
        public final int fieldCount;
        public final String name = "dataRow";

        public DataRowMessage(int length, String[] fields) {
            this.length = length;
            this.fields = fields;
            this.fieldCount = fields.length;
        }

        @Override
        public String name() {
            return this.name;
        }

        @Override
        public int length() {
            return this.length;
        }
    }

    public static class NoticeMessage implements BackendMessage, NoticeOrError {
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
        public String name() {
            return this.name;
        }

        @Override
        public int length() {
            return this.length;
        }

        @Override
        public String message() {
            return this.message;
        }

        @Override
        public String severity() {
            return this.severity;
        }

        @Override
        public String code() {
            return this.code;
        }

        @Override
        public String detail() {
            return this.detail;
        }

        @Override
        public String hint() {
            return this.hint;
        }

        @Override
        public String position() {
            return this.position;
        }

        @Override
        public String internalPosition() {
            return this.internalPosition;
        }

        @Override
        public String internalQuery() {
            return this.internalQuery;
        }

        @Override
        public String where() {
            return this.where;
        }

        @Override
        public String schema() {
            return this.schema;
        }

        @Override
        public String table() {
            return this.table;
        }

        @Override
        public String column() {
            return this.column;
        }

        @Override
        public String dataType() {
            return this.dataType;
        }

        @Override
        public String constraint() {
            return this.constraint;
        }

        @Override
        public String file() {
            return this.file;
        }

        @Override
        public String line() {
            return this.line;
        }

        @Override
        public String routine() {
            return this.routine;
        }
    }

    private messages() {
    }
}
