package io.github.hidekatsu_izuno.pglite_jdbc.pglite.contrib;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.extensionCatalog;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;

public class btree_gist {
    public static final interface_.Extension EXTENSION = extensionCatalog.create("btree_gist", "btree_gist.tar.gz");

    private btree_gist() {}
}
