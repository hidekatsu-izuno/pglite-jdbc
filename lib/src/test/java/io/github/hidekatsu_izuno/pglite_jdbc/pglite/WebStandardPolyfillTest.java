package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.BroadcastChannel;
import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Navigator;
import org.junit.jupiter.api.Test;

public class WebStandardPolyfillTest {
    @Test
    void shouldRejectBrowserPolyfillsInJvmOnlyMode() {
        assertThrows(UnsupportedOperationException.class, () -> new BroadcastChannel("chan"));
        assertThrows(UnsupportedOperationException.class, Navigator::locks);
    }
}
