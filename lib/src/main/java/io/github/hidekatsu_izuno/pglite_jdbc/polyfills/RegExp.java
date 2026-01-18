package io.github.hidekatsu_izuno.pglite_jdbc.polyfills;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegExp {
    private final Pattern pattern;
    private final String source;
    private final String flags;
    private final boolean global;
    private final boolean sticky;
    private int lastIndex;

    public RegExp(String pattern) {
        this(pattern, "");
    }

    public RegExp(String pattern, String flags) {
        this.source = pattern == null ? "" : pattern;
        this.flags = flags == null ? "" : flags;
        FlagConfig config = parseFlags(this.flags);
        this.global = config.global;
        this.sticky = config.sticky;
        this.pattern = Pattern.compile(this.source, config.patternFlags);
        this.lastIndex = 0;
    }

    public boolean test(String input) {
        return exec(input) != null;
    }

    public MatchResult exec(String input) {
        if (input == null) {
            return null;
        }
        int start = (global || sticky) ? lastIndex : 0;
        if (start < 0 || start > input.length()) {
            lastIndex = 0;
            return null;
        }
        Matcher matcher = pattern.matcher(input);
        boolean matched;
        if (sticky) {
            matcher.region(start, input.length());
            matched = matcher.lookingAt();
        } else {
            matched = matcher.find(start);
        }
        if (!matched) {
            lastIndex = 0;
            return null;
        }
        if (global || sticky) {
            lastIndex = matcher.end();
        }
        return matcher.toMatchResult();
    }

    public String replace(String input, String replacement) {
        if (input == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(input);
        return global ? matcher.replaceAll(replacement) : matcher.replaceFirst(replacement);
    }

    public int getLastIndex() {
        return lastIndex;
    }

    public void setLastIndex(int lastIndex) {
        this.lastIndex = lastIndex;
    }

    public String getSource() {
        return source;
    }

    public String getFlags() {
        return flags;
    }

    private static FlagConfig parseFlags(String flags) {
        int patternFlags = 0;
        boolean global = false;
        boolean sticky = false;
        Set<Character> seen = new HashSet<>();
        for (int i = 0; i < flags.length(); i++) {
            char flag = flags.charAt(i);
            if (!seen.add(flag)) {
                throw new IllegalArgumentException("duplicate flag: " + flag);
            }
            switch (flag) {
                case 'g' -> global = true;
                case 'i' -> patternFlags |= Pattern.CASE_INSENSITIVE;
                case 'm' -> patternFlags |= Pattern.MULTILINE;
                case 's' -> patternFlags |= Pattern.DOTALL;
                case 'u' -> patternFlags |= (Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);
                case 'y' -> sticky = true;
                default -> throw new IllegalArgumentException("unsupported flag: " + flag);
            }
        }
        return new FlagConfig(patternFlags, global, sticky);
    }

    private record FlagConfig(int patternFlags, boolean global, boolean sticky) {
    }
}
