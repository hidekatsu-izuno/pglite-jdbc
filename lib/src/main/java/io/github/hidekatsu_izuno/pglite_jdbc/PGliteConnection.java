package io.github.hidekatsu_izuno.pglite_jdbc;

import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * PGlite JDBC Connection implementation
 */
public class PGliteConnection implements Connection {
    private PGliteWasmEngine wasmEngine;
    private boolean closed = false;
    private boolean autoCommit = true;
    
    public PGliteConnection() throws SQLException {
        this(new PGliteWasmEngine());
    }
    
    public PGliteConnection(PGliteWasmEngine wasmEngine) throws SQLException {
        try {
            this.wasmEngine = wasmEngine;
            wasmEngine.initialize();
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
        return sql;
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
        wasmEngine.executeQuery("COMMIT");
    }
    
    @Override
    public void rollback() throws SQLException {
        checkClosed();
        if (autoCommit) {
            throw new SQLException("Cannot rollback when in auto-commit mode");
        }
        wasmEngine.executeQuery("ROLLBACK");
    }
    
    @Override
    public void close() throws SQLException {
        if (!closed) {
            wasmEngine.shutdown();
            closed = true;
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
        // PostgreSQL doesn't have a direct read-only mode at connection level
    }
    
    @Override
    public boolean isReadOnly() throws SQLException {
        checkClosed();
        return false;
    }
    
    @Override
    public void setCatalog(String catalog) throws SQLException {
        checkClosed();
        // PostgreSQL doesn't use catalogs in the same way as other databases
    }
    
    @Override
    public String getCatalog() throws SQLException {
        checkClosed();
        return null;
    }
    
    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        checkClosed();
        String isolationLevel;
        switch (level) {
            case TRANSACTION_READ_UNCOMMITTED:
                isolationLevel = "READ UNCOMMITTED";
                break;
            case TRANSACTION_READ_COMMITTED:
                isolationLevel = "READ COMMITTED";
                break;
            case TRANSACTION_REPEATABLE_READ:
                isolationLevel = "REPEATABLE READ";
                break;
            case TRANSACTION_SERIALIZABLE:
                isolationLevel = "SERIALIZABLE";
                break;
            default:
                throw new SQLException("Unsupported transaction isolation level: " + level);
        }
        wasmEngine.executeQuery("SET TRANSACTION ISOLATION LEVEL " + isolationLevel);
    }
    
    @Override
    public int getTransactionIsolation() throws SQLException {
        checkClosed();
        return TRANSACTION_READ_COMMITTED; // Default for PostgreSQL
    }
    
    @Override
    public SQLWarning getWarnings() throws SQLException {
        checkClosed();
        return null;
    }
    
    @Override
    public void clearWarnings() throws SQLException {
        checkClosed();
        // No-op for now
    }
    
    private void checkClosed() throws SQLException {
        if (closed) {
            throw new SQLException("Connection is closed");
        }
    }
    
    // Additional methods required by Connection interface
    // Most are not implemented for basic functionality
    
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
        // No-op
    }
    
    @Override
    public int getHoldability() throws SQLException {
        return ResultSet.HOLD_CURSORS_OVER_COMMIT;
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
        throw new SQLFeatureNotSupportedException("CLOBs not supported");
    }
    
    @Override
    public Blob createBlob() throws SQLException {
        throw new SQLFeatureNotSupportedException("BLOBs not supported");
    }
    
    @Override
    public NClob createNClob() throws SQLException {
        throw new SQLFeatureNotSupportedException("NCLOBs not supported");
    }
    
    @Override
    public SQLXML createSQLXML() throws SQLException {
        throw new SQLFeatureNotSupportedException("SQLXML not supported");
    }
    
    @Override
    public boolean isValid(int timeout) throws SQLException {
        return !closed && wasmEngine.isInitialized();
    }
    
    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        // No-op
    }
    
    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        // No-op
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
        wasmEngine.executeQuery("SET search_path TO " + schema);
    }
    
    @Override
    public String getSchema() throws SQLException {
        checkClosed();
        return "public"; // Default schema
    }
    
    @Override
    public void abort(Executor executor) throws SQLException {
        close();
    }
    
    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        // No-op for embedded database
    }
    
    @Override
    public int getNetworkTimeout() throws SQLException {
        return 0;
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