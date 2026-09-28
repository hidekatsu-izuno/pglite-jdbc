package io.github.hidekatsu_izuno.pglite_jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.extensionCatalog;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class UpstreamWasmTest {
    @Test
    void bundledFilesMatchTheOfficialPackageManifest() throws Exception {
        var root = extensionCatalog.RELEASE_RESOURCE_ROOT;
        var loader = getClass().getClassLoader();
        try (var input = loader.getResourceAsStream(root + "upstream-manifest.json")) {
            assertNotNull(input);
            var manifest = new ObjectMapper().readTree(input);
            assertEquals("@electric-sql/pglite", manifest.path("package").asText());
            assertEquals("0.5.3", manifest.path("version").asText());
            assertTrue(manifest.path("integrity").asText().startsWith("sha512-"));
            var entries = manifest.path("files").properties();
            assertTrue(entries.size() > 100);
            for (var entry : entries) {
                try (var file = loader.getResourceAsStream(root + entry.getKey())) {
                    assertNotNull(file, entry.getKey());
                    var digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.readAllBytes()));
                    assertEquals(entry.getValue().asText(), digest, entry.getKey());
                }
            }
            assertNotNull(loader.getResource(root + "initdb.wasm"));
            assertNotNull(loader.getResource(root + "hstore.tar.gz/lib/postgresql/hstore.so"));
            assertNull(loader.getResource(root + "hstore.tar.gz/lib/postgresql/hstore.so.wasm"));
        }
    }
}
