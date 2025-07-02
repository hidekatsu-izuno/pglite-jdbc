package io.github.hidekatsu_izuno.pglite_jdbc;

import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.types.ExternalType;
import com.dylibso.chicory.wasm.types.FunctionImport;
import com.dylibso.chicory.wasm.types.FunctionType;
import java.io.IOException;

public class AllSyscallAnalyzer {
    public static void main(String[] args) throws IOException {
        try (var in = AllSyscallAnalyzer.class.getResourceAsStream("/postgres.wasm")) {
            var module = Parser.parse(in);
            System.out.println("=== All Syscall Signature Analysis ===");
            
            for (int i = 0; i < module.importSection().importCount(); i++) {
                var imp = module.importSection().getImport(i);
                
                // Look for all syscalls
                if (imp.name().startsWith("__syscall_")) {
                    System.out.println(String.format("\n%s.%s:", imp.module(), imp.name()));
                    
                    if (imp.importType() == ExternalType.FUNCTION && imp instanceof FunctionImport) {
                        FunctionImport funcImp = (FunctionImport) imp;
                        int typeIndex = funcImp.typeIndex();
                        
                        if (typeIndex < module.typeSection().typeCount()) {
                            FunctionType expectedType = module.typeSection().getType(typeIndex);
                            System.out.println("  Expected: " + expectedType.params() + " -> " + expectedType.returns());
                        }
                    }
                }
            }
        }
    }
}