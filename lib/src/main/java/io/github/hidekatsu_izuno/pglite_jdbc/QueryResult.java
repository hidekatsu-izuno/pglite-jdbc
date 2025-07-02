package io.github.hidekatsu_izuno.pglite_jdbc;

import java.util.List;

public class QueryResult {
    public final List<String> columnNames;
    public final List<String> columnTypes;
    public final List<List<Object>> rows;
    
    public QueryResult(List<String> columnNames, List<String> columnTypes, List<List<Object>> rows) {
        this.columnNames = columnNames;
        this.columnTypes = columnTypes;
        this.rows = rows;
    }
    
    public static QueryResult empty() {
        return new QueryResult(
            java.util.Collections.emptyList(),
            java.util.Collections.emptyList(),
            java.util.Collections.emptyList()
        );
    }
}