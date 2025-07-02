package io.github.hidekatsu_izuno.pglite_jdbc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;

class PGliteJdbcTest {
    
    @Test
    void testDriverRegistration() throws SQLException {
        // Test that the driver is automatically registered
        Driver driver = DriverManager.getDriver("jdbc:pglite:");
        assertNotNull(driver);
        assertTrue(driver instanceof PGliteDriver);
    }
    
    @Test
    void testConnectionCreation() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:pglite:");
        assertNotNull(conn);
        assertFalse(conn.isClosed());
        
        // Test basic connection properties
        assertTrue(conn.getAutoCommit());
        assertEquals("public", conn.getSchema());
        
        conn.close();
        assertTrue(conn.isClosed());
    }
    
    @Test
    void testStatement() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:pglite:")) {
            Statement stmt = conn.createStatement();
            assertNotNull(stmt);
            
            // Test result set from SELECT 2
            ResultSet rs = stmt.executeQuery("SELECT 2");
            assertNotNull(rs);
            assertTrue(rs.next()); // Should have one row
            assertEquals(2, rs.getInt(1)); // Should return 2
            assertFalse(rs.next()); // No more rows
            rs.close();
            
            // Test update statement
            int count = stmt.executeUpdate("CREATE TABLE test (id INTEGER)");
            assertEquals(0, count); // Stub returns 0
            
            stmt.close();
        }
    }
    
    @Test
    void testPreparedStatement() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:pglite:")) {
            PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM test WHERE id = ?");
            assertNotNull(pstmt);
            
            // Test parameter setting (stubbed for now)
            pstmt.setInt(1, 123);
            
            ResultSet rs = pstmt.executeQuery();
            assertNotNull(rs);
            assertFalse(rs.next()); // Empty result set for now
            rs.close();
            
            pstmt.close();
        }
    }
    
    @Test
    void testDatabaseMetaData() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:pglite:")) {
            DatabaseMetaData metaData = conn.getMetaData();
            assertNotNull(metaData);
            
            assertEquals("PGlite", metaData.getDatabaseProductName());
            assertEquals("PGlite JDBC Driver", metaData.getDriverName());
            assertEquals(0, metaData.getDriverMajorVersion());
            assertEquals(1, metaData.getDriverMinorVersion());
            assertTrue(metaData.supportsTransactions());
            assertFalse(metaData.supportsMultipleResultSets());
        }
    }
    
    @Test
    void testUrlAcceptance() throws SQLException {
        PGliteDriver driver = new PGliteDriver();
        
        assertTrue(driver.acceptsURL("jdbc:pglite:"));
        assertTrue(driver.acceptsURL("jdbc:pglite:memory"));
        assertTrue(driver.acceptsURL("jdbc:pglite:/path/to/db"));
        
        assertFalse(driver.acceptsURL("jdbc:postgresql://localhost/test"));
        assertFalse(driver.acceptsURL("jdbc:mysql://localhost/test"));
        assertFalse(driver.acceptsURL(null));
    }
    
    @Test
    void testConnectionWithPath() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:pglite:/tmp/testdb")) {
            assertNotNull(conn);
            assertFalse(conn.isClosed());
        }
    }
}