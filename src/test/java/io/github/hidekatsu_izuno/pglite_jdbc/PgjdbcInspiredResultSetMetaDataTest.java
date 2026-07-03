package io.github.hidekatsu_izuno.pglite_jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Types;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PgjdbcInspiredResultSetMetaDataTest {
    private Connection connection;

    @BeforeAll
    void connect() throws Exception {
        connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000");
    }

    @AfterAll
    void disconnect() throws Exception {
        connection.close();
    }

    @Test
    void resultSetMetadataReportsLabelsTypesAndClassesLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("""
                 SELECT
                   1::int4 AS id,
                   2::int8 AS big_id,
                   3.50::numeric AS amount,
                   'body'::text AS body,
                   decode('0102', 'hex') AS payload
                 """)) {
            var metadata = resultSet.getMetaData();

            assertEquals(5, metadata.getColumnCount());
            assertEquals("id", metadata.getColumnLabel(1));
            assertEquals("id", metadata.getColumnName(1));
            assertEquals(Types.INTEGER, metadata.getColumnType(1));
            assertEquals(Integer.class.getName(), metadata.getColumnClassName(1));
            assertEquals(true, metadata.isSigned(1));
            assertEquals(false, metadata.isCaseSensitive(1));
            assertEquals(false, metadata.isReadOnly(1));
            assertEquals(true, metadata.isWritable(1));
            assertEquals(false, metadata.isDefinitelyWritable(1));

            assertEquals(Types.BIGINT, metadata.getColumnType(2));
            assertEquals(Long.class.getName(), metadata.getColumnClassName(2));
            assertEquals(true, metadata.isSigned(2));
            assertEquals(false, metadata.isCaseSensitive(2));
            assertEquals(Types.NUMERIC, metadata.getColumnType(3));
            assertEquals(java.math.BigDecimal.class.getName(), metadata.getColumnClassName(3));
            assertEquals(true, metadata.isSigned(3));
            assertEquals(false, metadata.isCaseSensitive(3));
            assertEquals(Types.VARCHAR, metadata.getColumnType(4));
            assertEquals(String.class.getName(), metadata.getColumnClassName(4));
            assertEquals(false, metadata.isSigned(4));
            assertEquals(true, metadata.isCaseSensitive(4));
            assertEquals(Types.BINARY, metadata.getColumnType(5));
            assertEquals(byte[].class.getName(), metadata.getColumnClassName(5));
            assertEquals(false, metadata.isSigned(5));
            assertEquals(true, metadata.isCaseSensitive(5));
        }
    }

    @Test
    void resultSetMetadataRejectsOutOfRangeColumnIndexes() throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT 1 AS value")) {
            var metadata = resultSet.getMetaData();

            assertThrows(SQLException.class, () -> metadata.getColumnLabel(0));
            assertThrows(SQLException.class, () -> metadata.getColumnLabel(2));
            assertThrows(SQLException.class, () -> metadata.getColumnType(0));
            assertThrows(SQLException.class, () -> metadata.getColumnClassName(2));
            assertThrows(SQLException.class, () -> metadata.isNullable(0));
            assertThrows(SQLException.class, () -> metadata.isSigned(2));
            assertThrows(SQLException.class, () -> metadata.getPrecision(0));
            assertThrows(SQLException.class, () -> metadata.getSchemaName(2));
        }
    }
}
