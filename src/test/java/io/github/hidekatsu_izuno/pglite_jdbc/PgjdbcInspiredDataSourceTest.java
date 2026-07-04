package io.github.hidekatsu_izuno.pglite_jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.hidekatsu_izuno.pglite_jdbc.ds.PGConnectionPoolDataSource;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import org.junit.jupiter.api.Test;

class PgjdbcInspiredDataSourceTest {
    @Test
    void pooledConnectionLogicalConnectionImplementsPgConnectionLikePgjdbc() throws Exception {
        var dataSource = new PGConnectionPoolDataSource();
        dataSource.setUrl("jdbc:pglite:?protocolTimeoutMs=5000");

        var pooled = dataSource.getPooledConnection();
        try {
            try (var connection = pooled.getConnection()) {
                assertTrue(connection instanceof org.postgresql.PGConnection);
                assertTrue(connection.isWrapperFor(org.postgresql.PGConnection.class));
                assertEquals(
                    connection,
                    connection.unwrap(org.postgresql.PGConnection.class)
                );
                try (var statement = connection.createStatement()) {
                    assertTrue(statement instanceof org.postgresql.PGStatement);
                    assertEquals(connection, statement.getConnection());
                }
            }
        } finally {
            pooled.close();
        }
    }

    @Test
    void pooledConnectionClosedErrorMatchesPgjdbc() throws Exception {
        var dataSource = new PGConnectionPoolDataSource();
        dataSource.setUrl("jdbc:pglite:?protocolTimeoutMs=5000");
        var pooled = dataSource.getPooledConnection();
        var event = new AtomicReference<ConnectionEvent>();
        pooled.addConnectionEventListener(new ConnectionEventListener() {
            @Override
            public void connectionClosed(ConnectionEvent event) {
            }

            @Override
            public void connectionErrorOccurred(ConnectionEvent connectionEvent) {
                event.set(connectionEvent);
            }
        });

        pooled.close();
        var error = org.junit.jupiter.api.Assertions.assertThrows(
            SQLException.class,
            pooled::getConnection
        );
        assertEquals("This PooledConnection has already been closed.", error.getMessage());
        assertEquals("08003", error.getSQLState());
        assertEquals("08003", event.get().getSQLException().getSQLState());
    }
}
