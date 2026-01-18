package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol;

import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.buffer_writer.Writer;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.ArrayBuffer;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.DataView;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.TextEncoder;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.TypedArray;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array;
import java.util.Map;

public final class serializer {
    private static final byte CODE_STARTUP = 0x70;
    private static final byte CODE_QUERY = 0x51;
    private static final byte CODE_PARSE = 0x50;
    private static final byte CODE_BIND = 0x42;
    private static final byte CODE_EXECUTE = 0x45;
    private static final byte CODE_FLUSH = 0x48;
    private static final byte CODE_SYNC = 0x53;
    private static final byte CODE_END = 0x58;
    private static final byte CODE_CLOSE = 0x43;
    private static final byte CODE_DESCRIBE = 0x44;
    private static final byte CODE_COPY_FROM_CHUNK = 0x64;
    private static final byte CODE_COPY_DONE = 0x63;
    private static final byte CODE_COPY_FAIL = 0x66;

    private static final int PARAM_TYPE_STRING = 0;
    private static final int PARAM_TYPE_BINARY = 1;

    private static final Writer writer = new Writer();
    private static final Writer paramWriter = new Writer();
    private static final TextEncoder encoder = new TextEncoder();
    private static final Object[] emptyValueArray = new Object[0];

    private static final Uint8Array emptyExecute = buildEmptyExecute();
    private static final Uint8Array emptyDescribePortal = new Writer()
        .addCString("P")
        .flush(CODE_DESCRIBE);
    private static final Uint8Array emptyDescribeStatement = new Writer()
        .addCString("S")
        .flush(CODE_DESCRIBE);

    private static final Uint8Array flushBuffer = codeOnlyBuffer(CODE_FLUSH);
    private static final Uint8Array syncBuffer = codeOnlyBuffer(CODE_SYNC);
    private static final Uint8Array endBuffer = codeOnlyBuffer(CODE_END);
    private static final Uint8Array copyDoneBuffer = codeOnlyBuffer(CODE_COPY_DONE);

    public static final class ParseOpts {
        public String name;
        public int[] types;
        public String text;
    }

    public interface ValueMapper {
        Object apply(Object param, int index);
    }

    public static final class BindOpts {
        public String portal;
        public Boolean binary;
        public String statement;
        public Object[] values;
        public ValueMapper valueMapper;
    }

    public static final class ExecOpts {
        public String portal;
        public Integer rows;
    }

    public static final class PortalOpts {
        public char type;
        public String name;
    }

    public static Uint8Array startup(Map<String, String> opts) {
        writer.addInt16(3).addInt16(0);
        for (var entry : opts.entrySet()) {
            writer.addCString(entry.getKey()).addCString(entry.getValue());
        }
        writer.addCString("client_encoding").addCString("UTF8");
        var bodyBuffer = writer.addCString("").flush();
        var length = bodyBuffer.byteLength + 4;
        return new Writer().addInt32(length).add(bodyBuffer.buffer).flush();
    }

    public static Uint8Array requestSsl() {
        var bufferView = new DataView(new ArrayBuffer(8));
        bufferView.setInt32(0, 8, false);
        bufferView.setInt32(4, 80877103, false);
        return new Uint8Array(bufferView.buffer);
    }

    public static Uint8Array password(String password) {
        return writer.addCString(password).flush(CODE_STARTUP);
    }

    public static Uint8Array sendSASLInitialResponseMessage(
        String mechanism,
        String initialResponse
    ) {
        writer
            .addCString(mechanism)
            .addInt32(string_utils.byteLengthUtf8(initialResponse))
            .addString(initialResponse);
        return writer.flush(CODE_STARTUP);
    }

    public static Uint8Array sendSCRAMClientFinalMessage(String additionalData) {
        return writer.addString(additionalData).flush(CODE_STARTUP);
    }

    public static Uint8Array query(String text) {
        return writer.addCString(text).flush(CODE_QUERY);
    }

    public static Uint8Array parse(ParseOpts query) {
        var name = query.name != null ? query.name : "";
        if (name.length() > 63) {
            System.err.println(
                "Warning! Postgres only supports 63 characters for query names."
            );
            System.err.printf("You supplied %s (%s)%n", name, name.length());
            System.err.println(
                "This can cause conflicts and silent errors executing queries"
            );
        }

        var buffer = writer
            .addCString(name)
            .addCString(query.text)
            .addInt16(query.types != null ? query.types.length : 0);

        if (query.types != null) {
            for (var type : query.types) {
                buffer.addInt32(type);
            }
        }

        return writer.flush(CODE_PARSE);
    }

