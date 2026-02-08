package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.pglite;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeWasiDispatchTest {
    @Test
    void shouldDispatchFdDatasyncToFdSyncPath() throws Exception {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var dispatch = pglite.class.getDeclaredMethod(
            "handleWasiFunction",
            mod.getClass(),
            String.class,
            long[].class
        );
        dispatch.setAccessible(true);

        var ret = (long[]) dispatch.invoke(null, mod, "fd_datasync", new long[] { 9999 });
        // EBADF on wasi path
        assertEquals(8L, ret[0]);
    }

    @Test
    void shouldKeepEnosysForUnknownWasiFunction() throws Exception {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var dispatch = findWasiDispatch(mod.getClass());
        var ret = (long[]) dispatch.invoke(null, mod, "fd_unknown", new long[] {});
        assertEquals(52L, ret[0]);
    }

    private static Method findWasiDispatch(Class<?> runtimeModClass) throws Exception {
        var method = pglite.class.getDeclaredMethod(
            "handleWasiFunction",
            runtimeModClass,
            String.class,
            long[].class
        );
        method.setAccessible(true);
        return method;
    }
}
