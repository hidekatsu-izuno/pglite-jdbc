package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class argsParser {
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String CONTROL =
        "(?:"
            + String.join(
                "|",
                "\\|\\|",
                "\\&\\&",
                ";;",
                "\\|\\&",
                "\\<\\(",
                "\\<\\<\\<",
                ">>",
                ">\\&",
                "<\\&",
                "[&;()|<>]"
            )
            + ")";
    private static final Pattern CONTROL_RE = Pattern.compile("^" + CONTROL + "$");
    private static final String META = "|&;()<> \\t";
    private static final String SINGLE_QUOTE = "\"((\\\\\"|[^\"])*?)\"";
    private static final String DOUBLE_QUOTE = "'((\\\\'|[^'])*?)'";
    private static final Pattern HASH = Pattern.compile("^#$");

    private static final String SQ = "'";
    private static final String DQ = "\"";
    private static final String DS = "$";

    private static final String TOKEN = generateToken();
    private static final Pattern STARTS_WITH_TOKEN = Pattern.compile("^" + Pattern.quote(TOKEN));
    private static final Pattern TOKEN_SPLIT = Pattern.compile("(" + Pattern.quote(TOKEN) + ".*?" + Pattern.quote(TOKEN) + ")");

    public record OpToken(String op, String pattern) {
        public OpToken(String op) {
            this(op, null);
        }
    }

    public record CommentToken(String comment) {}

    public record ParseOpts(String escape) {
        public ParseOpts() {
            this(null);
        }
    }

    @FunctionalInterface
    public interface EnvFunction {
        Object apply(String key);
    }

    private record TextMatch(int start, String text) {}

    private argsParser() {}

    private static String generateToken() {
        var token = new StringBuilder();
        var mult = 0x100000000L;
        for (var i = 0; i < 4; i++) {
            token.append(Long.toHexString((long) (mult * Math.random())));
        }
        return token.toString();
    }

    public static List<Object> parse(String s) {
        return parse(s, Map.<String, String>of(), null);
    }

    public static List<Object> parse(String s, Map<String, String> env) {
        return parse(s, env, null);
    }

    public static List<Object> parse(String s, Map<String, String> env, ParseOpts opts) {
        var mapped = parseInternal(s, env, null, opts);
        return mapped;
    }

    public static List<Object> parse(String s, EnvFunction env) {
        return parse(s, env, null);
    }

    public static List<Object> parse(String s, EnvFunction env, ParseOpts opts) {
        var mapped = parseInternal(s, null, env, opts);
        var acc = new ArrayList<Object>();
        for (var token : mapped) {
            if (token instanceof OpToken || token instanceof CommentToken) {
                acc.add(token);
                continue;
            }
            var text = (String) token;
            var parts = TOKEN_SPLIT.split(text, -1);
            if (parts.length == 1) {
                acc.add(parts[0]);
                continue;
            }
            for (var part : parts) {
                if (part == null || part.isEmpty()) {
                    continue;
                }
                if (STARTS_WITH_TOKEN.matcher(part).lookingAt()) {
                    var json = part.split(Pattern.quote(TOKEN), 3)[1];
                    try {
                        acc.add(JSON.readValue(json, Object.class));
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    acc.add(part);
                }
            }
        }
        return acc;
    }

    private static List<Object> parseInternal(
        String string,
        Map<String, String> env,
        EnvFunction envFn,
        ParseOpts opts
    ) {
        if (opts == null) {
            opts = new ParseOpts();
        }
        var bs = opts.escape() != null ? opts.escape() : "\\";
        var bareword = "(\\" + bs + "['\"" + META + "]|[^\\s'\"" + META + "])+";

        var chunker = Pattern.compile(
            String.join(
                "|",
                "(" + CONTROL + ")",
                "(" + bareword + "|" + SINGLE_QUOTE + "|" + DOUBLE_QUOTE + ")+"
            )
        );

        var matches = matchAll(string, chunker);

        if (matches.isEmpty()) {
            return List.of();
        }

        var commented = new boolean[] {false};
        var out = new ArrayList<Object>();

        for (var match : matches) {
            var mapped = mapMatch(string, match, env, envFn, commented, bs);
            if (mapped == null) {
                continue;
            }
            if (mapped instanceof List<?> list) {
                out.addAll(list);
            } else {
                out.add(mapped);
            }
        }

        return out;
    }

    private static Object mapMatch(
        String string,
        TextMatch match,
        Map<String, String> env,
        EnvFunction envFn,
        boolean[] commented,
        String bs
    ) {
        var s = match.text();
        if (s == null || s.isEmpty() || commented[0]) {
            return null;
        }
        if (CONTROL_RE.matcher(s).matches()) {
            return new OpToken(s);
        }

        var quote = new String[] {null};
        var esc = new boolean[] {false};
        var out = new StringBuilder();
        var isGlob = new boolean[] {false};
        var i = new int[] {0};

        Runnable parseEnvVar = () -> {
            i[0] += 1;
            int varend;
            String varname;
            var charAt = s.charAt(i[0]);

            if (charAt == '{') {
                i[0] += 1;
                if (s.charAt(i[0]) == '}') {
                    throw new Error("Bad substitution: " + s.substring(i[0] - 2, i[0] + 1));
                }
                varend = s.indexOf('}', i[0]);
                if (varend < 0) {
                    throw new Error("Bad substitution: " + s.substring(i[0]));
                }
                varname = s.substring(i[0], varend);
                i[0] = varend;
            } else if ("*@#?$!_-".indexOf(charAt) >= 0) {
                varname = String.valueOf(charAt);
                i[0] += 1;
            } else {
                var slicedFromI = s.substring(i[0]);
                var varendMatch = Pattern.compile("[^\\w\\d_]").matcher(slicedFromI);
                if (!varendMatch.find()) {
                    varname = slicedFromI;
                    i[0] = s.length();
                } else {
                    varname = slicedFromI.substring(0, varendMatch.start());
                    i[0] += varendMatch.start() - 1;
                }
            }
            out.append(getVar(env, envFn, "", varname));
        };

        for (i[0] = 0; i[0] < s.length(); i[0]++) {
            var c = s.charAt(i[0]);
            isGlob[0] = isGlob[0] || (quote[0] == null && (c == '*' || c == '?'));
            if (esc[0]) {
                out.append(c);
                esc[0] = false;
            } else if (quote[0] != null) {
                if (c == quote[0].charAt(0)) {
                    quote[0] = null;
                } else if (SQ.equals(quote[0])) {
                    out.append(c);
                } else {
                    if (c == bs.charAt(0)) {
                        i[0] += 1;
                        c = s.charAt(i[0]);
                        if (c == DQ.charAt(0) || c == bs.charAt(0) || c == DS.charAt(0)) {
                            out.append(c);
                        } else {
                            out.append(bs).append(c);
                        }
                    } else if (c == DS.charAt(0)) {
                        parseEnvVar.run();
                    } else {
                        out.append(c);
                    }
                }
            } else if (c == DQ.charAt(0) || c == SQ.charAt(0)) {
                quote[0] = String.valueOf(c);
            } else if (CONTROL_RE.matcher(String.valueOf(c)).matches()) {
                return new OpToken(s);
            } else if (HASH.matcher(String.valueOf(c)).matches()) {
                commented[0] = true;
                var commentObj = new CommentToken(string.substring(match.start() + i[0] + 1));
                if (!out.isEmpty()) {
                    return List.of(out.toString(), commentObj);
                }
                return List.of(commentObj);
            } else if (c == bs.charAt(0)) {
                esc[0] = true;
            } else if (c == DS.charAt(0)) {
                parseEnvVar.run();
            } else {
                out.append(c);
            }
        }

        if (isGlob[0]) {
            return new OpToken("glob", out.toString());
        }

        return out.toString();
    }

    private static List<TextMatch> matchAll(String s, Pattern pattern) {
        var matches = new ArrayList<TextMatch>();
        var matcher = pattern.matcher(s);
        var lastIndex = 0;
        while (matcher.find(lastIndex)) {
            matches.add(new TextMatch(matcher.start(), matcher.group(0)));
            var nextIndex = matcher.end();
            if (nextIndex == matcher.start()) {
                nextIndex += 1;
            }
            lastIndex = nextIndex;
        }
        return matches;
    }

    private static String getVar(Map<String, String> env, EnvFunction envFn, String pre, String key) {
        Object r;
        if (envFn != null) {
            r = envFn.apply(key);
        } else if (env != null) {
            r = env.get(key);
        } else {
            r = null;
        }
        if (r == null && !key.isEmpty()) {
            r = "";
        } else if (r == null) {
            r = "$";
        }

        if (r instanceof Map<?, ?> || r instanceof List<?>) {
            try {
                return pre + TOKEN + JSON.writeValueAsString(r) + TOKEN;
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        return pre + String.valueOf(r);
    }
}
