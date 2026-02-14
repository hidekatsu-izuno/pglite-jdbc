package io.github.hidekatsu_izuno.pglite_jdbc.pglite.pg_ivm;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.extensionCatalog;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;

public class index {
    public static final interface_.Extension EXTENSION = extensionCatalog.create("pg_ivm", "pg_ivm.tar.gz");

    private index() {}
}
