package io.github.hidekatsu_izuno.pglite_jdbc.polyfills;

public record MessageEvent<T>(String type, T data, Worker target) {}
