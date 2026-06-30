package io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.preload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.core.runtimeTypes;
import java.io.InputStream;
import java.util.ArrayList;

public class dataManifest_generated {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private dataManifest_generated() {}

    public static final runtimeTypes.DataManifest dataManifest = loadManifest();

    private static runtimeTypes.DataManifest loadManifest() {
        try (InputStream in = dataManifest_generated.class.getClassLoader().getResourceAsStream("pglite.data.manifest.json")) {
            if (in == null) {
                return runtimeTypes.DataManifest.empty();
            }
            JsonNode root = MAPPER.readTree(in);
            var files = new ArrayList<runtimeTypes.DataFileEntry>();
            for (var node : root.path("files")) {
                files.add(
                    new runtimeTypes.DataFileEntry(
                        node.path("filename").asText(),
                        node.path("start").asInt(),
                        node.path("end").asInt(),
                        node.path("audio").asInt(0)
                    )
                );
            }
            return new runtimeTypes.DataManifest(files, root.path("remote_package_size").asInt(0));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
