package io.github.hidekatsu_izuno.pglite_jdbc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PgjdbcInspiredLobTest {
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
    void blobSetBytesRejectsInvalidRangesWithSQLExceptionLikePgjdbc() throws Exception {
        var blob = connection.createBlob();
        blob.setBytes(1, "abcdef".getBytes(StandardCharsets.UTF_8));

        assertThrows(SQLException.class, () -> blob.setBytes(1, "xy".getBytes(StandardCharsets.UTF_8), -1, 1));
        assertThrows(SQLException.class, () -> blob.setBytes(1, "xy".getBytes(StandardCharsets.UTF_8), 0, -1));
        assertThrows(SQLException.class, () -> blob.setBytes(1, "xy".getBytes(StandardCharsets.UTF_8), 1, 2));
        assertThrows(SQLException.class, () -> blob.setBytes(0, "xy".getBytes(StandardCharsets.UTF_8)));

        assertEquals(2, blob.setBytes(2, "XY".getBytes(StandardCharsets.UTF_8), 0, 2));
        assertArrayEquals("aXYdef".getBytes(StandardCharsets.UTF_8), blob.getBytes(1, (int) blob.length()));
        blob.free();
        assertThrows(SQLException.class, blob::length);
    }

    @Test
    void clobSetStringRejectsInvalidRangesWithSQLExceptionLikePgjdbc() throws Exception {
        var clob = connection.createClob();
        clob.setString(1, "abcdef");

        assertThrows(SQLException.class, () -> clob.setString(1, "xy", -1, 1));
        assertThrows(SQLException.class, () -> clob.setString(1, "xy", 0, -1));
        assertThrows(SQLException.class, () -> clob.setString(1, "xy", 1, 2));
        assertThrows(SQLException.class, () -> clob.setString(0, "xy"));

        assertEquals(2, clob.setString(2, "XY", 0, 2));
        assertEquals("aXYdef", clob.getSubString(1, (int) clob.length()));
        clob.free();
        assertThrows(SQLException.class, clob::length);
    }
}
