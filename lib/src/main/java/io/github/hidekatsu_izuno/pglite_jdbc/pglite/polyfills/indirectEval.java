package io.github.hidekatsu_izuno.pglite_jdbc.pglite.polyfills;

public class indirectEval {
    private indirectEval() {}

    public static Object eval(String source) {
        throw new UnsupportedOperationException("Dynamic eval is not supported");
    }
}
