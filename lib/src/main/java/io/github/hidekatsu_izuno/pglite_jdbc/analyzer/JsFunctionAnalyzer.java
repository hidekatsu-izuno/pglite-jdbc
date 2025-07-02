package io.github.hidekatsu_izuno.pglite_jdbc.analyzer;

import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.types.ExternalType;
import com.dylibso.chicory.wasm.types.FunctionImport;
import com.dylibso.chicory.wasm.types.FunctionType;
import java.io.IOException;

public class JsFunctionAnalyzer {
    public static void main(String[] args) throws IOException {
        try (var in = JsFunctionAnalyzer.class.getResourceAsStream("/postgres.wasm")) {
            var module = Parser.parse(in);
            System.out.println("=== JavaScript Function (_js) Signature Analysis ===");
            
            boolean foundMunmap = false;
            
            for (int i = 0; i < module.importSection().importCount(); i++) {
                var imp = module.importSection().getImport(i);
                
                // Look for all _js functions
                if (imp.name().endsWith("_js")) {
                    System.out.println(String.format("\n%s.%s:", imp.module(), imp.name()));
                    
                    if (imp.name().equals("_munmap_js")) {
                        foundMunmap = true;
                        System.out.println("  *** FOUND _munmap_js! ***");
                    }
                    
                    if (imp.importType() == ExternalType.FUNCTION && imp instanceof FunctionImport) {
                        FunctionImport funcImp = (FunctionImport) imp;
                        int typeIndex = funcImp.typeIndex();
                        
                        if (typeIndex < module.typeSection().typeCount()) {
                            FunctionType expectedType = module.typeSection().getType(typeIndex);
                            System.out.println("  Expected: " + expectedType.params() + " -> " + expectedType.returns());
                            
                            // Highlight void returns
                            if (expectedType.returns().toString().equals("[]")) {
                                System.out.println("  NOTE: This function returns VOID!");
                            }
                        }
                    }
                }
            }
            
            if (!foundMunmap) {
                System.out.println("\n_munmap_js NOT FOUND in imports!");
            }
        }
    }
}