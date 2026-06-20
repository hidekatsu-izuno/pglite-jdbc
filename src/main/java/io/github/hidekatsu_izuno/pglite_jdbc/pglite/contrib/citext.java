package io.github.hidekatsu_izuno.pglite_jdbc.pglite.contrib;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.extensionCatalog;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;

public class citext {
    public static final interface_.Extension EXTENSION = extensionCatalog.create("citext", "citext.tar.gz");

    private citext() {}
}
