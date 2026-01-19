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
    private static int CODE_LENGTH = 1;
    private static int LEN_LENGTH = 4;

    private static int  HEADER_LENGTH = CODE_LENGTH + LEN_LENGTH;

    public interface Packet {
        public int code();
        public ArrayBuffer packet();
    }

    private parser() {
    }
}
