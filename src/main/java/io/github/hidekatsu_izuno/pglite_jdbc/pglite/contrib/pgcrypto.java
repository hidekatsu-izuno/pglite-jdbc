package io.github.hidekatsu_izuno.pglite_jdbc.pglite.contrib;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.extensionCatalog;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;

public class pgcrypto {
    public static final interface_.Extension EXTENSION = extensionCatalog.create("pgcrypto", "pgcrypto.tar.gz");

    private pgcrypto() {}
}
