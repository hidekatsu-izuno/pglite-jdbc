package io.github.hidekatsu_izuno.pglite_jdbc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.sql.DriverManager;
import java.sql.SQLException;
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
            var connectionError = assertThrows(SQLException.class, () -> connection.unwrap(String.class));
            assertEquals("Cannot unwrap to java.lang.String", connectionError.getMessage());
            assertEquals(16, pgConnection.getDefaultFetchSize());
            assertEquals(AutoSave.CONSERVATIVE, pgConnection.getAutosave());
            assertEquals(PreferQueryMode.EXTENDED, pgConnection.getPreferQueryMode());
            try (var statement = connection.createStatement()) {
                assertEquals(5, statement.getQueryTimeout());
            }

            try (var prepared = connection.prepareStatement("SELECT ?::int4 AS value")) {
                var pgStatement = prepared.unwrap(PGStatement.class);
                var statementError = assertThrows(SQLException.class, () -> prepared.unwrap(String.class));
                assertEquals("Cannot unwrap to java.lang.String", statementError.getMessage());
                pgStatement.setPrepareThreshold(1);
                assertTrue(pgStatement.isUseServerPrepare());
                pgStatement.setAdaptiveFetch(true);
                assertTrue(pgStatement.getAdaptiveFetch());
                prepared.setInt(1, 42);
                try (var resultSet = prepared.executeQuery()) {
                    assertTrue(resultSet.next());
                    var pgResultSet = resultSet.unwrap(org.postgresql.PGRefCursorResultSet.class);
                    assertNull(pgResultSet.getRefCursor());
                    var resultSetError = assertThrows(SQLException.class, () -> resultSet.unwrap(String.class));
                    assertEquals("Cannot unwrap to java.lang.String", resultSetError.getMessage());
                    assertEquals(42, resultSet.getInt(1));
                    var metadata = resultSet.getMetaData();
                    assertEquals("int4", metadata.getColumnTypeName(1));
                    assertEquals(Integer.class.getName(), metadata.getColumnClassName(1));
                    assertEquals("", metadata.unwrap(PGResultSetMetaData.class).getBaseColumnName(1));
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
                 var statement = connection.createStatement(
                     java.sql.ResultSet.TYPE_SCROLL_INSENSITIVE,
                     java.sql.ResultSet.CONCUR_READ_ONLY
                 )) {
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
                try (var statement = connection.prepareStatement(
                    "SELECT ?::bytea AS payload, ?::text AS body"
                )) {
                    statement.setBytes(1, "blob-bytes".getBytes(StandardCharsets.UTF_8));
                    statement.setString(2, "clob text");
                    try (var resultSet = statement.executeQuery()) {
                        assertTrue(resultSet.next());
                        var readBlob = resultSet.getBlob("payload");
                        assertTrue(readBlob instanceof org.postgresql.jdbc.PgBlob);
                        assertArrayEquals(
                            "blob-bytes".getBytes(StandardCharsets.UTF_8),
                            readBlob.getBytes(1, (int) readBlob.length())
                        );
                        var blobPositionError = assertThrows(SQLException.class, () -> readBlob.getBytes(0, 1));
                        assertEquals("LOB positioning offsets start at 1.", blobPositionError.getMessage());
                        assertEquals("22023", blobPositionError.getSQLState());
                        readBlob.free();
                        var blobFreedError = assertThrows(SQLException.class, readBlob::length);
                        assertEquals("free() was called on this LOB previously", blobFreedError.getMessage());
                        assertEquals("55000", blobFreedError.getSQLState());

                        var readClob = resultSet.getClob("body");
                        assertTrue(readClob instanceof org.postgresql.jdbc.PgClob);
                        assertEquals("clob text", readClob.getSubString(1, (int) readClob.length()));
                        var clobPositionError = assertThrows(SQLException.class, () -> readClob.getSubString(0, 1));
                        assertEquals("LOB positioning offsets start at 1.", clobPositionError.getMessage());
                        assertEquals("22023", clobPositionError.getSQLState());
                        readClob.free();
                        var clobFreedError = assertThrows(SQLException.class, readClob::length);
                        assertEquals("free() was called on this LOB previously", clobFreedError.getMessage());
                        assertEquals("55000", clobFreedError.getSQLState());
                    }
                }
            }
        });
    }

    @Test
    void shouldSupportJdbcSqlXmlValues() throws Exception {
        assertTimeout(Duration.ofSeconds(180), () -> {
            try (var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000")) {
                var xml = connection.createSQLXML();
                assertEquals(org.postgresql.jdbc.PgSQLXML.class, xml.getClass());
                var uninitializedError = assertThrows(SQLException.class, xml::getString);
                assertEquals(
                    "This SQLXML object has not been initialized, so you cannot retrieve data from it.",
                    uninitializedError.getMessage()
                );
                assertEquals("55000", uninitializedError.getSQLState());

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
                var freedError = assertThrows(SQLException.class, xml::getString);
                assertEquals("This SQLXML object has already been freed.", freedError.getMessage());
                assertEquals("55000", freedError.getSQLState());
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
                statement.setString(1, "https://example.test/path?q=1");
                statement.setString(2, "unicode label");
                statement.setBigDecimal(3, new BigDecimal("12.345"));
                try (var resultSet = statement.executeQuery()) {
                    assertTrue(resultSet.next());
                    assertPgjdbcResultSetNotImplemented("getURL(int)", () -> resultSet.getURL("link"));
                    assertPgjdbcResultSetNotImplemented("getNString(int)", () -> resultSet.getNString("label"));
                    assertEquals(new BigDecimal("12.34"), resultSet.getBigDecimal("amount", 2));
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
                statement.setObject(5, "bytes", Types.BINARY);
                statement.setObject(6, "2024-01-02 03:04:05", Types.TIMESTAMP);

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
    void shouldRejectSqlTypeSetObjectLikePgjdbc() throws Exception {
        assertTimeout(Duration.ofSeconds(180), () -> {
            try (var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000");
                 var statement = connection.prepareStatement("SELECT ?")) {
                var error = assertThrows(
                    java.sql.SQLFeatureNotSupportedException.class,
                    () -> statement.setObject(1, "value", java.sql.JDBCType.VARCHAR)
                );
                assertEquals(
                    "Method org.postgresql.jdbc.PgPreparedStatement.setObject is not yet implemented.",
                    error.getMessage()
                );
                assertEquals(org.postgresql.util.PSQLState.NOT_IMPLEMENTED.getState(), error.getSQLState());

                var scaledError = assertThrows(
                    java.sql.SQLFeatureNotSupportedException.class,
                    () -> statement.setObject(1, null, java.sql.JDBCType.VARCHAR, 0)
                );
                assertEquals(
                    "Method org.postgresql.jdbc.PgPreparedStatement.setObject is not yet implemented.",
                    scaledError.getMessage()
                );
                assertEquals(org.postgresql.util.PSQLState.NOT_IMPLEMENTED.getState(), scaledError.getSQLState());
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
                assertEquals(0, typeInfo.getJavaArrayType("int4"));
                assertEquals(1007, typeInfo.getJavaArrayType(Integer.class.getName()));
                assertEquals(18, typeInfo.getPGType("char"));
                assertEquals("char", typeInfo.getPGType(18));
                assertEquals(java.sql.Types.CHAR, typeInfo.getSQLType(18));
                assertEquals(19, typeInfo.getPGType("name"));
                assertEquals("name", typeInfo.getPGType(19));
                assertEquals(java.sql.Types.VARCHAR, typeInfo.getSQLType(19));
                assertEquals(790, typeInfo.getPGType("money"));
                assertEquals("money", typeInfo.getPGType(790));
                assertEquals(java.sql.Types.DOUBLE, typeInfo.getSQLType(790));
                assertEquals(791, typeInfo.getPGArrayType("money"));
                assertEquals(790, typeInfo.getPGArrayElement(791));
                assertEquals(16, typeInfo.getPGType("bool"));
                assertEquals("bool", typeInfo.getPGType(16));
                assertEquals(java.sql.Types.BIT, typeInfo.getSQLType(16));
                assertEquals(Boolean.class.getName(), typeInfo.getJavaClass(16));
                assertEquals(java.sql.Types.CHAR, typeInfo.getSQLType(1042));
                assertEquals(1560, typeInfo.getPGType("bit"));
                assertEquals("bit", typeInfo.getPGType(1560));
                assertEquals(java.sql.Types.BIT, typeInfo.getSQLType(1560));
                assertEquals(Boolean.class.getName(), typeInfo.getJavaClass(1560));
                assertEquals(1562, typeInfo.getPGType("varbit"));
                assertEquals("varbit", typeInfo.getPGType(1562));
                assertEquals(java.sql.Types.OTHER, typeInfo.getSQLType(1562));
                assertEquals(String.class.getName(), typeInfo.getJavaClass(1562));
                assertEquals(java.sql.Types.TIME, typeInfo.getSQLType(1266));
                assertEquals(java.sql.Types.TIMESTAMP, typeInfo.getSQLType(1184));
                assertEquals(1790, typeInfo.getPGType("refcursor"));
                assertEquals("refcursor", typeInfo.getPGType(1790));
                assertEquals(java.sql.Types.REF_CURSOR, typeInfo.getSQLType(1790));
                assertEquals(java.sql.ResultSet.class.getName(), typeInfo.getJavaClass(1790));
                assertEquals(2201, typeInfo.getPGArrayType("refcursor"));
                assertEquals(1790, typeInfo.getPGArrayElement(2201));
                assertEquals(600, typeInfo.getPGType("point"));
                assertEquals("point", typeInfo.getPGType(600));
                assertEquals("org.postgresql.geometric.PGpoint", typeInfo.getJavaClass(600));
                assertEquals(1017, typeInfo.getPGArrayType("point"));
                assertEquals(600, typeInfo.getPGArrayElement(1017));
                assertEquals(603, typeInfo.getPGType("box"));
                assertEquals("box", typeInfo.getPGType(603));
                assertEquals("org.postgresql.geometric.PGBox", typeInfo.getJavaClass(603));
                assertEquals(1020, typeInfo.getPGArrayType("box"));
                assertEquals(603, typeInfo.getPGArrayElement(1020));
                assertEquals(1561, typeInfo.getPGArrayType("bit"));
                assertEquals(1563, typeInfo.getPGArrayType("varbit"));
                assertEquals(1560, typeInfo.getPGArrayElement(1561));
                assertEquals(1562, typeInfo.getPGArrayElement(1563));
                assertEquals(Integer.class.getName(), typeInfo.getJavaClass(23));
                assertEquals(10, typeInfo.getPrecision(23, -1));
                assertEquals(11, typeInfo.getDisplaySize(23, -1));
                assertEquals(0, typeInfo.getMaximumPrecision(23));
                assertEquals(10, typeInfo.getPrecision(1043, 14));
                assertEquals(10, typeInfo.getDisplaySize(1043, 14));
                assertEquals(Integer.MAX_VALUE, typeInfo.getPrecision(1043, -1));
                assertEquals(Integer.MAX_VALUE, typeInfo.getDisplaySize(1043, -1));
                assertEquals(Integer.MAX_VALUE, typeInfo.getPrecision(25, -1));
                assertEquals(Integer.MAX_VALUE, typeInfo.getDisplaySize(25, -1));
                assertEquals(Integer.MAX_VALUE, typeInfo.getPrecision(17, -1));
                assertEquals(Integer.MAX_VALUE, typeInfo.getDisplaySize(17, -1));
                assertEquals(Integer.MAX_VALUE, typeInfo.getPrecision(1562, -1));
                assertEquals(Integer.MAX_VALUE, typeInfo.getDisplaySize(1562, -1));
                assertEquals(10485760, typeInfo.getMaximumPrecision(1043));
                assertEquals(12, typeInfo.getPrecision(1700, 786438));
                assertEquals(2, typeInfo.getScale(1700, 786438));
                assertEquals(14, typeInfo.getDisplaySize(1700, 786438));
                assertEquals(15, typeInfo.getPrecision(1083, -1));
                assertEquals(15, typeInfo.getDisplaySize(1083, -1));
                assertEquals(6, typeInfo.getScale(1083, -1));
                assertTrue(typeInfo.isSigned(23));
                assertEquals(false, typeInfo.isCaseSensitive(23));
                assertEquals(false, typeInfo.isSigned(25));
                assertEquals(false, typeInfo.isSigned(26));
                assertTrue(typeInfo.isCaseSensitive(25));

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
                assertEquals(CompatJsonObject.class.getName(), typeInfo.getJavaClass(3802));

                try (var statement = connection.createStatement();
                     var resultSet = statement.executeQuery("SELECT '{\"ok\":true}'::jsonb AS payload")) {
                    assertTrue(resultSet.next());
                    assertEquals(CompatJsonObject.class.getName(), resultSet.getMetaData().getColumnClassName(1));
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
    void shouldCopyViaDirectOrgPostgresqlCopyManager() throws Exception {
        assertTimeout(Duration.ofSeconds(180), () -> {
            try (var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000")) {
                var baseConnection = connection.unwrap(org.postgresql.core.BaseConnection.class);
                var copyManager = new org.postgresql.copy.CopyManager(baseConnection);
                try (var statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE IF NOT EXISTS pg_direct_copy_test(id int, name text)");
                    statement.execute("DELETE FROM pg_direct_copy_test");
                }

                var csv = "1,alice\n2,bob\n";
                var copied = copyManager.copyIn(
                    "COPY pg_direct_copy_test(id,name) FROM STDIN WITH (FORMAT csv)",
                    new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))
                );
                assertTrue(copied >= 0);

                var out = new ByteArrayOutputStream();
                copyManager.copyOut(
                    "COPY (SELECT id, name FROM pg_direct_copy_test ORDER BY id) TO STDOUT WITH (FORMAT csv)",
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
                assertEquals(java.sql.Types.TIMESTAMP, typeInfo.getSQLType("timestamptz"));

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

    private void assertPgjdbcResultSetNotImplemented(String method, ThrowingSqlCall call) {
        var error = assertThrows(java.sql.SQLFeatureNotSupportedException.class, call::run);
        assertEquals(
            "Method org.postgresql.jdbc.PgResultSet." + method + " is not yet implemented.",
            error.getMessage()
        );
        assertEquals(org.postgresql.util.PSQLState.NOT_IMPLEMENTED.getState(), error.getSQLState());
    }

    @FunctionalInterface
    private interface ThrowingSqlCall {
        void run() throws Exception;
    }
}