    private static void writeValues(Object[] values, ValueMapper valueMapper) {
        for (var i = 0; i < values.length; i++) {
            var mappedVal = valueMapper != null
                ? valueMapper.apply(values[i], i)
                : values[i];
            if (mappedVal == null) {
                writer.addInt16(PARAM_TYPE_STRING);
                paramWriter.addInt32(-1);
            } else if (mappedVal instanceof ArrayBuffer) {
                var buffer = (ArrayBuffer) mappedVal;
                writer.addInt16(PARAM_TYPE_BINARY);
                paramWriter.addInt32(buffer.byteLength);
                paramWriter.add(buffer);
            } else if (mappedVal instanceof TypedArray) {
                var view = (TypedArray) mappedVal;
                var buffer = view
                    .getBuffer()
                    .slice(view.getByteOffset(), view.getByteOffset() + view.getByteLength());
                writer.addInt16(PARAM_TYPE_BINARY);
                paramWriter.addInt32(buffer.byteLength);
                paramWriter.add(buffer);
            } else {
                var stringValue = (String) mappedVal;
                writer.addInt16(PARAM_TYPE_STRING);
                paramWriter.addInt32(string_utils.byteLengthUtf8(stringValue));
                paramWriter.addString(stringValue);
            }
        }
    }

    public static Uint8Array bind(BindOpts config) {
        var portal = config != null && config.portal != null ? config.portal : "";
        var statement = config != null && config.statement != null ? config.statement : "";
        var binary = config != null && config.binary != null ? config.binary : false;
        var values = config != null && config.values != null ? config.values : emptyValueArray;
        var len = values.length;

        writer.addCString(portal).addCString(statement);
        writer.addInt16(len);

        writeValues(values, config != null ? config.valueMapper : null);

        writer.addInt16(len);
        writer.add(paramWriter.flush().buffer);
        writer.addInt16(binary ? PARAM_TYPE_BINARY : PARAM_TYPE_STRING);
        return writer.flush(CODE_BIND);
    }

    public static Uint8Array execute(ExecOpts config) {
        if (
            config == null ||
            (
                (config.portal == null || config.portal.isEmpty()) &&
                (config.rows == null || config.rows == 0)
            )
        ) {
            return emptyExecute;
        }

        var portal = config.portal != null ? config.portal : "";
        var rows = config.rows != null ? config.rows : 0;

        var portalLength = string_utils.byteLengthUtf8(portal);
        var len = 4 + portalLength + 1 + 4;
        var bufferView = new DataView(new ArrayBuffer(1 + len));
        bufferView.setUint8(0, CODE_EXECUTE);
        bufferView.setInt32(1, len, false);
        encoder.encodeInto(
            portal,
            new Uint8Array(bufferView.buffer, 5, portalLength)
        );
        bufferView.setUint8(portalLength + 5, 0);
        bufferView.setInt32(bufferView.buffer.byteLength - 4, rows, false);
        return new Uint8Array(bufferView.buffer);
    }

    public static Uint8Array cancel(int processID, int secretKey) {
        var bufferView = new DataView(new ArrayBuffer(16));
        bufferView.setInt32(0, 16, false);
        bufferView.setInt16(4, (short) 1234, false);
        bufferView.setInt16(6, (short) 5678, false);
        bufferView.setInt32(8, processID, false);
        bufferView.setInt32(12, secretKey, false);
        return new Uint8Array(bufferView.buffer);
    }

    private static Uint8Array cstringMessage(byte code, String string) {
        var writer = new Writer();
        writer.addCString(string);
        return writer.flush(code);
    }

    public static Uint8Array describe(PortalOpts msg) {
        if (msg.name != null) {
            return cstringMessage(
                CODE_DESCRIBE,
                msg.type + (msg.name != null ? msg.name : "")
            );
        }
        return msg.type == 'P' ? emptyDescribePortal : emptyDescribeStatement;
    }

    public static Uint8Array close(PortalOpts msg) {
        var text = msg.type + (msg.name != null ? msg.name : "");
        return cstringMessage(CODE_CLOSE, text);
    }

    public static Uint8Array copyData(ArrayBuffer chunk) {
        return writer.add(chunk).flush(CODE_COPY_FROM_CHUNK);
    }

    public static Uint8Array copyFail(String message) {
        return cstringMessage(CODE_COPY_FAIL, message);
    }

    public static Uint8Array flush() {
        return flushBuffer;
    }

    public static Uint8Array sync() {
        return syncBuffer;
    }

    public static Uint8Array end() {
        return endBuffer;
    }

    public static Uint8Array copyDone() {
        return copyDoneBuffer;
    }

    private static Uint8Array codeOnlyBuffer(byte code) {
        var bufferView = new DataView(new ArrayBuffer(5));
        bufferView.setUint8(0, code);
        bufferView.setInt32(1, 4, false);
        return new Uint8Array(bufferView.buffer);
    }

    private static Uint8Array buildEmptyExecute() {
        var bufferView = new DataView(new ArrayBuffer(10));
        bufferView.setUint8(0, CODE_EXECUTE);
        bufferView.setInt32(1, 9, false);
        bufferView.setUint8(5, 0);
        bufferView.setInt32(6, 0, false);
        return new Uint8Array(bufferView.buffer);
    }

    private serializer() {

    }
}
