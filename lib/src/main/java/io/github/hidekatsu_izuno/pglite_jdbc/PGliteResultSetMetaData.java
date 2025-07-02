package io.github.hidekatsu_izuno.pglite_jdbc;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;

public class PGliteResultSetMetaData implements ResultSetMetaData {
    
    @Override
    public int getColumnCount() throws SQLException {
        return 0; // Empty result set
    }
    
    @Override
    public boolean isAutoIncrement(int column) throws SQLException {
        throw new SQLException("Column index out of range: " + column);
    }
    
    @Override
    public boolean isCaseSensitive(int column) throws SQLException {
        throw new SQLException("Column index out of range: " + column);
    }
    
    @Override
    public boolean isSearchable(int column) throws SQLException {
        throw new SQLException("Column index out of range: " + column);
    }
    
    @Override
    public boolean isCurrency(int column) throws SQLException {
        throw new SQLException("Column index out of range: " + column);
    }
    
    @Override
    public int isNullable(int column) throws SQLException {
        throw new SQLException("Column index out of range: " + column);
    }
    
    @Override
    public boolean isSigned(int column) throws SQLException {
        throw new SQLException("Column index out of range: " + column);
    }
    
    @Override
    public int getColumnDisplaySize(int column) throws SQLException {
        throw new SQLException("Column index out of range: " + column);
    }
    
    @Override
    public String getColumnLabel(int column) throws SQLException {
        throw new SQLException("Column index out of range: " + column);
    }
    
    @Override
    public String getColumnName(int column) throws SQLException {
        throw new SQLException("Column index out of range: " + column);
    }
    
    @Override
    public String getSchemaName(int column) throws SQLException {
        throw new SQLException("Column index out of range: " + column);
    }
    
    @Override
    public int getPrecision(int column) throws SQLException {
        throw new SQLException("Column index out of range: " + column);
    }
    
    @Override
    public int getScale(int column) throws SQLException {
        throw new SQLException("Column index out of range: " + column);
    }
    
    @Override
    public String getTableName(int column) throws SQLException {
        throw new SQLException("Column index out of range: " + column);
    }
    
    @Override
    public String getCatalogName(int column) throws SQLException {
        throw new SQLException("Column index out of range: " + column);
    }
    
    @Override
    public int getColumnType(int column) throws SQLException {
        throw new SQLException("Column index out of range: " + column);
    }
    
    @Override
    public String getColumnTypeName(int column) throws SQLException {
        throw new SQLException("Column index out of range: " + column);
    }
    
    @Override
    public boolean isReadOnly(int column) throws SQLException {
        throw new SQLException("Column index out of range: " + column);
    }
    
    @Override
    public boolean isWritable(int column) throws SQLException {
        throw new SQLException("Column index out of range: " + column);
    }
    
    @Override
    public boolean isDefinitelyWritable(int column) throws SQLException {
        throw new SQLException("Column index out of range: " + column);
    }
    
    @Override
    public String getColumnClassName(int column) throws SQLException {
        throw new SQLException("Column index out of range: " + column);
    }
    
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return iface.cast(this);
        }
        throw new SQLException("Cannot unwrap to " + iface.getName());
    }
    
    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isAssignableFrom(getClass());
    }
}