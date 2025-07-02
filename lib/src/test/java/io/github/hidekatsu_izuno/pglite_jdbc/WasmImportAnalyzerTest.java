package io.github.hidekatsu_izuno.pglite_jdbc;

import com.dylibso.chicory.wasm.Parser;
import org.junit.jupiter.api.Test;
import java.io.IOException;

class WasmImportAnalyzerTest {
    @Test
    void analyzeWasmImports() throws IOException {
        try (var in = WasmImportAnalyzerTest.class.getResourceAsStream("/postgres.wasm")) {
            var module = Parser.parse(in);
            System.out.println("=== WASM Module Imports ===");
            for (int i = 0; i < module.importSection().importCount(); i++) {
                var imp = module.importSection().getImport(i);
                System.out.println(String.format("Import #%d: %s.%s (type: %s)", 
                    i, imp.module(), imp.name(), imp.importType()));
            }
            
            System.out.println("\n=== WASM Module Exports ===");
            for (int i = 0; i < module.exportSection().exportCount(); i++) {
                var exp = module.exportSection().getExport(i);
                System.out.println(String.format("Export #%d: %s (type: %s)", 
                    i, exp.name(), exp.exportType()));
            }
        }
    }
}