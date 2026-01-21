package io.github.hidekatsu_izuno.pglite_jdbc.pglite;

import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.BackendMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.CommandCompleteMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.DataRowMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.ParameterDescriptionMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pg_protocol.messages.RowDescriptionMessage;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Blob;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.QueryOptions;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_.Results;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.types.Parser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class parse {
    /**
     * This function is used to parse the results of either a simple or extended query.
     * https://www.postgresql.org/docs/current/protocol-flow.html#PROTOCOL-FLOW-SIMPLE-QUERY
     */
    public static List<Results> parseResults(
        List<BackendMessage> messages,
        Map<Integer, Parser> defaultParsers,
        QueryOptions options,
        Blob blob
    ) {
        var resultSets = new ArrayList<Results>();
        var currentResultSet = new Results();
        currentResultSet.rows = new ArrayList<>();
        currentResultSet.fields = new ArrayList<>();
        var affectedRows = 0;
        var parsers = new HashMap<Integer, Parser>(defaultParsers);
        if (options != null && options.parsers != null) {
            parsers.putAll(options.parsers);
        }

        for (var message : messages) {
            switch (message.name()) {
                case "rowDescription": {
                    var msg = (RowDescriptionMessage) message;
                    var fields = new ArrayList<Results.Field>(msg.fieldCount);
                    for (var field : msg.fields) {
                        var resultField = new Results.Field();
                        resultField.name = field.name;
                        resultField.dataTypeID = field.dataTypeID;
                        fields.add(resultField);
                    }
                    currentResultSet.fields = fields;
                    break;
                }
                case "dataRow": {
                    if (currentResultSet == null) break;
                    var msg = (DataRowMessage) message;
                    if (options != null && interface_.RowMode.array.equals(options.rowMode)) {
                        var row = new ArrayList<Object>(msg.fields.length);
                        for (var i = 0; i < msg.fields.length; i++) {
                            row.add(
                                types.parseType(
                                    msg.fields[i],
                                    currentResultSet.fields.get(i).dataTypeID,
                                    parsers
                                )
                            );
                        }
                        currentResultSet.rows.add(row);
                    } else {
                        // rowMode === "object"
                        var row = new LinkedHashMap<String, Object>(msg.fields.length);
                        for (var i = 0; i < msg.fields.length; i++) {
                            row.put(
                                currentResultSet.fields.get(i).name,
                                types.parseType(
                                    msg.fields[i],
                                    currentResultSet.fields.get(i).dataTypeID,
                                    parsers
                                )
                            );
                        }
                        currentResultSet.rows.add(row);
                    }
                    break;
                }
                case "commandComplete": {
                    var msg = (CommandCompleteMessage) message;
                    affectedRows += retrieveRowCount(msg);

                    var resultSet = new Results();
                    resultSet.rows = currentResultSet.rows;
                    resultSet.fields = currentResultSet.fields;
                    resultSet.affectedRows = affectedRows;
                    if (blob != null) {
                        resultSet.blob = blob;
                    }
                    resultSets.add(resultSet);

                    currentResultSet = new Results();
                    currentResultSet.rows = new ArrayList<>();
                    currentResultSet.fields = new ArrayList<>();
                    break;
                }
            }
        }

        if (resultSets.size() == 0) {
            var emptyResultSet = new Results();
            emptyResultSet.affectedRows = 0;
            emptyResultSet.rows = new ArrayList<>();
            emptyResultSet.fields = new ArrayList<>();
            resultSets.add(emptyResultSet);
        }

        return resultSets;
    }

    private static int retrieveRowCount(CommandCompleteMessage msg) {
        var parts = msg.text.split(" ");
        switch (parts[0]) {
            case "INSERT":
                return Integer.parseInt(parts[2], 10);
            case "UPDATE":
            case "DELETE":
            case "COPY":
            case "MERGE":
                return Integer.parseInt(parts[1], 10);
            default:
                return 0;
        }
    }

    /** Get the dataTypeIDs from a list of messages, if it's available. */
    public static int[] parseDescribeStatementResults(
        List<BackendMessage> messages
    ) {
        for (var message : messages) {
            if (message instanceof ParameterDescriptionMessage) {
                return ((ParameterDescriptionMessage) message).dataTypeIDs;
            }
        }

        return new int[0];
    }

    private parse() {
    }
}
