package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExtensionUtilsTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldLoadPlainTarBundle() throws Exception {
        var tar = createTar("pg_lib/test.txt", "ok".getBytes());
        var bundle = tempDir.resolve("ext.tar");
        Files.write(bundle, tar);

        var blob = extensionUtils.loadExtensionBundle(bundle.toUri().toURL());
        assertArrayEquals(tar, new Uint8Array(blob.arrayBuffer()).toByteArray());
    }

    @Test
    void shouldGunzipBundleBeforeReturningBlob() throws Exception {
        var tar = createTar("pg_lib/test.txt", "ok".getBytes());
        var gzip = gzip(tar);
        var bundle = tempDir.resolve("ext.tar.gz");
        Files.write(bundle, gzip);

        var blob = extensionUtils.loadExtensionBundle(bundle.toUri().toURL());
        assertArrayEquals(tar, new Uint8Array(blob.arrayBuffer()).toByteArray());
    }

    @Test
    void shouldFailForCorruptedGzipBundle() throws Exception {
        var bad = new byte[] { 0x1f, (byte) 0x8b, 0x08, 0x00, 0x01, 0x02, 0x03 };
        var bundle = tempDir.resolve("broken.tar.gz");
        Files.write(bundle, bad);

        assertThrows(RuntimeException.class, () -> extensionUtils.loadExtensionBundle(bundle.toUri().toURL()));
    }

    private static byte[] createTar(String name, byte[] content) throws Exception {
        try (
            var output = new ByteArrayOutputStream();
            var tar = new TarArchiveOutputStream(output)
        ) {
            var entry = new TarArchiveEntry(name);
            entry.setSize(content.length);
            tar.putArchiveEntry(entry);
            tar.write(content);
            tar.closeArchiveEntry();
            tar.finish();
            return output.toByteArray();
        }
    }

    private static byte[] gzip(byte[] bytes) throws Exception {
        try (
            var output = new ByteArrayOutputStream();
            var gzip = new GzipCompressorOutputStream(output)
        ) {
            gzip.write(bytes);
            gzip.finish();
            return output.toByteArray();
        }
    }
}
