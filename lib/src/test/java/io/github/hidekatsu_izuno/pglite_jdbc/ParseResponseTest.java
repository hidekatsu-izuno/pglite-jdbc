package io.github.hidekatsu_izuno.pglite_jdbc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;

/**
 * Test the parseResponse functionality specifically
 */
public class ParseResponseTest {
    @Test
    public void testPostgreSQLResponseParsing() throws SQLException {
        // Test various PostgreSQL-like responses
        testResponseFormat("SELECT 1 returned: 1", true, 1, "?column?");
        testResponseFormat("Result: 42", true, 1, "?column?");
        testResponseFormat("INSERT COMPLETE", false, 0, null);
        testResponseFormat("ERROR: syntax error", false, 0, null);
    }
    
    private void testResponseFormat(String response, boolean expectData, int expectedRows, String expectedColumn) throws SQLException {
        // Create a ResultSet directly to test parsing
        PGliteResultSet resultSet = new PGliteResultSet(response);
        
        if (expectData) {
            // Should have data
            assertTrue(resultSet.next(), "Expected result set to have data for response: " + response);
            
            // Check metadata
            ResultSetMetaData metadata = resultSet.getMetaData();
            assertEquals(1, metadata.getColumnCount(), "Expected 1 column for response: " + response);
            
            if (expectedColumn != null) {
                assertEquals(expectedColumn, metadata.getColumnName(1), "Column name mismatch for response: " + response);
            }
            
            // Should be exactly one row for our test cases
            assertFalse(resultSet.next(), "Expected only one row for response: " + response);
        } else {
            // Should not have data
            assertFalse(resultSet.next(), "Expected no data for response: " + response);
            
            // Check metadata shows no columns
            ResultSetMetaData metadata = resultSet.getMetaData();
            assertEquals(0, metadata.getColumnCount(), "Expected 0 columns for response: " + response);
        }
        
        resultSet.close();
    }
    
    @Test
    public void testSelectOneSpecifically() throws SQLException {
        // Test the specific "SELECT 1" case that we care about most
        PGliteResultSet resultSet = new PGliteResultSet("SELECT1:1");
        
        assertTrue(resultSet.next());
        assertEquals(1, resultSet.getInt(1));
        assertEquals("1", resultSet.getString(1));
        assertEquals(1L, resultSet.getLong(1));
        assertEquals(1.0, resultSet.getDouble(1), 0.001);
        
        ResultSetMetaData metadata = resultSet.getMetaData();
        assertEquals(1, metadata.getColumnCount());
        assertEquals("?column?", metadata.getColumnName(1));
        assertEquals(Types.INTEGER, metadata.getColumnType(1));
        
        assertFalse(resultSet.next());
        resultSet.close();
    }
}