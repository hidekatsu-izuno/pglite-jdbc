package io.github.hidekatsu_izuno.pglite_jdbc.jdbc;

import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.serializer;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.ArrayBuffer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.postgresql.copy.CopyDual;
import org.postgresql.copy.CopyIn;
import org.postgresql.copy.CopyOut;
import org.postgresql.util.ByteStreamWriter;

final class PgCopyManagerAdapter extends org.postgresql.copy.CopyManager {
    private static final int BUFFER_SIZE = 65536;
    private final PgConnection connection;

    PgCopyManagerAdapter(PgConnection connection) throws SQLException {
        super(PgBaseConnectionAdapter.create(connection, new java.util.concurrent.atomic.AtomicReference<>()));
        this.connection = connection;
    }

    @Override
    public CopyIn copyIn(String sql) throws SQLException {
        return new CopyInOp(connection, sql);
    }

    @Override
    public CopyOut copyOut(String sql) throws SQLException {
        return new CopyOutOp(connection, sql);
    }

    @Override
    public CopyDual copyDual(String sql) throws SQLException {
        throw new SQLFeatureNotSupportedException("COPY DUAL is not supported");
    }

    @Override
    public long copyOut(String sql, Writer to) throws SQLException, IOException {
        var op = copyOut(sql);
        byte[] chunk;
        while ((chunk = op.readFromCopy()) != null) {
            to.write(new String(chunk, StandardCharsets.UTF_8));
        }
        return op.getHandledRowCount();
    }

    @Override
    public long copyOut(String sql, OutputStream to) throws SQLException, IOException {
        var op = copyOut(sql);
        byte[] chunk;
        while ((chunk = op.readFromCopy()) != null) {
            to.write(chunk);
        }
        return op.getHandledRowCount();
    }

    @Override
    public long copyIn(String sql, Reader from) throws SQLException, IOException {
        return copyIn(sql, from, BUFFER_SIZE);
    }

    @Override
    public long copyIn(String sql, Reader from, int bufferSize) throws SQLException, IOException {
        var op = copyIn(sql);
        var cbuf = new char[Math.max(bufferSize, 1)];
        int len;
        while ((len = from.read(cbuf)) >= 0) {
            if (len > 0) {
                var chunk = new String(cbuf, 0, len).getBytes(StandardCharsets.UTF_8);
                op.writeToCopy(chunk, 0, chunk.length);
            }
        }
        return op.endCopy();
    }

    @Override
    public long copyIn(String sql, InputStream from) throws SQLException, IOException {
        return copyIn(sql, from, BUFFER_SIZE);
    }

    @Override
    public long copyIn(String sql, InputStream from, int bufferSize) throws SQLException, IOException {
        var op = copyIn(sql);
        var buf = new byte[Math.max(bufferSize, 1)];
        int len;
        while ((len = from.read(buf)) >= 0) {
            if (len > 0) {
                op.writeToCopy(buf, 0, len);
            }
        }
        return op.endCopy();
    }

    @Override
    public long copyIn(String sql, ByteStreamWriter from) throws SQLException, IOException {
        var bytes = new byte[from.getLength()];
        var out = new ByteArrayOutputStream(bytes.length);
        from.writeTo(() -> out);
        var op = copyIn(sql);
        op.writeToCopy(out.toByteArray(), 0, out.size());
        return op.endCopy();
    }

    private static final class CopyInOp implements CopyIn {
        private final PgConnection connection;
        private final String sql;
        private final List<byte[]> bufferedChunks = new ArrayList<>();
        private boolean active = true;
        private long handledRowCount = -1L;

        private CopyInOp(PgConnection connection, String sql) throws SQLException {
            this.connection = connection;
            this.sql = sql;
        }

        @Override
        public void writeToCopy(byte[] buf, int off, int siz) throws SQLException {
            ensureActive();
            var chunk = new byte[siz];
            System.arraycopy(buf, off, chunk, 0, siz);
            bufferedChunks.add(chunk);
        }

        @Override
        public void writeToCopy(ByteStreamWriter from) throws SQLException {
            ensureActive();
            try {
                var out = new ByteArrayOutputStream(from.getLength());
                from.writeTo(() -> out);
                var bytes = out.toByteArray();
                writeToCopy(bytes, 0, bytes.length);
            } catch (IOException e) {
                throw new SQLException("Failed to write copy chunk", e);
            }
        }

