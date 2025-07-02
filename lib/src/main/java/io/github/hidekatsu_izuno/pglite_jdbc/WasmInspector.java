package io.github.hidekatsu_izuno.pglite_jdbc;

import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Store;

import java.io.IOException;

/**
 * Utility to inspect the postgres.wasm module and list available exports
 */
public class WasmInspector {
    public static void main(String[] args) {
        try {
            // Create a PGliteWasmEngine to initialize WASM
            var engine = new PGliteWasmEngine();
            var instance = engine.getInstance();
            
            System.out.println("=== POSTGRES.WASM EXPORTS ===");
            
            // Test specific function exports by name (safer approach)
            String[] testFunctions = {
                "malloc", "free", "__wasm_call_ctors",
                "PQexec", "PQclear", "PQresultStatus", "PQntuples", "PQnfields", 
                "PQfname", "PQgetvalue", "PQfinish", "PQconnectdb", "PQerrorMessage",
                "pg_exec_query", "pg_get_result", "_main",
                // PGlite specific functions
                "_pgl_initdb", "_pgl_backend", "_interactive_write", "_interactive_one",
                "_pgl_shutdown", "pglite_init", "pglite_exec", "pglite_connect", 
                "postgres_init", "init_db", "init_postgres"
            };
            
            System.out.println("TESTING COMMON FUNCTION EXPORTS:");
            for (String funcName : testFunctions) {
                try {
                    var func = instance.export(funcName);
                    if (func != null) {
                        System.out.println("  ✓ " + funcName + " - available");
                    } else {
                        System.out.println("  ✗ " + funcName + " - not found");
                    }
                } catch (Exception e) {
                    System.out.println("  ✗ " + funcName + " - error: " + e.getMessage());
                }
            }
            
            System.out.println();
            
            // Try to test basic memory allocation
            System.out.println("TESTING BASIC WASM FUNCTIONALITY:");
            try {
                var malloc = instance.export("malloc");
                if (malloc != null) {
                    System.out.println("  ✓ malloc function found");
                    // Try to allocate 10 bytes
                    long[] result = malloc.apply(10);
                    int ptr = (int) result[0];
                    System.out.println("  ✓ malloc(10) returned pointer: " + ptr);
                    
                    var free = instance.export("free");
                    if (free != null) {
                        free.apply(ptr);
                        System.out.println("  ✓ free(ptr) completed");
                    }
                } else {
                    System.out.println("  ✗ malloc function not available");
                }
            } catch (Exception e) {
                System.out.println("  ✗ Error testing malloc: " + e.getMessage());
            }
            
        } catch (Exception e) {
            System.err.println("Error inspecting WASM module: " + e.getMessage());
            e.printStackTrace();
        }
    }
}