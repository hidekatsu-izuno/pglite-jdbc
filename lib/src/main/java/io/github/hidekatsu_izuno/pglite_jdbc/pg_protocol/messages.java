package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol;

import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.types.Mode;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array;

public final class messages {
    public static enum MessageName {
        parseComplete,
        bindComplete,
        closeComplete,
        noData,
        portalSuspended,
        replicationStart,
        emptyQuery,
        copyDone,
        copyData,
        rowDescription,
        parameterDescription,
        parameterStatus,
        backendKeyData,
        notification,
        readyForQuery,
        commandComplete,
        dataRow,
        copyInResponse,
        copyOutResponse,
        authenticationOk,
        authenticationMD5Password,
        authenticationCleartextPassword,
        authenticationSASL,
        authenticationSASLContinue,
        authenticationSASLFinal,
        error,
        notice,
    }

    public static interface BackendMessage extends NoticeOrError {
        MessageName name();

        int length();
    }

    public static interface AuthenticationMessage extends BackendMessage {
    }

    private static final class SimpleMessage implements BackendMessage {
        public final MessageName name;
        public final int length;

        private SimpleMessage(MessageName name, int length) {
            this.name = name;
            this.length = length;
        }

        @Override
        public MessageName name() {
            return this.name;
        }

        @Override
        public int length() {
            return this.length;
        }
    }

    public static final BackendMessage parseComplete = new SimpleMessage(
        MessageName.parseComplete,
        5
    );
    public static final BackendMessage bindComplete = new SimpleMessage(
        MessageName.bindComplete,
        5
    );
    public static final BackendMessage closeComplete = new SimpleMessage(
        MessageName.closeComplete,
        5
    );
    public static final BackendMessage noData = new SimpleMessage(
        MessageName.noData,
        5
    );
    public static final BackendMessage portalSuspended = new SimpleMessage(
        MessageName.portalSuspended,
        5
    );
    public static final BackendMessage replicationStart = new SimpleMessage(
        MessageName.replicationStart,
        4
    );
    public static final BackendMessage emptyQuery = new SimpleMessage(
        MessageName.emptyQuery,
        4
    );
    public static final BackendMessage copyDone = new SimpleMessage(
        MessageName.copyDone,
        4
    );

    public static final class AuthenticationOk implements AuthenticationMessage {
        public final MessageName name = MessageName.authenticationOk;
        public final int length;

        public AuthenticationOk(int length) {
            this.length = length;
        }

        @Override
        public MessageName name() {
            return this.name;
        }

        @Override
        public int length() {
            return this.length;
        }
    }

    public static final class AuthenticationCleartextPassword
        implements AuthenticationMessage {
        public final MessageName name = MessageName.authenticationCleartextPassword;
        public final int length;

        public AuthenticationCleartextPassword(int length) {
            this.length = length;
        }

        @Override
        public MessageName name() {
            return this.name;
        }

        @Override
        public int length() {
            return this.length;
        }
    }

    public static final class AuthenticationMD5Password
        implements AuthenticationMessage {
        public final MessageName name = MessageName.authenticationMD5Password;
        public final int length;
        public final Uint8Array salt;

        public AuthenticationMD5Password(int length, Uint8Array salt) {
            this.length = length;
            this.salt = salt;
        }

        @Override
        public MessageName name() {
            return this.name;
        }

        @Override
        public int length() {
            return this.length;
        }
    }

    public static final class AuthenticationSASL
        implements AuthenticationMessage {
        public final MessageName name = MessageName.authenticationSASL;
        public final int length;
        public final String[] mechanisms;

        public AuthenticationSASL(int length, String[] mechanisms) {
            this.length = length;
            this.mechanisms = mechanisms;
        }

        @Override
        public MessageName name() {
            return this.name;
        }

        @Override
        public int length() {
            return this.length;
        }
    }

    public static final class AuthenticationSASLContinue
        implements AuthenticationMessage {
        public final MessageName name = MessageName.authenticationSASLContinue;
        public final int length;
        public final String data;

        public AuthenticationSASLContinue(int length, String data) {
            this.length = length;
            this.data = data;
        }

        @Override
        public MessageName name() {
            return this.name;
        }

        @Override
        public int length() {
            return this.length;
        }
    }

    public static final class AuthenticationSASLFinal
        implements AuthenticationMessage {
        public final MessageName name = MessageName.authenticationSASLFinal;
        public final int length;
        public final String data;

        public AuthenticationSASLFinal(int length, String data) {
            this.length = length;
            this.data = data;
        }

        @Override
        public MessageName name() {
            return this.name;
        }

        @Override
        public int length() {
            return this.length;
        }
    }

    public static interface NoticeOrError {
    }

