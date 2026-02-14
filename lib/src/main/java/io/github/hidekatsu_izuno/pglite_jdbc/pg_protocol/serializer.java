package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol;

import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.buffer_writer.Writer;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.ArrayBuffer;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.DataView;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.TextEncoder;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.TypedArray;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array;
import java.util.Map;

public final class serializer {
    private enum code {
        startup((byte) 0x70),
        query((byte) 0x51),
        parse((byte) 0x50),
        bind((byte) 0x42),
        execute((byte) 0x45),
        flush((byte) 0x48),
        sync((byte) 0x53),
        end((byte) 0x58),
        close((byte) 0x43),
        describe((byte) 0x44),
        copyFromChunk((byte) 0x64),
        copyDone((byte) 0x63),
        copyFail((byte) 0x66),
        ;

        public final byte value;

        code(byte value) {
            this.value = value;
        }
    }

    // type LegalValue = string | ArrayBuffer | ArrayBufferView | null

    private static final Writer writer = new Writer();

    private static Uint8Array startup(Map<String, String> opts) {
        // protocol version
        writer.addInt16(3).addInt16(0);
        for (var entry : opts.entrySet()) {
            writer.addCString(entry.getKey()).addCString(entry.getValue());
        }

        writer.addCString("client_encoding").addCString("UTF8");

        var bodyBuffer = writer.addCString("").flush(null);
        // this message is sent without a code

        var length = bodyBuffer.byteLength + 4;

        return new Writer().addInt32(length).add(bodyBuffer.buffer).flush(null);
    }

    private static Uint8Array requestSsl() {
        var bufferView = new DataView(new ArrayBuffer(8));
        bufferView.setInt32(0, 8, false);
        bufferView.setInt32(4, 80877103, false);
        return new Uint8Array(bufferView.buffer);
    }

    private static Uint8Array password(String password) {
        return writer.addCString(password).flush(code.startup.value);
    }

    private static Uint8Array sendSASLInitialResponseMessage(
        String mechanism,
        String initialResponse
    ) {
        // 0x70 = 'p'
        writer
            .addCString(mechanism)
            .addInt32(string_utils.byteLengthUtf8(initialResponse))
            .addString(initialResponse);

        return writer.flush(code.startup.value);
    }

    private static Uint8Array sendSCRAMClientFinalMessage(String additionalData) {
        return writer.addString(additionalData).flush(code.startup.value);
    }

    private static Uint8Array query(String text) {
        return writer.addCString(text).flush(code.query.value);
    }

    public static final class ParseOpts {
        public String name;
        public int[] types;
        public String text;
    }

    private static final Object[] emptyValueArray = new Object[0];

    private static Uint8Array parse(ParseOpts query) {
        // expect something like this:
        // { name: 'queryName',
        //   text: 'select * from blah',
        //   types: ['int8', 'bool'] }

        // normalize missing query names to allow for null
        var name = query.name == null ? "" : query.name;
        if (name.length() > 63) {
            /* eslint-disable no-console */
            System.err.println(
                "Warning! Postgres only supports 63 characters for query names."
            );
            System.err.println("You supplied %s (%s)".formatted(name, name.length()));
            System.err.println(
                "This can cause conflicts and silent errors executing queries"
            );
            /* eslint-enable no-console */
        }

        var buffer = writer
            .addCString(name) // name of query
            .addCString(query.text) // actual query text
            .addInt16(query.types == null ? 0 : query.types.length);

        if (query.types != null) {
            for (var type : query.types) {
                buffer.addInt32(type);
            }
        }

        return writer.flush(code.parse.value);
    }

    public interface ValueMapper {
        Object map(Object param, int index);
    }

    public static final class BindOpts {
        public String portal;
        public boolean binary;
        public String statement;
        public Object[] values;
        // optional map from JS value to postgres value per parameter
        public ValueMapper valueMapper;
    }

    private static final Writer paramWriter = new Writer();

    // make this a const enum so typescript will inline the value
    private enum ParamType {
        STRING(0),
        BINARY(1),
        ;

        public final int value;

        ParamType(int value) {
            this.value = value;
        }
    }

    private static void writeValues(Object[] values, ValueMapper valueMapper) {
        for (var i = 0; i < values.length; i++) {
            var mappedVal = valueMapper != null ? valueMapper.map(values[i], i) : values[i];
            if (mappedVal == null) {
                // add the param type (string) to the writer
                writer.addInt16(ParamType.STRING.value);
                // write -1 to the param writer to indicate null
                paramWriter.addInt32(-1);
            } else if (mappedVal instanceof ArrayBuffer) {
                var buffer = (ArrayBuffer) mappedVal;
                // add the param type (binary) to the writer
                writer.addInt16(ParamType.BINARY.value);
                // add the buffer to the param writer
                paramWriter.addInt32(buffer.byteLength);
                paramWriter.add(buffer);
            } else if (mappedVal instanceof TypedArray) {
                var view = (TypedArray) mappedVal;
                var buffer = view
                    .getBuffer()
                    .slice(view.getByteOffset(), view.getByteOffset() + view.getByteLength());
                // add the param type (binary) to the writer
                writer.addInt16(ParamType.BINARY.value);
                // add the buffer to the param writer
                paramWriter.addInt32(buffer.byteLength);
                paramWriter.add(buffer);
            } else {
                var text = String.valueOf(mappedVal);
                // add the param type (string) to the writer
                writer.addInt16(ParamType.STRING.value);
                paramWriter.addInt32(string_utils.byteLengthUtf8(text));
                paramWriter.addString(text);
            }
        }
    }

