package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class parse {
    private parse() {}

    public static List<interface_.Results<Map<String, Object>>> parseResults(
        List<messages.BackendMessage> messageList,
        Map<Integer, types.Parser> defaultParsers,
        interface_.QueryOptions options,
        byte[] blob
    ) {
        var resultSets = new ArrayList<interface_.Results<Map<String, Object>>>();
        var currentRows = new ArrayList<Map<String, Object>>();
        var currentFields = new ArrayList<interface_.Field>();
        var affectedRows = 0;

        var parsers = new HashMap<Integer, types.Parser>();
        if (defaultParsers != null) {
            parsers.putAll(defaultParsers);
        }
        if (options != null && options.parsers() != null) {
            for (var e : options.parsers().entrySet()) {
                parsers.put(e.getKey(), (value, typeId) -> e.getValue().parse(value, typeId));
            }
        }

        for (var message : messageList) {
            switch (message.name()) {
                case "rowDescription" -> {
                    var msg = (messages.RowDescriptionMessage) message;
                    currentFields.clear();
                    for (var field : msg.fields) {
                        currentFields.add(new interface_.Field(field.name, field.dataTypeID));
                    }
                }
                case "dataRow" -> {
                    var msg = (messages.DataRowMessage) message;
                    if (options != null && options.rowMode() == interface_.RowMode.array) {
                        var row = new HashMap<String, Object>();
                        for (var i = 0; i < msg.fields.length; i++) {
                            var field = msg.fields[i];
                            var typeId = currentFields.get(i).dataTypeID();
                            row.put(Integer.toString(i), types.parseType(field, typeId, parsers));
                        }
                        currentRows.add(row);
                    } else {
                        var row = new HashMap<String, Object>();
                        for (var i = 0; i < msg.fields.length; i++) {
                            var field = msg.fields[i];
                            var typeId = currentFields.get(i).dataTypeID();
                            var fieldName = currentFields.get(i).name();
                            row.put(fieldName, types.parseType(field, typeId, parsers));
                        }
                        currentRows.add(row);
                    }
                }
                case "commandComplete" -> {
                    var msg = (messages.CommandCompleteMessage) message;
                    affectedRows += retrieveRowCount(msg);
                    resultSets.add(
                        new interface_.Results<>(
                            List.copyOf(currentRows),
                            affectedRows,
                            List.copyOf(currentFields),
                            blob
                        )
                    );
                    currentRows = new ArrayList<>();
                    currentFields = new ArrayList<>();
                }
                default -> {}
            }
        }

        if (resultSets.isEmpty()) {
            resultSets.add(new interface_.Results<>(List.of(), 0, List.of(), blob));
        }
        return resultSets;
    }

    private static int retrieveRowCount(messages.CommandCompleteMessage msg) {
        var parts = msg.text.split(" ");
        if (parts.length == 0) {
            return 0;
        }
        return switch (parts[0]) {
            case "INSERT" -> parts.length > 2 ? parseInt(parts[2]) : 0;
            case "UPDATE", "DELETE", "COPY", "MERGE" -> parts.length > 1 ? parseInt(parts[1]) : 0;
            default -> 0;
        };
    }

    private static int parseInt(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static int[] parseDescribeStatementResults(List<messages.BackendMessage> messageList) {
        for (var message : messageList) {
            if (message instanceof messages.ParameterDescriptionMessage parameterDescriptionMessage) {
                return parameterDescriptionMessage.dataTypeIDs;
            }
        }
        return new int[0];
    }
}
