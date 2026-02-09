package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.pglite;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeEnvTimerAndLongjmpTest {
    @Test
    void shouldRegisterOverwriteAndClearSetitimer() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();

        var first = invokeLong(
            runtime,
            "setitimerJs",
            new long[] { 7, Double.doubleToRawLongBits(60_000.0) }
        );
        assertEquals(0L, first);
        assertEquals(1, invokeInt(runtime, "activeTimerCount"));
        assertEquals(1, invokeInt(runtime, "keepaliveCount"));

        var overwrite = invokeLong(
            runtime,
            "setitimerJs",
            new long[] { 7, Double.doubleToRawLongBits(30_000.0) }
        );
        assertEquals(0L, overwrite);
        assertEquals(1, invokeInt(runtime, "activeTimerCount"));
        assertEquals(1, invokeInt(runtime, "keepaliveCount"));

        var clear = invokeLong(runtime, "setitimerJs", new long[] { 7, 0L });
        assertEquals(0L, clear);
        assertEquals(0, invokeInt(runtime, "activeTimerCount"));
        assertEquals(0, invokeInt(runtime, "keepaliveCount"));
    }

    @Test
    void shouldClearKeepaliveAndTimersViaRuntimeKeepaliveClear() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        invokeLong(runtime, "setitimerJs", new long[] { 11, Double.doubleToRawLongBits(60_000.0) });
        invokeLong(runtime, "setitimerJs", new long[] { 12, Double.doubleToRawLongBits(60_000.0) });
        assertEquals(2, invokeInt(runtime, "activeTimerCount"));

        invokeVoid(runtime, "emscriptenRuntimeKeepaliveClear", new long[] {});
        assertEquals(0, invokeInt(runtime, "activeTimerCount"));
        assertEquals(0, invokeInt(runtime, "keepaliveCount"));
    }

    @Test
    void shouldThrowDedicatedLongjmpException() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();

        var error = assertThrows(
            RuntimeException.class,
            () -> invokeLong(runtime, "emscriptenThrowLongjmp", new long[] {})
        );
        assertTrue(error.getClass().getSimpleName().contains("RuntimeLongjmpException"));
        assertTrue(error.getMessage().contains("longjmp"));
    }

    private static long invokeLong(Object target, String methodName, long[] args) {
        try {
            var method = target.getClass().getDeclaredMethod(methodName, long[].class);
            method.setAccessible(true);
            return (long) method.invoke(target, (Object) args);
        } catch (InvocationTargetException e) {
            var cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke runtime method: " + methodName, e);
        }
    }

    private static int invokeInt(Object target, String methodName) {
        try {
            var method = target.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            return (int) method.invoke(target);
        } catch (InvocationTargetException e) {
            var cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke runtime method: " + methodName, e);
        }
    }

    private static void invokeVoid(Object target, String methodName, long[] args) {
        try {
            var method = target.getClass().getDeclaredMethod(methodName, long[].class);
            method.setAccessible(true);
            method.invoke(target, (Object) args);
        } catch (InvocationTargetException e) {
            var cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke runtime method: " + methodName, e);
        }
    }
}
