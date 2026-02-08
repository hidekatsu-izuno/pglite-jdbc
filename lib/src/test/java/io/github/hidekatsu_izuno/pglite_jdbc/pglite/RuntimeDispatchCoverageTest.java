package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.pglite;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeDispatchCoverageTest {
    @Test
    void shouldDispatchSocketSyscallsViaEmscriptenSwitch() throws Exception {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var dispatch = findDispatch(mod.getClass());
        var socketRet = (long[]) dispatch.invoke(
            null,
            mod,
            "__syscall_socket",
            new long[] { 2, 1, 0 },
            1
        );
        assertTrue(socketRet[0] >= 3);

        var bindRet = (long[]) dispatch.invoke(
            null,
            mod,
            "__syscall_bind",
            new long[] { socketRet[0], 0, 0 },
            1
        );
        assertEquals(0L, bindRet[0]);
    }

    @Test
    void shouldKeepEnosysForUnknownSyscallName() throws Exception {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var dispatch = findDispatch(mod.getClass());
        var ret = (long[]) dispatch.invoke(
            null,
            mod,
            "__syscall_nonexistent",
            new long[] {},
            1
        );
        assertEquals(-52L, ret[0]);
    }

    private static Method findDispatch(Class<?> runtimeModClass) throws Exception {
        for (var method : pglite.class.getDeclaredMethods()) {
            if (
                "handleEnvFunction".equals(method.getName()) &&
                method.getParameterCount() == 4
            ) {
                var params = method.getParameterTypes();
                if (
                    params[0].isAssignableFrom(runtimeModClass) &&
                    params[1] == String.class &&
                    params[2] == long[].class &&
                    params[3] == int.class
                ) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        throw new NoSuchMethodException("handleEnvFunction");
    }
}
