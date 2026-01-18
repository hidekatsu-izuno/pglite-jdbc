package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol;

import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.buffer_reader.BufferReader;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.AuthenticationCleartextPassword;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.AuthenticationMD5Password;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.AuthenticationMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.AuthenticationOk;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.AuthenticationSASL;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.AuthenticationSASLContinue;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.AuthenticationSASLFinal;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.BackendKeyDataMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.CommandCompleteMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.CopyDataMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.CopyResponse;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.DatabaseError;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.DataRowMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.Field;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.MessageName;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.NoticeMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.NoticeOrError;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.NotificationResponseMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.ParameterDescriptionMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.ParameterStatusMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.ReadyForQueryMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.RowDescriptionMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.types.BufferParameter;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.types.Mode;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.ArrayBuffer;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.DataView;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.TypedArray;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array;
import java.util.ArrayList;
import java.util.HashMap;

public final class parser {
    private static final int CODE_LENGTH = 1;
    private static final int LEN_LENGTH = 4;
    private static final int HEADER_LENGTH = CODE_LENGTH + LEN_LENGTH;

    private static final ArrayBuffer emptyBuffer = new ArrayBuffer(0);

    private static final int DATA_ROW = 0x44;
    private static final int PARSE_COMPLETE = 0x31;
    private static final int BIND_COMPLETE = 0x32;
    private static final int CLOSE_COMPLETE = 0x33;
    private static final int COMMAND_COMPLETE = 0x43;
    private static final int READY_FOR_QUERY = 0x5a;
    private static final int NO_DATA = 0x6e;
    private static final int NOTIFICATION_RESPONSE = 0x41;
    private static final int AUTHENTICATION_RESPONSE = 0x52;
    private static final int PARAMETER_STATUS = 0x53;
    private static final int BACKEND_KEY_DATA = 0x4b;
    private static final int ERROR_MESSAGE = 0x45;
    private static final int NOTICE_MESSAGE = 0x4e;
    private static final int ROW_DESCRIPTION_MESSAGE = 0x54;
    private static final int PARAMETER_DESCRIPTION_MESSAGE = 0x74;
    private static final int PORTAL_SUSPENDED = 0x73;
    private static final int REPLICATION_START = 0x57;
    private static final int EMPTY_QUERY = 0x49;
    private static final int COPY_IN = 0x47;
    private static final int COPY_OUT = 0x48;
    private static final int COPY_DONE = 0x63;
    private static final int COPY_DATA = 0x64;

    public static interface MessageCallback {
        void apply(NoticeOrError msg);
    }

    public static class Parser {
        private DataView bufferView = new DataView(emptyBuffer);
        private int bufferRemainingLength = 0;
        private int bufferOffset = 0;
        private BufferReader reader = new BufferReader(0);

        public void parse(BufferParameter buffer, MessageCallback callback) {
            this.mergeBuffer(this.normalizeBuffer(buffer));
            var bufferFullLength = this.bufferOffset + this.bufferRemainingLength;
            var offset = this.bufferOffset;
            while (offset + HEADER_LENGTH <= bufferFullLength) {
                var code = this.bufferView.getUint8(offset) & 0xFF;
                var length = this.bufferView.getUint32(offset + CODE_LENGTH, false);
                var fullMessageLength = CODE_LENGTH + length;
                if (fullMessageLength + offset <= bufferFullLength && length > 0) {
                    var message = this.handlePacket(
                        offset + HEADER_LENGTH,
                        code,
                        length,
                        this.bufferView.buffer
                    );
                    callback.apply(message);
                    offset += fullMessageLength;
                } else {
                    break;
                }
            }
            if (offset == bufferFullLength) {
                this.bufferView = new DataView(emptyBuffer);
                this.bufferRemainingLength = 0;
                this.bufferOffset = 0;
            } else {
                this.bufferRemainingLength = bufferFullLength - offset;
                this.bufferOffset = offset;
            }
        }

