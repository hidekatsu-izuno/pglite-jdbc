package io.github.hidekatsu_izuno.pglite_jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Types;
import org.postgresql.PGResultSetMetaData;
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
                   3.50::numeric(8, 3) AS amount,
                   'body'::varchar(10) AS body,
                   decode('0102', 'hex') AS payload,
                   42::oid AS object_id,
                   12.34::money AS price,
                   B'1'::bit(1) AS flag_bit,
                   B'101'::varbit(3) AS bits
                 """)) {
            var metadata = resultSet.getMetaData();

            assertEquals(9, metadata.getColumnCount());
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
            assertEquals(8, metadata.getPrecision(3));
            assertEquals(3, metadata.getScale(3));
            assertEquals(10, metadata.getColumnDisplaySize(3));
            assertEquals(Types.VARCHAR, metadata.getColumnType(4));
            assertEquals(String.class.getName(), metadata.getColumnClassName(4));
            assertEquals(false, metadata.isSigned(4));
            assertEquals(true, metadata.isCaseSensitive(4));
            assertEquals(10, metadata.getPrecision(4));
            assertEquals(10, metadata.getColumnDisplaySize(4));
            assertEquals(Types.BINARY, metadata.getColumnType(5));
            assertEquals(byte[].class.getName(), metadata.getColumnClassName(5));
            assertEquals(false, metadata.isSigned(5));
            assertEquals(true, metadata.isCaseSensitive(5));
            assertEquals(Types.BIGINT, metadata.getColumnType(6));
            assertEquals(Long.class.getName(), metadata.getColumnClassName(6));
            assertEquals(false, metadata.isSigned(6));
            assertEquals(false, metadata.isCaseSensitive(6));
            assertEquals(Types.DOUBLE, metadata.getColumnType(7));
            assertEquals(Double.class.getName(), metadata.getColumnClassName(7));
            assertEquals(false, metadata.isSigned(7));
            assertEquals(true, metadata.isCaseSensitive(7));
            assertEquals(true, metadata.isCurrency(7));
            assertEquals(Types.BIT, metadata.getColumnType(8));
            assertEquals(Boolean.class.getName(), metadata.getColumnClassName(8));
            assertEquals(1, metadata.getPrecision(8));
            assertEquals(1, metadata.getColumnDisplaySize(8));
            assertEquals(Types.BIT, metadata.getColumnType(9));
            assertEquals(String.class.getName(), metadata.getColumnClassName(9));
            assertEquals(3, metadata.getPrecision(9));
            assertEquals(3, metadata.getColumnDisplaySize(9));
        }
    }

    @Test
    void resultSetMetadataReportsBaseNamesLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TEMP TABLE pgjdbc_rsmd_base(id serial PRIMARY KEY, name text)");
            statement.execute("INSERT INTO pgjdbc_rsmd_base VALUES (1, 'alice')");

            try (var resultSet = statement.executeQuery(
                    "SELECT id AS alias_id, name, id + 1 AS expression_value FROM pgjdbc_rsmd_base"
                )) {
                var resultSetMetaData = resultSet.getMetaData();
                var metadata = resultSetMetaData.unwrap(PGResultSetMetaData.class);

                assertEquals("id", metadata.getBaseColumnName(1));
                assertEquals("pgjdbc_rsmd_base", metadata.getBaseTableName(1));
                assertEquals("pg_temp", metadata.getBaseSchemaName(1).substring(0, 7));
                assertEquals(true, resultSetMetaData.isAutoIncrement(1));
                assertEquals("serial", resultSetMetaData.getColumnTypeName(1));
                assertEquals(java.sql.ResultSetMetaData.columnNoNulls, resultSetMetaData.isNullable(1));
                assertEquals("name", metadata.getBaseColumnName(2));
                assertEquals("pgjdbc_rsmd_base", metadata.getBaseTableName(2));
                assertEquals(false, resultSetMetaData.isAutoIncrement(2));
                assertEquals(java.sql.ResultSetMetaData.columnNullable, resultSetMetaData.isNullable(2));
                assertEquals("", metadata.getBaseColumnName(3));
                assertEquals("", metadata.getBaseTableName(3));
                assertEquals("", metadata.getBaseSchemaName(3));
                assertEquals(false, resultSetMetaData.isAutoIncrement(3));
                assertEquals(java.sql.ResultSetMetaData.columnNullableUnknown, resultSetMetaData.isNullable(3));
            }
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
