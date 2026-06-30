package io.github.hidekatsu_izuno.pglite_jdbc.pglite.contrib;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.extensionCatalog;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;

public class btree_gin {
    public static final interface_.Extension EXTENSION = extensionCatalog.create("btree_gin", "btree_gin.tar.gz");

    private btree_gin() {}
}