        private ArrayBuffer normalizeBuffer(BufferParameter buffer) {
            if (buffer instanceof TypedArray) {
                var view = (TypedArray) buffer;
                return view.getBuffer()
                    .slice(
                        view.getByteOffset(),
                        view.getByteOffset() + view.getByteLength()
                    );
            }
            return (ArrayBuffer) buffer;
        }

        private void mergeBuffer(ArrayBuffer buffer) {
            if (this.bufferRemainingLength > 0) {
                var newLength = this.bufferRemainingLength + buffer.byteLength;
                var newFullLength = newLength + this.bufferOffset;
                if (newFullLength > this.bufferView.buffer.byteLength) {
                    ArrayBuffer newBuffer;
                    if (
                        newLength <= this.bufferView.buffer.byteLength &&
                        this.bufferOffset >= this.bufferRemainingLength
                    ) {
                        newBuffer = this.bufferView.buffer;
                    } else {
                        var newBufferLength = this.bufferView.buffer.byteLength * 2;
                        while (newLength >= newBufferLength) {
                            newBufferLength *= 2;
                        }
                        newBuffer = new ArrayBuffer(newBufferLength);
                    }
                    new Uint8Array(newBuffer).set(
                        new Uint8Array(
                            this.bufferView.buffer,
                            this.bufferOffset,
                            this.bufferRemainingLength
                        )
                    );
                    this.bufferView = new DataView(newBuffer);
                    this.bufferOffset = 0;
                }

                new Uint8Array(this.bufferView.buffer).set(
                    new Uint8Array(buffer),
                    this.bufferOffset + this.bufferRemainingLength
                );
                this.bufferRemainingLength = newLength;
            } else {
                this.bufferView = new DataView(buffer);
                this.bufferOffset = 0;
                this.bufferRemainingLength = buffer.byteLength;
            }
        }

        private NoticeOrError handlePacket(
            int offset,
            int code,
            int length,
            ArrayBuffer bytes
        ) {
            switch (code) {
                case BIND_COMPLETE:
                    return messages.bindComplete;
                case PARSE_COMPLETE:
                    return messages.parseComplete;
                case CLOSE_COMPLETE:
                    return messages.closeComplete;
                case NO_DATA:
                    return messages.noData;
                case PORTAL_SUSPENDED:
                    return messages.portalSuspended;
                case COPY_DONE:
                    return messages.copyDone;
                case REPLICATION_START:
                    return messages.replicationStart;
                case EMPTY_QUERY:
                    return messages.emptyQuery;
                case DATA_ROW:
                    return this.parseDataRowMessage(offset, length, bytes);
                case COMMAND_COMPLETE:
                    return this.parseCommandCompleteMessage(offset, length, bytes);
                case READY_FOR_QUERY:
                    return this.parseReadyForQueryMessage(offset, length, bytes);
                case NOTIFICATION_RESPONSE:
                    return this.parseNotificationMessage(offset, length, bytes);
                case AUTHENTICATION_RESPONSE:
                    return this.parseAuthenticationResponse(offset, length, bytes);
                case PARAMETER_STATUS:
                    return this.parseParameterStatusMessage(offset, length, bytes);
                case BACKEND_KEY_DATA:
                    return this.parseBackendKeyData(offset, length, bytes);
                case ERROR_MESSAGE:
                    return this.parseErrorMessage(offset, length, bytes, MessageName.error);
                case NOTICE_MESSAGE:
                    return this.parseErrorMessage(
                        offset,
                        length,
                        bytes,
                        MessageName.notice
                    );
                case ROW_DESCRIPTION_MESSAGE:
                    return this.parseRowDescriptionMessage(offset, length, bytes);
                case PARAMETER_DESCRIPTION_MESSAGE:
                    return this.parseParameterDescriptionMessage(offset, length, bytes);
                case COPY_IN:
                    return this.parseCopyInMessage(offset, length, bytes);
                case COPY_OUT:
                    return this.parseCopyOutMessage(offset, length, bytes);
                case COPY_DATA:
                    return this.parseCopyData(offset, length, bytes);
                default:
                    return new DatabaseError(
                        "received invalid response: " + Integer.toHexString(code),
                        length,
                        MessageName.error
                    );
            }
        }

