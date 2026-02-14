package io.github.hidekatsu_izuno.pglite_jdbc.pglite.vector;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.extensionCatalog;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;

public class index {
    public static final interface_.Extension EXTENSION = extensionCatalog.create("pgvector", "vector.tar.gz");

    private index() {}
}
