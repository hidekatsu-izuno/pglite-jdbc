package io.github.hidekatsu_izuno.pglite_jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.Types;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PgjdbcInspiredDatabaseMetaDataTest {
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
    void databaseMetadataReportsTablesColumnsAndTypeInfo() throws Exception {
        org.junit.jupiter.api.Assertions.assertTimeout(Duration.ofSeconds(180), () -> {
            try (var statement = connection.createStatement()) {
                statement.execute("""
                    CREATE TEMP TABLE pgjdbc_metadata_test(
                      id int4,
                      name text,
                      amount numeric(8, 3)
                    )
                    """);

                var metadata = connection.getMetaData();
                try (var tables = metadata.getTables(null, null, "pgjdbc_metadata_test", new String[] { "TABLE" })) {
                    assertTrue(tables.next());
                    assertEquals("pgjdbc_metadata_test", tables.getString("TABLE_NAME"));
                    assertNotNull(tables.getString("TABLE_TYPE"));
                    assertFalse(tables.next());
                }

                try (var columns = metadata.getColumns(null, null, "pgjdbc_metadata_test", "%")) {
                    assertTrue(columns.next());
                    assertEquals("id", columns.getString("COLUMN_NAME"));
                    assertEquals(Types.INTEGER, columns.getInt("DATA_TYPE"));

                    assertTrue(columns.next());
                    assertEquals("name", columns.getString("COLUMN_NAME"));
                    assertEquals(Types.VARCHAR, columns.getInt("DATA_TYPE"));

                    assertTrue(columns.next());
                    assertEquals("amount", columns.getString("COLUMN_NAME"));
                    assertEquals(Types.NUMERIC, columns.getInt("DATA_TYPE"));
                    assertEquals(8, columns.getInt("COLUMN_SIZE"));
                    assertEquals(3, columns.getInt("DECIMAL_DIGITS"));

                    assertFalse(columns.next());
                }

                var sawInt4 = false;
                var sawText = false;
                try (var types = metadata.getTypeInfo()) {
                    while (types.next()) {
                        if ("int4".equals(types.getString("TYPE_NAME"))) {
                            assertNull(types.getString("LITERAL_PREFIX"));
                            assertFalse(types.getBoolean("UNSIGNED_ATTRIBUTE"));
                            sawInt4 = true;
                        } else if ("text".equals(types.getString("TYPE_NAME"))) {
                            assertEquals("'", types.getString("LITERAL_PREFIX"));
                            assertEquals("'", types.getString("LITERAL_SUFFIX"));
                            sawText = true;
                        }
                    }
                }
                assertTrue(sawInt4);
                assertTrue(sawText);
            }
        });
    }

    @Test
    void databaseMetadataReportsPrimaryKeysForeignKeysAndIndexes() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("""
                CREATE TEMP TABLE pgjdbc_meta_parent(
                  a int4 NOT NULL,
                  b int4 NOT NULL,
                  payload text,
                  CONSTRAINT pgjdbc_meta_parent_pk PRIMARY KEY (a, b),
                  CONSTRAINT pgjdbc_meta_parent_payload_key UNIQUE (payload)
                )
                """);
            statement.execute("""
                CREATE TEMP TABLE pgjdbc_meta_child(
                  x int4 NOT NULL,
                  y int4 NOT NULL,
                  CONSTRAINT pgjdbc_meta_child_fk FOREIGN KEY (x, y)
                    REFERENCES pgjdbc_meta_parent (b, a)
                    ON UPDATE RESTRICT
                    ON DELETE CASCADE
                )
                """);
        }

        var metadata = connection.getMetaData();
        try (var primaryKeys = metadata.getPrimaryKeys(null, null, "pgjdbc_meta_parent")) {
            assertTrue(primaryKeys.next());
            assertEquals("pgjdbc_meta_parent", primaryKeys.getString("TABLE_NAME"));
            assertEquals("a", primaryKeys.getString("COLUMN_NAME"));
            assertEquals(1, primaryKeys.getInt("KEY_SEQ"));
            assertEquals("pgjdbc_meta_parent_pk", primaryKeys.getString("PK_NAME"));

            assertTrue(primaryKeys.next());
            assertEquals("b", primaryKeys.getString("COLUMN_NAME"));
            assertEquals(2, primaryKeys.getInt("KEY_SEQ"));
            assertFalse(primaryKeys.next());
        }

        try (var importedKeys = metadata.getImportedKeys(null, null, "pgjdbc_meta_child")) {
            assertTrue(importedKeys.next());
            assertEquals("pgjdbc_meta_parent", importedKeys.getString("PKTABLE_NAME"));
            assertEquals("pgjdbc_meta_child", importedKeys.getString("FKTABLE_NAME"));
            assertEquals("b", importedKeys.getString("PKCOLUMN_NAME"));
            assertEquals("x", importedKeys.getString("FKCOLUMN_NAME"));
            assertEquals(1, importedKeys.getInt("KEY_SEQ"));
            assertEquals(DatabaseMetaData.importedKeyRestrict, importedKeys.getInt("UPDATE_RULE"));
            assertEquals(DatabaseMetaData.importedKeyCascade, importedKeys.getInt("DELETE_RULE"));
            assertEquals("pgjdbc_meta_child_fk", importedKeys.getString("FK_NAME"));

            assertTrue(importedKeys.next());
            assertEquals("a", importedKeys.getString("PKCOLUMN_NAME"));
            assertEquals("y", importedKeys.getString("FKCOLUMN_NAME"));
            assertEquals(2, importedKeys.getInt("KEY_SEQ"));
            assertFalse(importedKeys.next());
        }

        try (var crossReference = metadata.getCrossReference(
            null,
            null,
            "pgjdbc_meta_parent",
            null,
            null,
            "pgjdbc_meta_child"
        )) {
            assertTrue(crossReference.next());
            assertEquals("pgjdbc_meta_parent", crossReference.getString("PKTABLE_NAME"));
            assertEquals("pgjdbc_meta_child", crossReference.getString("FKTABLE_NAME"));
        }

        try (var indexes = metadata.getIndexInfo(null, null, "pgjdbc_meta_parent", true, false)) {
            var sawPrimaryKeyIndex = false;
            var sawUniquePayloadIndex = false;
            while (indexes.next()) {
                if ("pgjdbc_meta_parent_pk".equals(indexes.getString("INDEX_NAME"))) {
                    assertFalse(indexes.getBoolean("NON_UNIQUE"));
                    sawPrimaryKeyIndex = true;
                } else if ("pgjdbc_meta_parent_payload_key".equals(indexes.getString("INDEX_NAME"))) {
                    assertEquals("payload", indexes.getString("COLUMN_NAME"));
                    assertFalse(indexes.getBoolean("NON_UNIQUE"));
                    sawUniquePayloadIndex = true;
                }
            }
            assertTrue(sawPrimaryKeyIndex);
            assertTrue(sawUniquePayloadIndex);
        }
    }

    @Test
    void databaseMetadataEmptyCatalogAndSchemaArgumentsReturnNoRows() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TEMP TABLE pgjdbc_meta_empty_args(id int4 PRIMARY KEY)");
        }

        var metadata = connection.getMetaData();
        try (var tables = metadata.getTables("", "", "pgjdbc_meta_empty_args", new String[] { "TABLE" })) {
            assertFalse(tables.next());
        }

        try (var columns = metadata.getColumns("", "", "pgjdbc_meta_empty_args", "%")) {
            assertFalse(columns.next());
        }

        try (var primaryKeys = metadata.getPrimaryKeys("", "", "pgjdbc_meta_empty_args")) {
            assertFalse(primaryKeys.next());
        }

        try (var indexes = metadata.getIndexInfo("", "", "pgjdbc_meta_empty_args", false, false)) {
            assertFalse(indexes.next());
        }
    }
}