        private ReadyForQueryMessage parseReadyForQueryMessage(
            int offset,
            int length,
            ArrayBuffer bytes
        ) {
            this.reader.setBuffer(offset, bytes);
            var status = this.reader.string(1);
            return new ReadyForQueryMessage(length, status);
        }

        private CommandCompleteMessage parseCommandCompleteMessage(
            int offset,
            int length,
            ArrayBuffer bytes
        ) {
            this.reader.setBuffer(offset, bytes);
            var text = this.reader.cstring();
            return new CommandCompleteMessage(length, text);
        }

        private CopyDataMessage parseCopyData(int offset, int length, ArrayBuffer bytes) {
            var chunk = bytes.slice(offset, offset + (length - 4));
            return new CopyDataMessage(length, new Uint8Array(chunk));
        }

        private CopyResponse parseCopyInMessage(
            int offset,
            int length,
            ArrayBuffer bytes
        ) {
            return this.parseCopyMessage(
                offset,
                length,
                bytes,
                MessageName.copyInResponse
            );
        }

        private CopyResponse parseCopyOutMessage(
            int offset,
            int length,
            ArrayBuffer bytes
        ) {
            return this.parseCopyMessage(
                offset,
                length,
                bytes,
                MessageName.copyOutResponse
            );
        }

        private CopyResponse parseCopyMessage(
            int offset,
            int length,
            ArrayBuffer bytes,
            MessageName messageName
        ) {
            this.reader.setBuffer(offset, bytes);
            var isBinary = this.reader.byte_() != 0;
            var columnCount = this.reader.int16();
            var message = new CopyResponse(length, messageName, isBinary, columnCount);
            for (var i = 0; i < columnCount; i++) {
                message.columnTypes[i] = this.reader.int16();
            }
            return message;
        }

        private NotificationResponseMessage parseNotificationMessage(
            int offset,
            int length,
            ArrayBuffer bytes
        ) {
            this.reader.setBuffer(offset, bytes);
            var processId = this.reader.int32();
            var channel = this.reader.cstring();
            var payload = this.reader.cstring();
            return new NotificationResponseMessage(length, processId, channel, payload);
        }

        private RowDescriptionMessage parseRowDescriptionMessage(
            int offset,
            int length,
            ArrayBuffer bytes
        ) {
            this.reader.setBuffer(offset, bytes);
            var fieldCount = this.reader.int16();
            var message = new RowDescriptionMessage(length, fieldCount);
            for (var i = 0; i < fieldCount; i++) {
                message.fields[i] = this.parseField();
            }
            return message;
        }

        private Field parseField() {
            var name = this.reader.cstring();
            var tableID = this.reader.int32();
            var columnID = this.reader.int16();
            var dataTypeID = this.reader.int32();
            var dataTypeSize = this.reader.int16();
            var dataTypeModifier = this.reader.int32();
            var mode = this.reader.int16() == 0 ? Mode.text : Mode.binary;
            return new Field(
                name,
                tableID,
                columnID,
                dataTypeID,
                dataTypeSize,
                dataTypeModifier,
                mode
            );
        }

        private ParameterDescriptionMessage parseParameterDescriptionMessage(
            int offset,
            int length,
            ArrayBuffer bytes
        ) {
            this.reader.setBuffer(offset, bytes);
            var parameterCount = this.reader.int16();
            var message = new ParameterDescriptionMessage(length, parameterCount);
            for (var i = 0; i < parameterCount; i++) {
                message.dataTypeIDs[i] = this.reader.int32();
            }
            return message;
        }

        private DataRowMessage parseDataRowMessage(
            int offset,
            int length,
            ArrayBuffer bytes
        ) {
            this.reader.setBuffer(offset, bytes);
            var fieldCount = this.reader.int16();
            var fields = new String[fieldCount];
            for (var i = 0; i < fieldCount; i++) {
                var len = this.reader.int32();
                fields[i] = len == -1 ? null : this.reader.string(len);
            }
            return new DataRowMessage(length, fields);
        }

