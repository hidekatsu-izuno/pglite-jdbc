package io.github.hidekatsu_izuno.pglite_jdbc;

import static org.junit.jupiter.api.Assertions.*;

import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.parser;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.serializer;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.index;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.errors.PGliteError;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.pglite;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.utils;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.base.InitResult;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.fs.memoryfs.MemoryFS;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UpstreamJsIntegrationTest {
    private pglite db;
    private final AtomicInteger fsInitializations = new AtomicInteger();

    @BeforeAll
    void open() {
        var options = new pglite.PGliteOptions();
        options.extensions = Map.of("spi", index.extension("spi"));
        options.fs = new MemoryFS() {
            @Override public Promise<InitResult> init(pglite pg, Map<String, Object> options) {
                if (fsInitializations.incrementAndGet() != 1) throw new IllegalStateException("Filesystem initialized twice");
                return super.init(pg, options);
            }
        };
        db = pglite.create(options).join();
    }

    @AfterAll
    void close() {
        if (db != null) db.close().join();
    }

    @Test
    void customFilesystemIsNotReinitializedByInitdb() {
        assertEquals(1, fsInitializations.get());
        assertEquals(42.0, db.<Map<String, Object>>querySync("SELECT 42 AS value", null, null).rows().getFirst().get("value"));
    }

    @Test
    void bundledSpiExtensionExecutesTrigger() {
        db.execSync("CREATE EXTENSION autoinc; CREATE SEQUENCE spi_ids; "
            + "CREATE TABLE spi_rows (id int); "
            + "CREATE TRIGGER spi_autoinc BEFORE INSERT ON spi_rows "
            + "FOR EACH ROW EXECUTE FUNCTION autoinc('id', 'spi_ids'); "
            + "INSERT INTO spi_rows VALUES (NULL), (NULL)", null);
        assertEquals(List.of(Map.of("id", 1.0), Map.of("id", 2.0)),
            db.<Map<String, Object>>querySync("SELECT id FROM spi_rows ORDER BY id", null, null).rows());
    }

    @Test
    void convertToPreservesEncodingAndSubsequentQueries() {
        var before = db.execSync("SELECT 1 AS value", null).getFirst();
        var converted = db.execSync("SELECT convert_to('abc', 'LATIN1') AS value", null).getFirst();
        assertArrayEquals(new byte[] {97, 98, 99},
            (byte[]) converted.rows().getFirst().get("value"));
        assertEquals("SELECT", converted.command());
        assertEquals(1, converted.rowCount());
        var latin1 = db.execSync("SELECT convert_to('café', 'LATIN1') AS value", null).getFirst();
        assertArrayEquals(new byte[] {99, 97, 102, (byte) 233},
            (byte[]) latin1.rows().getFirst().get("value"));
        for (var hex : List.of("c080", "eda080", "f0808080", "f4908080", "c241", "e28241", "f0908041")) {
            var error = assertThrows(PGliteError.class, () -> db.execSync(
                "SELECT convert(decode('" + hex + "', 'hex'), 'UTF8', 'LATIN1')", null));
            assertEquals("22021", error.error.code);
        }
        var untranslatable = assertThrows(PGliteError.class,
            () -> db.execSync("SELECT convert_to('日本語', 'LATIN1')", null));
        assertEquals("22P05", untranslatable.error.code);
        assertEquals(before.rows(), db.execSync("SELECT 1 AS value", null).getFirst().rows());
    }

    @Test
    void formatQueryUsesPositionsAndInferredTypes() {
        var formatted = utils.formatQuery(db, "SELECT $2::text AS second, $1::int AS first, $2::text AS again",
            new Object[] {7, "O'Reilly"}, null).join();
        assertEquals(Map.of("second", "O'Reilly", "first", 7.0, "again", "O'Reilly"),
            db.<Map<String, Object>>querySync(formatted, null, null).rows().getFirst());
        var input = Arrays.asList("NULL", null, "", "a,b");
        var arrays = utils.formatQuery(db, "SELECT $1::text[] AS value", new Object[] {input}, null).join();
        assertEquals(input, db.<Map<String, Object>>querySync(arrays, null, null).rows().getFirst().get("value"));
        var bytes = utils.formatQuery(db, "SELECT $1::bytea AS value", new Object[] {new byte[] {0, 1, -1}}, null).join();
        assertArrayEquals(new byte[] {0, 1, -1}, (byte[]) db.<Map<String, Object>>querySync(bytes, null, null).rows().getFirst().get("value"));
        assertThrows(RuntimeException.class, () -> utils.formatQuery(db, "SELECT $1::missing_type", new Object[] {1}, null).join());
        assertEquals("SELECT '9'::int AS value", utils.formatQuery(db, "SELECT $1::int AS value", new Object[] {9}, null).join());
    }

    @Test
    @Timeout(value = 20, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void transactionSqlDoesNotReacquireTheTransactionMutex() {
        var result = db.transactionSync(tx -> {
            var formatted = utils.formatQuery(db, "SELECT $2::int + $1::int AS value", new Object[] {2, 3}, tx).join();
            assertEquals(5.0, tx.<Map<String, Object>>query(formatted, null, null).join().rows().getFirst().get("value"));
            return tx.<Map<String, Object>>sql(List.of("SELECT ", "::int AS value"), 42);
        });
        assertEquals(42.0, result.rows().getFirst().get("value"));
    }

    @Test
    void allFinishedTransactionHandlesRejectOperations() {
        var saved = new AtomicReference<interface_.Transaction>();
        db.transactionSync(tx -> { saved.set(tx); return Promise.resolve(null); });
        assertClosed(saved.get());
        db.transactionSync(tx -> { saved.set(tx); return tx.rollback(); });
        assertClosed(saved.get());
        assertThrows(RuntimeException.class, () -> db.transactionSync(tx -> {
            saved.set(tx);
            return Promise.reject(new IllegalStateException("callback failed"));
        }));
        assertClosed(saved.get());
        assertThrows(RuntimeException.class, () -> db.transactionSync(tx -> {
            saved.set(tx);
            throw new IllegalStateException("synchronous callback failure");
        }));
        assertClosed(saved.get());
    }

    private void assertClosed(interface_.Transaction tx) {
        assertTrue(tx.closed());
        assertThrows(RuntimeException.class, () -> tx.query("SELECT 1", null, null).join());
        assertThrows(RuntimeException.class, () -> tx.exec("SELECT 1", null).join());
        assertThrows(RuntimeException.class, () -> tx.sql(List.of("SELECT 1")).join());
        assertThrows(RuntimeException.class, () -> tx.rollback().join());
        assertThrows(RuntimeException.class, () -> tx.listen("closed_transaction_test", ignored -> {}).join());
    }

    @Test
    void arraysAndUntypedDatesRoundTripThroughSql() {
        var input = Arrays.asList(null, "NULL", "", "quote\"", "back\\slash", "comma,value");
        assertEquals(input, db.<Map<String, Object>>querySync("SELECT $1::text[] AS value",
            new Object[] {input}, null).rows().getFirst().get("value"));
        var nested = List.of(input, input);
        assertEquals(nested, db.<Map<String, Object>>querySync("SELECT $1::text[][] AS value",
            new Object[] {nested}, null).rows().getFirst().get("value"));
        var result = db.<Map<String, Object>>querySync("SELECT $1 AS number, $2 AS date, $3 AS bool",
            new Object[] {42, Instant.parse("2024-01-15T12:34:56Z"), true}, null).rows().getFirst();
        assertEquals(Map.of("number", "42", "date", "2024-01-15T12:34:56.000Z", "bool", "true"), result);
    }

    @Test
    void jsonRecordsetsPreserveBigIntegersWithoutJsStringificationWorkarounds() {
        var values = List.of(Map.of("id", new BigInteger("9007199254740992")), Map.of("id", new BigInteger("9007199254740993")));
        var result = db.<Map<String, Object>>querySync("SELECT * FROM json_to_recordset($1) AS x(id bigint)",
            new Object[] {values}, null);
        assertEquals(List.of(Map.of("id", 9007199254740992L), Map.of("id", 9007199254740993L)), result.rows());
    }

    @Test
    void commandMetadataAndRawStreamingDoNotLeakAcrossQueries() {
        var results = db.execSync("CREATE TEMP TABLE js_counts (id int); INSERT INTO js_counts VALUES (1),(2); "
            + "UPDATE js_counts SET id=3 WHERE id=2; SELECT * FROM js_counts;", null);
        assertEquals("CREATE", results.getFirst().command());
        assertNull(results.getFirst().rowCount());
        assertEquals(2, results.get(1).rowCount());
        assertEquals(1, results.get(2).rowCount());
        assertEquals(3, results.get(2).affectedRows());
        assertEquals("SELECT", results.get(3).command());
        assertEquals(2, results.get(3).rowCount());
        var raw = new ArrayList<byte[]>();
        db.execProtocolRawStream(serializer.serialize.query("SELECT 7").toByteArray(),
            new interface_.ExecProtocolOptionsStream(false, raw::add)).join();
        var decoded = new ArrayList<messages.BackendMessage>();
        var parser = new parser.Parser();
        for (var bytes : raw) parser.parse(new Uint8Array(bytes), decoded::add);
        assertTrue(decoded.stream().anyMatch(m -> m instanceof messages.CommandCompleteMessage c && c.text.equals("SELECT 1")));
        var streamed = db.execProtocolStream(serializer.serialize.query("SELECT 8").toByteArray(),
            new interface_.ExecProtocolOptions(false, true, null)).join();
        assertTrue(streamed.stream().anyMatch(m -> m instanceof messages.DataRowMessage r && r.fields[0].equals("8")));
        assertEquals(9.0, db.<Map<String, Object>>querySync("SELECT 9 AS value", null, null).rows().getFirst().get("value"));
    }
}
