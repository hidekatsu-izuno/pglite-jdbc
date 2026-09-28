package io.github.hidekatsu_izuno.pglite_jdbc.wasmer;

import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Extracts and loads the platform-specific Wasmer shared library via JNA.
 */
public final class WasmerNativeLoader {
    private static final String RESOURCE_ROOT =
        "io/github/hidekatsu_izuno/pglite_jdbc/wasmer/native/";
    private static final AtomicReference<WasmerLibrary> LIBRARY = new AtomicReference<>();
    private static final AtomicReference<Throwable> LOAD_ERROR = new AtomicReference<>();

    private WasmerNativeLoader() {
    }

    public static boolean isAvailable() {
        return get() != null;
    }

    public static WasmerLibrary get() {
        var loaded = LIBRARY.get();
        if (loaded != null) {
            return loaded;
        }
        synchronized (WasmerNativeLoader.class) {
            loaded = LIBRARY.get();
            if (loaded != null) {
                return loaded;
            }
            if (LOAD_ERROR.get() != null) {
                return null;
            }
            try {
                loaded = loadLibrary();
                LIBRARY.set(loaded);
                return loaded;
            } catch (IOException | UnsatisfiedLinkError | SecurityException | UnsupportedOperationException e) {
                LOAD_ERROR.set(e);
                return null;
            }
        }
    }

    public static Throwable loadError() {
        return LOAD_ERROR.get();
    }

    public static String platformKey() {
        var os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        var arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        var osKey = osKey(os);
        var archKey = archKey(arch);
        if (osKey == null || archKey == null) {
            return null;
        }
        return osKey + "-" + archKey;
    }

    private static WasmerLibrary loadLibrary() throws IOException {
        var configured = System.getProperty("pglite.wasmer.library");
        if (configured != null && !configured.isBlank()) {
            return loadAndValidate(Path.of(configured).toAbsolutePath().toString());
        }
        var platform = platformKey();
        if (platform == null) {
            throw new UnsupportedOperationException(
                "Unsupported platform: os=" + System.getProperty("os.name")
                    + " arch=" + System.getProperty("os.arch")
            );
        }
        var libraryName = libraryFileName(platform);
        var resourcePath = RESOURCE_ROOT + platform + "/" + libraryName;
        var resource = WasmerNativeLoader.class.getClassLoader().getResource(resourcePath);
        if (resource == null) {
            try {
                return loadAndValidate("wasmer-headless");
            } catch (UnsatisfiedLinkError missingHeadless) {
                try {
                    return loadAndValidate("wasmer");
                } catch (UnsatisfiedLinkError missingCompiler) {
                    missingCompiler.addSuppressed(missingHeadless);
                    throw missingCompiler;
                }
            }
        }
        var tempDir = Files.createTempDirectory("pglite-wasmer-");
        tempDir.toFile().deleteOnExit();
        var target = tempDir.resolve(libraryName);
        try (var in = resource.openStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        target.toFile().deleteOnExit();
        makeExecutable(target);
        return loadAndValidate(target.toAbsolutePath().toString());
    }

    private static WasmerLibrary loadAndValidate(String path) {
        // JNA resolves symbols lazily. Validate the complete API now so an
        // incompatible library is treated as unavailable before creating a DB.
        var nativeLibrary = NativeLibrary.getInstance(path);
        try {
            for (var method : WasmerLibrary.class.getDeclaredMethods()) {
                nativeLibrary.getFunction(method.getName());
            }
            return Native.load(path, WasmerLibrary.class);
        } catch (UnsatisfiedLinkError e) {
            nativeLibrary.close();
            throw e;
        }
    }

    private static void makeExecutable(Path target) {
        try {
            target.toFile().setExecutable(true);
        } catch (SecurityException ignored) {
            // Best effort.
        }
    }

    private static String osKey(String os) {
        if (os.contains("linux")) {
            return "linux";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "darwin";
        }
        if (os.contains("win")) {
            return "windows";
        }
        return null;
    }

    private static String archKey(String arch) {
        var os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        var darwin = os.contains("mac") || os.contains("darwin");
        return switch (arch) {
            case "amd64", "x86_64", "x64" -> "amd64";
            case "aarch64", "arm64" -> darwin ? "arm64" : "aarch64";
            default -> {
                if (arch.contains("aarch") || arch.contains("arm64")) {
                    yield darwin ? "arm64" : "aarch64";
                }
                yield null;
            }
        };
    }

    private static String libraryFileName(String platform) {
        if (platform.startsWith("windows-")) {
            return "wasmer-headless.dll";
        }
        if (platform.startsWith("darwin-")) {
            return "libwasmer-headless.dylib";
        }
        return "libwasmer-headless.so";
    }
}
