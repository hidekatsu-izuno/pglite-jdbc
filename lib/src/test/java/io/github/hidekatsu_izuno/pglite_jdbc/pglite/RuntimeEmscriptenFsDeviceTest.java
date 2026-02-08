package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.pglite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeEmscriptenFsDeviceTest {
    @Test
    void shouldRejectDuplicateDeviceRegistration() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var fs = mod.runtime().FS();
        fs.registerDevice(1234, new Object());
        assertThrows(RuntimeException.class, () -> fs.registerDevice(1234, new Object()));
    }

    @Test
    void shouldRequireRegisteredDeviceBeforeMkdev() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var fs = mod.runtime().FS();
        assertThrows(RuntimeException.class, () -> fs.mkdev("/dev/unregistered", 1234));
    }

    @Test
    void shouldCreateDeviceNodeAndRejectDuplicatePath() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var fs = mod.runtime().FS();
        fs.registerDevice(5678, new Object());
        fs.mkdev("/dev/test-device", 5678);
        assertTrue(fs.analyzePath("/dev/test-device").exists);
        assertThrows(RuntimeException.class, () -> fs.mkdev("/dev/test-device", 5678));
    }
}
