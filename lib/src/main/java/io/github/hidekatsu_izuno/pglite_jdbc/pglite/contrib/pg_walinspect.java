package io.github.hidekatsu_izuno.pglite_jdbc.pglite.contrib;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.extensionCatalog;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;

public class pg_walinspect {
    public static final interface_.Extension EXTENSION = extensionCatalog.create("pg_walinspect", "pg_walinspect.tar.gz");

    private pg_walinspect() {}
}
