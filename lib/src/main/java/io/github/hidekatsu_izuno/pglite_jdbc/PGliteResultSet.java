package io.github.hidekatsu_izuno.pglite_jdbc;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.util.Calendar;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

public class PGliteResultSet implements ResultSet {
    
    private final Statement statement;
    private boolean closed = false;
    private boolean wasNull = false;
    private int currentRow = -1; // Before first row
    
    // Result data storage
    private List<String> columnNames;
    private List<String> columnTypes;
    private List<List<Object>> rows;
    private Map<String, Integer> columnNameToIndex;
    
    public PGliteResultSet(Statement statement) {
        this.statement = statement;
        this.columnNames = new ArrayList<>();
        this.columnTypes = new ArrayList<>();
        this.rows = new ArrayList<>();
        this.columnNameToIndex = new HashMap<>();
    }
    
    public PGliteResultSet(Statement statement, List<String> columnNames, List<String> columnTypes, List<List<Object>> rows) {
        this.statement = statement;
        this.columnNames = new ArrayList<>(columnNames);
        this.columnTypes = new ArrayList<>(columnTypes);
        this.rows = new ArrayList<>(rows);
        this.columnNameToIndex = new HashMap<>();
        
        // Build column name to index mapping
        for (int i = 0; i < columnNames.size(); i++) {
            columnNameToIndex.put(columnNames.get(i).toLowerCase(), i + 1);
        }
        
        this.currentRow = -1; // Before first row
    }
    
    @Override
    public boolean next() throws SQLException {
        checkClosed();
        if (currentRow + 1 < rows.size()) {
            currentRow++;
            return true;
        }
        return false;
    }
    
    @Override
    public void close() throws SQLException {
        closed = true;
    }
    
    @Override
    public boolean wasNull() throws SQLException {
        checkClosed();
        return wasNull;
    }
    
    @Override
    public String getString(int columnIndex) throws SQLException {
        checkClosed();
        checkValidColumn(columnIndex);
        checkValidRow();
        
        Object value = rows.get(currentRow).get(columnIndex - 1);
        wasNull = (value == null);
        return value == null ? null : value.toString();
    }
    
    @Override
    public boolean getBoolean(int columnIndex) throws SQLException {
        checkClosed();
        checkValidColumn(columnIndex);
        checkValidRow();
        
        Object value = rows.get(currentRow).get(columnIndex - 1);
        wasNull = (value == null);
        if (wasNull) return false;
        
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String str = value.toString().toLowerCase();
        return "true".equals(str) || "t".equals(str) || "1".equals(str);
    }
    
    @Override
    public byte getByte(int columnIndex) throws SQLException {
        checkClosed();
        checkValidColumn(columnIndex);
        checkValidRow();
        
        Object value = rows.get(currentRow).get(columnIndex - 1);
        wasNull = (value == null);
        if (wasNull) return 0;
        
        if (value instanceof Number) {
            return ((Number) value).byteValue();
        }
        try {
            return Byte.parseByte(value.toString());
        } catch (NumberFormatException e) {
            throw new SQLException("Cannot convert to byte: " + value, e);
        }
    }
    
    @Override
    public short getShort(int columnIndex) throws SQLException {
        checkClosed();
        checkValidColumn(columnIndex);
        checkValidRow();
        
        Object value = rows.get(currentRow).get(columnIndex - 1);
        wasNull = (value == null);
        if (wasNull) return 0;
        
        if (value instanceof Number) {
            return ((Number) value).shortValue();
        }
        try {
            return Short.parseShort(value.toString());
        } catch (NumberFormatException e) {
            throw new SQLException("Cannot convert to short: " + value, e);
        }
    }
    
