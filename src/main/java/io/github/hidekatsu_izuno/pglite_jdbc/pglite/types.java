package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class types {
    public static final int BOOL = 16;
    public static final int BYTEA = 17;
    public static final int INT8 = 20;
    public static final int INT2 = 21;
    public static final int INT4 = 23;
    public static final int TEXT = 25;
    public static final int OID = 26;
    public static final int JSON = 114;
    public static final int FLOAT4 = 700;
    public static final int FLOAT8 = 701;
    public static final int VARCHAR = 1043;
    public static final int DATE = 1082;
    public static final int TIMESTAMP = 1114;
    public static final int TIMESTAMPTZ = 1184;
    public static final int JSONB = 3802;

    public interface Parser {
        Object parse(String value, Integer typeId);
    }

    public interface Serializer {
        String serialize(Object value);
    }

    public static final Map<Integer, Parser> parsers = new HashMap<>();
    public static final Map<Integer, Serializer> serializers = new HashMap<>();

    static {
        serializers.put(0, v -> String.valueOf(v));
        registerString(TEXT);
        registerString(VARCHAR);
        registerNumber(INT2);
        registerNumber(INT4);
        registerNumber(OID);
        registerNumber(FLOAT4);
        registerNumber(FLOAT8);
        registerBigInt(INT8);
        registerBoolean(BOOL);
        registerDate(DATE);
        registerDate(TIMESTAMP);
        registerDate(TIMESTAMPTZ);
        registerJson(JSON);
        registerJson(JSONB);
        registerBytea(BYTEA);
    }

    private types() {}

    private static void registerString(int oid) {
        parsers.put(oid, (x, t) -> x);
        serializers.put(oid, v -> String.valueOf(v));
    }

    private static void registerNumber(int oid) {
        parsers.put(oid, (x, t) -> Double.parseDouble(x));
        serializers.put(oid, v -> String.valueOf(v));
    }

    private static void registerBigInt(int oid) {
        parsers.put(oid, (x, t) -> {
            var value = Long.parseLong(x);
            return value;
        });
        serializers.put(oid, v -> String.valueOf(v));
    }

    private static void registerBoolean(int oid) {
        parsers.put(oid, (x, t) -> "t".equals(x));
        serializers.put(oid, v -> {
            if (v instanceof Boolean bool) {
                return bool ? "t" : "f";
            }
            if (v instanceof Number number) {
                var value = number.doubleValue();
                if (value == 1.0d) {
                    return "t";
                }
                if (value == 0.0d) {
                    return "f";
                }
                throw new IllegalArgumentException("Invalid input for boolean type");
            }
            if (v instanceof String string) {
                var normalized = string.trim().toLowerCase();
                if (List.of("true", "t", "yes", "y", "on", "1").contains(normalized)) {
                    return "t";
                }
                if (List.of("false", "f", "no", "n", "off", "0").contains(normalized)) {
                    return "f";
                }
            }
            throw new IllegalArgumentException("Invalid input for boolean type");
        });
    }

    private static void registerDate(int oid) {
        parsers.put(oid, (x, t) -> parseInstant(x));
        serializers.put(oid, v -> {
            if (v instanceof Instant instant) {
                return formatInstant(instant);
            }
            if (v instanceof Number number) {
                return formatInstant(Instant.ofEpochMilli(number.longValue()));
            }
            if (v instanceof String string) {
                return formatInstant(parseInstant(string));
            }
            throw new IllegalArgumentException("Invalid input for timestamp type");
        });
    }

    private static Instant parseInstant(String value) {
        if (value == null) {
            return null;
        }
        var text = value.trim();
        if (text.indexOf('T') < 0 && text.indexOf(' ') >= 0) {
            text = text.replace(' ', 'T');
        }
        if (text.indexOf('T') < 0) {
            return LocalDate.parse(text).atStartOfDay().toInstant(ZoneOffset.UTC);
        }
        if (text.endsWith("Z") || text.matches(".*[+-][0-9]{2}:[0-9]{2}$")) {
            return Instant.parse(text);
        }
        return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
    }

    private static String formatInstant(Instant instant) {
        return new DateTimeFormatterBuilder().appendInstant(3).toFormatter().format(instant);
    }

    private static void registerJson(int oid) {
        parsers.put(oid, (x, t) -> io.github.hidekatsu_izuno.pglite_jdbc.polyfills.JSON.parse(x));
        serializers.put(
            oid,
            v -> v instanceof String string
                ? string
                : io.github.hidekatsu_izuno.pglite_jdbc.polyfills.JSON.stringify(v)
        );
    }

    private static void registerBytea(int oid) {
        parsers.put(
            oid,
            (x, t) -> {
                if (x == null || !x.startsWith("\\x")) {
                    return new byte[0];
                }
                var hex = x.substring(2);
                var out = new byte[hex.length() / 2];
                for (var i = 0; i < out.length; i++) {
                    out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
                }
                return out;
            }
        );
        serializers.put(
            oid,
            v -> {
                if (!(v instanceof byte[] bytes)) {
                    throw new IllegalArgumentException("Invalid input for bytea type");
                }
                var sb = new StringBuilder("\\x");
                for (var b : bytes) {
                    sb.append(String.format("%02x", b));
                }
                return sb.toString();
            }
        );
    }

    public static Object parseType(String value, int type, Map<Integer, Parser> overrideParsers) {
        if (value == null) {
            return null;
        }
        var parser = overrideParsers != null && overrideParsers.containsKey(type)
            ? overrideParsers.get(type)
            : parsers.get(type);
        if (parser == null) {
            return value;
        }
        return parser.parse(value, type);
    }

    public static String arraySerializer(Object xs, Serializer serializer, int typarray) {
        if (!(xs instanceof List<?> list)) {
            return String.valueOf(xs);
        }
        if (list.isEmpty()) {
            return "{}";
        }
        var delimiter = typarray == 1020 ? ";" : ",";
        var entries = new ArrayList<String>();
        for (var item : list) {
            if (item == null) {
                entries.add("null");
                continue;
            }
            if (item instanceof List<?>) {
                entries.add(arraySerializer(item, serializer, typarray));
                continue;
            }
            var text = serializer != null ? serializer.serialize(item) : String.valueOf(item);
            var escaped = text.replace("\\", "\\\\").replace("\"", "\\\"");
            entries.add("\"" + escaped + "\"");
        }
        return "{" + String.join(delimiter, entries) + "}";
    }

    public static List<Object> arrayParser(String text, Parser parser, int typarray) {
        var delimiter = typarray == 1020 ? ';' : ',';
        var out = new ArrayList<Object>();
        if (text == null || text.length() < 2 || text.charAt(0) != '{') {
            return out;
        }
        var current = new StringBuilder();
        var quoted = false;
        for (var i = 1; i < text.length() - 1; i++) {
            var ch = text.charAt(i);
            if (ch == '"' && (i == 1 || text.charAt(i - 1) != '\\')) {
                quoted = !quoted;
                continue;
            }
            if (!quoted && ch == delimiter) {
                var raw = current.toString();
                out.add(parser != null ? parser.parse(raw, null) : raw);
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        if (!current.isEmpty()) {
            var raw = current.toString();
            out.add(parser != null ? parser.parse(raw, null) : raw);
        }
        return out;
    }
}
