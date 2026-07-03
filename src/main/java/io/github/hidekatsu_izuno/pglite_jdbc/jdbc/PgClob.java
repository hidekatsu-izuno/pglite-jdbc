package io.github.hidekatsu_izuno.pglite_jdbc.jdbc;

import java.io.ByteArrayInputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

final class PgClob extends org.postgresql.jdbc.PgClob {
    private String value;
    private boolean freed;

    PgClob(org.postgresql.core.BaseConnection connection, String value) throws SQLException {
        super(connection, 0L);
        this.value = value == null ? "" : value;
    }

    @Override
    public long length() throws SQLException {
        ensureActive();
        return value.length();
    }

    @Override
    public String getSubString(long pos, int length) throws SQLException {
        ensureActive();
        var start = checkedStart(pos);
        if (length < 0 || start + length > value.length()) {
            throw new SQLException("Clob slice is out of bounds");
        }
        return value.substring(start, start + length);
    }

    @Override
    public Reader getCharacterStream() throws SQLException {
        ensureActive();
        return new StringReader(value);
    }

    @Override
    public java.io.InputStream getAsciiStream() throws SQLException {
        ensureActive();
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.US_ASCII));
    }

    @Override
    public long position(String searchstr, long start) throws SQLException {
        ensureActive();
        var offset = checkedStart(start);
        var found = value.indexOf(searchstr, offset);
        return found >= 0 ? found + 1L : -1L;
    }

    @Override
    public long position(java.sql.Clob searchstr, long start) throws SQLException {
        return position(searchstr.getSubString(1, Math.toIntExact(searchstr.length())), start);
    }

    @Override
    public int setString(long pos, String str) throws SQLException {
        return setString(pos, str, 0, str.length());
    }

    @Override
    public int setString(long pos, String str, int offset, int len) throws SQLException {
        ensureActive();
        var start = checkedStart(pos);
        if (str == null) {
            throw new SQLException("Clob string must not be null");
        }
        if (offset < 0 || len < 0 || offset > str.length() || offset + len > str.length()) {
            throw new SQLException("Clob string range is out of bounds");
        }
        var replacement = str.substring(offset, offset + len);
        var builder = new StringBuilder(value);
        while (builder.length() < start) {
            builder.append('\0');
        }
        var end = start + replacement.length();
        if (end > builder.length()) {
            builder.setLength(end);
        }
        builder.replace(start, end, replacement);
        value = builder.toString();
        return len;
    }

    @Override
    public java.io.OutputStream setAsciiStream(long pos) throws SQLException {
        ensureActive();
        var start = checkedStart(pos);
        return new java.io.ByteArrayOutputStream() {
            @Override
            public void close() {
                writeAt(start, toString(StandardCharsets.US_ASCII));
            }
        };
    }

    @Override
    public Writer setCharacterStream(long pos) throws SQLException {
        ensureActive();
        var start = checkedStart(pos);
        return new StringWriter() {
            @Override
            public void close() {
                writeAt(start, toString());
            }
        };
    }

    @Override
    public void truncate(long len) throws SQLException {
        ensureActive();
        if (len < 0 || len > Integer.MAX_VALUE) {
            throw new SQLException("Invalid Clob length: " + len);
        }
        if (len < value.length()) {
            value = value.substring(0, (int) len);
        }
    }

    @Override
    public void free() {
        freed = true;
        value = "";
    }

    @Override
    public Reader getCharacterStream(long pos, long length) throws SQLException {
        if (length < 0 || length > Integer.MAX_VALUE) {
            throw new SQLException("Invalid Clob stream length: " + length);
        }
        return new StringReader(getSubString(pos, (int) length));
    }

    private int checkedStart(long pos) throws SQLException {
        if (pos < 1 || pos > Integer.MAX_VALUE) {
            throw new SQLException("Clob position is out of bounds: " + pos);
        }
        return (int) pos - 1;
    }

    private void writeAt(int start, String replacement) {
        var builder = new StringBuilder(value);
        while (builder.length() < start) {
            builder.append('\0');
        }
        var end = start + replacement.length();
        if (end > builder.length()) {
            builder.setLength(end);
        }
        builder.replace(start, end, replacement);
        value = builder.toString();
    }

    private void ensureActive() throws SQLException {
        if (freed) {
            throw new SQLException("Clob has been freed");
        }
    }
}
