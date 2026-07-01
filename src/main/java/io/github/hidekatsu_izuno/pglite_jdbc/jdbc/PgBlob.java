package io.github.hidekatsu_izuno.pglite_jdbc.jdbc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.Arrays;

final class PgBlob extends org.postgresql.jdbc.PgBlob {
    private byte[] bytes;
    private boolean freed;

    PgBlob(org.postgresql.core.BaseConnection connection, byte[] bytes) throws SQLException {
        super(connection, 0L);
        this.bytes = bytes == null ? new byte[0] : Arrays.copyOf(bytes, bytes.length);
    }

    @Override
    public long length() throws SQLException {
        ensureActive();
        return bytes.length;
    }

    @Override
    public byte[] getBytes(long pos, int length) throws SQLException {
        ensureActive();
        var start = checkedStart(pos);
        if (length < 0 || start + length > bytes.length) {
            throw new SQLException("Blob slice is out of bounds");
        }
        return Arrays.copyOfRange(bytes, start, start + length);
    }

    @Override
    public InputStream getBinaryStream() throws SQLException {
        ensureActive();
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public long position(byte[] pattern, long start) throws SQLException {
        ensureActive();
        var offset = checkedStart(start);
        if (pattern == null || pattern.length == 0) {
            return start;
        }
        for (var i = offset; i <= bytes.length - pattern.length; i++) {
            var matches = true;
            for (var j = 0; j < pattern.length; j++) {
                if (bytes[i + j] != pattern[j]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return i + 1L;
            }
        }
        return -1;
    }

    @Override
    public long position(java.sql.Blob pattern, long start) throws SQLException {
        return position(pattern.getBytes(1, Math.toIntExact(pattern.length())), start);
    }

    @Override
    public int setBytes(long pos, byte[] bytes) throws SQLException {
        return setBytes(pos, bytes, 0, bytes.length);
    }

    @Override
    public int setBytes(long pos, byte[] source, int offset, int length) throws SQLException {
        ensureActive();
        var start = checkedStart(pos);
        var replacement = Arrays.copyOfRange(source, offset, offset + length);
        var required = start + length;
        if (required > bytes.length) {
            bytes = Arrays.copyOf(bytes, required);
        }
        System.arraycopy(replacement, 0, bytes, start, length);
        return length;
    }

    @Override
    public OutputStream setBinaryStream(long pos) throws SQLException {
        ensureActive();
        var start = checkedStart(pos);
        return new ByteArrayOutputStream() {
            @Override
            public void close() {
                var data = toByteArray();
                var required = start + data.length;
                if (required > bytes.length) {
                    bytes = Arrays.copyOf(bytes, required);
                }
                System.arraycopy(data, 0, bytes, start, data.length);
            }
        };
    }

    @Override
    public void truncate(long len) throws SQLException {
        ensureActive();
        if (len < 0 || len > Integer.MAX_VALUE) {
            throw new SQLException("Invalid Blob length: " + len);
        }
        bytes = Arrays.copyOf(bytes, (int) len);
    }

    @Override
    public void free() {
        freed = true;
        bytes = new byte[0];
    }

    @Override
    public InputStream getBinaryStream(long pos, long length) throws SQLException {
        if (length < 0 || length > Integer.MAX_VALUE) {
            throw new SQLException("Invalid Blob stream length: " + length);
        }
        return new ByteArrayInputStream(getBytes(pos, (int) length));
    }

    private int checkedStart(long pos) throws SQLException {
        if (pos < 1 || pos > Integer.MAX_VALUE) {
            throw new SQLException("Blob position is out of bounds: " + pos);
        }
        return (int) pos - 1;
    }

    private void ensureActive() throws SQLException {
        if (freed) {
            throw new SQLException("Blob has been freed");
        }
    }
}
