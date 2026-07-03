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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    void databaseMetadataReportsPartitionedTablesLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS pgjdbc_meta_partitioned_measurement");
            statement.execute("""
                CREATE TABLE pgjdbc_meta_partitioned_measurement (
                  logdate date NOT NULL,
                  peaktemp int,
                  unitsales int,
                  PRIMARY KEY (logdate)
                ) PARTITION BY RANGE (logdate)
                """);
        }

        var metadata = connection.getMetaData();
        try (var tables = metadata.getTables(
            null,
            null,
            "pgjdbc_meta_partitioned_measurement",
            new String[] { "PARTITIONED TABLE" }
        )) {
            assertTrue(tables.next());
            assertEquals("pgjdbc_meta_partitioned_measurement", tables.getString("TABLE_NAME"));
            assertEquals("PARTITIONED TABLE", tables.getString("TABLE_TYPE"));
            assertFalse(tables.next());
        }

        try (var tables = metadata.getTables(
            null,
            null,
            "pgjdbc_meta_partitioned_measurement",
            new String[] { "TABLE" }
        )) {
            assertFalse(tables.next());
        }

        try (var primaryKeys = metadata.getPrimaryKeys(null, null, "pgjdbc_meta_partitioned_measurement")) {
            assertTrue(primaryKeys.next());
            assertEquals("pgjdbc_meta_partitioned_measurement", primaryKeys.getString("TABLE_NAME"));
            assertEquals("logdate", primaryKeys.getString("COLUMN_NAME"));
            assertEquals(1, primaryKeys.getInt("KEY_SEQ"));
            assertEquals("pgjdbc_meta_partitioned_measurement_pkey", primaryKeys.getString("PK_NAME"));
            assertFalse(primaryKeys.next());
        }
    }

    @Test
    void databaseMetadataPrimaryKeysExcludeIncludedIndexColumnsLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("""
                CREATE TEMP TABLE pgjdbc_meta_pk_include_column(
                  a int,
                  b int,
                  c int,
                  d int,
                  CONSTRAINT pgjdbc_meta_pk_include_column_pkey PRIMARY KEY (b, d) INCLUDE (a)
                )
                """);
        }

        var metadata = connection.getMetaData();
        try (var primaryKeys = metadata.getPrimaryKeys(null, null, "pgjdbc_meta_pk_include_column")) {
            assertTrue(primaryKeys.next());
            assertEquals("pgjdbc_meta_pk_include_column", primaryKeys.getString("TABLE_NAME"));
            assertEquals("b", primaryKeys.getString("COLUMN_NAME"));
            assertEquals(1, primaryKeys.getInt("KEY_SEQ"));

            assertTrue(primaryKeys.next());
            assertEquals("d", primaryKeys.getString("COLUMN_NAME"));
            assertEquals(2, primaryKeys.getInt("KEY_SEQ"));
            assertFalse(primaryKeys.next());
        }
    }

    @Test
    void databaseMetadataReportsExpressionPartialIndexesAndUniqueFkTargetsLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("""
                CREATE TEMP TABLE pgjdbc_meta_index_expr(
                  id int4 NOT NULL,
                  name text,
                  colour text,
                  quest text
                )
                """);
            statement.execute("CREATE UNIQUE INDEX pgjdbc_meta_idx_un_id ON pgjdbc_meta_index_expr(id)");
            statement.execute("CREATE INDEX pgjdbc_meta_idx_func_single ON pgjdbc_meta_index_expr(upper(colour))");
            statement.execute("CREATE INDEX pgjdbc_meta_idx_func_multi ON pgjdbc_meta_index_expr(upper(colour), upper(quest))");
            statement.execute("CREATE INDEX pgjdbc_meta_idx_func_mixed ON pgjdbc_meta_index_expr(colour, upper(quest))");
            statement.execute("CREATE INDEX pgjdbc_meta_idx_partial ON pgjdbc_meta_index_expr(name) WHERE id > 5");
            statement.execute("CREATE INDEX pgjdbc_meta_idx_a_d ON pgjdbc_meta_index_expr(id ASC, quest DESC)");
            statement.execute("CREATE INDEX pgjdbc_meta_idx_remark ON pgjdbc_meta_index_expr(name)");
            statement.execute("COMMENT ON INDEX pgjdbc_meta_idx_remark IS 'index_comment'");

            statement.execute("""
                CREATE TEMP TABLE pgjdbc_meta_unique_parent(
                  a int4 NOT NULL,
                  b int4 NOT NULL,
                  CONSTRAINT pgjdbc_meta_unique_parent_pk PRIMARY KEY (a),
                  CONSTRAINT pgjdbc_meta_unique_parent_b_key UNIQUE (b)
                )
                """);
            statement.execute("""
                CREATE TEMP TABLE pgjdbc_meta_unique_child(
                  c int4,
                  CONSTRAINT pgjdbc_meta_unique_child_fk FOREIGN KEY (c)
                    REFERENCES pgjdbc_meta_unique_parent (b)
                )
                """);
        }

        var metadata = connection.getMetaData();
        try (var indexes = metadata.getIndexInfo(null, null, "pgjdbc_meta_index_expr", false, false)) {
            var sawUnique = false;
            var sawMixedColumn = false;
            var sawMixedExpression = false;
            var sawMultiFirstExpression = false;
            var sawMultiSecondExpression = false;
            var sawSingleExpression = false;
            var sawPartial = false;
            var sawAscending = false;
            var sawDescending = false;
            var sawRemark = false;
            while (indexes.next()) {
                var indexName = indexes.getString("INDEX_NAME");
                var position = indexes.getInt("ORDINAL_POSITION");
                if ("pgjdbc_meta_idx_un_id".equals(indexName)) {
                    assertFalse(indexes.getBoolean("NON_UNIQUE"));
                    assertEquals("id", indexes.getString("COLUMN_NAME"));
                    sawUnique = true;
                } else if ("pgjdbc_meta_idx_func_mixed".equals(indexName) && position == 1) {
                    assertEquals("colour", indexes.getString("COLUMN_NAME"));
                    sawMixedColumn = true;
                } else if ("pgjdbc_meta_idx_func_mixed".equals(indexName) && position == 2) {
                    assertEquals("upper(quest)", indexes.getString("COLUMN_NAME"));
                    sawMixedExpression = true;
                } else if ("pgjdbc_meta_idx_func_multi".equals(indexName) && position == 1) {
                    assertEquals("upper(colour)", indexes.getString("COLUMN_NAME"));
                    sawMultiFirstExpression = true;
                } else if ("pgjdbc_meta_idx_func_multi".equals(indexName) && position == 2) {
                    assertEquals("upper(quest)", indexes.getString("COLUMN_NAME"));
                    sawMultiSecondExpression = true;
                } else if ("pgjdbc_meta_idx_func_single".equals(indexName)) {
                    assertEquals("upper(colour)", indexes.getString("COLUMN_NAME"));
                    sawSingleExpression = true;
                } else if ("pgjdbc_meta_idx_partial".equals(indexName)) {
                    assertEquals("name", indexes.getString("COLUMN_NAME"));
                    assertEquals("(id > 5)", indexes.getString("FILTER_CONDITION"));
                    assertTrue(indexes.getBoolean("NON_UNIQUE"));
                    sawPartial = true;
                } else if ("pgjdbc_meta_idx_a_d".equals(indexName) && position == 1) {
                    assertEquals("id", indexes.getString("COLUMN_NAME"));
                    assertEquals("A", indexes.getString("ASC_OR_DESC"));
                    sawAscending = true;
                } else if ("pgjdbc_meta_idx_a_d".equals(indexName) && position == 2) {
                    assertEquals("quest", indexes.getString("COLUMN_NAME"));
                    assertEquals("D", indexes.getString("ASC_OR_DESC"));
                    sawDescending = true;
                } else if ("pgjdbc_meta_idx_remark".equals(indexName)) {
                    assertEquals("name", indexes.getString("COLUMN_NAME"));
                    assertEquals("index_comment", indexes.getString("REMARKS"));
                    sawRemark = true;
                }
            }
            assertTrue(sawUnique);
            assertTrue(sawMixedColumn);
            assertTrue(sawMixedExpression);
            assertTrue(sawMultiFirstExpression);
            assertTrue(sawMultiSecondExpression);
            assertTrue(sawSingleExpression);
            assertTrue(sawPartial);
            assertTrue(sawAscending);
            assertTrue(sawDescending);
            assertTrue(sawRemark);
        }

        try (var importedKeys = metadata.getImportedKeys(null, null, "pgjdbc_meta_unique_child")) {
            assertTrue(importedKeys.next());
            assertEquals("pgjdbc_meta_unique_parent", importedKeys.getString("PKTABLE_NAME"));
            assertEquals("pgjdbc_meta_unique_child", importedKeys.getString("FKTABLE_NAME"));
            assertEquals("b", importedKeys.getString("PKCOLUMN_NAME"));
            assertEquals("c", importedKeys.getString("FKCOLUMN_NAME"));
            assertEquals("pgjdbc_meta_unique_parent_b_key", importedKeys.getString("PK_NAME"));
            assertFalse(importedKeys.next());
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

    @Test
    void databaseMetadataNonMatchingCatalogReturnsNoRowsLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("""
                CREATE TEMP TABLE pgjdbc_meta_fake_catalog_parent(
                  id int4 PRIMARY KEY,
                  code int4 UNIQUE
                )
                """);
            statement.execute("""
                CREATE TEMP TABLE pgjdbc_meta_fake_catalog_child(
                  code int4 REFERENCES pgjdbc_meta_fake_catalog_parent(code)
                )
                """);
        }

        var metadata = connection.getMetaData();
        try (var tables = metadata.getTables("FakeCatalog", null, "pgjdbc_meta_fake_catalog_parent", null)) {
            assertFalse(tables.next());
        }
        try (var columns = metadata.getColumns("FakeCatalog", null, "pgjdbc_meta_fake_catalog_parent", "%")) {
            assertFalse(columns.next());
        }
        try (var primaryKeys = metadata.getPrimaryKeys("FakeCatalog", null, "pgjdbc_meta_fake_catalog_parent")) {
            assertFalse(primaryKeys.next());
        }
        try (var indexes = metadata.getIndexInfo("FakeCatalog", null, "pgjdbc_meta_fake_catalog_parent", false, false)) {
            assertFalse(indexes.next());
        }
        try (var importedKeys = metadata.getImportedKeys("FakeCatalog", null, "pgjdbc_meta_fake_catalog_child")) {
            assertFalse(importedKeys.next());
        }
        try (var exportedKeys = metadata.getExportedKeys("FakeCatalog", null, "pgjdbc_meta_fake_catalog_parent")) {
            assertFalse(exportedKeys.next());
        }
        try (var crossReference = metadata.getCrossReference(
            "FakeCatalog",
            null,
            "pgjdbc_meta_fake_catalog_parent",
            null,
            null,
            "pgjdbc_meta_fake_catalog_child"
        )) {
            assertFalse(crossReference.next());
        }
        try (var crossReference = metadata.getCrossReference(
            null,
            null,
            "pgjdbc_meta_fake_catalog_parent",
            "FakeCatalog",
            null,
            "pgjdbc_meta_fake_catalog_child"
        )) {
            assertFalse(crossReference.next());
        }
    }

    @Test
    void databaseMetadataResultSetLabelsAreUpperCaseLikePgjdbc() throws Exception {
        var metadata = connection.getMetaData();

        try (var tables = metadata.getTables(null, null, "pgjdbc_metadata_missing", new String[] { "TABLE" })) {
            var resultSetMetaData = tables.getMetaData();
            assertEquals("TABLE_CAT", resultSetMetaData.getColumnLabel(1));
            assertEquals("TABLE_SCHEM", resultSetMetaData.getColumnLabel(2));
            assertEquals("TABLE_NAME", resultSetMetaData.getColumnLabel(3));
            assertEquals("TABLE_TYPE", resultSetMetaData.getColumnLabel(4));
        }

        try (var columns = metadata.getColumns(null, null, "pgjdbc_metadata_missing", "%")) {
            var resultSetMetaData = columns.getMetaData();
            assertEquals("TABLE_CAT", resultSetMetaData.getColumnLabel(1));
            assertEquals("COLUMN_NAME", resultSetMetaData.getColumnLabel(4));
            assertEquals("DATA_TYPE", resultSetMetaData.getColumnLabel(5));
            assertEquals("IS_GENERATEDCOLUMN", resultSetMetaData.getColumnLabel(24));
        }

        try (var typeInfo = metadata.getTypeInfo()) {
            var resultSetMetaData = typeInfo.getMetaData();
            assertEquals("TYPE_NAME", resultSetMetaData.getColumnLabel(1));
            assertEquals("DATA_TYPE", resultSetMetaData.getColumnLabel(2));
            assertEquals("NUM_PREC_RADIX", resultSetMetaData.getColumnLabel(18));
        }
    }

    @Test
    void databaseMetadataReportsPostgresqlTableTypesLikePgjdbc() throws Exception {
        var metadata = connection.getMetaData();
        var foundTypes = new ArrayList<String>();
        try (var tableTypes = metadata.getTableTypes()) {
            while (tableTypes.next()) {
                foundTypes.add(tableTypes.getString("TABLE_TYPE"));
            }
        }

        var expectedTypes = new ArrayList<>(List.of(
            "FOREIGN TABLE",
            "INDEX",
            "PARTITIONED INDEX",
            "MATERIALIZED VIEW",
            "PARTITIONED TABLE",
            "SEQUENCE",
            "SYSTEM INDEX",
            "SYSTEM TABLE",
            "SYSTEM TOAST INDEX",
            "SYSTEM TOAST TABLE",
            "SYSTEM VIEW",
            "TABLE",
            "TEMPORARY INDEX",
            "TEMPORARY SEQUENCE",
            "TEMPORARY TABLE",
            "TEMPORARY VIEW",
            "TYPE",
            "VIEW"
        ));
        Collections.sort(expectedTypes);
        Collections.sort(foundTypes);
        assertEquals(expectedTypes, foundTypes);
    }

    @Test
    void databaseMetadataReportsSchemasCatalogsAndSearchEscapeLikePgjdbc() throws Exception {
        var metadata = connection.getMetaData();
        var currentDatabase = currentDatabase();
        var schemas = new ArrayList<String>();
        try (var resultSet = metadata.getSchemas()) {
            while (resultSet.next()) {
                schemas.add(resultSet.getString("TABLE_SCHEM"));
                assertEquals(currentDatabase, resultSet.getString("TABLE_CATALOG"));
            }
        }
        assertTrue(schemas.contains("public"));
        assertTrue(schemas.contains("pg_catalog"));
        assertFalse(schemas.contains(""));

        var catalogs = new ArrayList<String>();
        try (var resultSet = metadata.getCatalogs()) {
            while (resultSet.next()) {
                catalogs.add(resultSet.getString("TABLE_CAT"));
            }
        }
        var sortedCatalogs = new ArrayList<>(catalogs);
        Collections.sort(sortedCatalogs);
        assertEquals(sortedCatalogs, catalogs);
        assertTrue(catalogs.contains(currentDatabase));

        assertEquals("\\", metadata.getSearchStringEscape());
    }

    @Test
    void databaseMetadataTablePatternsEscapeLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TEMP TABLE \"a'\"(id int4)");
            statement.execute("CREATE TEMP TABLE \"a\\\"(id int4)");
        }

        var metadata = connection.getMetaData();
        try (var tables = metadata.getTables(null, null, "a'", new String[] { "TABLE" })) {
            assertTrue(tables.next());
            assertEquals("a'", tables.getString("TABLE_NAME"));
        }
        try (var tables = metadata.getTables(null, null, "a\\\\", new String[] { "TABLE" })) {
            assertTrue(tables.next());
            assertEquals("a\\", tables.getString("TABLE_NAME"));
        }
        try (var tables = metadata.getTables(null, null, "a\\", new String[] { "TABLE" })) {
            assertFalse(tables.next());
        }
    }

    @Test
    void databaseMetadataReportsSerialAndCharOctetLengthLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("""
                CREATE TEMP TABLE pgjdbc_meta_column_details(
                  small_id smallserial,
                  id serial,
                  big_id bigserial,
                  c_varchar varchar(100),
                  c_char char(10),
                  c_text text,
                  c_bytea bytea,
                  c_int int4,
                  c_numeric numeric(8,3)
                )
                """);
        }

        var metadata = connection.getMetaData();
        try (var columns = metadata.getColumns(null, null, "pgjdbc_meta_column_details", "%")) {
            while (columns.next()) {
                var column = columns.getString("COLUMN_NAME");
                if ("small_id".equals(column)) {
                    assertEquals(Types.SMALLINT, columns.getInt("DATA_TYPE"));
                    assertEquals("smallserial", columns.getString("TYPE_NAME"));
                    assertEquals("YES", columns.getString("IS_AUTOINCREMENT"));
                    assertTrue(columns.getString("COLUMN_DEF").startsWith("nextval("));
                } else if ("id".equals(column)) {
                    assertEquals(Types.INTEGER, columns.getInt("DATA_TYPE"));
                    assertEquals("serial", columns.getString("TYPE_NAME"));
                    assertEquals("YES", columns.getString("IS_AUTOINCREMENT"));
                } else if ("big_id".equals(column)) {
                    assertEquals(Types.BIGINT, columns.getInt("DATA_TYPE"));
                    assertEquals("bigserial", columns.getString("TYPE_NAME"));
                    assertEquals("YES", columns.getString("IS_AUTOINCREMENT"));
                } else if ("c_varchar".equals(column)) {
                    assertEquals(100, columns.getInt("COLUMN_SIZE"));
                    assertEquals(100, columns.getInt("CHAR_OCTET_LENGTH"));
                } else if ("c_char".equals(column)) {
                    assertEquals(10, columns.getInt("COLUMN_SIZE"));
                    assertEquals(10, columns.getInt("CHAR_OCTET_LENGTH"));
                } else if ("c_text".equals(column) || "c_bytea".equals(column)) {
                    var columnSize = columns.getInt("COLUMN_SIZE");
                    assertFalse(columns.wasNull());
                    assertEquals(columnSize, columns.getInt("CHAR_OCTET_LENGTH"));
                } else if ("c_int".equals(column) || "c_numeric".equals(column)) {
                    columns.getInt("CHAR_OCTET_LENGTH");
                    assertTrue(columns.wasNull());
                }
            }
        }
    }

    @Test
    void databaseMetadataReportsIdentityAndGeneratedColumnsLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS pgjdbc_meta_generated_columns");
            statement.execute("""
                CREATE TABLE pgjdbc_meta_generated_columns(
                  id int GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                  salary int,
                  bonus int,
                  gross_pay int GENERATED ALWAYS AS (salary + bonus) STORED
                )
                """);
        }

        var metadata = connection.getMetaData();
        try (var columns = metadata.getColumns(null, null, "pgjdbc_meta_generated_columns", "%")) {
            var sawIdentity = false;
            var sawGenerated = false;
            while (columns.next()) {
                var column = columns.getString("COLUMN_NAME");
                if ("id".equals(column)) {
                    assertEquals("YES", columns.getString("IS_AUTOINCREMENT"));
                    assertEquals("NO", columns.getString("IS_GENERATEDCOLUMN"));
                    sawIdentity = true;
                } else if ("gross_pay".equals(column)) {
                    assertEquals("NO", columns.getString("IS_AUTOINCREMENT"));
                    assertEquals("YES", columns.getString("IS_GENERATEDCOLUMN"));
                    sawGenerated = true;
                }
            }
            assertTrue(sawIdentity);
            assertTrue(sawGenerated);
        }
    }

    @Test
    void databaseMetadataReportsDomainColumnDetailsLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS pgjdbc_meta_domain_table");
            statement.execute("DROP DOMAIN IF EXISTS pgjdbc_meta_nndom");
            statement.execute("DROP DOMAIN IF EXISTS pgjdbc_meta_varbit");
            statement.execute("DROP DOMAIN IF EXISTS pgjdbc_meta_numeric");
            statement.execute("CREATE DOMAIN pgjdbc_meta_nndom AS int NOT NULL");
            statement.execute("CREATE DOMAIN pgjdbc_meta_varbit AS varbit(3)");
            statement.execute("CREATE DOMAIN pgjdbc_meta_numeric AS numeric(8,3)");
            statement.execute("""
                CREATE TABLE pgjdbc_meta_domain_table(
                  id pgjdbc_meta_nndom,
                  v pgjdbc_meta_varbit,
                  f pgjdbc_meta_numeric
                )
                """);
        }

        var metadata = connection.getMetaData();
        try (var columns = metadata.getColumns(null, null, "pgjdbc_meta_domain_table", "%")) {
            assertTrue(columns.next());
            assertEquals("id", columns.getString("COLUMN_NAME"));
            assertEquals("NO", columns.getString("IS_NULLABLE"));
            assertEquals(10, columns.getInt("COLUMN_SIZE"));

            assertTrue(columns.next());
            assertEquals("v", columns.getString("COLUMN_NAME"));
            assertEquals(3, columns.getInt("COLUMN_SIZE"));

            assertTrue(columns.next());
            assertEquals("f", columns.getString("COLUMN_NAME"));
            assertEquals(8, columns.getInt("COLUMN_SIZE"));
            assertEquals(3, columns.getInt("DECIMAL_DIGITS"));
            assertFalse(columns.next());
        }
    }

    @Test
    void databaseMetadataReportsArrayColumnDetailsLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("""
                CREATE TEMP TABLE pgjdbc_meta_array_table(
                  a numeric(5,2)[],
                  b varchar(100)[],
                  c int4[],
                  d int4[][]
                )
                """);
        }

        var metadata = connection.getMetaData();
        try (var columns = metadata.getColumns(null, null, "pgjdbc_meta_array_table", "%")) {
            assertTrue(columns.next());
            assertEquals("a", columns.getString("COLUMN_NAME"));
            assertEquals(Types.ARRAY, columns.getInt("DATA_TYPE"));
            assertEquals("_numeric", columns.getString("TYPE_NAME"));
            assertEquals(5, columns.getInt("COLUMN_SIZE"));
            assertEquals(2, columns.getInt("DECIMAL_DIGITS"));

            assertTrue(columns.next());
            assertEquals("b", columns.getString("COLUMN_NAME"));
            assertEquals(Types.ARRAY, columns.getInt("DATA_TYPE"));
            assertEquals("_varchar", columns.getString("TYPE_NAME"));
            assertEquals(100, columns.getInt("COLUMN_SIZE"));

            assertTrue(columns.next());
            assertEquals("c", columns.getString("COLUMN_NAME"));
            assertEquals("_int4", columns.getString("TYPE_NAME"));

            assertTrue(columns.next());
            assertEquals("d", columns.getString("COLUMN_NAME"));
            assertEquals("_int4", columns.getString("TYPE_NAME"));
            assertFalse(columns.next());
        }
    }

    @Test
    void databaseMetadataReportsBestRowAndVersionColumnsLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TEMP TABLE pgjdbc_meta_bestrow(id int4 PRIMARY KEY, name text)");
        }

        var metadata = connection.getMetaData();
        try (var bestRows = metadata.getBestRowIdentifier(
            null,
            null,
            "pgjdbc_meta_bestrow",
            DatabaseMetaData.bestRowSession,
            false
        )) {
            assertTrue(bestRows.next());
            assertEquals(DatabaseMetaData.bestRowSession, bestRows.getInt("SCOPE"));
            assertEquals("id", bestRows.getString("COLUMN_NAME"));
            assertEquals(Types.INTEGER, bestRows.getInt("DATA_TYPE"));
            assertEquals("int4", bestRows.getString("TYPE_NAME"));
            assertEquals(10, bestRows.getInt("COLUMN_SIZE"));
            assertEquals(DatabaseMetaData.bestRowNotPseudo, bestRows.getInt("PSEUDO_COLUMN"));
            assertFalse(bestRows.next());
        }

        try (var bestRows = metadata.getBestRowIdentifier(
            "nonsensecatalog",
            null,
            "pgjdbc_meta_bestrow",
            DatabaseMetaData.bestRowSession,
            false
        )) {
            assertFalse(bestRows.next());
        }

        try (var versionColumns = metadata.getVersionColumns(null, null, "pg_class")) {
            var resultSetMetaData = versionColumns.getMetaData();
            assertEquals("SCOPE", resultSetMetaData.getColumnLabel(1));
            assertEquals("PSEUDO_COLUMN", resultSetMetaData.getColumnLabel(8));
            assertFalse(versionColumns.next());
        }
    }

    @Test
    void databaseMetadataSqlKeywordsExcludeSqlStandardAndIncludePostgresqlKeywordsLikePgjdbc() throws Exception {
        var keywords = List.of(connection.getMetaData().getSQLKeywords().split(","));
        assertTrue(keywords.contains("reindex"));
        assertFalse(keywords.contains("select"));
        assertFalse(keywords.contains("table"));
        assertEquals(keywords.size(), new java.util.HashSet<>(keywords).size());
    }

    @Test
    void databaseMetadataReportsUserDefinedTypesLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("DROP DOMAIN IF EXISTS pgjdbc_meta_testint8");
            statement.execute("DROP TYPE IF EXISTS pgjdbc_meta_composite");
            statement.execute("CREATE DOMAIN pgjdbc_meta_testint8 AS int8");
            statement.execute("COMMENT ON DOMAIN pgjdbc_meta_testint8 IS 'jdbc123'");
            statement.execute("CREATE TYPE pgjdbc_meta_composite AS (i int8)");
        }

        var metadata = connection.getMetaData();
        try (var udts = metadata.getUDTs(null, null, "pgjdbc_meta_testint8", null)) {
            assertTrue(udts.next());
            assertEquals("public", udts.getString("TYPE_SCHEM"));
            assertEquals("pgjdbc_meta_testint8", udts.getString("TYPE_NAME"));
            assertEquals(Types.DISTINCT, udts.getInt("DATA_TYPE"));
            assertEquals("jdbc123", udts.getString("REMARKS"));
            assertEquals(Types.BIGINT, udts.getInt("BASE_TYPE"));
            assertFalse(udts.next());
        }

        try (var udts = metadata.getUDTs(
            null,
            null,
            "pgjdbc_meta_testint8",
            new int[] { Types.DISTINCT, Types.STRUCT }
        )) {
            assertTrue(udts.next());
            assertEquals(Types.DISTINCT, udts.getInt("DATA_TYPE"));
            assertFalse(udts.next());
        }

        try (var udts = metadata.getUDTs(null, null, "pgjdbc_meta_composite", new int[] { Types.STRUCT })) {
            assertTrue(udts.next());
            assertEquals("pgjdbc_meta_composite", udts.getString("TYPE_NAME"));
            assertEquals(Types.STRUCT, udts.getInt("DATA_TYPE"));
            udts.getInt("BASE_TYPE");
            assertTrue(udts.wasNull());
            assertFalse(udts.next());
        }

        try (var udts = metadata.getUDTs("nonsensecatalog", null, "pgjdbc_meta_composite", null)) {
            assertFalse(udts.next());
            assertEquals(1, udts.findColumn("type_cat"));
            assertEquals(7, udts.findColumn("base_type"));
        }
    }

    @Test
    void databaseMetadataReportsFunctionColumnsLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("""
                CREATE OR REPLACE FUNCTION pgjdbc_meta_f1(int, varchar)
                RETURNS int AS 'SELECT 1;' LANGUAGE SQL
                """);
            statement.execute("""
                CREATE OR REPLACE FUNCTION pgjdbc_meta_f3(IN a int, INOUT b varchar, OUT c timestamptz)
                AS $f$ BEGIN b := 'a'; c := now(); return; END; $f$ LANGUAGE plpgsql
                """);
        }

        var metadata = connection.getMetaData();
        try (var columns = metadata.getFunctionColumns(null, null, "pgjdbc_meta_f1", null)) {
            var resultSetMetaData = columns.getMetaData();
            assertEquals(17, resultSetMetaData.getColumnCount());
            assertEquals("FUNCTION_CAT", resultSetMetaData.getColumnName(1));
            assertEquals("SPECIFIC_NAME", resultSetMetaData.getColumnName(17));

            assertTrue(columns.next());
            assertEquals("public", columns.getString("FUNCTION_SCHEM"));
            assertEquals("pgjdbc_meta_f1", columns.getString("FUNCTION_NAME"));
            assertEquals("returnValue", columns.getString("COLUMN_NAME"));
            assertEquals(DatabaseMetaData.functionReturn, columns.getInt("COLUMN_TYPE"));
            assertEquals(Types.INTEGER, columns.getInt("DATA_TYPE"));
            assertEquals("int4", columns.getString("TYPE_NAME"));
            assertEquals(0, columns.getInt("ORDINAL_POSITION"));

            assertTrue(columns.next());
            assertEquals("$1", columns.getString("COLUMN_NAME"));
            assertEquals(DatabaseMetaData.functionColumnIn, columns.getInt("COLUMN_TYPE"));
            assertEquals(Types.INTEGER, columns.getInt("DATA_TYPE"));
            assertEquals(1, columns.getInt("ORDINAL_POSITION"));

            assertTrue(columns.next());
            assertEquals("$2", columns.getString("COLUMN_NAME"));
            assertEquals(DatabaseMetaData.functionColumnIn, columns.getInt("COLUMN_TYPE"));
            assertEquals(Types.VARCHAR, columns.getInt("DATA_TYPE"));
            assertEquals(2, columns.getInt("ORDINAL_POSITION"));
            assertFalse(columns.next());
        }

        try (var columns = metadata.getProcedureColumns(null, null, "pgjdbc_meta_f3", null)) {
            assertTrue(columns.next());
            assertEquals("a", columns.getString("COLUMN_NAME"));
            assertEquals(DatabaseMetaData.procedureColumnIn, columns.getInt("COLUMN_TYPE"));
            assertEquals(Types.INTEGER, columns.getInt("DATA_TYPE"));

            assertTrue(columns.next());
            assertEquals("b", columns.getString("COLUMN_NAME"));
            assertEquals(DatabaseMetaData.procedureColumnInOut, columns.getInt("COLUMN_TYPE"));
            assertEquals(Types.VARCHAR, columns.getInt("DATA_TYPE"));

            assertTrue(columns.next());
            assertEquals("c", columns.getString("COLUMN_NAME"));
            assertEquals(DatabaseMetaData.procedureColumnOut, columns.getInt("COLUMN_TYPE"));
            assertEquals(Types.TIMESTAMP_WITH_TIMEZONE, columns.getInt("DATA_TYPE"));
            assertFalse(columns.next());
        }
    }

    private String currentDatabase() throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT current_database()")) {
            assertTrue(resultSet.next());
            return resultSet.getString(1);
        }
    }
}
