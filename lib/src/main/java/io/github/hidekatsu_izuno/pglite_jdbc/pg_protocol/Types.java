package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol;

public final class Types {
    private Types() {
    }

    public static final class Modes {
        public static final byte text = 0;
        public static final byte binary = 1;

        private Modes() {
        }
    }

    public interface BufferParameter {
    }
}
