package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.parser;

import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.BackendMessage;

public interface MessageCallback {
    void onMessage(BackendMessage message);
}
