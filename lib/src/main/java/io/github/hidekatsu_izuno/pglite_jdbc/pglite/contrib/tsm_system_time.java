package io.github.hidekatsu_izuno.pglite_jdbc.pglite.contrib;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.extensionCatalog;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;

public class tsm_system_time {
    public static final interface_.Extension EXTENSION = extensionCatalog.create("tsm_system_time", "tsm_system_time.tar.gz");

    private tsm_system_time() {}
}
