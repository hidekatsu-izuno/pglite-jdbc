package io.github.hidekatsu_izuno.pglite_jdbc;

import org.junit.jupiter.api.Test;
import com.dylibso.chicory.runtime.HostFunction;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Store;
import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.types.ValueType;
import java.util.List;

class SignatureDebugTest {
    @Test
    void debugFcntl64Signature() {
        try {
            var store = new Store();
            
            // Try different signatures for fcntl64
            System.out.println("Testing different fcntl64 signatures...");
            
            // Test 1: (i32, i32, i64) -> i32
            store.addFunction(new HostFunction("env", "__syscall_fcntl64",
                List.of(ValueType.I32, ValueType.I32, ValueType.I64),
                List.of(ValueType.I32),
                (Instance inst, long... args) -> new long[]{-52}
            ));
            
            var imports = store.toImportValues();
            
            try (var in = SignatureDebugTest.class.getResourceAsStream("/postgres.wasm")) {
                var module = Parser.parse(in);
                System.out.println("Attempting to instantiate with (i32, i32, i64) -> i32...");
                var inst = Instance.builder(module)
                    .withImportValues(imports)
                    .build();
                System.out.println("SUCCESS: Module accepted (i32, i32, i64) -> i32 signature");
            } catch (Exception e) {
                System.out.println("FAILED with (i32, i32, i64) -> i32: " + e.getMessage());
                
                // Try alternative signatures
                testAlternativeSignatures();
            }
            
        } catch (Exception e) {
            System.out.println("Error during test setup: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void testAlternativeSignatures() {
        System.out.println("\nTesting alternative signatures...");
        
        // Test different possible signatures
        String[][] signatures = {
            {"(i32, i32, i32) -> i32", "I32,I32,I32", "I32"},
            {"(i32, i64) -> i32", "I32,I64", "I32"},
            {"(i32, i32, i32, i32) -> i32", "I32,I32,I32,I32", "I32"},
            {"(i64, i32, i64) -> i32", "I64,I32,I64", "I32"},
            {"() -> i32", "", "I32"}
        };
        
        for (String[] sig : signatures) {
            try {
                var store = new Store();
                
                List<ValueType> params = parseParams(sig[1]);
                List<ValueType> returns = parseReturns(sig[2]);
                
                store.addFunction(new HostFunction("env", "__syscall_fcntl64",
                    params, returns,
                    (Instance inst, long... args) -> new long[]{-52}
                ));
                
                var imports = store.toImportValues();
                
                try (var in = SignatureDebugTest.class.getResourceAsStream("/postgres.wasm")) {
                    var module = Parser.parse(in);
                    var inst = Instance.builder(module)
                        .withImportValues(imports)
                        .build();
                    System.out.println("SUCCESS: " + sig[0]);
                    return; // Found working signature
                } catch (Exception e) {
                    System.out.println("FAILED " + sig[0] + ": " + e.getMessage());
                }
            } catch (Exception e) {
                System.out.println("ERROR testing " + sig[0] + ": " + e.getMessage());
            }
        }
    }
    
    private List<ValueType> parseParams(String params) {
        if (params.isEmpty()) return List.of();
        
        return List.of(params.split(",")).stream()
            .map(p -> switch(p.trim()) {
                case "I32" -> ValueType.I32;
                case "I64" -> ValueType.I64;
                case "F32" -> ValueType.F32;
                case "F64" -> ValueType.F64;
                default -> throw new IllegalArgumentException("Unknown type: " + p);
            })
            .toList();
    }
    
    private List<ValueType> parseReturns(String returns) {
        if (returns.isEmpty()) return List.of();
        
        return List.of(returns.split(",")).stream()
            .map(r -> switch(r.trim()) {
                case "I32" -> ValueType.I32;
                case "I64" -> ValueType.I64;
                case "F32" -> ValueType.F32;
                case "F64" -> ValueType.F64;
                default -> throw new IllegalArgumentException("Unknown type: " + r);
            })
            .toList();
    }
}