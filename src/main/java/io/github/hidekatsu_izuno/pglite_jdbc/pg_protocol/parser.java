package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol;

import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.buffer_reader.BufferReader;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.AuthenticationCleartextPassword;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.AuthenticationMD5Password;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.AuthenticationOk;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.AuthenticationSASL;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.AuthenticationSASLContinue;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.AuthenticationSASLFinal;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.BackendKeyDataMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.BackendMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.CommandCompleteMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.CopyDataMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.CopyResponse;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.DatabaseError;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.DataRowMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.Field;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.NoticeMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.NotificationResponseMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.ParameterDescriptionMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.ParameterStatusMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.ReadyForQueryMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.RowDescriptionMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.types.Mode;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.ArrayBuffer;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.DataView;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.TypedArray;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array;
import java.util.ArrayList;
import java.util.HashMap;

public final class parser {
    // every message is prefixed with a single bye
    private static int CODE_LENGTH = 1;
    // every message has an int32 length which includes itself but does
    // NOT include the code in the length
    private static int LEN_LENGTH = 4;

    private static int HEADER_LENGTH = CODE_LENGTH + LEN_LENGTH;

    public static final class Packet {
        public int code;
        public ArrayBuffer packet;
    }

    private static ArrayBuffer emptyBuffer = new ArrayBuffer(0);

    private static final int MessageCodes_DataRow = 0x44; // D
    private static final int MessageCodes_ParseComplete = 0x31; // 1
    private static final int MessageCodes_BindComplete = 0x32; // 2
    private static final int MessageCodes_CloseComplete = 0x33; // 3
    private static final int MessageCodes_CommandComplete = 0x43; // C
    private static final int MessageCodes_ReadyForQuery = 0x5a; // Z
    private static final int MessageCodes_NoData = 0x6e; // n
    private static final int MessageCodes_NotificationResponse = 0x41; // A
    private static final int MessageCodes_AuthenticationResponse = 0x52; // R
    private static final int MessageCodes_ParameterStatus = 0x53; // S
    private static final int MessageCodes_BackendKeyData = 0x4b; // K
    private static final int MessageCodes_ErrorMessage = 0x45; // E
    private static final int MessageCodes_NoticeMessage = 0x4e; // N
    private static final int MessageCodes_RowDescriptionMessage = 0x54; // T
    private static final int MessageCodes_ParameterDescriptionMessage = 0x74; // t
    private static final int MessageCodes_PortalSuspended = 0x73; // s
    private static final int MessageCodes_ReplicationStart = 0x57; // W
    private static final int MessageCodes_EmptyQuery = 0x49; // I
    private static final int MessageCodes_CopyIn = 0x47; // G
    private static final int MessageCodes_CopyOut = 0x48; // H
    private static final int MessageCodes_CopyDone = 0x63; // c
    private static final int MessageCodes_CopyData = 0x64; // d

    public static interface MessageCallback {
        void onMessage(BackendMessage msg);
    }

    public static final class Parser {
        private DataView bufferView = new DataView(emptyBuffer);
        private int bufferRemainingLength = 0;
        private int bufferOffset = 0;
        private BufferReader reader = new BufferReader(0);

        public void parse(TypedArray buffer, MessageCallback callback) {
            parse(((TypedArray) buffer)
                .getBuffer()
                .slice(
                    ((TypedArray) buffer).getByteOffset(),
                    ((TypedArray) buffer).getByteOffset() +
                        ((TypedArray) buffer).getByteLength()
                ), callback);
        }

        public void parse(ArrayBuffer buffer, MessageCallback callback) {
            this.mergeBuffer(buffer);
            var bufferFullLength = this.bufferOffset + this.bufferRemainingLength;
            var offset = this.bufferOffset;
            while (offset + HEADER_LENGTH <= bufferFullLength) {
                // code is 1 byte long - it identifies the message type
                var code = this.bufferView.getUint8(offset) & 0xFF;
                // length is 1 Uint32BE - it is the length of the message EXCLUDING the code
                var length = this.bufferView.getUint32(offset + CODE_LENGTH, false);
                var fullMessageLength = CODE_LENGTH + length;
                if (fullMessageLength + offset <= bufferFullLength && length > 0) {
                    var message = this.handlePacket(
                        offset + HEADER_LENGTH,
                        code,
                        length,
                        this.bufferView.buffer
                    );
                    callback.onMessage(message);
                    offset += fullMessageLength;
                } else {
                    break;
                }
            }
            if (offset == bufferFullLength) {
                // No more use for the buffer
                this.bufferView = new DataView(emptyBuffer);
                this.bufferRemainingLength = 0;
                this.bufferOffset = 0;
            } else {
                // Adjust the cursors of remainingBuffer
                this.bufferRemainingLength = bufferFullLength - offset;
                this.bufferOffset = offset;
            }
        }