    private static Uint8Array bind(BindOpts config) {
        // normalize config
        var portal = config != null && config.portal != null ? config.portal : "";
        var statement = config != null && config.statement != null ? config.statement : "";
        var binary = config != null && config.binary;
        var values = config != null && config.values != null ? config.values : emptyValueArray;
        var len = values.length;

        writer.addCString(portal).addCString(statement);
        writer.addInt16(len);

        writeValues(values, config != null ? config.valueMapper : null);

        writer.addInt16(len);
        writer.add(paramWriter.flush(null).buffer);

        // format code
        writer.addInt16(binary ? ParamType.BINARY.value : ParamType.STRING.value);
        return writer.flush(code.bind.value);
    }

    public static final class ExecOpts {
        public String portal;
        public Integer rows;
    }

    private static final Uint8Array emptyExecute = new Uint8Array(new byte[] {
        code.execute.value,
        0x00,
        0x00,
        0x00,
        0x09,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
    });

    private static Uint8Array execute(ExecOpts config) {
        // this is the happy path for most queries
        if (
            config == null ||
            ((config.portal == null || config.portal.isEmpty()) &&
                (config.rows == null || config.rows == 0))
        ) {
            return emptyExecute;
        }

        var portal = config.portal == null ? "" : config.portal;
        var rows = config.rows == null ? 0 : config.rows;

        var portalLength = string_utils.byteLengthUtf8(portal);
        var len = 4 + portalLength + 1 + 4;
        // one extra bit for code
        var bufferView = new DataView(new ArrayBuffer(1 + len));
        bufferView.setUint8(0, code.execute.value);
        bufferView.setInt32(1, len, false);
        new TextEncoder().encodeInto(
            portal,
            new Uint8Array(bufferView.buffer, 5, portalLength)
        );
        bufferView.setUint8(portalLength + 5, 0); // null terminate portal cString
        bufferView.setInt32(bufferView.buffer.byteLength - 4, rows, false);
        return new Uint8Array(bufferView.buffer);
    }

    private static Uint8Array cancel(int processID, int secretKey) {
        var bufferView = new DataView(new ArrayBuffer(16));
        bufferView.setInt32(0, 16, false);
        bufferView.setInt16(4, (short) 1234, false);
        bufferView.setInt16(6, (short) 5678, false);
        bufferView.setInt32(8, processID, false);
        bufferView.setInt32(12, secretKey, false);
        return new Uint8Array(bufferView.buffer);
    }

    public static final class PortalOpts {
        public String type;
        public String name;
    }

    private static Uint8Array cstringMessage(code codeValue, String string) {
        var writer = new Writer();
        writer.addCString(string);
        return writer.flush(codeValue.value);
    }

    private static Uint8Array emptyDescribePortal = writer.addCString("P").flush(code.describe.value);
    private static Uint8Array emptyDescribeStatement = writer.addCString("S").flush(code.describe.value);

    private static Uint8Array describe(PortalOpts msg) {
        return msg.name != null && !msg.name.isEmpty()
            ? cstringMessage(code.describe, "%s%s".formatted(msg.type, msg.name))
            : "P".equals(msg.type)
                ? emptyDescribePortal
                : emptyDescribeStatement;
    }

    private static Uint8Array close(PortalOpts msg) {
        var text = "%s%s".formatted(msg.type, msg.name == null ? "" : msg.name);
        return cstringMessage(code.close, text);
    }

    private static Uint8Array copyData(ArrayBuffer chunk) {
        return writer.add(chunk).flush(code.copyFromChunk.value);
    }

    private static Uint8Array copyFail(String message) {
        return cstringMessage(code.copyFail, message);
    }

    private static Uint8Array codeOnlyBuffer(code codeValue) {
        return new Uint8Array(new byte[] {
            codeValue.value,
            0x00,
            0x00,
            0x00,
            0x04,
        });
    }

    private static Uint8Array flushBuffer = codeOnlyBuffer(code.flush);
    private static Uint8Array syncBuffer = codeOnlyBuffer(code.sync);
    private static Uint8Array endBuffer = codeOnlyBuffer(code.end);
    private static Uint8Array copyDoneBuffer = codeOnlyBuffer(code.copyDone);

    public static final class serialize {
        public static Uint8Array startup(Map<String, String> opts) {
            return serializer.startup(opts);
        }

        public static Uint8Array password(String password) {
            return serializer.password(password);
        }

        public static Uint8Array requestSsl() {
            return serializer.requestSsl();
        }

        public static Uint8Array sendSASLInitialResponseMessage(
            String mechanism,
            String initialResponse
        ) {
            return serializer.sendSASLInitialResponseMessage(mechanism, initialResponse);
        }

        public static Uint8Array sendSCRAMClientFinalMessage(String additionalData) {
            return serializer.sendSCRAMClientFinalMessage(additionalData);
        }

        public static Uint8Array query(String text) {
            return serializer.query(text);
        }

        public static Uint8Array parse(ParseOpts query) {
            return serializer.parse(query);
        }

        public static Uint8Array bind(BindOpts config) {
            return serializer.bind(config);
        }

        public static Uint8Array execute(ExecOpts config) {
            return serializer.execute(config);
        }

        public static Uint8Array describe(PortalOpts msg) {
            return serializer.describe(msg);
        }

        public static Uint8Array close(PortalOpts msg) {
            return serializer.close(msg);
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

        public static Uint8Array copyData(ArrayBuffer chunk) {
            return serializer.copyData(chunk);
        }

        public static Uint8Array copyDone() {
            return copyDoneBuffer;
        }

        public static Uint8Array copyFail(String message) {
            return serializer.copyFail(message);
        }

        public static Uint8Array cancel(int processID, int secretKey) {
            return serializer.cancel(processID, secretKey);
        }
    }

    private serializer() {
    }
}
