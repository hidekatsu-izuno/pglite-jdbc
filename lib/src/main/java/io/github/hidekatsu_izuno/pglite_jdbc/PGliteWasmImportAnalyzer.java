package io.github.hidekatsu_izuno.pglite_jdbc;

import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.types.Import;
import com.dylibso.chicory.wasm.types.ExternalType;
import com.dylibso.chicory.wasm.types.FunctionImport;
import java.io.IOException;
import java.util.*;

/**
 * Analyzes the imports required by the PGlite WASM module
 */
public class PGliteWasmImportAnalyzer {
    public static void main(String[] args) {
        try (var in = PGliteWasmImportAnalyzer.class.getResourceAsStream("/postgres.wasm")) {
            var module = Parser.parse(in);
            
            System.out.println("=== PGLITE WASM IMPORTS ===\n");
            
            var importSection = module.importSection();
            if (importSection != null && importSection.importCount() > 0) {
                Map<String, List<Import>> importsByModule = new HashMap<>();
                
                // Group imports by module name
                for (int i = 0; i < importSection.importCount(); i++) {
                    var imp = importSection.getImport(i);
                    importsByModule.computeIfAbsent(imp.module(), k -> new ArrayList<>()).add(imp);
                }
                
                // Print imports grouped by module
                for (var entry : importsByModule.entrySet()) {
                    System.out.println("Module: " + entry.getKey());
                    System.out.println("  Functions:");
                    for (var imp : entry.getValue()) {
                        if (imp.importType() == ExternalType.FUNCTION) {
                            System.out.println("    - " + imp.name());
                            
                            // Get function signature if available
                            if (imp instanceof FunctionImport) {
                                FunctionImport funcImp = (FunctionImport) imp;
                                int typeIndex = funcImp.typeIndex();
                                if (typeIndex < module.typeSection().typeCount()) {
                                    var funcType = module.typeSection().getType(typeIndex);
                                    System.out.println("      params: " + funcType.params());
                                    System.out.println("      returns: " + funcType.returns());
                                }
                            }
                        } else {
                            System.out.println("    - " + imp.name() + " (" + imp.importType() + ")");
                        }
                    }
                    System.out.println();
                }
                
                System.out.println("Total imports: " + importSection.importCount());
            } else {
                System.out.println("No import section found.");
            }
            
        } catch (IOException e) {
            System.err.println("Error parsing WASM module: " + e.getMessage());
            e.printStackTrace();
        }
    }
}