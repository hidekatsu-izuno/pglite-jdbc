package io.github.hidekatsu_izuno.pglite_jdbc.analyzer;

import org.junit.jupiter.api.Test;
import java.io.IOException;

class AllSyscallAnalyzerTest {
    @Test
    void analyzeAllSyscalls() throws IOException {
        AllSyscallAnalyzer.main(new String[]{});
    }
}