package io.github.hidekatsu_izuno.pglite_jdbc;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

public class PGliteResultSetMetaData implements ResultSetMetaData {
    
    private final List<String> columnNames;
    private final List<String> columnTypes;
    
    public PGliteResultSetMetaData() {
        this.columnNames = java.util.Collections.emptyList();
        this.columnTypes = java.util.Collections.emptyList();
    }
    
    public PGliteResultSetMetaData(List<String> columnNames, List<String> columnTypes) {
        this.columnNames = columnNames;
        this.columnTypes = columnTypes;
    }
    
    @Override
    public int getColumnCount() throws SQLException {
        return columnNames.size();
    }
    
    @Override
    public boolean isAutoIncrement(int column) throws SQLException {
        checkValidColumn(column);
        return false; // Not supported
    }
    
    @Override
    public boolean isCaseSensitive(int column) throws SQLException {
        checkValidColumn(column);
        String type = columnTypes.get(column - 1);
        return "text".equalsIgnoreCase(type) || "varchar".equalsIgnoreCase(type);
    }
    
    @Override
    public boolean isSearchable(int column) throws SQLException {
        checkValidColumn(column);
        return true; // All columns are searchable
    }
    
    @Override
    public boolean isCurrency(int column) throws SQLException {
        checkValidColumn(column);
        return false; // Not supported
    }
    
    @Override
    public int isNullable(int column) throws SQLException {
        checkValidColumn(column);
        return columnNullable; // Assume nullable
    }
    
    @Override
    public boolean isSigned(int column) throws SQLException {
        checkValidColumn(column);
        String type = columnTypes.get(column - 1).toLowerCase();
        return type.contains("int") || type.contains("numeric") || type.contains("decimal") || type.contains("float") || type.contains("double");
    }
    
    @Override
    public int getColumnDisplaySize(int column) throws SQLException {
        checkValidColumn(column);
        String type = columnTypes.get(column - 1).toLowerCase();
        if (type.contains("int")) return 10;
        if (type.contains("bigint")) return 20;
        if (type.contains("text") || type.contains("varchar")) return 255;
        return 50; // Default
    }
    
    @Override
    public String getColumnLabel(int column) throws SQLException {
        checkValidColumn(column);
        return columnNames.get(column - 1);
    }
    
    @Override
    public String getColumnName(int column) throws SQLException {
        checkValidColumn(column);
        return columnNames.get(column - 1);
    }
    
    @Override
    public String getSchemaName(int column) throws SQLException {
        checkValidColumn(column);
        return "public"; // Default schema
    }
    
    @Override
    public int getPrecision(int column) throws SQLException {
        checkValidColumn(column);
        String type = columnTypes.get(column - 1).toLowerCase();
        if (type.contains("int")) return 10;
        if (type.contains("bigint")) return 19;
        if (type.contains("numeric") || type.contains("decimal")) return 38;
        return 0; // Default
    }
    
    @Override
    public int getScale(int column) throws SQLException {
        checkValidColumn(column);
        String type = columnTypes.get(column - 1).toLowerCase();
        if (type.contains("numeric") || type.contains("decimal")) return 0; // Default scale
        return 0;
    }
    
    @Override
    public String getTableName(int column) throws SQLException {
        checkValidColumn(column);
        return ""; // Unknown table name
    }
    
    @Override
    public String getCatalogName(int column) throws SQLException {
        checkValidColumn(column);
        return "pglite"; // Default catalog
    }
    
    @Override
    public int getColumnType(int column) throws SQLException {
        checkValidColumn(column);
        String type = columnTypes.get(column - 1).toLowerCase();
        
        if (type.contains("int")) return Types.INTEGER;
        if (type.contains("bigint")) return Types.BIGINT;
        if (type.contains("smallint")) return Types.SMALLINT;
        if (type.contains("text")) return Types.LONGVARCHAR;
        if (type.contains("varchar")) return Types.VARCHAR;
        if (type.contains("char")) return Types.CHAR;
        if (type.contains("boolean")) return Types.BOOLEAN;
        if (type.contains("numeric") || type.contains("decimal")) return Types.NUMERIC;
        if (type.contains("float") || type.contains("real")) return Types.FLOAT;
        if (type.contains("double")) return Types.DOUBLE;
        if (type.contains("timestamp")) return Types.TIMESTAMP;
        if (type.contains("date")) return Types.DATE;
        if (type.contains("time")) return Types.TIME;
        
        return Types.OTHER; // Default
    }
    
    @Override
    public String getColumnTypeName(int column) throws SQLException {
        checkValidColumn(column);
        return columnTypes.get(column - 1);
    }
    
    @Override
    public boolean isReadOnly(int column) throws SQLException {
        checkValidColumn(column);
        return true; // All columns are read-only in this implementation
    }
    
    @Override
    public boolean isWritable(int column) throws SQLException {
        checkValidColumn(column);
        return false; // No updates supported
    }
    
    @Override
    public boolean isDefinitelyWritable(int column) throws SQLException {
        checkValidColumn(column);
        return false; // No updates supported
    }
    
    @Override
    public String getColumnClassName(int column) throws SQLException {
        checkValidColumn(column);
        String type = columnTypes.get(column - 1).toLowerCase();
        
        if (type.contains("int")) return "java.lang.Integer";
        if (type.contains("bigint")) return "java.lang.Long";
        if (type.contains("smallint")) return "java.lang.Short";
        if (type.contains("text") || type.contains("varchar") || type.contains("char")) return "java.lang.String";
        if (type.contains("boolean")) return "java.lang.Boolean";
        if (type.contains("numeric") || type.contains("decimal")) return "java.math.BigDecimal";
        if (type.contains("float") || type.contains("real")) return "java.lang.Float";
        if (type.contains("double")) return "java.lang.Double";
        if (type.contains("timestamp")) return "java.sql.Timestamp";
        if (type.contains("date")) return "java.sql.Date";
        if (type.contains("time")) return "java.sql.Time";
        
        return "java.lang.Object"; // Default
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
    
    private void checkValidColumn(int column) throws SQLException {
        if (column < 1 || column > columnNames.size()) {
            throw new SQLException("Column index out of range: " + column);
        }
    }
}