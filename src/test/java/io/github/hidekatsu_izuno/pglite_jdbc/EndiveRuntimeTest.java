package io.github.hidekatsu_izuno.pglite_jdbc;

import static org.junit.jupiter.api.Assertions.*;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.index;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.pglite;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class EndiveRuntimeTest {
    @Test
    void officialWasmRunsWithoutNativeLibraries() throws Exception {
        // Isolate runtime selection and the interpreter's larger heap from the native tests.
        var output = Files.createTempFile("pglite-endive-", ".log");
        var executable = Path.of(System.getProperty("java.home"), "bin",
            System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        var process = new ProcessBuilder(executable.toString(), "-Xmx1800m", "-Xss8m", "-XX:ActiveProcessorCount=1",
            "-Dpglite.force_endive=true", "-Dpglite.wasmer.library=/nonexistent/wasmer",
            "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
            Probe.class.getName()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(300, TimeUnit.SECONDS), () -> "Endive timed out: " + readLog(output));
            assertEquals(0, process.exitValue(), () -> readLog(output));
        } finally {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
            Files.deleteIfExists(output);
        }
    }

    private static String readLog(Path output) {
        try { return Files.readString(output); }
        catch (java.io.IOException error) { return error.toString(); }
    }

    public static class Probe {
        public static void main(String[] args) {
            var options = new pglite.PGliteOptions();
            options.extensions = Map.of("hstore", index.extension("hstore"),
                "pgcrypto", index.extension("pgcrypto"), "cube", index.extension("cube"));
            var db = new pglite(options);
            try {
                db.waitReady().join();
                db.execSync("CREATE EXTENSION hstore; CREATE EXTENSION pgcrypto; CREATE EXTENSION cube;", null);
                var row = db.<Map<String, Object>>querySync(
                    "SELECT 'a=>1'::hstore -> 'a' AS value, encode(digest('abc', 'sha256'), 'hex') AS digest, "
                        + "cube_distance('(0,0)'::cube, '(3,4)'::cube) AS distance, "
                        + "length(gen_random_bytes(16))::int AS bytes", null, null).rows().getFirst();
                assertEquals("1", row.get("value"));
                assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", row.get("digest"));
                assertEquals(5.0, row.get("distance"));
                assertEquals(16.0, row.get("bytes"));
                db.execSync("CREATE TABLE data (id integer, value text);", null);
                db.querySync("INSERT INTO data VALUES ($1, $2)", new Object[] {42, "日本語"}, null);
                assertEquals("日本語", db.<Map<String, Object>>querySync(
                    "SELECT value FROM data WHERE id = $1", new Object[] {42}, null).rows().getFirst().get("value"));
                db.execSync("BEGIN; INSERT INTO data VALUES (99, 'rollback'); ROLLBACK;", null);
                assertEquals(1.0, db.<Map<String, Object>>querySync(
                    "SELECT count(*)::int AS count FROM data", null, null).rows().getFirst().get("count"));
                db.execSync("SELECT pg_sleep(0.01)", null);
                // Exercise nested longjmp across the dynamically linked PL/pgSQL module.
                db.execSync("CREATE FUNCTION recover() RETURNS integer LANGUAGE plpgsql AS $$ "
                    + "BEGIN PERFORM 1/0; EXCEPTION WHEN division_by_zero THEN RETURN 7; END $$;", null);
                for (var i = 0; i < 10; i++) {
                    assertThrows(RuntimeException.class, () -> db.execSync("SELECT 1/0", null));
                    assertEquals(7.0, db.<Map<String, Object>>querySync(
                        "SELECT recover() AS value", null, null).rows().getFirst().get("value"));
                    assertEquals(42.0, db.<Map<String, Object>>querySync(
                        "SELECT 42::int AS value", null, null).rows().getFirst().get("value"));
                }
            } catch (Throwable error) {
                try { db.close().join(); } catch (Throwable close) { error.addSuppressed(close); }
                throw error;
            }
            db.close().join();
        }
    }
}
