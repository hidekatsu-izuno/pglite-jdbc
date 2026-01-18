package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol;

import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.BackendMessage;

public final class Messages {
    public static final BackendMessage parseComplete = new BackendMessage() {
        public String getName() {
            return "parseComplete";
        }

        @Override
        public int getLength() {
            return 5;
        }
    };

    private Messages() {
    }
}
