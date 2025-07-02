package io.github.hidekatsu_izuno.pglite_jdbc;

import org.junit.jupiter.api.Test;

class ErrorCaptureTest {
    @Test
    void captureWasmError() {
        try {
            var engine = new PGliteWasmEngine();
            var instance = engine.getInstance();
            System.out.println("WASM instance created successfully!");
        } catch (Exception e) {
            System.err.println("WASM Error: " + e.getMessage());
            System.err.println("Full error:");
            e.printStackTrace();
            
            // Check for specific signature mismatch errors
            if (e.getMessage() != null && e.getMessage().contains("signature")) {
                System.err.println("\n*** SIGNATURE MISMATCH DETECTED ***");
                System.err.println("Error message contains 'signature': " + e.getMessage());
            }
            
            // Also check the cause
            Throwable cause = e.getCause();
            while (cause != null) {
                System.err.println("Caused by: " + cause.getMessage());
                if (cause.getMessage() != null && cause.getMessage().contains("fcntl")) {
                    System.err.println("*** FCNTL RELATED ERROR ***");
                }
                cause = cause.getCause();
            }
        }
    }
}