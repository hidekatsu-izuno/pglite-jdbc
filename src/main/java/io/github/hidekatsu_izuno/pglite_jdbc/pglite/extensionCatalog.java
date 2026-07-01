package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.io.IOException;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Promise;

public class extensionCatalog {
    public static final String RELEASE_RESOURCE_ROOT =
        "io/github/hidekatsu_izuno/pglite_jdbc/pglite/release/";
    public static final String EXTENSIONS_PROPERTIES =
        "io/github/hidekatsu_izuno/pglite_jdbc/pglite/extensions.properties";
    private static final Map<String, ExtensionDescriptor> EXTENSIONS = loadExtensions();

    private extensionCatalog() {}

    public static interface_.Extension create(String extensionName, String bundleFilename) {
        return create(extensionName, bundleFilename, List.of());
    }

    public static interface_.Extension create(
        String extensionName,
        String bundleFilename,
        List<String> sharedPreloadLibraries
    ) {
        return new interface_.Extension() {
            @Override
            public String name() {
                return extensionName;
            }

            @Override
            public interface_.ExtensionSetup setup() {
                return (pg, emscriptenOpts, clientOnly) -> Promise.resolve(
                    new interface_.ExtensionSetupResult(
                        emscriptenOpts,
                        Map.of(),
                        resolveBundleUrl(bundleFilename),
                        sharedPreloadLibraries == null || sharedPreloadLibraries.isEmpty()
                            ? null
                            : List.copyOf(sharedPreloadLibraries),
                        null,
                        null
                    )
                );
            }
        };
    }

    public static interface_.Extension get(String extensionName) {
        var descriptor = EXTENSIONS.get(extensionName);
        if (descriptor == null) {
            throw new IllegalArgumentException("Unknown PGlite extension: " + extensionName);
        }
        return create(descriptor.name(), descriptor.bundle(), descriptor.sharedPreloadLibraries());
    }

    public static Map<String, interface_.Extension> getAll() {
        var extensions = new LinkedHashMap<String, interface_.Extension>();
        for (var descriptor : EXTENSIONS.values()) {
            extensions.put(
                descriptor.name(),
                create(descriptor.name(), descriptor.bundle(), descriptor.sharedPreloadLibraries())
            );
        }
        return Collections.unmodifiableMap(extensions);
    }

    public static Map<String, ExtensionDescriptor> descriptors() {
        return EXTENSIONS;
    }

    private static URL resolveBundleUrl(String bundleFilename) {
        var normalized = bundleFilename.startsWith("/")
            ? bundleFilename.substring(1)
            : bundleFilename;
        var resource = extensionCatalog.class.getClassLoader().getResource(normalized);
        if (resource == null && !normalized.startsWith(RELEASE_RESOURCE_ROOT)) {
            resource = extensionCatalog.class.getClassLoader().getResource(
                RELEASE_RESOURCE_ROOT + normalized
            );
        }
        if (resource != null) {
            return resource;
        }
        try {
            return new URI("file://" + bundleFilename).toURL();
        } catch (URISyntaxException | MalformedURLException e) {
            throw new IllegalArgumentException("Invalid extension bundle path: " + bundleFilename, e);
        }
    }

    private static Map<String, ExtensionDescriptor> loadExtensions() {
        var properties = new Properties();
        try (var in = extensionCatalog.class.getClassLoader().getResourceAsStream(EXTENSIONS_PROPERTIES)) {
            if (in == null) {
                return Map.of();
            }
            properties.load(in);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }

        var descriptors = new LinkedHashMap<String, ExtensionDescriptor>();
        for (var name : properties.stringPropertyNames()) {
            if (!name.endsWith(".bundle")) {
                continue;
            }
            var extensionName = name.substring(0, name.length() - ".bundle".length());
            var bundle = properties.getProperty(name).trim();
            var sharedPreloadLibraries = new ArrayList<String>();
            var preload = properties.getProperty(extensionName + ".sharedPreloadLibraries", "");
            for (var item : preload.split(",")) {
                var trimmed = item.trim();
                if (!trimmed.isEmpty()) {
                    sharedPreloadLibraries.add(trimmed);
                }
            }
            descriptors.put(
                extensionName,
                new ExtensionDescriptor(extensionName, bundle, List.copyOf(sharedPreloadLibraries))
            );
        }
        return Collections.unmodifiableMap(descriptors);
    }

    public record ExtensionDescriptor(
        String name,
        String bundle,
        List<String> sharedPreloadLibraries
    ) {}
}
