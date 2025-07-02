package io.github.hidekatsu_izuno.pglite_jdbc;

import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.types.ExternalType;
import com.dylibso.chicory.wasm.types.FunctionImport;
import com.dylibso.chicory.wasm.types.FunctionType;
import java.io.IOException;

public class FunctionSignatureAnalyzer {
    public static void main(String[] args) throws IOException {
        try (var in = FunctionSignatureAnalyzer.class.getResourceAsStream("/postgres.wasm")) {
            var module = Parser.parse(in);
            System.out.println("=== Function Signature Analysis ===");
            
            for (int i = 0; i < module.importSection().importCount(); i++) {
                var imp = module.importSection().getImport(i);
                
                // Look specifically for fcntl64
                if (imp.name().equals("__syscall_fcntl64")) {
                    System.out.println(String.format("\n*** FOUND: %s.%s ***", 
                        imp.module(), imp.name()));
                    
                    if (imp.importType() == ExternalType.FUNCTION && imp instanceof FunctionImport) {
                        FunctionImport funcImp = (FunctionImport) imp;
                        int typeIndex = funcImp.typeIndex();
                        
                        if (typeIndex < module.typeSection().typeCount()) {
                            FunctionType expectedType = module.typeSection().getType(typeIndex);
                            System.out.println("EXPECTED SIGNATURE:");
                            System.out.println("  Parameters: " + expectedType.params());
                            System.out.println("  Returns: " + expectedType.returns());
                            
                            // Show what we're currently providing
                            System.out.println("\nCURRENT PROVIDED SIGNATURE:");
                            System.out.println("  Parameters: [I32, I32, I64]");
                            System.out.println("  Returns: [I32]");
                            
                            // Check if they match
                            boolean paramsMatch = expectedType.params().toString().equals("[I32, I32, I64]");
                            boolean returnsMatch = expectedType.returns().toString().equals("[I32]");
                            
                            System.out.println("\nMATCH STATUS:");
                            System.out.println("  Parameters match: " + paramsMatch);
                            System.out.println("  Returns match: " + returnsMatch);
                            System.out.println("  Overall match: " + (paramsMatch && returnsMatch));
                            
                            if (!paramsMatch || !returnsMatch) {
                                System.out.println("\n*** SIGNATURE MISMATCH DETECTED ***");
                                System.out.println("This explains why the WASM module is rejecting the function!");
                            }
                        }
                    }
                    return; // Found what we're looking for
                }
            }
            
            System.out.println("__syscall_fcntl64 not found in imports!");
        }
    }
}