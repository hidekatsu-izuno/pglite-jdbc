package io.github.hidekatsu_izuno.pglite_jdbc;

public class DebugWasmErrors {
    public static void main(String[] args) {
        try {
            System.out.println("Attempting to create PGliteWasmEngine...");
            var engine = new PGliteWasmEngine();
            System.out.println("✓ PGliteWasmEngine created successfully");
            
            System.out.println("Attempting to get instance...");
            var instance = engine.getInstance();
            System.out.println("✓ Instance created successfully");
            
        } catch (Exception e) {
            System.err.println("✗ Error creating WASM engine:");
            System.err.println("Exception type: " + e.getClass().getSimpleName());
            System.err.println("Message: " + e.getMessage());
            e.printStackTrace();
        }
    }
}