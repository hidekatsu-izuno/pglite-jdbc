package io.github.hidekatsu_izuno.pglite_jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ParameterMetaData;
import java.sql.SQLException;
import java.sql.Types;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PgjdbcInspiredParameterMetaDataTest {
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
    void parameterMetadataReportsPgjdbcCompatibleTypesAndClasses() throws Exception {
        try (var prepared = connection.prepareStatement(
                 "SELECT ?::int4, ?::text, ?::numeric, ?::bytea, ?::timestamp"
             )) {
            var metadata = prepared.getParameterMetaData();

            assertEquals(5, metadata.getParameterCount());
            assertEquals(
                org.postgresql.jdbc.PgParameterMetaData.class,
                metadata.unwrap(org.postgresql.jdbc.PgParameterMetaData.class).getClass()
            );
            assertParameter(metadata, 1, Types.INTEGER, "int4", Integer.class.getName());
            assertParameter(metadata, 2, Types.VARCHAR, "text", String.class.getName());
            assertParameter(metadata, 3, Types.NUMERIC, "numeric", java.math.BigDecimal.class.getName());
            assertParameter(metadata, 4, Types.BINARY, "bytea", byte[].class.getName());
            assertParameter(metadata, 5, Types.TIMESTAMP, "timestamp", java.sql.Timestamp.class.getName());
        }
    }

    @Test
    void parameterMetadataRejectsOutOfRangeIndexesWithoutNullPointerException() throws Exception {
        try (var prepared = connection.prepareStatement("SELECT 1")) {
            var metadata = prepared.getParameterMetaData();

            assertEquals(0, metadata.getParameterCount());
            assertThrows(SQLException.class, () -> metadata.getParameterType(1));
            assertThrows(SQLException.class, () -> prepared.setString(1, "unused"));
        }

        try (var prepared = connection.prepareStatement("SELECT ?::int4")) {
            var metadata = prepared.getParameterMetaData();

            assertThrows(SQLException.class, () -> metadata.getParameterType(0));
            assertThrows(SQLException.class, () -> metadata.getParameterType(2));
            assertThrows(SQLException.class, () -> metadata.getParameterClassName(2));
            assertThrows(SQLException.class, () -> metadata.isNullable(0));
            assertThrows(SQLException.class, () -> metadata.isSigned(2));
            assertThrows(SQLException.class, () -> metadata.getPrecision(0));
            assertThrows(SQLException.class, () -> metadata.getParameterMode(2));
        }
    }

    private void assertParameter(
        ParameterMetaData metadata,
        int index,
        int type,
        String typeName,
        String className
    ) throws Exception {
        assertEquals(ParameterMetaData.parameterNullableUnknown, metadata.isNullable(index));
        assertEquals(ParameterMetaData.parameterModeIn, metadata.getParameterMode(index));
        assertEquals(type, metadata.getParameterType(index));
        assertEquals(typeName, metadata.getParameterTypeName(index));
        assertEquals(className, metadata.getParameterClassName(index));
    }
}
