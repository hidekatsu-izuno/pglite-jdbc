package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.polyfills.Uint8Array;
import java.math.BigInteger;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class types {

    public static final int BOOL = 16;
    public static final int BYTEA = 17;
    public static final int CHAR = 18;
    public static final int INT8 = 20;
    public static final int INT2 = 21;
    public static final int INT4 = 23;
    public static final int REGPROC = 24;
    public static final int TEXT = 25;
    public static final int OID = 26;
    public static final int TID = 27;
    public static final int XID = 28;
    public static final int CID = 29;
    public static final int JSON = 114;
    public static final int XML = 142;
    public static final int PG_NODE_TREE = 194;
    public static final int SMGR = 210;
    public static final int PATH = 602;
    public static final int POLYGON = 604;
    public static final int CIDR = 650;
    public static final int FLOAT4 = 700;
    public static final int FLOAT8 = 701;
    public static final int ABSTIME = 702;
    public static final int RELTIME = 703;
    public static final int TINTERVAL = 704;
    public static final int CIRCLE = 718;
    public static final int MACADDR8 = 774;
    public static final int MONEY = 790;
    public static final int MACADDR = 829;
    public static final int INET = 869;
    public static final int ACLITEM = 1033;
    public static final int BPCHAR = 1042;
    public static final int VARCHAR = 1043;
    public static final int DATE = 1082;
    public static final int TIME = 1083;
    public static final int TIMESTAMP = 1114;
    public static final int TIMESTAMPTZ = 1184;
    public static final int INTERVAL = 1186;
    public static final int TIMETZ = 1266;
    public static final int BIT = 1560;
    public static final int VARBIT = 1562;
    public static final int NUMERIC = 1700;
    public static final int REFCURSOR = 1790;
    public static final int REGPROCEDURE = 2202;
    public static final int REGOPER = 2203;
    public static final int REGOPERATOR = 2204;
    public static final int REGCLASS = 2205;
    public static final int REGTYPE = 2206;
    public static final int UUID = 2950;
    public static final int TXID_SNAPSHOT = 2970;
    public static final int PG_LSN = 3220;
    public static final int PG_NDISTINCT = 3361;
    public static final int PG_DEPENDENCIES = 3402;
    public static final int TSVECTOR = 3614;
    public static final int TSQUERY = 3615;
    public static final int GTSVECTOR = 3642;
    public static final int REGCONFIG = 3734;
    public static final int REGDICTIONARY = 3769;
    public static final int JSONB = 3802;
    public static final int REGNAMESPACE = 4089;
    public static final int REGROLE = 4096;

    public static final Map<String, TypeHandler> types = Map.of(
        "string",
        new TypeHandler(
            TEXT,
            new int[] { TEXT, VARCHAR, BPCHAR },
            x -> {
                if (x instanceof String) {
                    return (String) x;
                } else if (x instanceof Number) {
                    return x.toString();
                } else {
                    throw new IllegalArgumentException("Invalid input for string type");
                }
            },
            (x, typeId) -> x
        ),
        "number",
        new TypeHandler(
            0,
            new int[] { INT2, INT4, OID, FLOAT4, FLOAT8 },
            x -> x.toString(),
            (x, typeId) -> Double.parseDouble(x)
        ),
        "bigint",
        new TypeHandler(
            INT8,
            new int[] { INT8 },
            x -> {
                if (x instanceof BigInteger) {
                    return ((BigInteger) x).toString();
                } else if (x instanceof Long) {
                    return x.toString();
                } else if (x instanceof Integer) {
                    return x.toString();
                } else {
                    throw new IllegalArgumentException("Invalid input for bigint type");
                }
            },
            (x, typeId) -> {
                var n = new BigInteger(x);
                if (
                    n.compareTo(BigInteger.valueOf(-9007199254740991L)) < 0
                        || n.compareTo(BigInteger.valueOf(9007199254740991L)) > 0
                ) {
                    return n; // return BigInteger
                } else {
                    return n.longValue(); // in range of standard JS numbers so return number
                }
            }
        ),
    "json",
        new TypeHandler(
            JSON,
            new int[] { JSON, JSONB },
            x -> {
                if (x instanceof String) {
                    return (String) x;
                } else {
                    return io.github.hidekatsu_izuno.pglite_jdbc.polyfills.JSON.stringify(x);
                }
            },
            (x, typeId) -> io.github.hidekatsu_izuno.pglite_jdbc.polyfills.JSON.parse(x)
        ),
        "boolean",
        new TypeHandler(
            BOOL,
            new int[] { BOOL },
            x -> {
                if (!(x instanceof Boolean)) {
                    throw new IllegalArgumentException("Invalid input for boolean type");
                }
                return (Boolean) x ? "t" : "f";
            },
            (x, typeId) -> x.equals("t")
        ),
        "date",
        new TypeHandler(
            TIMESTAMPTZ,
            new int[] { DATE, TIMESTAMP, TIMESTAMPTZ },
            x -> {
                if (x instanceof String) {
                    return (String) x;
                } else if (x instanceof Number) {
                    return new java.util.Date(((Number) x).longValue()).toInstant().toString();
                } else if (x instanceof java.util.Date) {
                    return ((java.util.Date) x).toInstant().toString();
                } else {
                    throw new IllegalArgumentException("Invalid input for date type");
                }
            },
            (x, typeId) -> {
                try {
                    return DateFormat.getDateTimeInstance().parse(x);
                } catch (ParseException e) {
                    throw new IllegalArgumentException(e);
                }
            }
        ),
    "bytea",
        new TypeHandler(
            BYTEA,
            new int[] { BYTEA },
            x -> {
                if (!(x instanceof Uint8Array)) {
                    throw new IllegalArgumentException("Invalid input for bytea type");
                }
                var bytes = (Uint8Array) x;
                var builder = new StringBuilder();
                builder.append("\\x");
                for (var i = 0; i < bytes.length; i++) {
                    var hex = Integer.toHexString(bytes.get(i) & 0xFF);
                    if (hex.length() == 1) {
                        builder.append('0');
                    }
                    builder.append(hex);
                }
                return builder.toString();
            },
            (x, typeId) -> {
                var hexString = x.substring(2);
                var byteLength = hexString.length() / 2;
                var data = new byte[byteLength];
                for (var i = 0; i < byteLength; i++) {
                    var idx = i * 2;
                    data[i] = (byte) Integer.parseInt(hexString.substring(idx, idx + 2), 16);
                }
                return new Uint8Array(data);
            }
        )
    );

    public interface Parser {
        Object parse(String x, Integer typeId);
    }

    public interface Serializer {
        String serialize(Object x);
    }

    public static final class TypeHandler {
        public final int to;
        public final Object from;
        public final Serializer serialize;
        public final Parser parse;

        public TypeHandler(int to, int[] from, Serializer serialize, Parser parse) {
            this.to = to;
            this.from = from;
            this.serialize = serialize;
            this.parse = parse;
        }

        public TypeHandler(int to, int from, Serializer serialize, Parser parse) {
            this.to = to;
            this.from = from;
            this.serialize = serialize;
            this.parse = parse;
        }
    }

    public interface TypeHandlers {
        TypeHandler get(String key);
    }

    private static final HandlerMaps defaultHandlers = typeHandlers(types);

    public static final Map<Object, Parser> parsers = defaultHandlers.parsers;
    public static final Map<Object, Serializer> serializers = defaultHandlers.serializers;

    public static Object parseType(
        String x,
        int type,
        Map<Integer, Parser> parsers
    ) {
        if (x == null) {
            return null;
        }
        var handler = parsers != null ? parsers.get(type) : null;
        if (handler == null) {
            handler = defaultHandlers.parsers.get(type);
        }
        if (handler != null) {
            return handler.parse(x, type);
        } else {
            return x;
        }
    }

    private static HandlerMaps typeHandlers(Map<String, TypeHandler> types) {
        var parsers = new HashMap<Object, Parser>();
        var serializers = new HashMap<Object, Serializer>();
        for (var entry : types.entrySet()) {
            var k = entry.getKey();
            var handler = entry.getValue();
            var to = handler.to;
            var from = handler.from;
            var serialize = handler.serialize;
            var parse = handler.parse;
            serializers.put(to, serialize);
            serializers.put(k, serialize);
            parsers.put(k, parse);
            if (from instanceof int[]) {
                for (var f : (int[]) from) {
                    parsers.put(f, parse);
                    serializers.put(f, serialize);
                }
            } else {
                parsers.put(from, parse);
                serializers.put(from, serialize);
            }
        }
        return new HandlerMaps(parsers, serializers);
    }

    private static final String escapeBackslash = "\\";
    private static final String escapeQuote = "\"";

    private static String arrayEscape(String x) {
        return x.replace(escapeBackslash, "\\\\").replace(escapeQuote, "\\\"");
    }

    public static String arraySerializer(
        Object xs,
        Serializer serializer,
        int typarray
    ) {
        if (!(xs instanceof List<?> || xs instanceof Object[])) {
            return xs == null ? null : xs.toString();
        }

        var list = xs instanceof List<?> ? (List<?>) xs : null;
        var array = xs instanceof Object[] ? (Object[]) xs : null;
        var length = list != null ? list.size() : array.length;

        if (length == 0) return "{}";

        var first = list != null ? list.get(0) : array[0];
        // Only _box (1020) has the ';' delimiter for arrays, all other types use the ',' delimiter
        var delimiter = typarray == 1020 ? ";" : ",";

        if (first instanceof List<?> || first instanceof Object[]) {
            var builder = new StringBuilder();
            builder.append('{');
            for (var i = 0; i < length; i++) {
                if (i > 0) {
                    builder.append(delimiter);
                }
                var value = list != null ? list.get(i) : array[i];
                builder.append(arraySerializer(value, serializer, typarray));
            }
            builder.append('}');
            return builder.toString();
        } else {
            var builder = new StringBuilder();
            builder.append('{');
            for (var i = 0; i < length; i++) {
                if (i > 0) {
                    builder.append(delimiter);
                }
                var x = list != null ? list.get(i) : array[i];
                if (x == null) {
                    // TODO: Add an option to specify how to handle undefined values
                }
                if (x == null) {
                    builder.append("null");
                } else {
                    var serialized = serializer != null ? serializer.serialize(x) : x.toString();
                    builder.append('"').append(arrayEscape(serialized)).append('"');
                }
            }
            builder.append('}');
            return builder.toString();
        }
    }

    private static final ArrayParserState arrayParserState = new ArrayParserState();

    @SuppressWarnings("unchecked")
    public static List<Object> arrayParser(String x, Parser parser, int typarray) {
        arrayParserState.i = arrayParserState.last = 0;
        return (List<Object>) arrayParserLoop(arrayParserState, x, parser, typarray).get(0);
    }

    private static List<Object> arrayParserLoop(
        ArrayParserState s,
        String x,
        Parser parser,
        int typarray
    ) {
        var xs = new ArrayList<Object>();
        // Only _box (1020) has the ';' delimiter for arrays, all other types use the ',' delimiter
        var delimiter = typarray == 1020 ? ';' : ',';
        for (; s.i < x.length(); s.i++) {
            s.ch = x.charAt(s.i);
            if (s.quoted) {
                if (s.ch == '\\') {
                    s.str.append(x.charAt(++s.i));
                } else if (s.ch == '"') {
                    var parsed = s.str.toString();
                    xs.add(parser != null ? parser.parse(parsed, null) : parsed);
                    s.str.setLength(0);
                    s.quoted = s.i + 1 < x.length() && x.charAt(s.i + 1) == '"';
                    s.last = s.i + 2;
                } else {
                    s.str.append(s.ch);
                }
            } else if (s.ch == '"') {
                s.quoted = true;
            } else if (s.ch == '{') {
                s.last = ++s.i;
                xs.add(arrayParserLoop(s, x, parser, typarray));
            } else if (s.ch == '}') {
                s.quoted = false;
                if (s.last < s.i) {
                    var slice = x.substring(s.last, s.i);
                    xs.add(parser != null ? parser.parse(slice, null) : slice);
                }
                s.last = s.i + 1;
                break;
            } else if (s.ch == delimiter && (s.p == null || (s.p != '}' && s.p != '"'))) {
                var slice = x.substring(s.last, s.i);
                xs.add(parser != null ? parser.parse(slice, null) : slice);
                s.last = s.i + 1;
            }
            s.p = s.ch;
        }
        if (s.last < s.i) {
            var end = Math.min(s.i + 1, x.length());
            var slice = x.substring(s.last, end);
            xs.add(parser != null ? parser.parse(slice, null) : slice);
        }
        return xs;
    }

    private static final class ArrayParserState {
        private int i = 0;
        private Character ch = null;
        private StringBuilder str = new StringBuilder();
        private boolean quoted = false;
        private int last = 0;
        private Character p = null;
    }

    private static final class HandlerMaps {
        private final Map<Object, Parser> parsers;
        private final Map<Object, Serializer> serializers;

        private HandlerMaps(Map<Object, Parser> parsers, Map<Object, Serializer> serializers) {
            this.parsers = parsers;
            this.serializers = serializers;
        }
    }
    
    private types() {
    }
}
