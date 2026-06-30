package io.github.hidekatsu_izuno.pglite_jdbc.pglite.contrib;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.extensionCatalog;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;

public class auto_explain {
    public static final interface_.Extension EXTENSION = extensionCatalog.create("auto_explain", "auto_explain.tar.gz");

    private auto_explain() {}
}
