package io.github.hidekatsu_izuno.pglite_jdbc;

import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.types.ExternalType;
import com.dylibso.chicory.wasm.types.FunctionImport;
import com.dylibso.chicory.wasm.types.FunctionType;
import java.io.IOException;

public class DlopenSignatureAnalyzer {
    public static void main(String[] args) throws IOException {
        try (var in = DlopenSignatureAnalyzer.class.getResourceAsStream("/postgres.wasm")) {
            var module = Parser.parse(in);
            System.out.println("=== Function Signature Analysis ===");
            
            for (int i = 0; i < module.importSection().importCount(); i++) {
                var imp = module.importSection().getImport(i);
                
                // Look specifically for problematic functions
                if (imp.name().equals("_dlopen_js") || imp.name().equals("_dlsym_js") || imp.name().equals("_tzset_js")) {
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
                            if (imp.name().equals("_dlopen_js")) {
                                System.out.println("  Parameters: [I32]");
                                System.out.println("  Returns: [I32]");
                            } else if (imp.name().equals("_dlsym_js")) {
                                System.out.println("  Parameters: [I32, I32, I32]");
                                System.out.println("  Returns: [I32]");
                            } else if (imp.name().equals("_tzset_js")) {
                                System.out.println("  Parameters: [I32, I32, I32, I32, I32, I32, I32, I32, I32]");
                                System.out.println("  Returns: []");
                            }
                            
                            // Check if they match
                            String expectedParams = expectedType.params().toString();
                            String expectedReturns = expectedType.returns().toString();
                            String currentParams;
                            String currentReturns;
                            if (imp.name().equals("_dlopen_js")) {
                                currentParams = "[I32]";
                                currentReturns = "[I32]";
                            } else if (imp.name().equals("_dlsym_js")) {
                                currentParams = "[I32, I32, I32]";
                                currentReturns = "[I32]";
                            } else if (imp.name().equals("_tzset_js")) {
                                currentParams = "[I32, I32, I32, I32, I32, I32, I32, I32, I32]";
                                currentReturns = "[]";
                            } else {
                                currentParams = "[UNKNOWN]";
                                currentReturns = "[UNKNOWN]";
                            }
                            
                            System.out.println("\nCOMPARISON:");
                            System.out.println("  Expected params: " + expectedParams);
                            System.out.println("  Current params:  " + currentParams);
                            System.out.println("  Expected returns: " + expectedReturns);
                            System.out.println("  Current returns:  " + currentReturns);
                            
                            boolean paramsMatch = expectedParams.equals(currentParams);
                            boolean returnsMatch = expectedReturns.equals(currentReturns);
                            
                            System.out.println("\nMATCH STATUS:");
                            System.out.println("  Parameters match: " + paramsMatch);
                            System.out.println("  Returns match: " + returnsMatch);
                            System.out.println("  Overall match: " + (paramsMatch && returnsMatch));
                            
                            if (!paramsMatch || !returnsMatch) {
                                System.out.println("\n*** SIGNATURE MISMATCH DETECTED ***");
                                System.out.println("Correct signature should be:");
                                System.out.println("  Parameters: " + expectedParams);
                                System.out.println("  Returns: " + expectedReturns);
                            }
                        }
                    }
                }
            }
            
            System.out.println("Functions not found in imports!");
        }
    }
}