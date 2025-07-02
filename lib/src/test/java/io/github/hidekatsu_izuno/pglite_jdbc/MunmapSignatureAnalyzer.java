package io.github.hidekatsu_izuno.pglite_jdbc;

import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.types.ExternalType;
import com.dylibso.chicory.wasm.types.FunctionImport;
import com.dylibso.chicory.wasm.types.FunctionType;
import java.io.IOException;

public class MunmapSignatureAnalyzer {
    public static void main(String[] args) throws IOException {
        try (var in = MunmapSignatureAnalyzer.class.getResourceAsStream("/postgres.wasm")) {
            var module = Parser.parse(in);
            System.out.println("=== Analyzing _munmap_js Function Signature ===");
            
            for (int i = 0; i < module.importSection().importCount(); i++) {
                var imp = module.importSection().getImport(i);
                
                if (imp.name().equals("_munmap_js")) {
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
                            System.out.println("  Parameters: [I32, I32]");
                            System.out.println("  Returns: [I32]");
                            
                            // Check if they match
                            boolean paramsMatch = expectedType.params().toString().equals("[I32, I32]");
                            boolean returnsMatch = expectedType.returns().toString().equals("[I32]");
                            
                            System.out.println("\nMATCH STATUS:");
                            System.out.println("  Parameters match: " + paramsMatch);
                            System.out.println("  Returns match: " + returnsMatch);
                            System.out.println("  Overall match: " + (paramsMatch && returnsMatch));
                            
                            if (!paramsMatch || !returnsMatch) {
                                System.out.println("\n*** SIGNATURE MISMATCH DETECTED ***");
                                System.out.println("Expected returns: " + expectedType.returns());
                                System.out.println("If returns is '[]', the function should return void!");
                            }
                        }
                    }
                    return;
                }
            }
            
            System.out.println("_munmap_js not found in imports!");
        }
    }
}