package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.pglite;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeFsHelperParityTest {
    @Test
    void shouldCreateLazyFileFromClasspathResourceAndUnlinkIt() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var fs = mod.runtime().FS();
        fs.mkdirTree("/tmp/lazy");
        fs.createLazyFile("/tmp/lazy", "pgcrypto.tar.gz", "pgcrypto.tar.gz", true, true);
        assertTrue(fs.analyzePath("/tmp/lazy/pgcrypto.tar.gz").exists);
        assertTrue(fs.readFile("/tmp/lazy/pgcrypto.tar.gz").length > 0);

        fs.unlink("/tmp/lazy/pgcrypto.tar.gz");
        assertFalse(fs.analyzePath("/tmp/lazy/pgcrypto.tar.gz").exists);
    }

    @Test
    void shouldCreateCharacterDeviceFromCallbacks() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var fs = mod.runtime().FS();
        var nextChar = new AtomicInteger('A');
        fs.createDevice(
            "/dev",
            "lazy-char",
            (java.util.function.IntSupplier) () -> {
                var current = nextChar.getAndIncrement();
                return current <= 'C' ? current : -1;
            },
            (java.util.function.IntConsumer) ignored -> {
            }
        );
        assertTrue(fs.analyzePath("/dev/lazy-char").exists);
    }
}
