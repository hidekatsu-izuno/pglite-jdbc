package io.github.hidekatsu_izuno.pglite_jdbc.pglite.release;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.postgresMod.PostgresMod;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.extensionCatalog;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.postgresMod.PartialPostgresMod;
import java.net.URL;

public final class pglite {
    private static final URL PGLITE_WASM_URL = resolvePgliteWasmUrl();

    public static class PostgresModFactory {
        public static PostgresMod create(PartialPostgresMod moduleOverrides) {
            return new EndivePostgresMod(moduleOverrides, PGLITE_WASM_URL);
        }
    }

    private static URL resolvePgliteWasmUrl() {
        var url = pglite.class.getClassLoader().getResource(
            extensionCatalog.RELEASE_RESOURCE_ROOT + "pglite.wasm"
        );
        if (url == null) {
            url = pglite.class.getResource("/pglite.wasm");
        }
        if (url == null) {
            url = pglite.class.getResource("pglite.wasm");
        }
        return url;
    }

    private pglite() {
    }
}
