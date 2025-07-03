package io.github.hidekatsu_izuno.pglite_jdbc;

import java.sql.*;

/**
 * PGlite JDBC Statement implementation
 */
public class PGliteStatement implements Statement {
    protected PGliteConnection connection;
    protected PGliteWasmEngine wasmEngine;
    protected boolean closed = false;
    protected ResultSet currentResultSet;
    
    public PGliteStatement(PGliteConnection connection, PGliteWasmEngine wasmEngine) {
        this.connection = connection;
        this.wasmEngine = wasmEngine;
    }
    
    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        checkClosed();
        
        try {
            String response = wasmEngine.executeQuery(sql);
            currentResultSet = new PGliteResultSet(response);
            return currentResultSet;
        } catch (Exception e) {
            throw new SQLException("Error executing query: " + sql, e);
        }
    }
    
    @Override
    public int executeUpdate(String sql) throws SQLException {
        checkClosed();
        
        try {
            String response = wasmEngine.executeQuery(sql);
            // Parse response to get affected rows count
            // For now, return 0 as placeholder
            return 0;
        } catch (Exception e) {
            throw new SQLException("Error executing update: " + sql, e);
        }
    }
    
    @Override
    public void close() throws SQLException {
        if (!closed) {
            if (currentResultSet != null) {
                currentResultSet.close();
            }
            closed = true;
        }
    }
    
    @Override
    public int getMaxFieldSize() throws SQLException {
        return 0;
    }
    
    @Override
    public void setMaxFieldSize(int max) throws SQLException {
        // No-op
    }
    
    @Override
    public int getMaxRows() throws SQLException {
        return 0;
    }
    
    @Override
    public void setMaxRows(int max) throws SQLException {
        // No-op
    }
    
    @Override
    public void setEscapeProcessing(boolean enable) throws SQLException {
        // No-op
    }
    
    @Override
    public int getQueryTimeout() throws SQLException {
        return 0;
    }
    
    @Override
    public void setQueryTimeout(int seconds) throws SQLException {
        // No-op
    }
    
    @Override
    public void cancel() throws SQLException {
        // No-op
    }
    
    @Override
    public SQLWarning getWarnings() throws SQLException {
        return null;
    }
    
    @Override
    public void clearWarnings() throws SQLException {
        // No-op
    }
    
    @Override
    public void setCursorName(String name) throws SQLException {
        throw new SQLFeatureNotSupportedException("Named cursors not supported");
    }
    
    @Override
    public boolean execute(String sql) throws SQLException {
        checkClosed();
        
        try {
            String response = wasmEngine.executeQuery(sql);
            currentResultSet = new PGliteResultSet(response);
            return true; // Assume query returns result set
        } catch (Exception e) {
            throw new SQLException("Error executing statement: " + sql, e);
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
        return -1; // No more results
    }
    
    @Override
    public boolean getMoreResults() throws SQLException {
        checkClosed();
        if (currentResultSet != null) {
            currentResultSet.close();
            currentResultSet = null;
        }
        return false;
    }
    
    @Override
    public void setFetchDirection(int direction) throws SQLException {
        // No-op
    }
    
    @Override
    public int getFetchDirection() throws SQLException {
        return ResultSet.FETCH_FORWARD;
    }
    
    @Override
    public void setFetchSize(int rows) throws SQLException {
        // No-op
    }
    
    @Override
    public int getFetchSize() throws SQLException {
        return 0;
    }
    
    @Override
    public int getResultSetConcurrency() throws SQLException {
        return ResultSet.CONCUR_READ_ONLY;
    }
    
    @Override
    public int getResultSetType() throws SQLException {
        return ResultSet.TYPE_FORWARD_ONLY;
    }
    
    @Override
    public void addBatch(String sql) throws SQLException {
        throw new SQLFeatureNotSupportedException("Batch operations not supported");
    }
    
    @Override
    public void clearBatch() throws SQLException {
        throw new SQLFeatureNotSupportedException("Batch operations not supported");
    }
    
    @Override
    public int[] executeBatch() throws SQLException {
        throw new SQLFeatureNotSupportedException("Batch operations not supported");
    }
    
    @Override
    public Connection getConnection() throws SQLException {
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
        return ResultSet.HOLD_CURSORS_OVER_COMMIT;
    }
    
    @Override
    public boolean isClosed() throws SQLException {
        return closed;
    }
    
    @Override
    public void setPoolable(boolean poolable) throws SQLException {
        // No-op
    }
    
    @Override
    public boolean isPoolable() throws SQLException {
        return false;
    }
    
    @Override
    public void closeOnCompletion() throws SQLException {
        // No-op
    }
    
    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        return false;
    }
    
    protected void checkClosed() throws SQLException {
        if (closed) {
            throw new SQLException("Statement is closed");
        }
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