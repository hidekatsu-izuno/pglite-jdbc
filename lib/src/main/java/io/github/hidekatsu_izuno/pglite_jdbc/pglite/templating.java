package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import java.util.ArrayList;

public final class templating {

    private static final class TemplateType {
        private static final String part = "part";
        private static final String container = "container";

        private TemplateType() {
        }
    }

    public static final class TemplatePart {
        public final String _templateType;
        public final String str;

        public TemplatePart(String _templateType, String str) {
            this._templateType = _templateType;
            this.str = str;
        }
    }

    public static final class TemplateStringsArray {
        public final String[] strings;
        public final String[] raw;

        public TemplateStringsArray(String[] strings, String[] raw) {
            this.strings = strings;
            this.raw = raw;
        }
    }

    public static final class TemplateContainer {
        public final String _templateType;
        public final TemplateStringsArray strings;
        public final Object[] values;

        public TemplateContainer(
            String _templateType,
            TemplateStringsArray strings,
            Object[] values
        ) {
            this._templateType = _templateType;
            this.strings = strings;
            this.values = values;
        }
    }

    public static final class TemplatedQuery {
        public final String query;
        public final Object[] params;

        public TemplatedQuery(String query, Object[] params) {
            this.query = query;
            this.params = params;
        }
    }

    private static void addToLastAndPushWithSuffix(
        ArrayList<String> arr,
        String suffix,
        String... values
    ) {
        var lastArrIdx = arr.size() - 1;
        var lastValIdx = values.length - 1;

        // no-op
        if (lastValIdx == -1) return;

        // overwrite last element
        if (lastValIdx == 0) {
            arr.set(lastArrIdx, arr.get(lastArrIdx) + values[0] + suffix);
            return;
        }

        // sandwich values between array and suffix
        arr.set(lastArrIdx, arr.get(lastArrIdx) + values[0]);
        for (var i = 1; i < lastValIdx; i++) {
            arr.add(values[i]);
        }
        arr.add(values[lastValIdx] + suffix);
    }

    /**
     * Templating utility that allows nesting multiple SQL strings without
     * losing the automatic parametrization capabilities of {@link query}.
     *
     * @example
     * ```ts
     * query`SELECT * FROM tale ${withFilter ? sql`WHERE foo = ${fooVar}` : sql``}`
     * // > { query: 'SELECT * FROM tale WHERE foo = $1', params: [fooVar] }
     * // or
     * // > { query: 'SELECT * FROM tale', params: [] }
     * ```
     */
    public static TemplateContainer sql(
        TemplateStringsArray strings,
        Object... values
    ) {
        var parsedStrings = new ArrayList<String>();
        parsedStrings.add(strings.strings[0]);
        var parsedRaw = new ArrayList<String>();
        parsedRaw.add(strings.raw[0]);

        var parsedValues = new ArrayList<Object>();
        for (var i = 0; i < values.length; i++) {
            var value = values[i];
            var nextStringIdx = i + 1;

            // if value is a template tag, collapse into last string
            if (
                value instanceof TemplatePart
                    && TemplateType.part.equals(((TemplatePart) value)._templateType)
            ) {
                var partValue = (TemplatePart) value;
                addToLastAndPushWithSuffix(
                    parsedStrings,
                    strings.strings[nextStringIdx],
                    partValue.str
                );
                addToLastAndPushWithSuffix(
                    parsedRaw,
                    strings.raw[nextStringIdx],
                    partValue.str
                );
                continue;
            }

            // if value is an output of this method, append in place
            if (
                value instanceof TemplateContainer
                    && TemplateType.container.equals(
                        ((TemplateContainer) value)._templateType
                    )
            ) {
                var containerValue = (TemplateContainer) value;
                addToLastAndPushWithSuffix(
                    parsedStrings,
                    strings.strings[nextStringIdx],
                    containerValue.strings.strings
                );
                addToLastAndPushWithSuffix(
                    parsedRaw,
                    strings.raw[nextStringIdx],
                    containerValue.strings.raw
                );
                for (var containerValueItem : containerValue.values) {
                    parsedValues.add(containerValueItem);
                }
                continue;
            }

            // otherwise keep reconstructing
            parsedStrings.add(strings.strings[nextStringIdx]);
            parsedRaw.add(strings.raw[nextStringIdx]);
            parsedValues.add(value);
        }

        return new TemplateContainer(
            TemplateType.container,
            new TemplateStringsArray(
                parsedStrings.toArray(new String[0]),
                parsedRaw.toArray(new String[0])
            ),
            parsedValues.toArray(new Object[0])
        );
    }

    /**
     * Allows adding identifiers into a query template string without
     * parametrizing them. This method will automatically escape identifiers.
     *
     * @example
     * ```ts
     * query`SELECT * FROM ${identifier`foo`} WHERE ${identifier`id`} = ${id}`
     * // > { query: 'SELECT * FROM "foo" WHERE "id" = $1', params: [id] }
     * ```
     */
    public static TemplatePart identifier(
        TemplateStringsArray strings,
        Object... values
    ) {
        var rawStrings = strings.raw;
        var rawBuilder = new StringBuilder();
        for (var i = 0; i < values.length; i++) {
            rawBuilder.append(rawStrings[i]);
            rawBuilder.append(String.valueOf(values[i]));
        }
        rawBuilder.append(rawStrings[rawStrings.length - 1]);
        return new TemplatePart(
            TemplateType.part,
            "\"" + rawBuilder + "\""
        );
    }

    /**
     * Allows adding raw strings into a query template string without
     * parametrizing or modifying them in any way.
     *
     * @example
     * ```ts
     * query`SELECT * FROM foo ${raw`WHERE id = ${2+3}`}`
     * // > { query: 'SELECT * FROM foo WHERE id = 5', params: [] }
     * ```
     */

    public static TemplatePart raw(
        TemplateStringsArray strings,
        Object... values
    ) {
        var rawStrings = strings.raw;
        var rawBuilder = new StringBuilder();
        for (var i = 0; i < values.length; i++) {
            rawBuilder.append(rawStrings[i]);
            rawBuilder.append(String.valueOf(values[i]));
        }
        rawBuilder.append(rawStrings[rawStrings.length - 1]);
        return new TemplatePart(
            TemplateType.part,
            rawBuilder.toString()
        );
    }

    /**
     * Generates a parametrized query from a templated query string, assigning
     * the provided values to the appropriate named parameters.
     *
     * You can use templating helpers like {@link identifier} and {@link raw} to
     * add identifiers and raw strings to the query without making them parameters,
     * and you can use {@link sql} to nest multiple queries and create utilities.
     *
     * @example
     * ```ts
     * query`SELECT * FROM ${identifier`foo`} WHERE id = ${id} and name = ${name}`
     * // > { query: 'SELECT * FROM "foo" WHERE id = $1 and name = $2', params: [id, name] }
     * ```
     */
    public static TemplatedQuery query(
        TemplateStringsArray strings,
        Object... values
    ) {
        var sqlResult = sql(strings, values);
        var queryStringParts = sqlResult.strings.strings;
        var params = sqlResult.values;
        var queryBuilder = new StringBuilder();
        queryBuilder.append(queryStringParts[0]);
        for (var idx = 0; idx < params.length; idx++) {
            queryBuilder.append("$").append(idx + 1);
            queryBuilder.append(queryStringParts[idx + 1]);
        }
        return new TemplatedQuery(queryBuilder.toString(), params);
    }

    private templating() {
    }
}