        private void mergeBuffer(ArrayBuffer buffer) {
            if (this.bufferRemainingLength > 0) {
                var newLength = this.bufferRemainingLength + buffer.byteLength;
                var newFullLength = newLength + this.bufferOffset;
                if (newFullLength > this.bufferView.buffer.byteLength) {
                    // We can't concat the new buffer with the remaining one
                    ArrayBuffer newBuffer;
                    if (
                        newLength <= this.bufferView.buffer.byteLength &&
                        this.bufferOffset >= this.bufferRemainingLength
                    ) {
                        // We can move the relevant part to the beginning of the buffer instead of allocating a new buffer
                        newBuffer = this.bufferView.buffer;
                    } else {
                        // Allocate a new larger buffer
                        var newBufferLength = this.bufferView.buffer.byteLength * 2;
                        while (newLength >= newBufferLength) {
                            newBufferLength *= 2;
                        }
                        newBuffer = new ArrayBuffer(newBufferLength);
                    }
                    // Move the remaining buffer to the new one
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

                // Concat the new buffer with the remaining one
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

        private BackendMessage handlePacket(
            int offset,
            int code,
            int length,
            ArrayBuffer bytes
        ) {
            switch (code) {
                case MessageCodes_BindComplete:
                    return messages.bindComplete;
                case MessageCodes_ParseComplete:
                    return messages.parseComplete;
                case MessageCodes_CloseComplete:
                    return messages.closeComplete;
                case MessageCodes_NoData:
                    return messages.noData;
                case MessageCodes_PortalSuspended:
                    return messages.portalSuspended;
                case MessageCodes_CopyDone:
                    return messages.copyDone;
                case MessageCodes_ReplicationStart:
                    return messages.replicationStart;
                case MessageCodes_EmptyQuery:
                    return messages.emptyQuery;
                case MessageCodes_DataRow:
                    return this.parseDataRowMessage(offset, length, bytes);
                case MessageCodes_CommandComplete:
                    return this.parseCommandCompleteMessage(offset, length, bytes);
                case MessageCodes_ReadyForQuery:
                    return this.parseReadyForQueryMessage(offset, length, bytes);
                case MessageCodes_NotificationResponse:
                    return this.parseNotificationMessage(offset, length, bytes);
                case MessageCodes_AuthenticationResponse:
                    return this.parseAuthenticationResponse(offset, length, bytes);
                case MessageCodes_ParameterStatus:
                    return this.parseParameterStatusMessage(offset, length, bytes);
                case MessageCodes_BackendKeyData:
                    return this.parseBackendKeyData(offset, length, bytes);
                case MessageCodes_ErrorMessage:
                    return this.parseErrorMessage(offset, length, bytes, "error");
                case MessageCodes_NoticeMessage:
                    return this.parseErrorMessage(offset, length, bytes, "notice");
                case MessageCodes_RowDescriptionMessage:
                    return this.parseRowDescriptionMessage(offset, length, bytes);
                case MessageCodes_ParameterDescriptionMessage:
                    return this.parseParameterDescriptionMessage(offset, length, bytes);
                case MessageCodes_CopyIn:
                    return this.parseCopyInMessage(offset, length, bytes);
                case MessageCodes_CopyOut:
                    return this.parseCopyOutMessage(offset, length, bytes);
                case MessageCodes_CopyData:
                    return this.parseCopyData(offset, length, bytes);
                default:
                    return new DatabaseError(
                        "received invalid response: " + Integer.toHexString(code),
                        length,
                        "error"
                    );
            }
        }

        private BackendMessage parseReadyForQueryMessage(
            int offset,
            int length,
            ArrayBuffer bytes
        ) {
            this.reader.setBuffer(offset, bytes);
            var status = this.reader.string(1);
            return new ReadyForQueryMessage(length, status);
        }

        private BackendMessage parseCommandCompleteMessage(
            int offset,
            int length,
            ArrayBuffer bytes
        ) {
            this.reader.setBuffer(offset, bytes);
            var text = this.reader.cstring();
            return new CommandCompleteMessage(length, text);
        }

        private BackendMessage parseCopyData(int offset, int length, ArrayBuffer bytes) {
            var chunk = bytes.slice(offset, offset + (length - 4));
            return new CopyDataMessage(length, new Uint8Array(chunk));
        }

        private BackendMessage parseCopyInMessage(int offset, int length, ArrayBuffer bytes) {
            return this.parseCopyMessage(offset, length, bytes, "copyInResponse");
        }

        private BackendMessage parseCopyOutMessage(int offset, int length, ArrayBuffer bytes) {
            return this.parseCopyMessage(offset, length, bytes, "copyOutResponse");
        }

        private BackendMessage parseCopyMessage(
            int offset,
            int length,
            ArrayBuffer bytes,
            String messageName
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

        private BackendMessage parseNotificationMessage(
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

        private BackendMessage parseRowDescriptionMessage(
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

        private BackendMessage parseParameterDescriptionMessage(
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

        private BackendMessage parseDataRowMessage(int offset, int length, ArrayBuffer bytes) {
            this.reader.setBuffer(offset, bytes);
            var fieldCount = this.reader.int16();
            var fields = new String[fieldCount];
            for (var i = 0; i < fieldCount; i++) {
                var len = this.reader.int32();
                // a -1 for length means the value of the field is null
                fields[i] = len == -1 ? null : this.reader.string(len);
            }
            return new DataRowMessage(length, fields);
        }

        private BackendMessage parseParameterStatusMessage(
            int offset,
            int length,
            ArrayBuffer bytes
        ) {
            this.reader.setBuffer(offset, bytes);
            var name = this.reader.cstring();
            var value = this.reader.cstring();
            return new ParameterStatusMessage(length, name, value);
        }

        private BackendMessage parseBackendKeyData(int offset, int length, ArrayBuffer bytes) {
            this.reader.setBuffer(offset, bytes);
            var processID = this.reader.int32();
            var secretKey = this.reader.int32();
            return new BackendKeyDataMessage(length, processID, secretKey);
        }

        private BackendMessage parseAuthenticationResponse(
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
                case 10: {
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

        private BackendMessage parseErrorMessage(
            int offset,
            int length,
            ArrayBuffer bytes,
            String name
        ) {
            this.reader.setBuffer(offset, bytes);
            var fields = new HashMap<String, String>();
            var fieldType = this.reader.string(1);
            while (!fieldType.equals("\0")) {
                fields.put(fieldType, this.reader.cstring());
                fieldType = this.reader.string(1);
            }

            var messageValue = fields.get("M");

            var message =
                "notice".equals(name)
                    ? new NoticeMessage(length, messageValue)
                    : new DatabaseError(messageValue, length, name);

            if (message instanceof NoticeMessage) {
                var notice = (NoticeMessage) message;
                notice.severity = fields.get("S");
                notice.code = fields.get("C");
                notice.detail = fields.get("D");
                notice.hint = fields.get("H");
                notice.position = fields.get("P");
                notice.internalPosition = fields.get("p");
                notice.internalQuery = fields.get("q");
                notice.where = fields.get("W");
                notice.schema = fields.get("s");
                notice.table = fields.get("t");
                notice.column = fields.get("c");
                notice.dataType = fields.get("d");
                notice.constraint = fields.get("n");
                notice.file = fields.get("F");
                notice.line = fields.get("L");
                notice.routine = fields.get("R");
            } else {
                var error = (DatabaseError) message;
                error.severity = fields.get("S");
                error.code = fields.get("C");
                error.detail = fields.get("D");
                error.hint = fields.get("H");
                error.position = fields.get("P");
                error.internalPosition = fields.get("p");
                error.internalQuery = fields.get("q");
                error.where = fields.get("W");
                error.schema = fields.get("s");
                error.table = fields.get("t");
                error.column = fields.get("c");
                error.dataType = fields.get("d");
                error.constraint = fields.get("n");
                error.file = fields.get("F");
                error.line = fields.get("L");
                error.routine = fields.get("R");
            }
            return message;
        }
    }

    private parser() {
    }
}
