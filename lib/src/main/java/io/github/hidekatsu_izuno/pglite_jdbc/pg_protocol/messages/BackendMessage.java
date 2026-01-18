package io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;

public interface BackendMessage {
    String getName();
    int getLength();
}
