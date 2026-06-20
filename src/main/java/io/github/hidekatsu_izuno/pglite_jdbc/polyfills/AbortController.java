package io.github.hidekatsu_izuno.pglite_jdbc.polyfills;

public class AbortController {
    private final AbortSignal signal = new AbortSignal();

    public AbortSignal signal() {
        return signal;
    }

    public void abort() {
        signal.abort();
    }
}
