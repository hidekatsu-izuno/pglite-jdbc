package io.github.hidekatsu_izuno.pglite_jdbc;

import java.sql.*;

/**
 * Basic stub for PGlite ResultSetMetaData
 */
public class PGliteResultSetMetaData implements ResultSetMetaData {
    private String[] columnNames;
    
    public PGliteResultSetMetaData(String[] columnNames) {
        this.columnNames = columnNames;
    }
    
    @Override
    public int getColumnCount() throws SQLException {
        return columnNames.length;
    }
    
    @Override
    public boolean isAutoIncrement(int column) throws SQLException {
        return false;
    }
    
    @Override
    public boolean isCaseSensitive(int column) throws SQLException {
        return false;
    }
    
    @Override
    public boolean isSearchable(int column) throws SQLException {
        return true;
    }
    
    @Override
    public boolean isCurrency(int column) throws SQLException {
        return false;
    }
    
    @Override
    public int isNullable(int column) throws SQLException {
        return columnNullable;
    }
    
    @Override
    public boolean isSigned(int column) throws SQLException {
        return true;
    }
    
    @Override
    public int getColumnDisplaySize(int column) throws SQLException {
        return 10;
    }
    
    @Override
    public String getColumnLabel(int column) throws SQLException {
        if (column < 1 || column > columnNames.length) {
            throw new SQLException("Column index out of bounds: " + column);
        }
        return columnNames[column - 1];
    }
    
    @Override
    public String getColumnName(int column) throws SQLException {
        return getColumnLabel(column);
    }
    
    @Override
    public String getSchemaName(int column) throws SQLException {
        return "public";
    }
    
    @Override
    public int getPrecision(int column) throws SQLException {
        return 0;
    }
    
    @Override
    public int getScale(int column) throws SQLException {
        return 0;
    }
    
    @Override
    public String getTableName(int column) throws SQLException {
        return "";
    }
    
    @Override
    public String getCatalogName(int column) throws SQLException {
        return "";
    }
    
    @Override
    public int getColumnType(int column) throws SQLException {
        return Types.INTEGER; // Default for "SELECT 1"
    }
    
    @Override
    public String getColumnTypeName(int column) throws SQLException {
        return "int4";
    }
    
    @Override
    public boolean isReadOnly(int column) throws SQLException {
        return true;
    }
    
    @Override
    public boolean isWritable(int column) throws SQLException {
        return false;
    }
    
    @Override
    public boolean isDefinitelyWritable(int column) throws SQLException {
        return false;
    }
    
    @Override
    public String getColumnClassName(int column) throws SQLException {
        return "java.lang.Integer";
    }
    
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("Cannot unwrap to " + iface.getName());
    }
    
    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this);
    }
}