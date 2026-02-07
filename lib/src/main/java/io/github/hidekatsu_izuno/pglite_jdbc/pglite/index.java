package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Extension;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.interface_.LiveNamespace;
import java.util.concurrent.CompletableFuture;

/**
 * Public entry points equivalent to pglite/index.ts exports.
 */
public final class index {
    public static final Extension<LiveNamespace> live =
        io.github.hidekatsu_izuno.pglite_jdbc.pglite.live.index.live;

    public static final Extension<Object> pg_hashids =
        io.github.hidekatsu_izuno.pglite_jdbc.pglite.pg_hashids.index.pg_hashids;
    public static final Extension<Object> pg_ivm =
        io.github.hidekatsu_izuno.pglite_jdbc.pglite.pg_ivm.index.pg_ivm;
    public static final Extension<Object> pg_uuidv7 =
        io.github.hidekatsu_izuno.pglite_jdbc.pglite.pg_uuidv7.index.pg_uuidv7;
    public static final Extension<Object> pgtap =
        io.github.hidekatsu_izuno.pglite_jdbc.pglite.pgtap.index.pgtap;
    public static final Extension<Object> vector =
        io.github.hidekatsu_izuno.pglite_jdbc.pglite.vector.index.vector;

    public static CompletableFuture<pglite> create() {
        return pglite.create(
            new interface_.PGliteOptions<interface_.Extensions>()
        );
    }

    public static CompletableFuture<pglite> create(
        interface_.PGliteOptions<interface_.Extensions> options
    ) {
        return pglite.create(options);
    }

    private index() {
    }
}
