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
    protected SQLWarning firstWarning = null;
    
    public PGliteStatement(PGliteConnection connection, PGliteWasmEngine wasmEngine) {
        this.connection = connection;
        this.wasmEngine = wasmEngine;
    }
    
    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        checkClosed();
        
        updateCount = -1;
        
        // Execute SQL through WASM engine
        try {
            var result = wasmEngine.executeQuery(sql);
            currentResultSet = new PGliteResultSet(this, result.columnNames, result.columnTypes, result.rows);
        } catch (Exception e) {
            throw new SQLException("Failed to execute query: " + e.getMessage(), e);
        }
        
        return currentResultSet;
    }
    
    @Override
    public int executeUpdate(String sql) throws SQLException {
        checkClosed();
        
        // Execute SQL through WASM engine
        try {
            updateCount = wasmEngine.executeUpdate(sql);
            currentResultSet = null;
            return updateCount;
        } catch (Exception e) {
            throw new SQLException("Failed to execute update: " + e.getMessage(), e);
        }
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
        // Cancel any ongoing query in the WASM engine
        try {
            wasmEngine.cancelQuery();
        } catch (Exception e) {
            throw new SQLException("Failed to cancel query: " + e.getMessage(), e);
        }
    }
    
    @Override
    public SQLWarning getWarnings() throws SQLException {
        checkClosed();
        return firstWarning;
    }
    
    @Override
    public void clearWarnings() throws SQLException {
        checkClosed();
        firstWarning = null;
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
            
            // Check if we should close on completion
            if (closeOnCompletion) {
                close();
            }
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
    
    private boolean closeOnCompletion = false;
    
    @Override
    public void closeOnCompletion() throws SQLException {
        checkClosed();
        this.closeOnCompletion = true;
    }
    
    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        checkClosed();
        return closeOnCompletion;
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
    
    // Helper method to add warnings
    protected void addWarning(SQLWarning warning) {
        if (firstWarning == null) {
            firstWarning = warning;
        } else {
            firstWarning.setNextWarning(warning);
        }
    }
}