package io.github.hidekatsu_izuno.pglite_jdbc;

/**
 * Debug utility to test WASM function calls directly
 */
public class WasmDebugger {
    public static void main(String[] args) {
        try {
            var engine = new PGliteWasmEngine();
            var instance = engine.getInstance();
            
            System.out.println("=== TESTING WASM FUNCTION CALLS ===");
            
            // Test PQexec with null connection
            var pqExecFunc = instance.export("PQexec");
            if (pqExecFunc != null) {
                System.out.println("✓ PQexec function found");
                
                // Write a simple SQL query to memory
                int sqlPtr = engine.writeString("SELECT 1");
                System.out.println("✓ SQL written to memory at ptr: " + sqlPtr);
                
                try {
                    // Get the connection pointer from the engine
                    int connPtr = engine.getConnectionPtr();
                    System.out.println("Calling PQexec(" + connPtr + ", " + sqlPtr + ")...");
                    long[] result = pqExecFunc.apply(connPtr, sqlPtr);
                    int resultPtr = (int) result[0];
                    System.out.println("✓ PQexec returned result ptr: " + resultPtr);
                    
                    if (resultPtr != 0) {
                        // Test PQresultStatus
                        var pqResultStatusFunc = instance.export("PQresultStatus");
                        if (pqResultStatusFunc != null) {
                            int status = (int) pqResultStatusFunc.apply(resultPtr)[0];
                            System.out.println("✓ Result status: " + status + " (" + getStatusName(status) + ")");
                            
                            // If there's an error, try to get the error message
                            if (status > 2) {
                                var pqErrorMessageFunc = instance.export("PQerrorMessage");
                                if (pqErrorMessageFunc != null) {
                                    int errorPtr = (int) pqErrorMessageFunc.apply(connPtr)[0];
                                    if (errorPtr != 0) {
                                        String errorMsg = engine.readString(errorPtr);
                                        System.out.println("✗ Error message: " + errorMsg);
                                    }
                                }
                            }
                        }
                        
                        // Test PQnfields and PQntuples
                        var pqNfieldsFunc = instance.export("PQnfields");
                        var pqNtuplesFunc = instance.export("PQntuples");
                        
                        if (pqNfieldsFunc != null && pqNtuplesFunc != null) {
                            int fields = (int) pqNfieldsFunc.apply(resultPtr)[0];
                            int tuples = (int) pqNtuplesFunc.apply(resultPtr)[0];
                            System.out.println("✓ Fields: " + fields + ", Tuples: " + tuples);
                            
                            // Try to get a value
                            if (fields > 0 && tuples > 0) {
                                var pqGetvalueFunc = instance.export("PQgetvalue");
                                if (pqGetvalueFunc != null) {
                                    int valuePtr = (int) pqGetvalueFunc.apply(resultPtr, 0, 0)[0];
                                    if (valuePtr != 0) {
                                        String value = engine.readString(valuePtr);
                                        System.out.println("✓ Value[0,0]: " + value);
                                    }
                                }
                            }
                        }
                        
                        // Clean up
                        var pqClearFunc = instance.export("PQclear");
                        if (pqClearFunc != null) {
                            pqClearFunc.apply(resultPtr);
                            System.out.println("✓ Result cleared");
                        }
                    } else {
                        System.out.println("✗ PQexec returned null result");
                    }
                    
                } catch (Exception e) {
                    System.out.println("✗ Error calling PQexec: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    engine.freeString(sqlPtr);
                }
            } else {
                System.out.println("✗ PQexec function not found");
            }
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static String getStatusName(int status) {
        switch (status) {
            case 0: return "EMPTY_QUERY";
            case 1: return "COMMAND_OK";
            case 2: return "TUPLES_OK";
            case 3: return "COPY_OUT";
            case 4: return "COPY_IN";
            case 5: return "BAD_RESPONSE";
            case 6: return "NONFATAL_ERROR";
            case 7: return "FATAL_ERROR";
            default: return "UNKNOWN(" + status + ")";
        }
    }
}