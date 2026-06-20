package io.github.hidekatsu_izuno.pglite_jdbc.pglite.contrib;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.extensionCatalog;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;

public class file_fdw {
    public static final interface_.Extension EXTENSION = extensionCatalog.create("file_fdw", "file_fdw.tar.gz");

    private file_fdw() {}
}
