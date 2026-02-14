package io.github.hidekatsu_izuno.pglite_jdbc.jdbc;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;

final class PgArray implements Array {
    private final String baseTypeName;
    private final Object array;

    PgArray(String baseTypeName, Object array) {
        this.baseTypeName = baseTypeName;
        this.array = array;
    }

    @Override
    public String getBaseTypeName() {
        return baseTypeName;
    }

    @Override
    public int getBaseType() {
        if (baseTypeName == null) {
            return Types.OTHER;
        }
        return switch (baseTypeName.toLowerCase()) {
            case "bool", "boolean" -> Types.BOOLEAN;
            case "int2", "smallint" -> Types.SMALLINT;
            case "int4", "integer", "int" -> Types.INTEGER;
            case "int8", "bigint" -> Types.BIGINT;
            case "float4", "real" -> Types.REAL;
            case "float8", "double", "double precision" -> Types.DOUBLE;
            case "numeric", "decimal" -> Types.NUMERIC;
            case "text", "varchar", "character varying", "char", "character" -> Types.VARCHAR;
            case "bytea" -> Types.BINARY;
            case "date" -> Types.DATE;
            case "time" -> Types.TIME;
            case "timestamp", "timestamptz" -> Types.TIMESTAMP;
            default -> Types.OTHER;
        };
    }

    @Override
    public Object getArray() {
        return array;
    }

    @Override
    public Object getArray(java.util.Map<String, Class<?>> map) {
        return array;
    }

    @Override
    public Object getArray(long index, int count) throws SQLException {
        throw new SQLFeatureNotSupportedException("Sliced array access is not supported");
    }

    @Override
    public Object getArray(long index, int count, java.util.Map<String, Class<?>> map)
        throws SQLException {
        throw new SQLFeatureNotSupportedException("Sliced array access is not supported");
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        throw new SQLFeatureNotSupportedException("Array result set is not supported");
    }

    @Override
    public ResultSet getResultSet(java.util.Map<String, Class<?>> map) throws SQLException {
        throw new SQLFeatureNotSupportedException("Array result set is not supported");
    }

    @Override
    public ResultSet getResultSet(long index, int count) throws SQLException {
        throw new SQLFeatureNotSupportedException("Array result set is not supported");
    }

    @Override
    public ResultSet getResultSet(long index, int count, java.util.Map<String, Class<?>> map)
        throws SQLException {
        throw new SQLFeatureNotSupportedException("Array result set is not supported");
    }

    @Override
    public void free() {
        // No resources.
    }
}
