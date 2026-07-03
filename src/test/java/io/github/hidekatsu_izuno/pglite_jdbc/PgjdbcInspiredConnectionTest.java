package io.github.hidekatsu_izuno.pglite_jdbc;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class PgjdbcInspiredConnectionTest {
    @Test
    void connectionRejectsOperationsAfterCloseLikePgjdbc() throws Exception {
        var connection = DriverManager.getConnection("jdbc:pglite:?protocolTimeoutMs=5000");
        connection.close();
        connection.close();

        assertTrue(connection.isClosed());
        assertThrows(SQLException.class, connection::createStatement);
        assertThrows(SQLException.class, () -> connection.prepareStatement("SELECT 1"));
        assertThrows(SQLException.class, connection::getAutoCommit);
        assertThrows(SQLException.class, connection::getMetaData);
        assertThrows(SQLException.class, connection::commit);
        assertThrows(SQLException.class, connection::rollback);
        assertThrows(SQLException.class, () -> connection.setReadOnly(true));
    }
}
