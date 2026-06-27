package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

public class extensionUtils {
    private extensionUtils() {}

    public static final int extensionBundleFileType = TarArchiveEntry.LF_NORMAL;
    public static final int extensionBundleDirectoryType = TarArchiveEntry.LF_DIR;

    public record ExtensionBundleEntry(
        String name,
        byte[] data,
        Integer type,
        Integer mode,
        Long size,
        Instant modifyTime
    ) {}

    public static byte[] loadExtensionBundle(String bundlePath) {
        var path = Path.of(bundlePath);
        if (Files.exists(path)) {
            try {
                return maybeUnzip(Files.readAllBytes(path));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        try (InputStream in = extensionUtils.class.getClassLoader().getResourceAsStream(bundlePath)) {
            if (in == null) {
                throw new IllegalArgumentException("Extension bundle not found: " + bundlePath);
            }
            return maybeUnzip(in.readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] loadExtensionBundle(URL bundlePath) {
        try (var in = bundlePath.openStream()) {
            return maybeUnzip(in.readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<ExtensionBundleEntry> unpackExtensionBundle(byte[] bytes) {
        var entries = new ArrayList<ExtensionBundleEntry>();
        try (var tarInput = new TarArchiveInputStream(new ByteArrayInputStream(bytes))) {
            TarArchiveEntry entry;
            while ((entry = tarInput.getNextEntry()) != null) {
                var data = entry.isDirectory() ? new byte[0] : tarInput.readAllBytes();
                entries.add(new ExtensionBundleEntry(
                    entry.getName(),
                    data,
                    (int) entry.getLinkFlag(),
                    entry.getMode(),
                    entry.getSize(),
                    entry.getLastModifiedDate() != null ? entry.getLastModifiedDate().toInstant() : null
                ));
            }
            return entries;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] packExtensionBundle(List<ExtensionBundleEntry> entries) {
        try (var out = new ByteArrayOutputStream();
             var tar = new TarArchiveOutputStream(out)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            for (var entry : entries) {
                var tarEntry = new TarArchiveEntry(entry.name());
                if (entry.mode() != null) {
                    tarEntry.setMode(entry.mode());
                }
                if (entry.modifyTime() != null) {
                    tarEntry.setModTime(entry.modifyTime().toEpochMilli());
                }
                var data = entry.data() != null ? entry.data() : new byte[0];
                tarEntry.setSize(data.length);
                tar.putArchiveEntry(tarEntry);
                if (data.length > 0) {
                    tar.write(data);
                }
                tar.closeArchiveEntry();
            }
            tar.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] maybeUnzip(byte[] bytes) {
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return gzip.readAllBytes();
        } catch (IOException ignored) {
            return bytes;
        }
    }
}
