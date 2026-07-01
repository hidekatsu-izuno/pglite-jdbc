package io.github.hidekatsu_izuno.pglite_jdbc.jdbc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLXML;
import javax.xml.transform.Result;
import javax.xml.transform.Source;

final class PgSQLXML implements SQLXML {
    private String value;
    private boolean freed;

    PgSQLXML(String value) {
        this.value = value;
    }

    @Override
    public void free() {
        freed = true;
        value = null;
    }

    @Override
    public InputStream getBinaryStream() throws SQLException {
        ensureActive();
        return value == null ? null : new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public OutputStream setBinaryStream() throws SQLException {
        ensureActive();
        return new ByteArrayOutputStream() {
            @Override
            public void close() {
                value = toString(StandardCharsets.UTF_8);
            }
        };
    }

    @Override
    public Reader getCharacterStream() throws SQLException {
        ensureActive();
        return value == null ? null : new StringReader(value);
    }

    @Override
    public Writer setCharacterStream() throws SQLException {
        ensureActive();
        return new StringWriter() {
            @Override
            public void close() {
                value = toString();
            }
        };
    }

    @Override
    public String getString() throws SQLException {
        ensureActive();
        return value;
    }

    @Override
    public void setString(String value) throws SQLException {
        ensureActive();
        this.value = value;
    }

    @Override
    public <T extends Source> T getSource(Class<T> sourceClass) throws SQLException {
        throw new SQLFeatureNotSupportedException("SQLXML Source is not supported");
    }

    @Override
    public <T extends Result> T setResult(Class<T> resultClass) throws SQLException {
        throw new SQLFeatureNotSupportedException("SQLXML Result is not supported");
    }

    private void ensureActive() throws SQLException {
        if (freed) {
            throw new SQLException("SQLXML has been freed");
        }
    }
}