    public static final class DatabaseError
        extends RuntimeException
        implements NoticeOrError {
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
        public final int length;
        public final MessageName name;

        public DatabaseError(String message, int length, MessageName name) {
            super(message);
            this.length = length;
            this.name = name;
        }
    }

    public static final class CopyDataMessage implements BackendMessage {
        public final MessageName name = MessageName.copyData;
        public final int length;
        public final Uint8Array chunk;

        public CopyDataMessage(int length, Uint8Array chunk) {
            this.length = length;
            this.chunk = chunk;
        }

        @Override
        public MessageName name() {
            return this.name;
        }

        @Override
        public int length() {
            return this.length;
        }
    }

    public static final class CopyResponse implements BackendMessage {
        public final int length;
        public final MessageName name;
        public final boolean binary;
        public final int[] columnTypes;

        public CopyResponse(
            int length,
            MessageName name,
            boolean binary,
            int columnCount
        ) {
            this.length = length;
            this.name = name;
            this.binary = binary;
            this.columnTypes = new int[columnCount];
        }

        @Override
        public MessageName name() {
            return this.name;
        }

        @Override
        public int length() {
            return this.length;
        }
    }

    public static final class Field {
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

    public static final class RowDescriptionMessage implements BackendMessage {
        public final MessageName name = MessageName.rowDescription;
        public final int length;
        public final int fieldCount;
        public final Field[] fields;

        public RowDescriptionMessage(int length, int fieldCount) {
            this.length = length;
            this.fieldCount = fieldCount;
            this.fields = new Field[this.fieldCount];
        }

        @Override
        public MessageName name() {
            return this.name;
        }

        @Override
        public int length() {
            return this.length;
        }
    }

    public static final class ParameterDescriptionMessage
        implements BackendMessage {
        public final MessageName name = MessageName.parameterDescription;
        public final int length;
        public final int parameterCount;
        public final int[] dataTypeIDs;

        public ParameterDescriptionMessage(int length, int parameterCount) {
            this.length = length;
            this.parameterCount = parameterCount;
            this.dataTypeIDs = new int[this.parameterCount];
        }

        @Override
        public MessageName name() {
            return this.name;
        }

        @Override
        public int length() {
            return this.length;
        }
    }

    public static final class ParameterStatusMessage implements BackendMessage {
        public final MessageName name = MessageName.parameterStatus;
        public final int length;
        public final String parameterName;
        public final String parameterValue;

        public ParameterStatusMessage(
            int length,
            String parameterName,
            String parameterValue
        ) {
            this.length = length;
            this.parameterName = parameterName;
            this.parameterValue = parameterValue;
        }

        @Override
        public MessageName name() {
            return this.name;
        }

        @Override
        public int length() {
            return this.length;
        }
    }

    public static final class BackendKeyDataMessage implements BackendMessage {
        public final MessageName name = MessageName.backendKeyData;
        public final int length;
        public final int processID;
        public final int secretKey;

        public BackendKeyDataMessage(int length, int processID, int secretKey) {
            this.length = length;
            this.processID = processID;
            this.secretKey = secretKey;
        }

        @Override
        public MessageName name() {
            return this.name;
        }

        @Override
        public int length() {
            return this.length;
        }
    }

    public static final class NotificationResponseMessage
        implements BackendMessage {
        public final MessageName name = MessageName.notification;
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
        public MessageName name() {
            return this.name;
        }

        @Override
        public int length() {
            return this.length;
        }
    }

    public static final class ReadyForQueryMessage implements BackendMessage {
        public final MessageName name = MessageName.readyForQuery;
        public final int length;
        public final String status;

        public ReadyForQueryMessage(int length, String status) {
            this.length = length;
            this.status = status;
        }

        @Override
        public MessageName name() {
            return this.name;
        }

        @Override
        public int length() {
            return this.length;
        }
    }

    public static final class CommandCompleteMessage implements BackendMessage {
        public final MessageName name = MessageName.commandComplete;
        public final int length;
        public final String text;

        public CommandCompleteMessage(int length, String text) {
            this.length = length;
            this.text = text;
        }

        @Override
        public MessageName name() {
            return this.name;
        }

        @Override
        public int length() {
            return this.length;
        }
    }

    public static final class DataRowMessage implements BackendMessage {
        public final MessageName name = MessageName.dataRow;
        public int length;
        public final String[] fields;
        public final int fieldCount;

        public DataRowMessage(int length, String[] fields) {
            this.length = length;
            this.fields = fields;
            this.fieldCount = fields.length;
        }

        @Override
        public MessageName name() {
            return this.name;
        }

        @Override
        public int length() {
            return this.length;
        }
    }

    public static final class NoticeMessage
        implements BackendMessage {
        public final MessageName name = MessageName.notice;
        public final int length;
        public final String message;
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
        public MessageName name() {
            return this.name;
        }

        @Override
        public int length() {
            return this.length;
        }
    }
    
    private messages() {
    }
}