        @Override
        public void flushCopy() {
            // No buffering in adapter.
        }

        @Override
        public long endCopy() throws SQLException {
            ensureActive();
            var payload = new ByteArrayOutputStream();
            writeBatch(payload);
            var stage = executeStage(
                connection,
                "copyIn.done",
                payload.toByteArray(),
                true
            );
            ensureCopyResponse(stage.messages(), "copyInResponse");
            handledRowCount = commandCompleteCount(stage.messages());
            active = false;
            bufferedChunks.clear();
            return handledRowCount;
        }

        @Override
        public int getFieldCount() {
            return 0;
        }

        @Override
        public int getFormat() {
            return 0;
        }

        @Override
        public int getFieldFormat(int field) {
            return 0;
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public void cancelCopy() throws SQLException {
            if (!active) {
                return;
            }
            bufferedChunks.clear();
            active = false;
        }

        @Override
        public long getHandledRowCount() {
            return handledRowCount;
        }

        private void ensureActive() throws SQLException {
            if (!active) {
                throw new SQLException("COPY IN operation is not active");
            }
        }

        private void writeBatch(ByteArrayOutputStream payload) {
            payload.writeBytes(serializer.serialize.query(sql).toByteArray());
            for (var chunk : bufferedChunks) {
                var buffer = new ArrayBuffer(chunk.length);
                System.arraycopy(chunk, 0, buffer.getBytes(), 0, chunk.length);
                payload.writeBytes(serializer.serialize.copyData(buffer).toByteArray());
            }
            payload.writeBytes(serializer.serialize.copyDone().toByteArray());
        }
    }

    private static final class CopyOutOp implements CopyOut {
        private final ArrayDeque<byte[]> chunks = new ArrayDeque<>();
        private final long handledRowCount;
        private boolean active = true;

        private CopyOutOp(PgConnection connection, String sql) throws SQLException {
            var stage = executeStage(
                connection,
                "copyOut.begin",
                serializer.serialize.query(sql).toByteArray(),
                true
            );
            ensureCopyResponse(stage.messages(), "copyOutResponse");
            for (var message : stage.messages()) {
                if (message instanceof messages.CopyDataMessage dataMessage) {
                    chunks.add(dataMessage.chunk.toByteArray());
                }
            }
            handledRowCount = commandCompleteCount(stage.messages());
        }

        @Override
        public byte[] readFromCopy() {
            return readFromCopy(true);
        }

        @Override
        public byte[] readFromCopy(boolean block) {
            var chunk = chunks.pollFirst();
            if (chunk == null) {
                active = false;
            }
            return chunk;
        }

        @Override
        public int getFieldCount() {
            return 0;
        }

        @Override
        public int getFormat() {
            return 0;
        }

        @Override
        public int getFieldFormat(int field) {
            return 0;
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public void cancelCopy() {
            chunks.clear();
            active = false;
        }

        @Override
        public long getHandledRowCount() {
            return handledRowCount;
        }
    }

    private static void ensureCopyResponse(List<messages.BackendMessage> messages, String responseName)
        throws SQLException {
        for (var message : messages) {
            if (message instanceof messages.CopyResponse copyResponse &&
                responseName.equals(copyResponse.name)) {
                return;
            }
        }
        throw new SQLException("COPY protocol response was not returned: " + responseName);
    }

    private static interface_.ExecProtocolResult executeStage(
        PgConnection connection,
        String stage,
        byte[] payload,
        boolean throwOnError
    ) throws SQLException {
        return connection.execProtocol(payload, throwOnError, stage);
    }

    private static long commandCompleteCount(List<messages.BackendMessage> messages) {
        for (var message : messages) {
            if (message instanceof messages.CommandCompleteMessage commandComplete) {
                var text = commandComplete.text;
                var parts = text != null ? text.trim().split("\\s+") : new String[0];
                if (parts.length > 0) {
                    var last = parts[parts.length - 1];
                    try {
                        return Long.parseLong(last);
                    } catch (NumberFormatException ignored) {
                        return -1L;
                    }
                }
            }
        }
        return -1L;
    }
}