        private ParameterStatusMessage parseParameterStatusMessage(
            int offset,
            int length,
            ArrayBuffer bytes
        ) {
            this.reader.setBuffer(offset, bytes);
            var name = this.reader.cstring();
            var value = this.reader.cstring();
            return new ParameterStatusMessage(length, name, value);
        }

        private BackendKeyDataMessage parseBackendKeyData(
            int offset,
            int length,
            ArrayBuffer bytes
        ) {
            this.reader.setBuffer(offset, bytes);
            var processID = this.reader.int32();
            var secretKey = this.reader.int32();
            return new BackendKeyDataMessage(length, processID, secretKey);
        }

        private AuthenticationMessage parseAuthenticationResponse(
            int offset,
            int length,
            ArrayBuffer bytes
        ) {
            this.reader.setBuffer(offset, bytes);
            var code = this.reader.int32();
            switch (code) {
                case 0:
                    return new AuthenticationOk(length);
                case 3:
                    return new AuthenticationCleartextPassword(length);
                case 5:
                    return new AuthenticationMD5Password(length, this.reader.bytes(4));
                case 10:
                    var mechanisms = new ArrayList<String>();
                    while (true) {
                        var mechanism = this.reader.cstring();
                        if (mechanism.length() == 0) {
                            return new AuthenticationSASL(
                                length,
                                mechanisms.toArray(new String[0])
                            );
                        }
                        mechanisms.add(mechanism);
                    }
                case 11:
                    return new AuthenticationSASLContinue(
                        length,
                        this.reader.string(length - 8)
                    );
                case 12:
                    return new AuthenticationSASLFinal(
                        length,
                        this.reader.string(length - 8)
                    );
                default:
                    throw new RuntimeException(
                        "Unknown authenticationOk message type " + code
                    );
            }
        }

        private NoticeOrError parseErrorMessage(
            int offset,
            int length,
            ArrayBuffer bytes,
            MessageName name
        ) {
            this.reader.setBuffer(offset, bytes);
            var fields = new HashMap<String, String>();
            var fieldType = this.reader.string(1);
            while (!"\0".equals(fieldType)) {
                fields.put(fieldType, this.reader.cstring());
                fieldType = this.reader.string(1);
            }

            var messageValue = fields.get("M");
            if (name == MessageName.notice) {
                var message = new NoticeMessage(length, messageValue);
                this.populateNoticeFields(message, fields);
                return message;
            }
            var message = new DatabaseError(messageValue, length, name);
            this.populateErrorFields(message, fields);
            return message;
        }

        private void populateNoticeFields(
            NoticeMessage message,
            HashMap<String, String> fields
        ) {
            message.severity = fields.get("S");
            message.code = fields.get("C");
            message.detail = fields.get("D");
            message.hint = fields.get("H");
            message.position = fields.get("P");
            message.internalPosition = fields.get("p");
            message.internalQuery = fields.get("q");
            message.where = fields.get("W");
            message.schema = fields.get("s");
            message.table = fields.get("t");
            message.column = fields.get("c");
            message.dataType = fields.get("d");
            message.constraint = fields.get("n");
            message.file = fields.get("F");
            message.line = fields.get("L");
            message.routine = fields.get("R");
        }

        private void populateErrorFields(
            DatabaseError message,
            HashMap<String, String> fields
        ) {
            message.severity = fields.get("S");
            message.code = fields.get("C");
            message.detail = fields.get("D");
            message.hint = fields.get("H");
            message.position = fields.get("P");
            message.internalPosition = fields.get("p");
            message.internalQuery = fields.get("q");
            message.where = fields.get("W");
            message.schema = fields.get("s");
            message.table = fields.get("t");
            message.column = fields.get("c");
            message.dataType = fields.get("d");
            message.constraint = fields.get("n");
            message.file = fields.get("F");
            message.line = fields.get("L");
            message.routine = fields.get("R");
        }
    }

    private parser() {
    }
}
