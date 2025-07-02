package io.github.hidekatsu_izuno.pglite_jdbc.analyzer;

import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.types.Import;
import com.dylibso.chicory.wasm.types.FunctionType;
import com.dylibso.chicory.wasm.types.ExternalType;
import com.dylibso.chicory.wasm.types.FunctionImport;
import java.io.IOException;

public class DetailedWasmAnalyzer {
    public static void main(String[] args) throws IOException {
        try (var in = DetailedWasmAnalyzer.class.getResourceAsStream("/postgres.wasm")) {
            var module = Parser.parse(in);
            System.out.println("=== WASM Module Detailed Import Analysis ===");
            
            for (int i = 0; i < module.importSection().importCount(); i++) {
                var imp = module.importSection().getImport(i);
                
                // Focus on function imports and particularly fcntl64
                if (imp.name().contains("fcntl") || imp.name().contains("__syscall_fcntl64")) {
                    System.out.println(String.format("\n*** Found fcntl function: %s.%s ***", 
                        imp.module(), imp.name()));
                    System.out.println("Import type: " + imp.importType());
                    
                    // Get the function type details
                    if (imp.importType() == ExternalType.FUNCTION && imp instanceof FunctionImport) {
                        FunctionImport funcImp = (FunctionImport) imp;
                        int typeIndex = funcImp.typeIndex();
                        if (typeIndex < module.typeSection().typeCount()) {
                            FunctionType funcType = module.typeSection().getType(typeIndex);
                            System.out.println("Function signature:");
                            System.out.println("  Parameters: " + funcType.params());
                            System.out.println("  Returns: " + funcType.returns());
                        }
                    }
                }
                
                // Also show all syscall functions
                if (imp.name().startsWith("__syscall_")) {
                    System.out.println(String.format("\nSyscall: %s.%s", 
                        imp.module(), imp.name()));
                    
                    if (imp.importType() == ExternalType.FUNCTION && imp instanceof FunctionImport) {
                        FunctionImport funcImp = (FunctionImport) imp;
                        int typeIndex = funcImp.typeIndex();
                        if (typeIndex < module.typeSection().typeCount()) {
                            FunctionType funcType = module.typeSection().getType(typeIndex);
                            System.out.println("  Params: " + funcType.params() + " -> Returns: " + funcType.returns());
                        }
                    }
                }
            }
        }
    }
}