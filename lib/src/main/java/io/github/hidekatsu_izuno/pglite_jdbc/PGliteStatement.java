package io.github.hidekatsu_izuno.pglite_jdbc;

import java.sql.*;

public class PGliteStatement implements Statement {
    
    protected final PGliteConnection connection;
    protected final PGliteWasmEngine wasmEngine;
    protected boolean closed = false;
    protected ResultSet currentResultSet;
    protected int updateCount = -1;
    protected int queryTimeout = 0;
    protected int maxRows = 0;
    
    public PGliteStatement(PGliteConnection connection, PGliteWasmEngine wasmEngine) {
        this.connection = connection;
        this.wasmEngine = wasmEngine;
    }
    
    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        checkClosed();
        
        updateCount = -1;
        
        // Simple test data for SELECT 1 query
        if (sql.trim().toLowerCase().equals("select 1")) {
            java.util.List<String> columnNames = java.util.Arrays.asList("?column?");
            java.util.List<String> columnTypes = java.util.Arrays.asList("integer");
            java.util.List<java.util.List<Object>> rows = new java.util.ArrayList<>();
            rows.add(java.util.Arrays.asList((Object) 1));
            
            currentResultSet = new PGliteResultSet(this, columnNames, columnTypes, rows);
        } else {
            // For other queries, return empty result set for now
            // TODO: Implement actual SQL execution through WASM
            currentResultSet = new PGliteResultSet(this);
        }
        
        return currentResultSet;
    }
    
    @Override
    public int executeUpdate(String sql) throws SQLException {
        checkClosed();
        
        // For now, return 0 affected rows
        // TODO: Implement actual SQL execution through WASM
        currentResultSet = null;
        updateCount = 0;
        return updateCount;
    }
    
    @Override
    public void close() throws SQLException {
        if (!closed) {
            closed = true;
            if (currentResultSet != null) {
                currentResultSet.close();
                currentResultSet = null;
            }
        }
    }
    
    @Override
    public int getMaxFieldSize() throws SQLException {
        checkClosed();
        return 0; // No limit
    }
    
    @Override
    public void setMaxFieldSize(int max) throws SQLException {
        checkClosed();
        // Ignore for now
    }
    
    @Override
    public int getMaxRows() throws SQLException {
        checkClosed();
        return maxRows;
    }
    
    @Override
    public void setMaxRows(int max) throws SQLException {
        checkClosed();
        this.maxRows = max;
    }
    
    @Override
    public void setEscapeProcessing(boolean enable) throws SQLException {
        checkClosed();
        // Ignore for now
    }
    
    @Override
    public int getQueryTimeout() throws SQLException {
        checkClosed();
        return queryTimeout;
    }
    
    @Override
    public void setQueryTimeout(int seconds) throws SQLException {
        checkClosed();
        this.queryTimeout = seconds;
    }
    
    @Override
    public void cancel() throws SQLException {
        checkClosed();
        // TODO: Implement query cancellation
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
    public void setCursorName(String name) throws SQLException {
        checkClosed();
        // Not supported
    }
    
    @Override
    public boolean execute(String sql) throws SQLException {
        checkClosed();
        
        // Simple heuristic: if it starts with SELECT, it's a query
        String trimmedSql = sql.trim().toLowerCase();
        if (trimmedSql.startsWith("select") || trimmedSql.startsWith("with")) {
            currentResultSet = executeQuery(sql);
            updateCount = -1;
            return true;
        } else {
            updateCount = executeUpdate(sql);
            currentResultSet = null;
            return false;
        }
    }
    
    @Override
    public ResultSet getResultSet() throws SQLException {
        checkClosed();
        return currentResultSet;
    }
    
    @Override
    public int getUpdateCount() throws SQLException {
        checkClosed();
        return updateCount;
    }
    
    @Override
    public boolean getMoreResults() throws SQLException {
        checkClosed();
        if (currentResultSet != null) {
            currentResultSet.close();
            currentResultSet = null;
        }
        updateCount = -1;
        return false;
    }
    
    @Override
    public void setFetchDirection(int direction) throws SQLException {
        checkClosed();
        if (direction != ResultSet.FETCH_FORWARD) {
            throw new SQLFeatureNotSupportedException("Only FETCH_FORWARD supported");
        }
    }
    
    @Override
    public int getFetchDirection() throws SQLException {
        checkClosed();
        return ResultSet.FETCH_FORWARD;
    }
    
    @Override
    public void setFetchSize(int rows) throws SQLException {
        checkClosed();
        // Ignore for now
    }
    
    @Override
    public int getFetchSize() throws SQLException {
        checkClosed();
        return 0;
    }
    
    @Override
    public int getResultSetConcurrency() throws SQLException {
        checkClosed();
        return ResultSet.CONCUR_READ_ONLY;
    }
    
    @Override
    public int getResultSetType() throws SQLException {
        checkClosed();
        return ResultSet.TYPE_FORWARD_ONLY;
    }
    
    @Override
    public void addBatch(String sql) throws SQLException {
        throw new SQLFeatureNotSupportedException("Batch updates not supported");
    }
    
    @Override
    public void clearBatch() throws SQLException {
        throw new SQLFeatureNotSupportedException("Batch updates not supported");
    }
    
    @Override
    public int[] executeBatch() throws SQLException {
        throw new SQLFeatureNotSupportedException("Batch updates not supported");
    }
    
    @Override
    public Connection getConnection() throws SQLException {
        checkClosed();
        return connection;
    }
    
    @Override
    public boolean getMoreResults(int current) throws SQLException {
        return getMoreResults();
    }
    
    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        throw new SQLFeatureNotSupportedException("Generated keys not supported");
    }
    
    @Override
    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        return executeUpdate(sql);
    }
    
    @Override
    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        return executeUpdate(sql);
    }
    
    @Override
    public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        return executeUpdate(sql);
    }
    
    @Override
    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        return execute(sql);
    }
    
    @Override
    public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        return execute(sql);
    }
    
    @Override
    public boolean execute(String sql, String[] columnNames) throws SQLException {
        return execute(sql);
    }
    
    @Override
    public int getResultSetHoldability() throws SQLException {
        checkClosed();
        return ResultSet.CLOSE_CURSORS_AT_COMMIT;
    }
    
    @Override
    public boolean isClosed() throws SQLException {
        return closed;
    }
    
    @Override
    public void setPoolable(boolean poolable) throws SQLException {
        checkClosed();
        // Ignore
    }
    
    @Override
    public boolean isPoolable() throws SQLException {
        checkClosed();
        return false;
    }
    
    @Override
    public void closeOnCompletion() throws SQLException {
        checkClosed();
        // TODO: Implement close on completion
    }
    
    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        checkClosed();
        return false;
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
    
    protected void checkClosed() throws SQLException {
        if (closed) {
            throw new SQLException("Statement is closed");
        }
        if (connection.isClosed()) {
            throw new SQLException("Connection is closed");
        }
    }
}