    @Override
    public int getInt(int columnIndex) throws SQLException {
        checkClosed();
        checkValidColumn(columnIndex);
        checkValidRow();
        
        Object value = rows.get(currentRow).get(columnIndex - 1);
        wasNull = (value == null);
        if (wasNull) return 0;
        
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            throw new SQLException("Cannot convert to int: " + value, e);
        }
    }
    
    @Override
    public long getLong(int columnIndex) throws SQLException {
        checkClosed();
        checkValidColumn(columnIndex);
        checkValidRow();
        
        Object value = rows.get(currentRow).get(columnIndex - 1);
        wasNull = (value == null);
        if (wasNull) return 0;
        
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            throw new SQLException("Cannot convert to long: " + value, e);
        }
    }
    
    @Override
    public float getFloat(int columnIndex) throws SQLException {
        checkClosed();
        checkValidColumn(columnIndex);
        checkValidRow();
        
        Object value = rows.get(currentRow).get(columnIndex - 1);
        wasNull = (value == null);
        if (wasNull) return 0;
        
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        try {
            return Float.parseFloat(value.toString());
        } catch (NumberFormatException e) {
            throw new SQLException("Cannot convert to float: " + value, e);
        }
    }
    
    @Override
    public double getDouble(int columnIndex) throws SQLException {
        checkClosed();
        checkValidColumn(columnIndex);
        checkValidRow();
        
        Object value = rows.get(currentRow).get(columnIndex - 1);
        wasNull = (value == null);
        if (wasNull) return 0;
        
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            throw new SQLException("Cannot convert to double: " + value, e);
        }
    }
    
    @Override
    public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException {
        checkClosed();
        checkValidColumn(columnIndex);
        checkValidRow();
        
        Object value = rows.get(currentRow).get(columnIndex - 1);
        wasNull = (value == null);
        if (wasNull) return null;
        
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).setScale(scale, java.math.RoundingMode.HALF_UP);
        }
        try {
            BigDecimal bd = new BigDecimal(value.toString());
            return bd.setScale(scale, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new SQLException("Cannot convert to BigDecimal: " + value, e);
        }
    }
    
    @Override
    public byte[] getBytes(int columnIndex) throws SQLException {
        checkClosed();
        checkValidColumn(columnIndex);
        checkValidRow();
        
        Object value = rows.get(currentRow).get(columnIndex - 1);
        wasNull = (value == null);
        if (wasNull) return null;
        
        if (value instanceof byte[]) {
            return (byte[]) value;
        }
        return value.toString().getBytes();
    }
    
    @Override
    public Date getDate(int columnIndex) throws SQLException {
        checkClosed();
        checkValidColumn(columnIndex);
        checkValidRow();
        
        Object value = rows.get(currentRow).get(columnIndex - 1);
        wasNull = (value == null);
        if (wasNull) return null;
        
        if (value instanceof Date) {
            return (Date) value;
        }
        if (value instanceof java.util.Date) {
            return new Date(((java.util.Date) value).getTime());
        }
        try {
            return Date.valueOf(value.toString());
        } catch (IllegalArgumentException e) {
            throw new SQLException("Cannot convert to Date: " + value, e);
        }
    }
    
    @Override
    public Time getTime(int columnIndex) throws SQLException {
        checkClosed();
        checkValidColumn(columnIndex);
        checkValidRow();
        
        Object value = rows.get(currentRow).get(columnIndex - 1);
        wasNull = (value == null);
        if (wasNull) return null;
        
        if (value instanceof Time) {
            return (Time) value;
        }
        if (value instanceof java.util.Date) {
            return new Time(((java.util.Date) value).getTime());
        }
        try {
            return Time.valueOf(value.toString());
        } catch (IllegalArgumentException e) {
            throw new SQLException("Cannot convert to Time: " + value, e);
        }
    }
    
    @Override
    public Timestamp getTimestamp(int columnIndex) throws SQLException {
        checkClosed();
        checkValidColumn(columnIndex);
        checkValidRow();
        
        Object value = rows.get(currentRow).get(columnIndex - 1);
        wasNull = (value == null);
        if (wasNull) return null;
        
        if (value instanceof Timestamp) {
            return (Timestamp) value;
        }
        if (value instanceof java.util.Date) {
            return new Timestamp(((java.util.Date) value).getTime());
        }
        try {
            return Timestamp.valueOf(value.toString());
        } catch (IllegalArgumentException e) {
            throw new SQLException("Cannot convert to Timestamp: " + value, e);
        }
    }
    
    @Override
    public InputStream getAsciiStream(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("ASCII streams not supported");
    }
    
    @Override
    public InputStream getUnicodeStream(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("Unicode streams not supported");
    }
    
    @Override
    public InputStream getBinaryStream(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("Binary streams not supported");
    }
    
    // String-based getters delegate to index-based ones
    @Override
    public String getString(String columnLabel) throws SQLException {
        return getString(findColumn(columnLabel));
    }
    
    @Override
    public boolean getBoolean(String columnLabel) throws SQLException {
        return getBoolean(findColumn(columnLabel));
    }
    
    @Override
    public byte getByte(String columnLabel) throws SQLException {
        return getByte(findColumn(columnLabel));
    }
    
    @Override
    public short getShort(String columnLabel) throws SQLException {
        return getShort(findColumn(columnLabel));
    }
    
    @Override
    public int getInt(String columnLabel) throws SQLException {
        return getInt(findColumn(columnLabel));
    }
    
    @Override
    public long getLong(String columnLabel) throws SQLException {
        return getLong(findColumn(columnLabel));
    }
    
    @Override
    public float getFloat(String columnLabel) throws SQLException {
        return getFloat(findColumn(columnLabel));
    }
    
    @Override
    public double getDouble(String columnLabel) throws SQLException {
        return getDouble(findColumn(columnLabel));
    }
    
    @Override
    public BigDecimal getBigDecimal(String columnLabel, int scale) throws SQLException {
        return getBigDecimal(findColumn(columnLabel), scale);
    }
    
    @Override
    public byte[] getBytes(String columnLabel) throws SQLException {
        return getBytes(findColumn(columnLabel));
    }
    
    @Override
    public Date getDate(String columnLabel) throws SQLException {
        return getDate(findColumn(columnLabel));
    }
    
    @Override
    public Time getTime(String columnLabel) throws SQLException {
        return getTime(findColumn(columnLabel));
    }
    
    @Override
    public Timestamp getTimestamp(String columnLabel) throws SQLException {
        return getTimestamp(findColumn(columnLabel));
    }
    
    @Override
    public InputStream getAsciiStream(String columnLabel) throws SQLException {
        return getAsciiStream(findColumn(columnLabel));
    }
    
    @Override
    public InputStream getUnicodeStream(String columnLabel) throws SQLException {
        return getUnicodeStream(findColumn(columnLabel));
    }
    
    @Override
    public InputStream getBinaryStream(String columnLabel) throws SQLException {
        return getBinaryStream(findColumn(columnLabel));
    }
    
    @Override
    public SQLWarning getWarnings() throws SQLException {
        checkClosed();
        return null;
    }
    
    @Override
    public void clearWarnings() throws SQLException {
        checkClosed();
    }
    
    @Override
    public String getCursorName() throws SQLException {
        throw new SQLFeatureNotSupportedException("Named cursors not supported");
    }
    
    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        checkClosed();
        return new PGliteResultSetMetaData(columnNames, columnTypes);
    }
    
    @Override
    public Object getObject(int columnIndex) throws SQLException {
        checkClosed();
        checkValidColumn(columnIndex);
        checkValidRow();
        
        Object value = rows.get(currentRow).get(columnIndex - 1);
        wasNull = (value == null);
        return value;
    }
    
    @Override
    public Object getObject(String columnLabel) throws SQLException {
        return getObject(findColumn(columnLabel));
    }
    
    @Override
    public int findColumn(String columnLabel) throws SQLException {
        checkClosed();
        Integer index = columnNameToIndex.get(columnLabel.toLowerCase());
        if (index == null) {
            throw new SQLException("Column not found: " + columnLabel);
        }
        return index;
    }
    
    @Override
    public Reader getCharacterStream(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("Character streams not supported");
    }
    
    @Override
    public Reader getCharacterStream(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("Character streams not supported");
    }
    
    @Override
    public BigDecimal getBigDecimal(int columnIndex) throws SQLException {
        return getBigDecimal(columnIndex, 0);
    }
    
    @Override
    public BigDecimal getBigDecimal(String columnLabel) throws SQLException {
        return getBigDecimal(findColumn(columnLabel));
    }
    
    @Override
    public boolean isBeforeFirst() throws SQLException {
        checkClosed();
        return currentRow == -1 && !rows.isEmpty();
    }
    
    @Override
    public boolean isAfterLast() throws SQLException {
        checkClosed();
        return currentRow >= rows.size() && !rows.isEmpty();
    }
    
    @Override
    public boolean isFirst() throws SQLException {
        checkClosed();
        return currentRow == 0 && !rows.isEmpty();
    }
    
    @Override
    public boolean isLast() throws SQLException {
        checkClosed();
        return currentRow == rows.size() - 1 && !rows.isEmpty();
    }
    
    @Override
    public void beforeFirst() throws SQLException {
        checkClosed();
        currentRow = -1;
    }
    
    @Override
    public void afterLast() throws SQLException {
        checkClosed();
        currentRow = rows.size();
    }
    
    @Override
    public boolean first() throws SQLException {
        checkClosed();
        if (rows.isEmpty()) {
            return false;
        }
        currentRow = 0;
        return true;
    }
    
    @Override
    public boolean last() throws SQLException {
        checkClosed();
        if (rows.isEmpty()) {
            return false;
        }
        currentRow = rows.size() - 1;
        return true;
    }
    
    @Override
    public int getRow() throws SQLException {
        checkClosed();
        if (currentRow < 0 || currentRow >= rows.size()) {
            return 0;
        }
        return currentRow + 1; // 1-based row number
    }
    
    @Override
    public boolean absolute(int row) throws SQLException {
        checkClosed();
        if (row == 0) {
            beforeFirst();
            return false;
        }
        
        if (row > 0) {
            if (row <= rows.size()) {
                currentRow = row - 1; // Convert to 0-based
                return true;
            } else {
                afterLast();
                return false;
            }
        } else {
            // Negative row number - count from end
            int targetRow = rows.size() + row;
            if (targetRow >= 0) {
                currentRow = targetRow;
                return true;
            } else {
                beforeFirst();
                return false;
            }
        }
    }
    
    @Override
    public boolean relative(int rowOffset) throws SQLException {
        checkClosed();
        return absolute(getRow() + rowOffset);
    }
    
    @Override
    public boolean previous() throws SQLException {
        checkClosed();
        if (currentRow > 0) {
            currentRow--;
            return true;
        }
        return false;
    }
    
    @Override
    public void setFetchDirection(int direction) throws SQLException {
        checkClosed();
        if (direction != FETCH_FORWARD) {
            throw new SQLFeatureNotSupportedException("Only FETCH_FORWARD supported");
        }
    }
    
    @Override
    public int getFetchDirection() throws SQLException {
        checkClosed();
        return FETCH_FORWARD;
    }
    
    @Override
    public void setFetchSize(int rows) throws SQLException {
        checkClosed();
        // Ignore
    }
    
    @Override
    public int getFetchSize() throws SQLException {
        checkClosed();
        return 0;
    }
    
    @Override
    public int getType() throws SQLException {
        checkClosed();
        return TYPE_FORWARD_ONLY;
    }
    
    @Override
    public int getConcurrency() throws SQLException {
        checkClosed();
        return CONCUR_READ_ONLY;
    }
    
    @Override
    public boolean rowUpdated() throws SQLException {
        checkClosed();
        return false;
    }
    
    @Override
    public boolean rowInserted() throws SQLException {
        checkClosed();
        return false;
    }
    
    @Override
    public boolean rowDeleted() throws SQLException {
        checkClosed();
        return false;
    }
    
    // Update methods - not supported
    @Override
    public void updateNull(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateBoolean(int columnIndex, boolean x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateByte(int columnIndex, byte x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateShort(int columnIndex, short x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateInt(int columnIndex, int x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateLong(int columnIndex, long x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateFloat(int columnIndex, float x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateDouble(int columnIndex, double x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateString(int columnIndex, String x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateBytes(int columnIndex, byte[] x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateDate(int columnIndex, Date x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateTime(int columnIndex, Time x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateTimestamp(int columnIndex, Timestamp x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateAsciiStream(int columnIndex, InputStream x, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateBinaryStream(int columnIndex, InputStream x, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateCharacterStream(int columnIndex, Reader x, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateObject(int columnIndex, Object x, int scaleOrLength) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateObject(int columnIndex, Object x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    // String-based update methods
    @Override
    public void updateNull(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateBoolean(String columnLabel, boolean x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateByte(String columnLabel, byte x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateShort(String columnLabel, short x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateInt(String columnLabel, int x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateLong(String columnLabel, long x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateFloat(String columnLabel, float x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateDouble(String columnLabel, double x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateBigDecimal(String columnLabel, BigDecimal x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateString(String columnLabel, String x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateBytes(String columnLabel, byte[] x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateDate(String columnLabel, Date x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateTime(String columnLabel, Time x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateTimestamp(String columnLabel, Timestamp x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateAsciiStream(String columnLabel, InputStream x, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateBinaryStream(String columnLabel, InputStream x, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateCharacterStream(String columnLabel, Reader reader, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateObject(String columnLabel, Object x, int scaleOrLength) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateObject(String columnLabel, Object x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void insertRow() throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateRow() throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void deleteRow() throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void refreshRow() throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void cancelRowUpdates() throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void moveToInsertRow() throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void moveToCurrentRow() throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public Statement getStatement() throws SQLException {
        checkClosed();
        return statement;
    }
    
    @Override
    public Object getObject(int columnIndex, Map<String, Class<?>> map) throws SQLException {
        return getObject(columnIndex);
    }
    
    @Override
    public Ref getRef(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("Ref not supported");
    }
    
    @Override
    public Blob getBlob(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("Blob not supported");
    }
    
    @Override
    public Clob getClob(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("Clob not supported");
    }
    
    @Override
    public Array getArray(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("Array not supported");
    }
    
    @Override
    public Object getObject(String columnLabel, Map<String, Class<?>> map) throws SQLException {
        return getObject(findColumn(columnLabel), map);
    }
    
    @Override
    public Ref getRef(String columnLabel) throws SQLException {
        return getRef(findColumn(columnLabel));
    }
    
    @Override
    public Blob getBlob(String columnLabel) throws SQLException {
        return getBlob(findColumn(columnLabel));
    }
    
    @Override
    public Clob getClob(String columnLabel) throws SQLException {
        return getClob(findColumn(columnLabel));
    }
    
    @Override
    public Array getArray(String columnLabel) throws SQLException {
        return getArray(findColumn(columnLabel));
    }
    
    @Override
    public Date getDate(int columnIndex, Calendar cal) throws SQLException {
        return getDate(columnIndex);
    }
    
    @Override
    public Date getDate(String columnLabel, Calendar cal) throws SQLException {
        return getDate(findColumn(columnLabel), cal);
    }
    
    @Override
    public Time getTime(int columnIndex, Calendar cal) throws SQLException {
        return getTime(columnIndex);
    }
    
    @Override
    public Time getTime(String columnLabel, Calendar cal) throws SQLException {
        return getTime(findColumn(columnLabel), cal);
    }
    
    @Override
    public Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException {
        return getTimestamp(columnIndex);
    }
    
    @Override
    public Timestamp getTimestamp(String columnLabel, Calendar cal) throws SQLException {
        return getTimestamp(findColumn(columnLabel), cal);
    }
    
    @Override
    public URL getURL(int columnIndex) throws SQLException {
        String urlString = getString(columnIndex);
        if (urlString == null || wasNull) {
            return null;
        }
        try {
            return new URL(urlString);
        } catch (Exception e) {
            throw new SQLException("Invalid URL: " + urlString, e);
        }
    }
    
    @Override
    public URL getURL(String columnLabel) throws SQLException {
        return getURL(findColumn(columnLabel));
    }
    
    @Override
    public void updateRef(int columnIndex, Ref x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateRef(String columnLabel, Ref x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateBlob(int columnIndex, Blob x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateBlob(String columnLabel, Blob x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateClob(int columnIndex, Clob x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateClob(String columnLabel, Clob x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateArray(int columnIndex, Array x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateArray(String columnLabel, Array x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public RowId getRowId(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("RowId not supported");
    }
    
    @Override
    public RowId getRowId(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("RowId not supported");
    }
    
    @Override
    public void updateRowId(int columnIndex, RowId x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateRowId(String columnLabel, RowId x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public int getHoldability() throws SQLException {
        checkClosed();
        return CLOSE_CURSORS_AT_COMMIT;
    }
    
    @Override
    public boolean isClosed() throws SQLException {
        return closed;
    }
    
    @Override
    public void updateNString(int columnIndex, String nString) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateNString(String columnLabel, String nString) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateNClob(int columnIndex, NClob nClob) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateNClob(String columnLabel, NClob nClob) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public NClob getNClob(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("NClob not supported");
    }
    
    @Override
    public NClob getNClob(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("NClob not supported");
    }
    
    @Override
    public SQLXML getSQLXML(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("SQLXML not supported");
    }
    
    @Override
    public SQLXML getSQLXML(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("SQLXML not supported");
    }
    
    @Override
    public void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateSQLXML(String columnLabel, SQLXML xmlObject) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public String getNString(int columnIndex) throws SQLException {
        return getString(columnIndex);
    }
    
    @Override
    public String getNString(String columnLabel) throws SQLException {
        return getString(columnLabel);
    }
    
    @Override
    public Reader getNCharacterStream(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("NCharacter streams not supported");
    }
    
    @Override
    public Reader getNCharacterStream(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("NCharacter streams not supported");
    }
    
    @Override
    public void updateNCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateNCharacterStream(String columnLabel, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateAsciiStream(int columnIndex, InputStream x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateBinaryStream(int columnIndex, InputStream x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateAsciiStream(String columnLabel, InputStream x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateBinaryStream(String columnLabel, InputStream x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateCharacterStream(String columnLabel, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateBlob(int columnIndex, InputStream inputStream, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateBlob(String columnLabel, InputStream inputStream, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateClob(int columnIndex, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateClob(String columnLabel, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateNClob(int columnIndex, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateNClob(String columnLabel, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateNCharacterStream(int columnIndex, Reader x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateNCharacterStream(String columnLabel, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateAsciiStream(int columnIndex, InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateBinaryStream(int columnIndex, InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateCharacterStream(int columnIndex, Reader x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateAsciiStream(String columnLabel, InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateBinaryStream(String columnLabel, InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateCharacterStream(String columnLabel, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateBlob(int columnIndex, InputStream inputStream) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateBlob(String columnLabel, InputStream inputStream) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateClob(int columnIndex, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateClob(String columnLabel, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateNClob(int columnIndex, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public void updateNClob(String columnLabel, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("Updates not supported");
    }
    
    @Override
    public <T> T getObject(int columnIndex, Class<T> type) throws SQLException {
        throw new SQLFeatureNotSupportedException("Typed getObject not supported yet");
    }
    
    @Override
    public <T> T getObject(String columnLabel, Class<T> type) throws SQLException {
        return getObject(findColumn(columnLabel), type);
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
    
    private void checkClosed() throws SQLException {
        if (closed) {
            throw new SQLException("ResultSet is closed");
        }
    }
    
    private void checkValidColumn(int columnIndex) throws SQLException {
        if (columnIndex < 1 || columnIndex > columnNames.size()) {
            throw new SQLException("Column index out of range: " + columnIndex);
        }
    }
    
    private void checkValidRow() throws SQLException {
        if (currentRow < 0 || currentRow >= rows.size()) {
            throw new SQLException("No current row");
        }
    }
}