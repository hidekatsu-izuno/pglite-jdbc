package io.github.hidekatsu_izuno.pglite_jdbc.pglite.contrib;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.extensionCatalog;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;

public class pg_trgm {
    public static final interface_.Extension EXTENSION = extensionCatalog.create("pg_trgm", "pg_trgm.tar.gz");

    private pg_trgm() {}
}
