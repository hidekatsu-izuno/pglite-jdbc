package io.github.hidekatsu_izuno.pglite_jdbc.polyfills;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public final class JSON {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static Object parse(String text) {
        try {
            var node = MAPPER.readTree(text);
            return toJavaObject(node);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String stringify(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String stringify(double value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static Object toJavaObject(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            var map = new LinkedHashMap<String, Object>(node.size());
            for (var entry : node.properties()) {
                map.put(entry.getKey(), toJavaObject(entry.getValue()));
            }
            return map;
        }
        if (node.isArray()) {
            var list = new ArrayList<Object>(node.size());
            for (var element : node) {
                list.add(toJavaObject(element));
            }
            return list;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        return node.asText();
    }
}
