package io.github.hidekatsu_izuno.pglite_jdbc.pglite.contrib;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.extensionCatalog;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;

public class pg_visibility {
    public static final interface_.Extension EXTENSION = extensionCatalog.create("pg_visibility", "pg_visibility.tar.gz");

    private pg_visibility() {}
}
