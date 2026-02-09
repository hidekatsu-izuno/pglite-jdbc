package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import com.dylibso.chicory.runtime.Instance;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.pglite;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeDynamicLinkParityTest {
    @Test
    void shouldCaptureDlopenFailureReason() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();

        var ret = invokeLong(runtime, "dlopenJs", new long[] { 0 });
        assertEquals(0L, ret);
        assertTrue(readDlError(runtime).contains("handle pointer is null"));
    }

    @Test
    void shouldCaptureDlopenEmptyPathReason() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var instance = extractInstance(runtime);
        var handlePtr = 0x7900;
        instance.memory().writeByte(handlePtr + 36, (byte) 0);

        var ret = invokeLong(runtime, "dlopenJs", new long[] { handlePtr });
        assertEquals(0L, ret);
        assertTrue(readDlError(runtime).contains("library path is empty"));
    }

    @Test
    void shouldCaptureDlsymFailureReason() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var instance = extractInstance(runtime);
        var symbolPtr = writeCString(instance, 0x7A00, "__missing_symbol__");

        var ret = invokeLong(runtime, "dlsymJs", new long[] { 0, symbolPtr, 0 });
        assertEquals(0L, ret);
        assertTrue(readDlError(runtime).contains("symbol not found"));
    }

    @Test
    void shouldCaptureDlsymUnknownHandleReason() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var instance = extractInstance(runtime);
        var symbolPtr = writeCString(instance, 0x7E00, "malloc");

        var ret = invokeLong(runtime, "dlsymJs", new long[] { 12345, symbolPtr, 0 });
        assertEquals(0L, ret);
        assertTrue(readDlError(runtime).contains("library handle not found"));
    }

    @Test
    void shouldClearDlErrorAfterSuccessfulDlsym() {
        var mod = pglite.PostgresModFactory(new postgresMod.PartialPostgresMod()).join();
        var runtime = mod.runtime();
        var instance = extractInstance(runtime);
        var missingPtr = writeCString(instance, 0x7B00, "__missing_symbol__");
        invokeLong(runtime, "dlsymJs", new long[] { 0, missingPtr, 0 });
        assertTrue(readDlError(runtime).contains("symbol not found"));

        var symbolPtr = writeCString(instance, 0x7C00, "malloc");
        var symbolIndexPtr = 0x7D00;
        var ret = invokeLong(runtime, "dlsymJs", new long[] { 0, symbolPtr, symbolIndexPtr });
        assertNotEquals(0L, ret);
        assertEquals("", readDlError(runtime));
    }

    private static String readDlError(Object runtime) {
        try {
            var method = runtime.getClass().getDeclaredMethod("lastDlError");
            method.setAccessible(true);
            return (String) method.invoke(runtime);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to read runtime dlerror state", e);
        }
    }

    private static Instance extractInstance(Object runtime) {
        try {
            var modField = runtime.getClass().getDeclaredField("mod");
            modField.setAccessible(true);
            var runtimeMod = modField.get(runtime);
            var instanceField = runtimeMod.getClass().getDeclaredField("instance");
            instanceField.setAccessible(true);
            return (Instance) instanceField.get(runtimeMod);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to extract runtime instance", e);
        }
    }

    private static int writeCString(Instance instance, int ptr, String value) {
        var data = (value + "\0").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        instance.memory().write(ptr, data, 0, data.length);
        return ptr;
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
}
