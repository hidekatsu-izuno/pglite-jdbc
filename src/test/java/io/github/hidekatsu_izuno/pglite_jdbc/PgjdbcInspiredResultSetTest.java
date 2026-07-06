package io.github.hidekatsu_izuno.pglite_jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PgjdbcInspiredResultSetTest {
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
    void resultSetScrollPositionAndColumnLookupFollowPgjdbcExpectations() throws Exception {
        try (var statement = connection.createStatement(
                 ResultSet.TYPE_SCROLL_INSENSITIVE,
                 ResultSet.CONCUR_READ_ONLY
             );
             var resultSet = statement.executeQuery(
                 "SELECT x AS id, x AS \"ID2\" FROM generate_series(1, 6) AS t(x) ORDER BY x"
             )) {
            assertEquals(1, resultSet.findColumn("id"));
            assertEquals(1, resultSet.findColumn("ID"));
            assertEquals(1, resultSet.findColumn("Id"));
            assertEquals(2, resultSet.findColumn("id2"));
            assertEquals(2, resultSet.findColumn("ID2"));
            assertEquals(2, resultSet.findColumn("Id2"));
            assertThrows(SQLException.class, () -> resultSet.findColumn("id3"));

            assertFalse(resultSet.absolute(0));
            assertEquals(0, resultSet.getRow());

            assertTrue(resultSet.absolute(-1));
            assertEquals(6, resultSet.getRow());
            assertEquals(6, resultSet.getInt("ID"));

            assertTrue(resultSet.absolute(1));
            assertEquals(1, resultSet.getRow());

            assertFalse(resultSet.absolute(-10));
            assertEquals(0, resultSet.getRow());
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getRow());

            assertTrue(resultSet.relative(2));
            assertEquals(3, resultSet.getRow());
            assertTrue(resultSet.relative(0));
            assertEquals(3, resultSet.getRow());

            assertFalse(resultSet.relative(10));
            assertEquals(0, resultSet.getRow());
            assertTrue(resultSet.isAfterLast());
        }
    }

    @Test
    void relativeMovementMatchesPgjdbcBoundaries() throws Exception {
        try (var statement = connection.createStatement(
                 ResultSet.TYPE_SCROLL_INSENSITIVE,
                 ResultSet.CONCUR_READ_ONLY
             );
             var resultSet = statement.executeQuery(
                 "SELECT x AS id FROM generate_series(1, 6) AS t(x) ORDER BY x"
             )) {
            assertFalse(resultSet.relative(0));
            assertEquals(0, resultSet.getRow());
            assertTrue(resultSet.isBeforeFirst());

            assertTrue(resultSet.relative(2));
            assertEquals(2, resultSet.getRow());

            assertTrue(resultSet.relative(1));
            assertEquals(3, resultSet.getRow());

            assertTrue(resultSet.relative(0));
            assertEquals(3, resultSet.getRow());

            assertFalse(resultSet.relative(-3));
            assertEquals(0, resultSet.getRow());
            assertTrue(resultSet.isBeforeFirst());

            assertTrue(resultSet.relative(4));
            assertEquals(4, resultSet.getRow());

            assertTrue(resultSet.relative(-1));
            assertEquals(3, resultSet.getRow());

            assertFalse(resultSet.relative(6));
            assertEquals(0, resultSet.getRow());
            assertTrue(resultSet.isAfterLast());

            assertTrue(resultSet.relative(-4));
            assertEquals(3, resultSet.getRow());

            assertFalse(resultSet.relative(-6));
            assertEquals(0, resultSet.getRow());
            assertTrue(resultSet.isBeforeFirst());
        }
    }

    @Test
    void zeroRowResultPositioningMatchesPgjdbc() throws Exception {
        try (var statement = connection.createStatement(
                 ResultSet.TYPE_SCROLL_INSENSITIVE,
                 ResultSet.CONCUR_UPDATABLE
             );
             var resultSet = statement.executeQuery("SELECT 1 AS value WHERE false")) {
            assertFalse(resultSet.previous());
            assertFalse(resultSet.previous());
            assertFalse(resultSet.next());
            assertFalse(resultSet.next());
            assertFalse(resultSet.first());
            assertFalse(resultSet.last());
            assertEquals(0, resultSet.getRow());
            assertFalse(resultSet.absolute(1));
            assertFalse(resultSet.relative(1));
            assertFalse(resultSet.isBeforeFirst());
            assertFalse(resultSet.isAfterLast());
            assertFalse(resultSet.isFirst());
            assertFalse(resultSet.isLast());
        }
    }

    @Test
    void resultSetNullAndClosedStateFollowJdbcContract() throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT NULL::int4 AS value")) {
            assertTrue(resultSet.next());
            assertEquals(0, resultSet.getInt("value"));
            assertTrue(resultSet.wasNull());

            resultSet.close();
            resultSet.close();
            assertTrue(resultSet.isClosed());
            assertThrows(SQLException.class, () -> resultSet.getInt("value"));
            assertThrows(SQLException.class, resultSet::wasNull);
            assertThrows(SQLException.class, resultSet::getMetaData);
            assertThrows(SQLException.class, resultSet::getType);
            assertThrows(SQLException.class, resultSet::getConcurrency);
            assertThrows(SQLException.class, resultSet::getHoldability);
            assertThrows(SQLException.class, resultSet::getRow);
            assertThrows(SQLException.class, () -> resultSet.findColumn("value"));
            assertThrows(SQLException.class, () -> resultSet.getObject("value"));
            assertThrows(SQLException.class, resultSet::getStatement);
            assertThrows(SQLException.class, resultSet::getCursorName);
            assertThrows(SQLException.class, resultSet::clearWarnings);
            assertThrows(SQLException.class, () -> resultSet.setFetchSize(1));
            assertThrows(SQLException.class, () -> resultSet.setFetchDirection(ResultSet.FETCH_FORWARD));
            assertThrows(SQLException.class, resultSet::rowUpdated);
            assertThrows(SQLException.class, () -> resultSet.updateInt(1, 1));
            assertThrows(SQLException.class, resultSet::moveToInsertRow);
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    void resultSetCursorNameAndUnicodeStreamMatchPgjdbc() throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT 'caf\u00e9'::text AS value, NULL::text AS nil")) {
            assertTrue(resultSet.next());
            assertNull(resultSet.getCursorName());
            assertEquals("caf\u00e9", new String(resultSet.getUnicodeStream(1).readAllBytes(), StandardCharsets.UTF_8));
            assertEquals(
                "caf\u00e9",
                new String(resultSet.getUnicodeStream("value").readAllBytes(), StandardCharsets.UTF_8)
            );
            assertNull(resultSet.getUnicodeStream("nil"));
        }
    }

    @Test
    void resultSetUnsupportedGetterMethodsMatchPgjdbc() throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT 'https://example.test' AS link, 'text' AS label")) {
            assertTrue(resultSet.next());

            assertPgjdbcResultSetNotImplemented("getURL(int)", () -> resultSet.getURL(1));
            assertPgjdbcResultSetNotImplemented("getURL(int)", () -> resultSet.getURL("link"));
            assertPgjdbcResultSetNotImplemented("getRef(int)", () -> resultSet.getRef(1));
            assertPgjdbcResultSetNotImplemented("getRef(int)", () -> resultSet.getRef("link"));
            assertPgjdbcResultSetNotImplemented("getRowId(int)", () -> resultSet.getRowId(1));
            assertPgjdbcResultSetNotImplemented("getRowId(int)", () -> resultSet.getRowId("link"));
            assertPgjdbcResultSetNotImplemented("getNClob(int)", () -> resultSet.getNClob(1));
            assertPgjdbcResultSetNotImplemented("getNClob(int)", () -> resultSet.getNClob("link"));
            assertPgjdbcResultSetNotImplemented("getNString(int)", () -> resultSet.getNString(2));
            assertPgjdbcResultSetNotImplemented("getNString(int)", () -> resultSet.getNString("label"));
            assertPgjdbcResultSetNotImplemented("getNCharacterStream(int)", () -> resultSet.getNCharacterStream(2));
            assertPgjdbcResultSetNotImplemented("getNCharacterStream(int)", () -> resultSet.getNCharacterStream("label"));
        }
    }

    @Test
    void resultSetObjectMapLookupMatchesPgjdbc() throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT 'value'::text AS value")) {
            assertTrue(resultSet.next());
            assertEquals("value", resultSet.getObject(1, new HashMap<String, Class<?>>()));
            assertEquals("value", resultSet.getObject("value", new HashMap<String, Class<?>>()));

            var map = new HashMap<String, Class<?>>();
            map.put("text", String.class);
            assertPgjdbcResultSetNotImplemented("getObjectImpl(int,Map)", () -> resultSet.getObject(1, map));
            assertPgjdbcResultSetNotImplemented("getObjectImpl(int,Map)", () -> resultSet.getObject("value", map));
        }
    }

    @Test
    void resultSetUpdateStatusDefaultsAndReadOnlyUpdatesAreRejectedLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT 1 AS value")) {
            assertTrue(resultSet.next());
            assertFalse(resultSet.rowUpdated());
            assertFalse(resultSet.rowInserted());
            assertFalse(resultSet.rowDeleted());
            assertThrows(SQLException.class, () -> resultSet.updateInt(1, 2));
            assertThrows(SQLException.class, resultSet::moveToInsertRow);
        }
    }

    @Test
    void resultSetUnsupportedUpdateMethodsMatchPgjdbc() throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT 1 AS value")) {
            assertTrue(resultSet.next());

            assertPgjdbcResultSetNotImplemented("updateRef(int,Ref)", () -> resultSet.updateRef(1, null));
            assertPgjdbcResultSetNotImplemented("updateRef(String,Ref)", () -> resultSet.updateRef("value", null));
            assertPgjdbcResultSetNotImplemented("updateBlob(int,Blob)", () -> resultSet.updateBlob(1, (java.sql.Blob) null));
            assertPgjdbcResultSetNotImplemented("updateBlob(String,Blob)", () -> resultSet.updateBlob("value", (java.sql.Blob) null));
            assertPgjdbcResultSetNotImplemented("updateClob(int,Clob)", () -> resultSet.updateClob(1, (java.sql.Clob) null));
            assertPgjdbcResultSetNotImplemented("updateClob(String,Clob)", () -> resultSet.updateClob("value", (java.sql.Clob) null));
            assertPgjdbcResultSetNotImplemented("updateObject", () -> resultSet.updateObject(1, "x", java.sql.JDBCType.VARCHAR));
            assertPgjdbcResultSetNotImplemented("updateObject", () -> resultSet.updateObject("value", "x", java.sql.JDBCType.VARCHAR, 1));
            assertPgjdbcResultSetNotImplemented("updateRowId(int, RowId)", () -> resultSet.updateRowId(1, null));
            assertPgjdbcResultSetNotImplemented("updateRowId(int, RowId)", () -> resultSet.updateRowId("value", null));
            assertPgjdbcResultSetNotImplemented("updateNString(int, String)", () -> resultSet.updateNString(1, "x"));
            assertPgjdbcResultSetNotImplemented("updateNString(int, String)", () -> resultSet.updateNString("value", "x"));
            assertPgjdbcResultSetNotImplemented("updateNClob(int, NClob)", () -> resultSet.updateNClob(1, (java.sql.NClob) null));
            assertPgjdbcResultSetNotImplemented("updateNClob(int, NClob)", () -> resultSet.updateNClob("value", (java.sql.NClob) null));
            assertPgjdbcResultSetNotImplemented("updateNClob(int, Reader)", () -> resultSet.updateNClob(1, new StringReader("x")));
            assertPgjdbcResultSetNotImplemented("updateNClob(int, Reader)", () -> resultSet.updateNClob("value", new StringReader("x")));
            assertPgjdbcResultSetNotImplemented("updateNClob(int, Reader, long)", () -> resultSet.updateNClob(1, new StringReader("x"), 1L));
            assertPgjdbcResultSetNotImplemented("updateNClob(int, Reader, long)", () -> resultSet.updateNClob("value", new StringReader("x"), 1L));
            assertPgjdbcResultSetNotImplemented("updateBlob(int, InputStream)", () -> resultSet.updateBlob(1, new ByteArrayInputStream(new byte[] { 1 })));
            assertPgjdbcResultSetNotImplemented("updateBlob(int, InputStream)", () -> resultSet.updateBlob("value", new ByteArrayInputStream(new byte[] { 1 })));
            assertPgjdbcResultSetNotImplemented("updateBlob(int, InputStream, long)", () -> resultSet.updateBlob(1, new ByteArrayInputStream(new byte[] { 1 }), 1L));
            assertPgjdbcResultSetNotImplemented("updateBlob(int, InputStream, long)", () -> resultSet.updateBlob("value", new ByteArrayInputStream(new byte[] { 1 }), 1L));
            assertPgjdbcResultSetNotImplemented("updateClob(int, Reader)", () -> resultSet.updateClob(1, new StringReader("x")));
            assertPgjdbcResultSetNotImplemented("updateClob(int, Reader)", () -> resultSet.updateClob("value", new StringReader("x")));
            assertPgjdbcResultSetNotImplemented("updateClob(int, Reader, long)", () -> resultSet.updateClob(1, new StringReader("x"), 1L));
            assertPgjdbcResultSetNotImplemented("updateClob(int, Reader, long)", () -> resultSet.updateClob("value", new StringReader("x"), 1L));
            assertPgjdbcResultSetNotImplemented("updateNCharacterStream(int, Reader, long)", () -> resultSet.updateNCharacterStream(1, new StringReader("x"), 1));
            assertPgjdbcResultSetNotImplemented("updateNCharacterStream(int, Reader, long)", () -> resultSet.updateNCharacterStream("value", new StringReader("x"), 1));
            assertPgjdbcResultSetNotImplemented("updateNCharacterStream(int, Reader)", () -> resultSet.updateNCharacterStream(1, new StringReader("x")));
            assertPgjdbcResultSetNotImplemented("updateNCharacterStream(int, Reader)", () -> resultSet.updateNCharacterStream("value", new StringReader("x")));
            assertPgjdbcResultSetNotImplemented("updateNCharacterStream(int, Reader, long)", () -> resultSet.updateNCharacterStream(1, new StringReader("x"), 1L));
            assertPgjdbcResultSetNotImplemented("updateNCharacterStream(int, Reader, long)", () -> resultSet.updateNCharacterStream("value", new StringReader("x"), 1L));
            assertPgjdbcResultSetNotImplemented("updateCharacterStream(int, Reader)", () -> resultSet.updateCharacterStream(1, new StringReader("x")));
            assertPgjdbcResultSetNotImplemented("updateCharacterStream(int, Reader)", () -> resultSet.updateCharacterStream("value", new StringReader("x")));
            assertPgjdbcResultSetNotImplemented("updateCharacterStream(int, Reader, long)", () -> resultSet.updateCharacterStream(1, new StringReader("x"), 1L));
            assertPgjdbcResultSetNotImplemented("updateCharacterStream(int, Reader, long)", () -> resultSet.updateCharacterStream("value", new StringReader("x"), 1L));
            assertPgjdbcResultSetNotImplemented("updateBinaryStream(int, InputStream)", () -> resultSet.updateBinaryStream(1, new ByteArrayInputStream(new byte[] { 1 })));
            assertPgjdbcResultSetNotImplemented("updateBinaryStream(int, InputStream)", () -> resultSet.updateBinaryStream("value", new ByteArrayInputStream(new byte[] { 1 })));
            assertPgjdbcResultSetNotImplemented("updateBinaryStream(int, InputStream, long)", () -> resultSet.updateBinaryStream(1, new ByteArrayInputStream(new byte[] { 1 }), 1L));
            assertPgjdbcResultSetNotImplemented("updateBinaryStream(int, InputStream, long)", () -> resultSet.updateBinaryStream("value", new ByteArrayInputStream(new byte[] { 1 }), 1L));
            assertPgjdbcResultSetNotImplemented("updateAsciiStream(int, InputStream)", () -> resultSet.updateAsciiStream(1, new ByteArrayInputStream(new byte[] { 1 })));
            assertPgjdbcResultSetNotImplemented("updateAsciiStream(int, InputStream)", () -> resultSet.updateAsciiStream("value", new ByteArrayInputStream(new byte[] { 1 })));
            assertPgjdbcResultSetNotImplemented("updateAsciiStream(int, InputStream, long)", () -> resultSet.updateAsciiStream(1, new ByteArrayInputStream(new byte[] { 1 }), 1L));
            assertPgjdbcResultSetNotImplemented("updateAsciiStream(int, InputStream, long)", () -> resultSet.updateAsciiStream("value", new ByteArrayInputStream(new byte[] { 1 }), 1L));
        }
    }

    @Test
    void resultSetFetchSizeRejectsNegativeValuesLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT 1 AS value")) {
            var error = assertThrows(SQLException.class, () -> resultSet.setFetchSize(-1));
            assertEquals("Fetch size must be a value greater than or equal to 0.", error.getMessage());
            assertEquals("22023", error.getSQLState());
            resultSet.setFetchSize(2);
            assertEquals(2, resultSet.getFetchSize());
        }
    }

    @Test
    void forwardOnlyResultSetRejectsNonForwardFetchDirectionLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT 1 AS value")) {
            assertEquals(ResultSet.FETCH_FORWARD, resultSet.getFetchDirection());
            var reverseError = assertThrows(
                SQLException.class,
                () -> resultSet.setFetchDirection(ResultSet.FETCH_REVERSE)
            );
            assertEquals(
                "Operation requires a scrollable ResultSet, but this ResultSet is FORWARD_ONLY.",
                reverseError.getMessage()
            );
            assertEquals("24000", reverseError.getSQLState());

            var unknownError = assertThrows(
                SQLException.class,
                () -> resultSet.setFetchDirection(ResultSet.FETCH_UNKNOWN)
            );
            assertEquals(
                "Operation requires a scrollable ResultSet, but this ResultSet is FORWARD_ONLY.",
                unknownError.getMessage()
            );
            assertEquals("24000", unknownError.getSQLState());

            var invalidError = assertThrows(SQLException.class, () -> resultSet.setFetchDirection(-1));
            assertEquals("Invalid fetch direction constant: -1.", invalidError.getMessage());
            assertEquals("22023", invalidError.getSQLState());
            resultSet.setFetchDirection(ResultSet.FETCH_FORWARD);
            assertEquals(ResultSet.FETCH_FORWARD, resultSet.getFetchDirection());
        }
    }

    @Test
    void forwardOnlyResultSetRejectsScrollMovementLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
             var resultSet = statement.executeQuery("SELECT 1 AS value")) {
            assertThrows(SQLException.class, () -> resultSet.absolute(1));
            assertThrows(SQLException.class, resultSet::afterLast);
            assertThrows(SQLException.class, resultSet::beforeFirst);
            assertThrows(SQLException.class, resultSet::first);
            assertThrows(SQLException.class, resultSet::last);
            assertThrows(SQLException.class, resultSet::previous);
            assertThrows(SQLException.class, () -> resultSet.relative(1));

            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt("value"));
        }
    }

    @Test
    void statementMaxFieldSizeAppliesOnlyToCharacterAndBinaryColumnsLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement()) {
            statement.setMaxFieldSize(2);
            assertEquals(2, statement.getMaxFieldSize());

            try (var resultSet = statement.executeQuery("""
                SELECT
                  12345::int4 AS int_value,
                  '12345'::text AS text_value,
                  '12345'::varchar AS varchar_value,
                  decode('0102030405', 'hex') AS bytea_value
                """)) {
                assertTrue(resultSet.next());
                assertTrue(resultSet.getString("int_value").length() > 2);
                assertTrue(resultSet.getBytes("int_value").length >= 4);
                assertEquals("12", resultSet.getString("text_value"));
                assertEquals("12", resultSet.getString("varchar_value"));
                org.junit.jupiter.api.Assertions.assertArrayEquals(
                    new byte[] { 1, 2 },
                    resultSet.getBytes("bytea_value")
                );
                assertFalse(resultSet.next());
            }
        }
    }

    @Test
    void duplicateColumnNameFindsFirstColumnAndIndexesAreBoundsChecked() throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT 1 AS a, 2 AS a")) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.findColumn("a"));
            assertThrows(SQLException.class, () -> resultSet.getInt(-9));
            assertThrows(SQLException.class, () -> resultSet.getInt(0));
            assertThrows(SQLException.class, () -> resultSet.getInt(3));
        }
    }

    @Test
    void zeroRowResultPositioningMatchesPgjdbcExpectations() throws Exception {
        try (var statement = connection.createStatement(
                 ResultSet.TYPE_SCROLL_INSENSITIVE,
                 ResultSet.CONCUR_READ_ONLY
             );
             var resultSet = statement.executeQuery("SELECT 1 AS id WHERE false")) {
            assertFalse(resultSet.previous());
            assertFalse(resultSet.next());
            assertFalse(resultSet.first());
            assertFalse(resultSet.last());
            assertEquals(0, resultSet.getRow());
            assertFalse(resultSet.absolute(1));
            assertFalse(resultSet.relative(1));
            assertFalse(resultSet.isBeforeFirst());
            assertFalse(resultSet.isAfterLast());
            assertFalse(resultSet.isFirst());
            assertFalse(resultSet.isLast());
        }
    }

    @Test
    void oneRowResultPositioningMatchesPgjdbcExpectations() throws Exception {
        try (var statement = connection.createStatement(
                 ResultSet.TYPE_SCROLL_INSENSITIVE,
                 ResultSet.CONCUR_READ_ONLY
             );
             var resultSet = statement.executeQuery("SELECT 1 AS id")) {
            assertTrue(resultSet.isBeforeFirst());
            assertFalse(resultSet.isAfterLast());
            assertFalse(resultSet.isFirst());
            assertFalse(resultSet.isLast());

            assertTrue(resultSet.next());
            assertFalse(resultSet.isBeforeFirst());
            assertFalse(resultSet.isAfterLast());
            assertTrue(resultSet.isFirst());
            assertTrue(resultSet.isLast());

            assertFalse(resultSet.next());
            assertFalse(resultSet.isBeforeFirst());
            assertTrue(resultSet.isAfterLast());
            assertFalse(resultSet.isFirst());
            assertFalse(resultSet.isLast());

            assertTrue(resultSet.previous());
            assertFalse(resultSet.isBeforeFirst());
            assertFalse(resultSet.isAfterLast());
            assertTrue(resultSet.isFirst());
            assertTrue(resultSet.isLast());

            assertTrue(resultSet.absolute(1));
            assertFalse(resultSet.isBeforeFirst());
            assertFalse(resultSet.isAfterLast());
            assertTrue(resultSet.isFirst());
            assertTrue(resultSet.isLast());

            assertFalse(resultSet.absolute(0));
            assertTrue(resultSet.isBeforeFirst());
            assertFalse(resultSet.isAfterLast());
            assertFalse(resultSet.isFirst());
            assertFalse(resultSet.isLast());

            assertFalse(resultSet.absolute(2));
            assertFalse(resultSet.isBeforeFirst());
            assertTrue(resultSet.isAfterLast());
            assertFalse(resultSet.isFirst());
            assertFalse(resultSet.isLast());
        }
    }

    @Test
    void booleanGetterAcceptsPgjdbcCompatibleValuesAndRejectsInvalidValues() throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("""
                 SELECT
                   true AS b_true,
                   false AS b_false,
                   '1'::text AS t_one,
                   '0'::text AS t_zero,
                   ' yes '::text AS t_yes,
                   'Off'::text AS t_off,
                   '2'::text AS t_bad_text,
                   2::int4 AS i_bad
                 """)) {
            assertTrue(resultSet.next());
            assertTrue(resultSet.getBoolean("b_true"));
            assertFalse(resultSet.getBoolean("b_false"));
            assertTrue(resultSet.getBoolean("t_one"));
            assertFalse(resultSet.getBoolean("t_zero"));
            assertTrue(resultSet.getBoolean("t_yes"));
            assertFalse(resultSet.getBoolean("t_off"));
            assertThrows(SQLException.class, () -> resultSet.getBoolean("t_bad_text"));
            assertThrows(SQLException.class, () -> resultSet.getBoolean("i_bad"));
        }
    }

    @Test
    void numericGettersRejectInvalidTextValues() throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT 'not a number'::text AS value")) {
            assertTrue(resultSet.next());
            assertThrows(SQLException.class, () -> resultSet.getInt("value"));
            assertThrows(SQLException.class, () -> resultSet.getLong("value"));
            assertThrows(SQLException.class, () -> resultSet.getBigDecimal("value"));
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    void bigDecimalScaleUsesHalfEvenRoundingLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT 1.125::numeric AS value")) {
            assertTrue(resultSet.next());
            assertEquals(new BigDecimal("1.12"), resultSet.getBigDecimal(1, 2));
            assertFalse(resultSet.next());
        }
    }

    @Test
    void numericSpecialValuesFollowPgjdbcConversions() throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("""
                 SELECT
                   'NaN'::numeric AS nan_numeric,
                   'NaN'::real AS nan_real,
                   'NaN'::double precision AS nan_double,
                   'Infinity'::numeric AS inf_numeric,
                   'Infinity'::real AS inf_real,
                   'Infinity'::double precision AS inf_double,
                   '-Infinity'::numeric AS neg_inf_numeric,
                   '-Infinity'::real AS neg_inf_real,
                   '-Infinity'::double precision AS neg_inf_double
                 """)) {
            assertTrue(resultSet.next());
            assertTrue(Double.isNaN((Double) resultSet.getObject("nan_numeric")));
            assertTrue(Double.isNaN(resultSet.getDouble("nan_numeric")));
            assertTrue(Float.isNaN((Float) resultSet.getObject("nan_real")));
            assertTrue(Float.isNaN(resultSet.getFloat("nan_real")));
            assertTrue(Double.isNaN((Double) resultSet.getObject("nan_double")));
            assertTrue(Double.isNaN(resultSet.getDouble("nan_double")));
            assertThrows(SQLException.class, () -> resultSet.getBigDecimal("nan_numeric"));

            assertEquals(Double.POSITIVE_INFINITY, resultSet.getObject("inf_numeric"));
            assertEquals(Double.POSITIVE_INFINITY, resultSet.getDouble("inf_numeric"));
            assertEquals(Float.POSITIVE_INFINITY, resultSet.getObject("inf_real"));
            assertEquals(Float.POSITIVE_INFINITY, resultSet.getFloat("inf_real"));
            assertEquals(Double.POSITIVE_INFINITY, resultSet.getObject("inf_double"));
            assertEquals(Double.POSITIVE_INFINITY, resultSet.getDouble("inf_double"));
            assertThrows(SQLException.class, () -> resultSet.getBigDecimal("inf_numeric"));

            assertEquals(Double.NEGATIVE_INFINITY, resultSet.getObject("neg_inf_numeric"));
            assertEquals(Double.NEGATIVE_INFINITY, resultSet.getDouble("neg_inf_numeric"));
            assertEquals(Float.NEGATIVE_INFINITY, resultSet.getObject("neg_inf_real"));
            assertEquals(Float.NEGATIVE_INFINITY, resultSet.getFloat("neg_inf_real"));
            assertEquals(Double.NEGATIVE_INFINITY, resultSet.getObject("neg_inf_double"));
            assertEquals(Double.NEGATIVE_INFINITY, resultSet.getDouble("neg_inf_double"));
            assertThrows(SQLException.class, () -> resultSet.getBigDecimal("neg_inf_numeric"));
        }
    }

    @Test
    void numericGettersTruncateAndCheckOverflowLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("""
                 SELECT *
                 FROM (VALUES
                   ('1.2'::numeric),
                   ('-2.5'::numeric),
                   ('127'::numeric),
                   ('128'::numeric),
                   ('32767'::numeric),
                   ('32768'::numeric),
                   ('2147483647'::numeric),
                   ('2147483648'::numeric),
                   ('9223372036854775807'::numeric),
                   ('9223372036854775807.9'::numeric),
                   ('9223372036854775808'::numeric),
                   ('-9223372036854775809'::numeric)
                 ) AS values(value)
                 """)) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getByte("value"));
            assertEquals(1, resultSet.getShort("value"));
            assertEquals(1, resultSet.getInt("value"));
            assertEquals(1L, resultSet.getLong("value"));

            assertTrue(resultSet.next());
            assertEquals(-2, resultSet.getByte("value"));
            assertEquals(-2, resultSet.getShort("value"));
            assertEquals(-2, resultSet.getInt("value"));
            assertEquals(-2L, resultSet.getLong("value"));

            assertTrue(resultSet.next());
            assertEquals(127, resultSet.getByte("value"));

            assertTrue(resultSet.next());
            assertThrows(SQLException.class, () -> resultSet.getByte("value"));

            assertTrue(resultSet.next());
            assertEquals(32767, resultSet.getShort("value"));

            assertTrue(resultSet.next());
            assertThrows(SQLException.class, () -> resultSet.getShort("value"));

            assertTrue(resultSet.next());
            assertEquals(Integer.MAX_VALUE, resultSet.getInt("value"));

            assertTrue(resultSet.next());
            assertThrows(SQLException.class, () -> resultSet.getInt("value"));

            assertTrue(resultSet.next());
            assertEquals(Long.MAX_VALUE, resultSet.getLong("value"));

            assertTrue(resultSet.next());
            assertEquals(Long.MAX_VALUE, resultSet.getLong("value"));

            assertTrue(resultSet.next());
            assertThrows(SQLException.class, () -> resultSet.getLong("value"));

            assertTrue(resultSet.next());
            assertThrows(SQLException.class, () -> resultSet.getLong("value"));

            assertFalse(resultSet.next());
        }
    }

    @Test
    void typedGetObjectNumericConversionsCheckOverflowLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("""
                 SELECT *
                 FROM (VALUES
                   ('1.2'::numeric),
                   ('128'::numeric),
                   ('32768'::numeric),
                   ('2147483648'::numeric),
                   ('9223372036854775808'::numeric)
                 ) AS values(value)
                 """)) {
            assertTrue(resultSet.next());
            assertEquals((byte) 1, resultSet.getObject("value", Byte.class));
            assertEquals((short) 1, resultSet.getObject("value", Short.class));
            assertEquals(1, resultSet.getObject("value", Integer.class));
            assertEquals(1L, resultSet.getObject("value", Long.class));

            assertTrue(resultSet.next());
            assertThrows(SQLException.class, () -> resultSet.getObject("value", Byte.class));

            assertTrue(resultSet.next());
            assertThrows(SQLException.class, () -> resultSet.getObject("value", Short.class));

            assertTrue(resultSet.next());
            assertThrows(SQLException.class, () -> resultSet.getObject("value", Integer.class));

            assertTrue(resultSet.next());
            assertThrows(SQLException.class, () -> resultSet.getObject("value", Long.class));
            assertFalse(resultSet.next());
        }
    }

    @Test
    void typedGetObjectRejectsUnsupportedAndInvalidConversionsLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("""
                 SELECT
                   'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'::uuid AS uid,
                   'not-a-uuid'::text AS bad_uuid,
                   '2024-02-29'::date AS leap_day,
                   'not-a-date'::text AS bad_date,
                   42::int4 AS value
                 """)) {
            assertTrue(resultSet.next());
            assertEquals(
                UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"),
                resultSet.getObject("uid", UUID.class)
            );
            assertEquals(LocalDate.of(2024, 2, 29), resultSet.getObject("leap_day", LocalDate.class));
            assertThrows(SQLException.class, () -> resultSet.getObject("bad_uuid", UUID.class));
            assertThrows(SQLException.class, () -> resultSet.getObject("bad_date", LocalDate.class));
            assertThrows(SQLException.class, () -> resultSet.getObject("value", File.class));
            assertFalse(resultSet.next());
        }
    }

    @Test
    void booleanGetterRejectsPgjdbcBadBooleanSourceTypes() throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("""
                 SELECT
                   '2017-03-13 14:25:48.130861'::timestamp AS bad_timestamp,
                   '2017-03-13'::date AS bad_date,
                   '14:25:48.130861'::time AS bad_time,
                   ARRAY[[1,0],[0,1]] AS bad_array,
                   29::bit(4) AS bad_bit,
                   'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'::uuid AS bad_uuid
                 """)) {
            assertTrue(resultSet.next());
            assertThrows(SQLException.class, () -> resultSet.getBoolean("bad_timestamp"));
            assertThrows(SQLException.class, () -> resultSet.getBoolean("bad_date"));
            assertThrows(SQLException.class, () -> resultSet.getBoolean("bad_time"));
            assertThrows(SQLException.class, () -> resultSet.getBoolean("bad_array"));
            assertThrows(SQLException.class, () -> resultSet.getBoolean("bad_bit"));
            assertThrows(SQLException.class, () -> resultSet.getBoolean("bad_uuid"));
            assertFalse(resultSet.next());
        }
    }

    @Test
    void columnLookupIsLocaleIndependentLikePgjdbcTurkishLocaleCase() throws Exception {
        var previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            try (var statement = connection.createStatement();
                 var resultSet = statement.executeQuery("SELECT 7 AS id")) {
                assertTrue(resultSet.next());
                assertEquals(7, resultSet.getInt("ID"));
            }
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void resultSetGettersRequireCursorPositionedOnRow() throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT 1 AS value")) {
            assertThrows(SQLException.class, () -> resultSet.getInt("value"));
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt("value"));
            assertFalse(resultSet.next());
            assertThrows(SQLException.class, () -> resultSet.getInt("value"));
        }
    }

    @Test
    void duplicateColumnLabelsPreserveColumnOrderLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT 1 AS value, 2 AS value, 3 AS value")) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
            assertEquals(2, resultSet.getInt(2));
            assertEquals(3, resultSet.getInt(3));
            assertEquals(1, resultSet.getInt("value"));
            assertFalse(resultSet.next());
        }

        try (var statement = connection.createStatement()) {
            assertTrue(statement.execute("SELECT 1 AS value, 2 AS value, 3 AS value"));
            try (var resultSet = statement.getResultSet()) {
                assertTrue(resultSet.next());
                assertEquals(1, resultSet.getInt(1));
                assertEquals(2, resultSet.getInt(2));
                assertEquals(3, resultSet.getInt(3));
                assertEquals(1, resultSet.getInt("value"));
                assertFalse(resultSet.next());
            }
        }

        try (var prepared = connection.prepareStatement("SELECT ? AS value, ? AS value, ? AS value")) {
            prepared.setInt(1, 1);
            prepared.setInt(2, 2);
            prepared.setInt(3, 3);

            try (var resultSet = prepared.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals(1, resultSet.getInt(1));
                assertEquals(2, resultSet.getInt(2));
                assertEquals(3, resultSet.getInt(3));
                assertEquals(1, resultSet.getInt("value"));
                assertFalse(resultSet.next());
            }
        }
    }

    @Test
    void connectionRejectsInvalidResultSetOptionsLikePgjdbc() throws Exception {
        assertThrows(SQLException.class, () -> connection.createStatement(-1, -1, -1));
        assertThrows(
            SQLException.class,
            () -> connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, -1)
        );
        assertThrows(
            SQLException.class,
            () -> connection.createStatement(
                ResultSet.TYPE_SCROLL_INSENSITIVE,
                ResultSet.CONCUR_READ_ONLY,
                -1
            )
        );

        assertThrows(SQLException.class, () -> connection.prepareStatement("SELECT 1", -1, -1, -1));
        assertThrows(
            SQLException.class,
            () -> connection.prepareStatement("SELECT 1", ResultSet.TYPE_SCROLL_INSENSITIVE, -1)
        );
        assertThrows(
            SQLException.class,
            () -> connection.prepareStatement(
                "SELECT 1",
                ResultSet.TYPE_SCROLL_INSENSITIVE,
                ResultSet.CONCUR_READ_ONLY,
                -1
            )
        );
    }

    @Test
    void resultSetReportsStatementRequestedOptionsLikePgjdbc() throws Exception {
        try (var statement = connection.createStatement(
                 ResultSet.TYPE_SCROLL_SENSITIVE,
                 ResultSet.CONCUR_UPDATABLE,
                 ResultSet.HOLD_CURSORS_OVER_COMMIT
             );
             var resultSet = statement.executeQuery("SELECT 1 AS value")) {
            assertEquals(ResultSet.TYPE_SCROLL_SENSITIVE, resultSet.getType());
            assertEquals(ResultSet.CONCUR_UPDATABLE, resultSet.getConcurrency());
            assertPgjdbcResultSetNotImplemented("getHoldability()", resultSet::getHoldability);
            assertEquals(statement, resultSet.getStatement());
        }

        try (var prepared = connection.prepareStatement(
                 "SELECT 1 AS value",
                 ResultSet.TYPE_SCROLL_INSENSITIVE,
                 ResultSet.CONCUR_READ_ONLY,
                 ResultSet.CLOSE_CURSORS_AT_COMMIT
             );
             var resultSet = prepared.executeQuery()) {
            assertEquals(ResultSet.TYPE_SCROLL_INSENSITIVE, resultSet.getType());
            assertEquals(ResultSet.CONCUR_READ_ONLY, resultSet.getConcurrency());
            assertPgjdbcResultSetNotImplemented("getHoldability()", resultSet::getHoldability);
            assertEquals(prepared, resultSet.getStatement());
        }
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
