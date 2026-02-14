package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import java.util.ArrayList;
import java.util.List;

public class templating {
    public record TemplatePart(String str) {}

    public record TemplateContainer(List<String> strings, List<Object> values) {}

    public record TemplatedQuery(String query, List<Object> params) {}

    private templating() {}

    public static TemplateContainer sql(List<String> strings, Object... values) {
        var parsedStrings = new ArrayList<String>();
        var parsedValues = new ArrayList<Object>();
        parsedStrings.add(strings.getFirst());

        for (var i = 0; i < values.length; i++) {
            var value = values[i];
            var suffix = strings.get(i + 1);
            if (value instanceof TemplatePart part) {
                var last = parsedStrings.removeLast();
                parsedStrings.add(last + part.str() + suffix);
                continue;
            }
            if (value instanceof TemplateContainer container) {
                var last = parsedStrings.removeLast();
                parsedStrings.add(last + container.strings().getFirst());
                for (var j = 1; j < container.strings().size(); j++) {
                    parsedStrings.add(container.strings().get(j));
                }
                var updatedLast = parsedStrings.removeLast();
                parsedStrings.add(updatedLast + suffix);
                parsedValues.addAll(container.values());
                continue;
            }
            parsedStrings.add(suffix);
            parsedValues.add(value);
        }
        return new TemplateContainer(parsedStrings, parsedValues);
    }

    public static TemplatePart identifier(String text) {
        return new TemplatePart("\"" + text + "\"");
    }

    public static TemplatePart raw(String text) {
        return new TemplatePart(text);
    }

    public static TemplatedQuery query(List<String> strings, Object... values) {
        var templated = sql(strings, values);
        var builder = new StringBuilder();
        builder.append(templated.strings().getFirst());
        for (var i = 0; i < templated.values().size(); i++) {
            builder.append("$").append(i + 1);
            builder.append(templated.strings().get(i + 1));
        }
        return new TemplatedQuery(builder.toString(), templated.values());
    }
}
