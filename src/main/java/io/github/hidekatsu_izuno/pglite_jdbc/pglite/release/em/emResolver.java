package io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.em;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.core.runtimeTypes;
import java.util.Map;
import java.util.regex.Pattern;

public class emResolver {
    private static final Pattern DYNAMIC_EVAL_PATTERN = Pattern.compile("\\beval\\s*\\(|\\bnew\\s+Function\\s*\\(");

    private emResolver() {}

    public static void assertNoDynamicEval(String sourceText) {
        if (sourceText != null && DYNAMIC_EVAL_PATTERN.matcher(sourceText).find()) {
            throw new IllegalArgumentException("Dynamic code evaluation is forbidden in pglite-custom outputs");
        }
    }

    public static void attachEmAsmHandlers(runtimeTypes.RuntimeState state, Map<Integer, runtimeTypes.EmAsmHandler> registry) {
        state.asmHandlers().clear();
        state.asmHandlers().putAll(registry);
    }

    public static void attachEmJsHandlers(runtimeTypes.RuntimeState state, Map<String, runtimeTypes.EmJsHandler> registry) {
        state.emJsHandlers().clear();
        state.emJsHandlers().putAll(registry);
    }
}
