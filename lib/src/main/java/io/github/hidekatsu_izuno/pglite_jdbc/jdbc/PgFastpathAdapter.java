package io.github.hidekatsu_izuno.pglite_jdbc.jdbc;

import io.github.hidekatsu_izuno.pglite_jdbc.util.PSQLException;
import io.github.hidekatsu_izuno.pglite_jdbc.util.PSQLState;
import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.atomic.AtomicReference;
import org.postgresql.fastpath.Fastpath;
import org.postgresql.fastpath.FastpathArg;

final class PgFastpathAdapter extends Fastpath {
    private static final Field ARG_BYTES;
    private static final Field ARG_BYTES_START;
    private static final Field ARG_BYTES_LENGTH;

    static {
        try {
            ARG_BYTES = FastpathArg.class.getDeclaredField("bytes");
            ARG_BYTES_START = FastpathArg.class.getDeclaredField("bytesStart");
            ARG_BYTES_LENGTH = FastpathArg.class.getDeclaredField("bytesLength");
            ARG_BYTES.setAccessible(true);
            ARG_BYTES_START.setAccessible(true);
            ARG_BYTES_LENGTH.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final PgConnection connection;

    PgFastpathAdapter(org.postgresql.core.BaseConnection baseConnection, PgConnection connection) {
        super(baseConnection);
        this.connection = connection;
    }

    static Fastpath create(PgConnection connection) {
        var holder = new AtomicReference<Fastpath>();
        var baseConnection = PgBaseConnectionAdapter.create(connection, holder);
        var fastpath = new PgFastpathAdapter(baseConnection, connection);
        holder.set(fastpath);
        return fastpath;
    }

    @Override
    public void addFunctions(ResultSet rs) {
        // Function names are resolved directly by SQL call.
    }

    @Override
    public byte[] fastpath(String name, FastpathArg[] args) throws SQLException {
        ensureSupported(name);
        var decodedArgs = decodeArgs(args);
        var value = executeFunction(name, decodedArgs);
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return bytes;
        }
        if (value instanceof Integer intValue) {
            return toInt4(intValue);
        }
        if (value instanceof Long longValue) {
            return toInt8(longValue);
        }
        if (value instanceof Number number) {
            return toInt8(number.longValue());
        }
        return String.valueOf(value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public int getInteger(String name, FastpathArg[] args) throws SQLException {
        ensureSupported(name);
        var value = executeFunction(name, decodeArgs(args));
        if (value == null) {
            throw new PSQLException("No result returned for " + name, PSQLState.NO_DATA);
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    @Override
    public long getLong(String name, FastpathArg[] args) throws SQLException {
        ensureSupported(name);
        var value = executeFunction(name, decodeArgs(args));
        if (value == null) {
            throw new PSQLException("No result returned for " + name, PSQLState.NO_DATA);
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    @Override
    public long getOID(String name, FastpathArg[] args) throws SQLException {
        var value = getLong(name, args);
        if (value < 0) {
            value += 4294967296L;
        }
        return value;
    }

    @Override
    public byte[] getData(String name, FastpathArg[] args) throws SQLException {
        ensureSupported(name);
        var value = executeFunction(name, decodeArgs(args));
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return bytes;
        }
        return fastpath(name, args);
    }

    private void ensureSupported(String name) throws SQLException {
        if (!name.startsWith("lo_") && !"loread".equals(name) && !"lowrite".equals(name)) {
            throw new SQLFeatureNotSupportedException("Fastpath API is not supported: " + name);
        }
    }

    private Object[] decodeArgs(FastpathArg[] args) throws SQLException {
        var decoded = new Object[args.length];
        for (var i = 0; i < args.length; i++) {
            decoded[i] = decodeArg(args[i]);
        }
        return decoded;
    }

    private Object executeFunction(String name, Object[] decodedArgs) throws SQLException {
        var sql = new StringBuilder("SELECT pg_catalog.").append(name).append("(");
        for (var i = 0; i < decodedArgs.length; i++) {
            if (i > 0) {
                sql.append(',');
            }
            sql.append('?');
        }
        sql.append(')');

        return connection.runProtocolStage(
            "fastpath.execute:" + name,
            () -> {
                try (PreparedStatement statement = connection.proxy().prepareStatement(sql.toString())) {
                    statement.setQueryTimeout(connection.protocolTimeoutSeconds());
                    for (var i = 0; i < decodedArgs.length; i++) {
                        bindArgument(statement, i + 1, decodedArgs[i]);
                    }
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            return null;
                        }
                        return resultSet.getObject(1);
                    }
                }
            }
        );
    }

    private Object decodeArg(FastpathArg arg) throws SQLException {
        try {
            var bytes = (byte[]) ARG_BYTES.get(arg);
            if (bytes == null) {
                return null;
            }
            var start = (Integer) ARG_BYTES_START.get(arg);
            var length = (Integer) ARG_BYTES_LENGTH.get(arg);
            if (length == 4) {
                return fromInt4(bytes, start);
            }
            if (length == 8) {
                return fromInt8(bytes, start);
            }
            var out = new byte[length];
            System.arraycopy(bytes, start, out, 0, length);
            return out;
        } catch (IllegalAccessException e) {
            throw new PSQLException(
                "Failed to decode Fastpath argument",
                PSQLState.INVALID_PARAMETER_VALUE,
                e
            );
        }
    }

    private void bindArgument(PreparedStatement statement, int index, Object value)
        throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else if (value instanceof Integer intValue) {
            statement.setInt(index, intValue);
        } else if (value instanceof Long longValue) {
            statement.setLong(index, longValue);
        } else if (value instanceof byte[] bytes) {
            statement.setBytes(index, bytes);
        } else {
            statement.setObject(index, value);
        }
    }

    private static int fromInt4(byte[] bytes, int start) {
        return ((bytes[start] & 0xFF) << 24) |
            ((bytes[start + 1] & 0xFF) << 16) |
            ((bytes[start + 2] & 0xFF) << 8) |
            (bytes[start + 3] & 0xFF);
    }

    private static long fromInt8(byte[] bytes, int start) {
        return ((long) (bytes[start] & 0xFF) << 56) |
            ((long) (bytes[start + 1] & 0xFF) << 48) |
            ((long) (bytes[start + 2] & 0xFF) << 40) |
            ((long) (bytes[start + 3] & 0xFF) << 32) |
            ((long) (bytes[start + 4] & 0xFF) << 24) |
            ((long) (bytes[start + 5] & 0xFF) << 16) |
            ((long) (bytes[start + 6] & 0xFF) << 8) |
            ((long) (bytes[start + 7] & 0xFF));
    }

    private static byte[] toInt4(int value) {
        return new byte[] {
            (byte) (value >> 24),
            (byte) (value >> 16),
            (byte) (value >> 8),
            (byte) value,
        };
    }

    private static byte[] toInt8(long value) {
        return new byte[] {
            (byte) (value >> 56),
            (byte) (value >> 48),
            (byte) (value >> 40),
            (byte) (value >> 32),
            (byte) (value >> 24),
            (byte) (value >> 16),
            (byte) (value >> 8),
            (byte) value,
        };
    }
}
