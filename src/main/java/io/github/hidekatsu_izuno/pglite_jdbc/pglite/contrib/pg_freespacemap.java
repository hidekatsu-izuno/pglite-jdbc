package io.github.hidekatsu_izuno.pglite_jdbc.pglite.contrib;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.extensionCatalog;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;

public class pg_freespacemap {
    public static final interface_.Extension EXTENSION = extensionCatalog.create("pg_freespacemap", "pg_freespacemap.tar.gz");

    private pg_freespacemap() {}
}
