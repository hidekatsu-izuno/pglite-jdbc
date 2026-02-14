package io.github.hidekatsu_izuno.pglite_jdbc.pglite.contrib;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.extensionCatalog;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;

public class pg_buffercache {
    public static final interface_.Extension EXTENSION = extensionCatalog.create("pg_buffercache", "pg_buffercache.tar.gz");

    private pg_buffercache() {}
}
