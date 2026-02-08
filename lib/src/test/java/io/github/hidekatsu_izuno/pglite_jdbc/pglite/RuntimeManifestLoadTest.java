package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.pglite;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeManifestLoadTest {
    @Test
    void shouldProvideManifestResourceOnClasspath() {
        var resource = pglite.class.getClassLoader().getResource("pglite.data.manifest.json");
        assertNotNull(resource);
    }

    @Test
    void shouldLoadManifestFromClasspathAndKeepRangesWithinDataPackage() throws Exception {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var method = runtime.getClass().getDeclaredMethod("loadPgliteDataManifest");
        method.setAccessible(true);
        var entries = (List<?>) method.invoke(runtime);
        assertFalse(entries.isEmpty());

        var packageSize = runtime.getPreloadedPackage("pglite.data", 0).length;
        var previousEnd = 0;
        for (var entry : entries) {
            var filename = (String) readField(entry, "filename");
            var start = (int) readField(entry, "start");
            var end = (int) readField(entry, "end");
            assertNotNull(filename);
            assertTrue(start >= 0, "start < 0 for " + filename);
            assertTrue(end >= start, "end < start for " + filename);
            assertTrue(end <= packageSize, "end > package size for " + filename);
            assertTrue(start >= previousEnd, "overlap detected at " + filename);
            previousEnd = end;
        }
    }

    private static Object readField(Object target, String name) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to read field: " + name, e);
        }
    }
}
