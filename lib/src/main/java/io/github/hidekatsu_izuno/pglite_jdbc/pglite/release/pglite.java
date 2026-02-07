package io.github.hidekatsu_izuno.pglite_jdbc.pglite.release;

import com.dylibso.chicory.wasm.Parser;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.postgresMod;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.utils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class pglite {
    /**
     * Placeholder factory for the generated Emscripten module.
     * The Java migration uses this as the entry point that mirrors release/pglite.js.
     */
    public static CompletableFuture<postgresMod.PostgresMod> PostgresModFactory(
        postgresMod.PartialPostgresMod moduleOverrides
    ) {
        var wasm = Parser.parse(utils.readFile("pglite.wasm"));
        var unsupportedImports = new ArrayList<String>();
        var seen = new HashSet<String>();
        for (var i = 0; i < wasm.importSection().importCount(); i++) {
            var importDecl = wasm.importSection().getImport(i);
            var importKey = importDecl.module() + "." + importDecl.name();
            if (seen.contains(importKey)) {
                continue;
            }
            seen.add(importKey);
            if (
                "wasi_snapshot_preview1".equals(importDecl.module()) ||
                "env".equals(importDecl.module()) &&
                importDecl.name().startsWith("invoke_")
            ) {
                continue;
            }
            unsupportedImports.add(importKey);
        }

        var message = new StringBuilder(
            "Postgres module factory is not yet implemented"
        );
        if (!unsupportedImports.isEmpty()) {
            message.append(". Unsupported imports: ");
            for (var i = 0; i < unsupportedImports.size(); i++) {
                if (i > 0) {
                    message.append(", ");
                }
                message.append(unsupportedImports.get(i));
            }
        }

        return CompletableFuture.failedFuture(
            new UnsupportedOperationException(message.toString())
        );
    }

    private pglite() {
    }
}
