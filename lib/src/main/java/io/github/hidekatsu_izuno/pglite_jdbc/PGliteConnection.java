package io.github.hidekatsu_izuno.pglite_jdbc;

import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

public class PGliteConnection implements Connection {
    
    private final PGliteWasmEngine wasmEngine;
    private final String databasePath;
    private boolean closed = false;
    private boolean autoCommit = true;
    private boolean readOnly = false;
    private int transactionIsolation = Connection.TRANSACTION_READ_COMMITTED;
    private SQLWarning firstWarning = null;
    
    public PGliteConnection(String databasePath, Properties info) throws SQLException {
        this.databasePath = databasePath;
        this.wasmEngine = new PGliteWasmEngine();
        
        // Initialize the WASM instance
        try {
            wasmEngine.getInstance();
        } catch (Exception e) {
            throw new SQLException("Failed to initialize PGlite WASM engine", e);
        }
    }
    
    @Override
    public Statement createStatement() throws SQLException {
        checkClosed();
        return new PGliteStatement(this, wasmEngine);
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        checkClosed();
        return new PGlitePreparedStatement(this, wasmEngine, sql);
    }
    
    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        throw new SQLFeatureNotSupportedException("Callable statements not supported");
    }
    
    @Override
    public String nativeSQL(String sql) throws SQLException {
        return sql; // No translation needed
    }
    
    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        checkClosed();
        this.autoCommit = autoCommit;
    }
    
    @Override
    public boolean getAutoCommit() throws SQLException {
        checkClosed();
        return autoCommit;
    }
    
    @Override
    public void commit() throws SQLException {
        checkClosed();
        if (autoCommit) {
            throw new SQLException("Cannot commit when in auto-commit mode");
        }
        try {
            // Execute COMMIT through WASM engine
            wasmEngine.executeUpdate("COMMIT");
        } catch (Exception e) {
            throw new SQLException("Failed to commit transaction", e);
        }
    }
    
    @Override
    public void rollback() throws SQLException {
        checkClosed();
        if (autoCommit) {
            throw new SQLException("Cannot rollback when in auto-commit mode");
        }
        try {
            // Execute ROLLBACK through WASM engine
            wasmEngine.executeUpdate("ROLLBACK");
        } catch (Exception e) {
            throw new SQLException("Failed to rollback transaction", e);
        }
    }
    
    @Override
    public void close() throws SQLException {
        if (!closed) {
            closed = true;
            // Cleanup WASM resources
            try {
                // Close any open transactions
                if (!autoCommit) {
                    rollback();
                }
                // Note: WASM engine cleanup would happen here
                // The WASM instance will be garbage collected when this connection is disposed
            } catch (Exception e) {
                // Log but don't throw - close should be idempotent
                System.err.println("Warning: Error during connection cleanup: " + e.getMessage());
            }
        }
    }
    
    @Override
    public boolean isClosed() throws SQLException {
        return closed;
    }
    
    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        checkClosed();
        return new PGliteDatabaseMetaData(this);
    }
    
    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        checkClosed();
        this.readOnly = readOnly;
        // In a full implementation, this would configure the WASM engine
        // to reject write operations when in read-only mode
    }
    
    @Override
    public boolean isReadOnly() throws SQLException {
        checkClosed();
        return readOnly;
    }
    
    @Override
    public void setCatalog(String catalog) throws SQLException {
        checkClosed();
        // PGlite doesn't support multiple catalogs
    }
    
    @Override
    public String getCatalog() throws SQLException {
        checkClosed();
        // PGlite uses a single database, return the database name
        return "postgres";
    }
    
    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        checkClosed();
        this.transactionIsolation = level;
    }
    
    @Override
    public int getTransactionIsolation() throws SQLException {
        checkClosed();
        return transactionIsolation;
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
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        return createStatement();
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        return prepareStatement(sql);
    }
    
    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        throw new SQLFeatureNotSupportedException("Callable statements not supported");
    }
    
    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        throw new SQLFeatureNotSupportedException("Type maps not supported");
    }
    
    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        throw new SQLFeatureNotSupportedException("Type maps not supported");
    }
    
    @Override
    public void setHoldability(int holdability) throws SQLException {
        checkClosed();
    }
    
    @Override
    public int getHoldability() throws SQLException {
        checkClosed();
        return ResultSet.CLOSE_CURSORS_AT_COMMIT;
    }
    
    @Override
    public Savepoint setSavepoint() throws SQLException {
        throw new SQLFeatureNotSupportedException("Savepoints not supported");
    }
    
    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        throw new SQLFeatureNotSupportedException("Savepoints not supported");
    }
    
    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        throw new SQLFeatureNotSupportedException("Savepoints not supported");
    }
    
    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        throw new SQLFeatureNotSupportedException("Savepoints not supported");
    }
    
    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return createStatement();
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return prepareStatement(sql);
    }
    
    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        throw new SQLFeatureNotSupportedException("Callable statements not supported");
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        return prepareStatement(sql);
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        return prepareStatement(sql);
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        return prepareStatement(sql);
    }
    
    @Override
    public Clob createClob() throws SQLException {
        throw new SQLFeatureNotSupportedException("Clob not supported");
    }
    
    @Override
    public Blob createBlob() throws SQLException {
        throw new SQLFeatureNotSupportedException("Blob not supported");
    }
    
    @Override
    public NClob createNClob() throws SQLException {
        throw new SQLFeatureNotSupportedException("NClob not supported");
    }
    
    @Override
    public SQLXML createSQLXML() throws SQLException {
        throw new SQLFeatureNotSupportedException("SQLXML not supported");
    }
    
    @Override
    public boolean isValid(int timeout) throws SQLException {
        return !closed;
    }
    
    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        // Ignore client info
    }
    
    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        // Ignore client info
    }
    
    @Override
    public String getClientInfo(String name) throws SQLException {
        return null;
    }
    
    @Override
    public Properties getClientInfo() throws SQLException {
        return new Properties();
    }
    
    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        throw new SQLFeatureNotSupportedException("Arrays not supported");
    }
    
    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        throw new SQLFeatureNotSupportedException("Structs not supported");
    }
    
    @Override
    public void setSchema(String schema) throws SQLException {
        checkClosed();
        // PGlite uses public schema by default
    }
    
    @Override
    public String getSchema() throws SQLException {
        checkClosed();
        return "public";
    }
    
    @Override
    public void abort(Executor executor) throws SQLException {
        close();
    }
    
    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        throw new SQLFeatureNotSupportedException("Network timeout not supported");
    }
    
    @Override
    public int getNetworkTimeout() throws SQLException {
        throw new SQLFeatureNotSupportedException("Network timeout not supported");
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
            throw new SQLException("Connection is closed");
        }
    }
    
    // Helper method to add warnings
    void addWarning(SQLWarning warning) {
        if (firstWarning == null) {
            firstWarning = warning;
        } else {
            firstWarning.setNextWarning(warning);
        }
    }
    
    // Package-private getter for internal use
    PGliteWasmEngine getWasmEngine() {
        return wasmEngine;
    }
    
    String getDatabasePath() {
        return databasePath;
    }
}