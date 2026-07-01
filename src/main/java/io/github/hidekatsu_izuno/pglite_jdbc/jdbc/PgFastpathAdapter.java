package io.github.hidekatsu_izuno.pglite_jdbc.jdbc;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import org.postgresql.fastpath.Fastpath;
import org.postgresql.fastpath.FastpathArg;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;

final class PgFastpathAdapter extends Fastpath {
    private static final Field BYTES_FIELD = field("bytes");
    private static final Field BYTES_START_FIELD = field("bytesStart");
    private static final Field BYTES_LENGTH_FIELD = field("bytesLength");

    private final PgConnection connection;

    PgFastpathAdapter(PgConnection connection) {
        super((org.postgresql.core.BaseConnection) connection.proxy());
        this.connection = connection;
    }

    @Override
    public Object fastpath(String name, boolean resultType, FastpathArg[] args)
        throws SQLException {
        var data = fastpath(name, args);
        if (!resultType || data == null) {
            return data;
        }
        return switch (data.length) {
            case 4 -> ByteBuffer.wrap(data).getInt();
            case 8 -> ByteBuffer.wrap(data).getLong();
            default -> data;
        };
    }

    @Override
    public byte[] fastpath(String name, FastpathArg[] args) throws SQLException {
        return switch (name) {
            case "lo_close" -> intResult(callInt("SELECT lo_close(?)", intArg(args, 0)));
            case "lo_unlink" -> intResult(callInt("SELECT lo_unlink(?::oid)", oidArg(args, 0)));
            case "lowrite" -> intResult(callInt("SELECT lowrite(?, ?)", intArg(args, 0), bytesArg(args, 1)));
            case "lo_lseek" -> intResult(callInt(
                "SELECT lo_lseek(?, ?, ?)",
                intArg(args, 0),
                intArg(args, 1),
                intArg(args, 2)
            ));
            case "lo_lseek64" -> longResult(callLong(
                "SELECT lo_lseek64(?, ?, ?)",
                intArg(args, 0),
                longArg(args, 1),
                intArg(args, 2)
            ));
            case "lo_truncate" -> intResult(callInt(
                "SELECT lo_truncate(?, ?)",
                intArg(args, 0),
                intArg(args, 1)
            ));
            case "lo_truncate64" -> intResult(callInt(
                "SELECT lo_truncate64(?, ?)",
                intArg(args, 0),
                longArg(args, 1)
            ));
            default -> throw unsupported(name);
        };
    }

    @Override
    public int getInteger(String name, FastpathArg[] args) throws SQLException {
        return switch (name) {
            case "lo_creat" -> callInt("SELECT lo_creat(?)::int", intArg(args, 0));
            case "lo_open" -> callInt("SELECT lo_open(?::oid, ?)", oidArg(args, 0), intArg(args, 1));
            case "lo_tell" -> callInt("SELECT lo_tell(?)", intArg(args, 0));
            case "lo_truncate" -> callInt("SELECT lo_truncate(?, ?)", intArg(args, 0), intArg(args, 1));
            case "lo_truncate64" -> callInt("SELECT lo_truncate64(?, ?)", intArg(args, 0), longArg(args, 1));
            default -> throw unsupported(name);
        };
    }

    @Override
    public long getLong(String name, FastpathArg[] args) throws SQLException {
        return switch (name) {
            case "lo_tell64" -> callLong("SELECT lo_tell64(?)", intArg(args, 0));
            default -> throw unsupported(name);
        };
    }

    @Override
    public long getOID(String name, FastpathArg[] args) throws SQLException {
        var oid = Integer.toUnsignedLong(getInteger(name, args));
        return oid;
    }

    @Override
    public byte[] getData(String name, FastpathArg[] args) throws SQLException {
        return switch (name) {
            case "loread" -> callBytes("SELECT loread(?, ?)", intArg(args, 0), intArg(args, 1));
            default -> fastpath(name, args);
        };
    }

    private int callInt(String sql, Object... args) throws SQLException {
        var value = callScalar(sql, args);
        return value == null ? 0 : ((Number) value).intValue();
    }

    private long callLong(String sql, Object... args) throws SQLException {
        var value = callScalar(sql, args);
        return value == null ? 0L : ((Number) value).longValue();
    }

    private byte[] callBytes(String sql, Object... args) throws SQLException {
        var value = callScalar(sql, args);
        return value instanceof byte[] bytes ? bytes : null;
    }

    private Object callScalar(String sql, Object... args) throws SQLException {
        try (var statement = connection.proxy().prepareStatement(sql)) {
            bind(statement, args);
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return result.getObject(1);
            }
        }
    }

    private void bind(PreparedStatement statement, Object[] args) throws SQLException {
        for (var i = 0; i < args.length; i++) {
            var value = args[i];
            if (value instanceof byte[] bytes) {
                statement.setBytes(i + 1, bytes);
            } else if (value instanceof Long longValue) {
                statement.setLong(i + 1, longValue);
            } else if (value instanceof Integer intValue) {
                statement.setInt(i + 1, intValue);
            } else {
                statement.setObject(i + 1, value);
            }
        }
    }

    private int intArg(FastpathArg[] args, int index) throws SQLException {
        var bytes = bytesArg(args, index);
        if (bytes.length != 4) {
            throw new PSQLException("Fastpath argument is not an int4", PSQLState.INVALID_PARAMETER_VALUE);
        }
        return ByteBuffer.wrap(bytes).getInt();
    }

    private long longArg(FastpathArg[] args, int index) throws SQLException {
        var bytes = bytesArg(args, index);
        if (bytes.length != 8) {
            throw new PSQLException("Fastpath argument is not an int8", PSQLState.INVALID_PARAMETER_VALUE);
        }
        return ByteBuffer.wrap(bytes).getLong();
    }

    private long oidArg(FastpathArg[] args, int index) throws SQLException {
        return Integer.toUnsignedLong(intArg(args, index));
    }

    private byte[] bytesArg(FastpathArg[] args, int index) throws SQLException {
        try {
            var source = (byte[]) BYTES_FIELD.get(args[index]);
            if (source == null) {
                throw new PSQLException(
                    "ByteStreamWriter fastpath arguments are not supported",
                    PSQLState.NOT_IMPLEMENTED
                );
            }
            var start = (Integer) BYTES_START_FIELD.get(args[index]);
            var length = (Integer) BYTES_LENGTH_FIELD.get(args[index]);
            return java.util.Arrays.copyOfRange(source, start, start + length);
        } catch (IllegalAccessException e) {
            throw new SQLException("Cannot read FastpathArg", e);
        }
    }

    private static byte[] intResult(int value) {
        return ByteBuffer.allocate(4).putInt(value).array();
    }

    private static byte[] longResult(long value) {
        return ByteBuffer.allocate(8).putLong(value).array();
    }

    private static Field field(String name) {
        try {
            var field = FastpathArg.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static SQLException unsupported(String name) {
        return new SQLFeatureNotSupportedException("Fastpath function is not supported: " + name);
    }
}
