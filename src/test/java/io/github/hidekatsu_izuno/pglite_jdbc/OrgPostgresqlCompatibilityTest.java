package io.github.hidekatsu_izuno.pglite_jdbc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.sql.DriverManager;
import java.sql.JDBCType;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.postgresql.PGResultSetMetaData;
import org.postgresql.PGStatement;
import org.postgresql.jdbc.AutoSave;
import org.postgresql.jdbc.PreferQueryMode;
import org.postgresql.largeobject.LargeObjectManager;
import org.postgresql.util.PGobject;

class OrgPostgresqlCompatibilityTest {
    public static final class CompatJsonObject extends PGobject {
    }

    @Test
    void shouldExposeOrgPostgresqlInterfaces() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:pglite:?defaultRowFetchSize=16&queryTimeout=5&autosave=conservative&preferQueryMode=extended"
            )) {
            var pgConnection = connection.unwrap(org.postgresql.PGConnection.class);
            assertNotNull(pgConnection);
            assertEquals(16, pgConnection.getDefaultFetchSize());
            assertEquals(AutoSave.CONSERVATIVE, pgConnection.getAutosave());
            assertEquals(PreferQueryMode.EXTENDED, pgConnection.getPreferQueryMode());
            try (var statement = connection.createStatement()) {
                assertEquals(5, statement.getQueryTimeout());
            }

            try (var prepared = connection.prepareStatement("SELECT ?::int4 AS value")) {
                var pgStatement = prepared.unwrap(PGStatement.class);
                pgStatement.setPrepareThreshold(1);
                assertTrue(pgStatement.isUseServerPrepare());
                pgStatement.setAdaptiveFetch(true);
                assertTrue(pgStatement.getAdaptiveFetch());
                prepared.setInt(1, 42);
                try (var resultSet = prepared.executeQuery()) {
                    assertTrue(resultSet.next());
                    assertEquals(42, resultSet.getInt(1));
                    var metadata = resultSet.getMetaData();
                    assertEquals("int4", metadata.getColumnTypeName(1));
                    assertEquals(Integer.class.getName(), metadata.getColumnClassName(1));
                    assertEquals("value", metadata.unwrap(PGResultSetMetaData.class).getBaseColumnName(1));
                }
            }
        }
    }

    @Test
    void shouldSupportJdbcSavepoints() throws Exception {
        assertTimeout(Duration.ofSeconds(180), () -> {
            try (var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000")) {
                try (var statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE savepoint_test(id int)");
                }
                connection.setAutoCommit(false);
                try (var statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT INTO savepoint_test VALUES (1)");
                    var savepoint = connection.setSavepoint("after_one");
                    statement.executeUpdate("INSERT INTO savepoint_test VALUES (2)");
                    connection.rollback(savepoint);
                    statement.executeUpdate("INSERT INTO savepoint_test VALUES (3)");
                    connection.releaseSavepoint(savepoint);
                    connection.commit();
                }
                try (var statement = connection.createStatement();
                     var resultSet = statement.executeQuery(
                         "SELECT array_agg(id ORDER BY id)::text AS ids FROM savepoint_test"
                     )) {
                    assertTrue(resultSet.next());
                    assertEquals("{1,3}", resultSet.getString(1));
                }
            }
        });
    }

    @Test
    void shouldExposeStatementMultipleResultsAndCursorState() throws Exception {
        assertTimeout(Duration.ofSeconds(180), () -> {
            try (var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000");
                 var statement = connection.createStatement()) {
                var hasResult = statement.execute("""
                    CREATE TABLE multi_result_test(id int);
                    INSERT INTO multi_result_test VALUES (1), (2);
                    SELECT id FROM multi_result_test ORDER BY id
                    """);
                while (!hasResult && statement.getUpdateCount() != -1) {
                    hasResult = statement.getMoreResults();
                }
                assertTrue(hasResult);

                try (var resultSet = statement.getResultSet()) {
                    assertTrue(resultSet.isBeforeFirst());
                    assertTrue(resultSet.first());
                    assertEquals(1, resultSet.getInt(1));
                    assertTrue(resultSet.relative(1));
                    assertTrue(resultSet.isLast());
                    assertEquals(2, resultSet.getInt(1));
                    assertTrue(resultSet.previous());
                    assertEquals(1, resultSet.getInt(1));
                    resultSet.afterLast();
                    assertTrue(resultSet.isAfterLast());
                }
                assertEquals(-1, statement.getUpdateCount());
            }
        });
    }

    @Test
    void shouldSupportJdbcArraysAndStreams() throws Exception {
        assertTimeout(Duration.ofSeconds(180), () -> {
            try (var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000")) {
                var array = connection.createArrayOf("int4", new Integer[] { 1, 2, 3 });
                assertEquals(org.postgresql.jdbc.PgArray.class, array.getClass());
                assertArrayEquals(new Object[] { 2, 3 }, (Object[]) array.getArray(2, 2));

                try (var arrayRows = array.getResultSet()) {
                    assertTrue(arrayRows.next());
                    assertEquals(1, arrayRows.getInt("INDEX"));
                    assertEquals(1, arrayRows.getInt("VALUE"));
                }

                try (var statement = connection.prepareStatement(
                    "SELECT ?::int4[] AS ints, ?::text AS body, ?::bytea AS bytes, ?::text AS reader_body, ?::bytea AS stream_bytes"
                )) {
                    statement.setArray(1, array);
                    statement.setString(2, "stream text");
                    statement.setBytes(3, "bytes".getBytes(StandardCharsets.UTF_8));
                    statement.setCharacterStream(4, new StringReader("reader text"));
                    statement.setBinaryStream(5, new ByteArrayInputStream("stream-bytes".getBytes(StandardCharsets.UTF_8)));
                    try (var resultSet = statement.executeQuery()) {
                        assertTrue(resultSet.next());
                        var readArray = resultSet.getArray("ints");
                        assertEquals(org.postgresql.jdbc.PgArray.class, readArray.getClass());
                        assertEquals(java.sql.Types.INTEGER, readArray.getBaseType());
                        assertArrayEquals(new Object[] { 1, 2, 3 }, (Object[]) readArray.getArray());

                        try (var reader = resultSet.getCharacterStream("body")) {
                            var buffer = new char[64];
                            var length = reader.read(buffer);
                            assertEquals("stream text", new String(buffer, 0, length));
                        }
                        try (var input = resultSet.getBinaryStream("bytes")) {
                            assertArrayEquals("bytes".getBytes(StandardCharsets.UTF_8), input.readAllBytes());
                        }
                        assertEquals("reader text", resultSet.getString("reader_body"));
                        assertArrayEquals(
                            "stream-bytes".getBytes(StandardCharsets.UTF_8),
                            resultSet.getBytes("stream_bytes")
                        );
                    }
                }
                array.free();
            }
        });
    }

    @Test
    void shouldSupportJdbcBlobAndClobValues() throws Exception {
        assertTimeout(Duration.ofSeconds(180), () -> {
            try (var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000")) {
                var blob = connection.createBlob();
                assertTrue(blob instanceof org.postgresql.jdbc.PgBlob);
                blob.setBytes(1, "blob-bytes".getBytes(StandardCharsets.UTF_8));
                var clob = connection.createClob();
                assertTrue(clob instanceof org.postgresql.jdbc.PgClob);
                clob.setString(1, "clob text");

                try (var statement = connection.prepareStatement(
                    "SELECT ?::bytea AS payload, ?::text AS body"
                )) {
                    statement.setBlob(1, blob);
                    statement.setClob(2, clob);
                    try (var resultSet = statement.executeQuery()) {
                        assertTrue(resultSet.next());
                        var readBlob = resultSet.getBlob("payload");
                        assertTrue(readBlob instanceof org.postgresql.jdbc.PgBlob);
                        assertArrayEquals(
                            "blob-bytes".getBytes(StandardCharsets.UTF_8),
                            readBlob.getBytes(1, (int) readBlob.length())
                        );
                        var readClob = resultSet.getClob("body");
                        assertTrue(readClob instanceof org.postgresql.jdbc.PgClob);
                        assertEquals("clob text", readClob.getSubString(1, (int) readClob.length()));
                    }
                }
                blob.free();
                clob.free();
            }
        });
    }

    @Test
    void shouldSupportJdbcSqlXmlValues() throws Exception {
        assertTimeout(Duration.ofSeconds(180), () -> {
            try (var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000")) {
                var xml = connection.createSQLXML();
                assertEquals(org.postgresql.jdbc.PgSQLXML.class, xml.getClass());
                xml.setString("<root><value>42</value></root>");
                try (var statement = connection.prepareStatement("SELECT ?::xml AS payload")) {
                    statement.setSQLXML(1, xml);
                    try (var resultSet = statement.executeQuery()) {
                        assertTrue(resultSet.next());
                        var readXml = resultSet.getSQLXML("payload");
                        assertEquals(org.postgresql.jdbc.PgSQLXML.class, readXml.getClass());
                        assertEquals("<root><value>42</value></root>", readXml.getString());
                    }
                }
                xml.free();
            }
        });
    }

    @Test
    void shouldSupportAdditionalJdbcValueConversions() throws Exception {
        assertTimeout(Duration.ofSeconds(180), () -> {
            try (var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000");
                 var statement = connection.prepareStatement(
                     "SELECT ?::text AS link, ?::text AS label, ?::numeric AS amount"
                 )) {
                statement.setURL(1, new URL("https://example.test/path?q=1"));
                statement.setNString(2, "unicode label");
                statement.setBigDecimal(3, new BigDecimal("12.345"));
                try (var resultSet = statement.executeQuery()) {
                    assertTrue(resultSet.next());
                    assertEquals(new URL("https://example.test/path?q=1"), resultSet.getURL("link"));
                    assertEquals("unicode label", resultSet.getNString("label"));
                    assertEquals(new BigDecimal("12.35"), resultSet.getBigDecimal("amount", 2));
                }
            }
        });
    }

    @Test
    void shouldSupportTypedSetObjectAndPgjdbcObjectValues() throws Exception {
        assertTimeout(Duration.ofSeconds(180), () -> {
            try (var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000");
                 var statement = connection.prepareStatement(
                     """
                     SELECT
                       ?::int4 AS id,
                       ?::numeric AS amount,
                       ?::jsonb AS payload,
                       ?::uuid AS uid,
                       ?::bytea AS bytes,
                       ?::timestamp AS created_at
                     """
                 )) {
                var uuid = UUID.fromString("0f3f69a2-35a0-4ff8-aac5-15d7f0a6c111");
                var payload = new PGobject();
                payload.setType("jsonb");
                payload.setValue("{\"ok\":true}");

                statement.setObject(1, "42", Types.INTEGER);
                statement.setObject(2, new BigDecimal("12.345"), Types.NUMERIC, 2);
                statement.setObject(3, payload, Types.OTHER);
                statement.setObject(4, uuid, Types.OTHER);
                statement.setObject(5, "bytes", JDBCType.BINARY);
                statement.setObject(6, "2024-01-02 03:04:05", JDBCType.TIMESTAMP);

                try (var resultSet = statement.executeQuery()) {
                    assertTrue(resultSet.next());
                    assertEquals(42, resultSet.getInt("id"));
                    assertEquals(new BigDecimal("12.35"), resultSet.getBigDecimal("amount"));
                    var readPayload = resultSet.getObject("payload", PGobject.class);
                    assertEquals("jsonb", readPayload.getType());
                    assertTrue(readPayload.getValue().contains("ok"));
                    assertEquals(uuid, resultSet.getObject("uid", UUID.class));
                    assertArrayEquals("bytes".getBytes(StandardCharsets.UTF_8), resultSet.getBytes("bytes"));
                    assertEquals(
                        LocalDateTime.of(2024, 1, 2, 3, 4, 5),
                        resultSet.getObject("created_at", LocalDateTime.class)
                    );
                }
            }
        });
    }

    @Test
    void shouldSupportConnectionSchemaAndParameterTypeMetadata() throws Exception {
        assertTimeout(Duration.ofSeconds(180), () -> {
            try (var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000")) {
                try (var statement = connection.createStatement()) {
                    statement.execute("CREATE SCHEMA jdbc_compat_schema");
                }
                connection.setSchema("jdbc_compat_schema");
                assertEquals("jdbc_compat_schema", connection.getSchema());

                try (var statement = connection.createStatement();
                     var resultSet = statement.executeQuery("SELECT current_schema()")) {
                    assertTrue(resultSet.next());
                    assertEquals("jdbc_compat_schema", resultSet.getString(1));
                }

                try (var prepared = connection.prepareStatement("SELECT ?::int4 AS value")) {
                    var metadata = prepared.getParameterMetaData();
                    assertEquals(1, metadata.getParameterCount());
                    assertEquals(java.sql.Types.INTEGER, metadata.getParameterType(1));
                    assertEquals("int4", metadata.getParameterTypeName(1));
                    assertEquals(Integer.class.getName(), metadata.getParameterClassName(1));
                }
            }
        });
    }

    @Test
    void shouldExposePgjdbcPublicConnectionSurfaces() throws Exception {
        assertTimeout(Duration.ofSeconds(180), () -> {
            try (var connection = DriverManager.getConnection(
                    "jdbc:pglite:?protocolTimeoutMs=5000&ApplicationName=pglite-jdbc-test"
                )) {
                var pgConnection = connection.unwrap(org.postgresql.PGConnection.class);
                pgConnection.addDataType("compat_object", org.postgresql.util.PGobject.class);
                assertEquals("18.3", pgConnection.getParameterStatus("server_version"));
                assertEquals("pglite-jdbc-test", pgConnection.getParameterStatus("application_name"));
                assertTrue(pgConnection.getParameterStatuses().containsKey("standard_conforming_strings"));
                pgConnection.setAdaptiveFetch(true);
                assertTrue(pgConnection.getAdaptiveFetch());

                var baseConnection = connection.unwrap(org.postgresql.core.BaseConnection.class);
                try (var resultSet = baseConnection.execSQLQuery("SELECT 7 AS value")) {
                    assertTrue(resultSet.next());
                    assertEquals(7, resultSet.getInt("value"));
                }
                baseConnection.execSQLUpdate("CREATE TEMP TABLE pgjdbc_public_surface(id int)");

                var typeInfo = baseConnection.getTypeInfo();
                assertEquals(23, typeInfo.getPGType("int4"));
                assertEquals("int4", typeInfo.getPGType(23));
                assertEquals(java.sql.Types.INTEGER, typeInfo.getJavaArrayType("int4"));
                assertEquals(Integer.class.getName(), typeInfo.getJavaClass(23));
                assertEquals(10, typeInfo.getMaximumPrecision(23));

                var queryExecutor = baseConnection.getQueryExecutor();
                assertEquals("18.3", queryExecutor.getServerVersion());
                assertEquals(180003, queryExecutor.getServerVersionNum());
                assertEquals("pglite-jdbc-test", queryExecutor.getApplicationName());
                queryExecutor.setAdaptiveFetch(false);
                assertEquals(false, queryExecutor.getAdaptiveFetch());
                queryExecutor.addBinaryReceiveOid(23);
                assertTrue(queryExecutor.getBinaryReceiveOids().contains(23));
                queryExecutor.removeBinaryReceiveOid(23);
                assertEquals(false, queryExecutor.getBinaryReceiveOids().contains(23));
            }
        });
    }

    @Test
    void shouldUseRegisteredPgjdbcObjectTypes() throws Exception {
        assertTimeout(Duration.ofSeconds(180), () -> {
            try (var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000")) {
                var pgConnection = connection.unwrap(org.postgresql.PGConnection.class);
                pgConnection.addDataType("jsonb", CompatJsonObject.class);

                var typeInfo = connection.unwrap(org.postgresql.core.BaseConnection.class).getTypeInfo();
                assertEquals(CompatJsonObject.class, typeInfo.getPGobject("jsonb"));

                try (var statement = connection.createStatement();
                     var resultSet = statement.executeQuery("SELECT '{\"ok\":true}'::jsonb AS payload")) {
                    assertTrue(resultSet.next());
                    var object = resultSet.getObject("payload");
                    assertEquals(CompatJsonObject.class, object.getClass());
                    assertEquals("jsonb", ((PGobject) object).getType());
                    assertTrue(((PGobject) object).getValue().contains("ok"));

                    var typedObject = resultSet.getObject("payload", CompatJsonObject.class);
                    assertEquals(CompatJsonObject.class, typedObject.getClass());
                    assertEquals("jsonb", typedObject.getType());
                }
            }
        });
    }

    @Test
    void shouldCopyInAndCopyOutWithOrgPostgresqlCopyManager() throws Exception {
        assertTimeout(Duration.ofSeconds(180), () -> {
            try (var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000")) {
                var pgConnection = connection.unwrap(org.postgresql.PGConnection.class);
                try (var statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE IF NOT EXISTS pg_copy_test(id int, name text)");
                    statement.execute("DELETE FROM pg_copy_test");
                }

                var csv = "1,alice\n2,bob\n";
                var copied = pgConnection.getCopyAPI().copyIn(
                    "COPY pg_copy_test(id,name) FROM STDIN WITH (FORMAT csv)",
                    new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))
                );
                assertTrue(copied >= 0);

                var out = new ByteArrayOutputStream();
                pgConnection.getCopyAPI().copyOut(
                    "COPY (SELECT id, name FROM pg_copy_test ORDER BY id) TO STDOUT WITH (FORMAT csv)",
                    out
                );
                var dumped = out.toString(StandardCharsets.UTF_8);
                assertTrue(dumped.contains("1,alice"));
                assertTrue(dumped.contains("2,bob"));
            }
        });
    }

    @Test
    void shouldSupportLargeObjectViaOrgPostgresqlApi() throws Exception {
        assertTimeout(Duration.ofSeconds(180), () -> {
            try (var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000")) {
                connection.setAutoCommit(false);
                var pgConnection = connection.unwrap(org.postgresql.PGConnection.class);
                LargeObjectManager manager = pgConnection.getLargeObjectAPI();
                var oid = manager.createLO();
                var largeObject = manager.open(oid);
                var payload = "hello-lobject".getBytes(StandardCharsets.UTF_8);
                largeObject.write(payload);
                largeObject.seek(0);
                var loaded = largeObject.read(payload.length);
                assertArrayEquals(payload, loaded);
                largeObject.close();
                manager.delete(oid);
                connection.commit();
            }
        });
    }

    @Test
    void shouldExposeJdbcDatabaseMetadata() throws Exception {
        assertTimeout(Duration.ofSeconds(180), () -> {
            try (var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000")) {
                try (var statement = connection.createStatement()) {
                    statement.execute("""
                        CREATE TABLE meta_parent(
                          id int PRIMARY KEY,
                          name text NOT NULL,
                          payload jsonb
                        )
                        """);
                    statement.execute("""
                        CREATE TABLE meta_child(
                          id int PRIMARY KEY,
                          parent_id int REFERENCES meta_parent(id)
                        )
                        """);
                    statement.execute("CREATE INDEX meta_parent_name_idx ON meta_parent(name)");
                }

                var metadata = connection.getMetaData();
                try (var tables = metadata.getTables(null, "public", "meta_parent", new String[] { "TABLE" })) {
                    assertTrue(tables.next());
                    assertEquals("public", tables.getString("TABLE_SCHEM"));
                    assertEquals("meta_parent", tables.getString("TABLE_NAME"));
                    assertEquals("TABLE", tables.getString("TABLE_TYPE"));
                }

                try (var columns = metadata.getColumns(null, "public", "meta_parent", "%")) {
                    assertTrue(columns.next());
                    assertEquals("id", columns.getString("COLUMN_NAME"));
                    assertEquals(java.sql.Types.INTEGER, columns.getInt("DATA_TYPE"));
                    assertTrue(columns.next());
                    assertEquals("name", columns.getString("COLUMN_NAME"));
                    assertEquals("NO", columns.getString("IS_NULLABLE"));
                }

                try (var keys = metadata.getPrimaryKeys(null, "public", "meta_parent")) {
                    assertTrue(keys.next());
                    assertEquals("id", keys.getString("COLUMN_NAME"));
                    assertEquals(1, keys.getShort("KEY_SEQ"));
                }

                var foundIndex = false;
                try (var indexes = metadata.getIndexInfo(null, "public", "meta_parent", false, false)) {
                    while (indexes.next()) {
                        if ("meta_parent_name_idx".equals(indexes.getString("INDEX_NAME"))) {
                            foundIndex = true;
                            assertEquals("name", indexes.getString("COLUMN_NAME"));
                        }
                    }
                }
                assertTrue(foundIndex);

                var foundJsonb = false;
                try (var types = metadata.getTypeInfo()) {
                    while (types.next()) {
                        if ("jsonb".equals(types.getString("TYPE_NAME"))) {
                            foundJsonb = true;
                            assertEquals(java.sql.Types.OTHER, types.getInt("DATA_TYPE"));
                        }
                    }
                }
                assertTrue(foundJsonb);

                var baseConnection = connection.unwrap(org.postgresql.core.BaseConnection.class);
                var typeInfo = baseConnection.getTypeInfo();
                assertEquals(2950, typeInfo.getPGType("uuid"));
                assertEquals("jsonb", typeInfo.getPGType(3802));
                assertEquals(2950, typeInfo.getPGArrayElement(2951));
                assertEquals(2951, typeInfo.getPGArrayType("uuid"));
                assertEquals(java.sql.Types.TIMESTAMP_WITH_TIMEZONE, typeInfo.getSQLType("timestamptz"));

                try (var imported = metadata.getImportedKeys(null, "public", "meta_child")) {
                    assertTrue(imported.next());
                    assertEquals("meta_parent", imported.getString("PKTABLE_NAME"));
                    assertEquals("id", imported.getString("PKCOLUMN_NAME"));
                    assertEquals("meta_child", imported.getString("FKTABLE_NAME"));
                    assertEquals("parent_id", imported.getString("FKCOLUMN_NAME"));
                }
            }
        });
    }
}
