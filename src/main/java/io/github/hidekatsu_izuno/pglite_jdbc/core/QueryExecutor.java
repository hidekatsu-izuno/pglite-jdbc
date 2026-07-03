package io.github.hidekatsu_izuno.pglite_jdbc.core;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.pglite;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public interface QueryExecutor {
    pglite getDatabase();

    interface_.Results<Map<String, Object>> query(String sql, Object[] params) throws SQLException;

    interface_.Results<List<Object>> queryArray(String sql, Object[] params) throws SQLException;

    List<interface_.Results<Map<String, Object>>> exec(String sql) throws SQLException;

    List<interface_.Results<List<Object>>> execArray(String sql) throws SQLException;

    interface_.DescribeQueryResult describe(String sql) throws SQLException;

    void close() throws SQLException;
}
