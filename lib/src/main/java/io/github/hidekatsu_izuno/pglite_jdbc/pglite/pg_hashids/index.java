package io.github.hidekatsu_izuno.pglite_jdbc.pglite.pg_hashids;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.extensionCatalog;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;

public class index {
    public static final interface_.Extension EXTENSION = extensionCatalog.create("pg_hashids", "pg_hashids.tar.gz");

    private index() {}
}
