package io.github.hidekatsu_izuno.pglite_jdbc.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PgDatabaseMetaData implements InvocationHandler {
    private final PgConnection connection;

    private PgDatabaseMetaData(PgConnection connection) {
        this.connection = connection;
    }

    static DatabaseMetaData create(PgConnection connection) {
        return (DatabaseMetaData) Proxy.newProxyInstance(
            PgDatabaseMetaData.class.getClassLoader(),
            new Class<?>[] { DatabaseMetaData.class },
            new PgDatabaseMetaData(connection)
        );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        var name = method.getName();
        if (method.getDeclaringClass() == Object.class) {
            return switch (name) {
                case "toString" -> "PgDatabaseMetaData";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> null;
            };
        }

        return switch (name) {
            case "getConnection" -> connection.proxy();
            case "getURL" -> connection.url();
            case "getUserName" -> connection.user();
            case "getDatabaseProductName" -> "PGlite";
            case "getDatabaseProductVersion" -> "local";
            case "getDriverName" -> "pglite-jdbc";
            case "getDriverVersion" -> "0.1";
            case "getDriverMajorVersion" -> 0;
            case "getDriverMinorVersion" -> 1;
            case "supportsTransactions" -> true;
            case "supportsResultSetType" -> (Integer) args[0] == ResultSet.TYPE_FORWARD_ONLY;
            case "supportsResultSetConcurrency" ->
                (Integer) args[0] == ResultSet.TYPE_FORWARD_ONLY &&
                (Integer) args[1] == ResultSet.CONCUR_READ_ONLY;
            case "supportsSchemasInTableDefinitions", "supportsSchemasInDataManipulation",
                "supportsCatalogsInTableDefinitions" -> true;
            case "getIdentifierQuoteString" -> "\"";
            case "storesLowerCaseIdentifiers" -> true;
            case "getCatalogSeparator" -> ".";
            case "getCatalogTerm" -> "database";
            case "getSchemaTerm" -> "schema";
            case "getProcedureTerm" -> "function";
            case "getSearchStringEscape" -> "\\";
            case "getDefaultTransactionIsolation" -> Connection.TRANSACTION_READ_COMMITTED;
            case "isReadOnly" -> connection.readOnly();
            case "getTables" -> getTables(args);
            case "getColumns" -> getColumns(args);
            case "getPrimaryKeys" -> getPrimaryKeys(args);
            case "getIndexInfo" -> getIndexInfo(args);
            case "getImportedKeys" -> getImportedKeys(args);
            case "getExportedKeys" -> getExportedKeys(args);
            case "getCrossReference" -> getCrossReference(args);
            case "getTableTypes" -> tableTypes();
            case "getSchemas" -> getSchemas();
            case "getCatalogs" -> singleColumn("TABLE_CAT", connection.database());
            case "getTypeInfo" -> getTypeInfo();
            case "unwrap" -> {
                var iface = (Class<?>) args[0];
                if (iface.isInstance(proxy)) {
                    yield proxy;
                }
                throw new IllegalArgumentException("Not a wrapper for " + iface.getName());
            }
            case "isWrapperFor" -> ((Class<?>) args[0]).isInstance(proxy);
            default -> JdbcCompat.defaultReturn(method.getReturnType());
        };
    }

    private ResultSet getTables(Object[] args) throws SQLException {
        var schemaPattern = pattern(args, 1);
        var tablePattern = pattern(args, 2);
        var types = args != null && args.length > 3 && args[3] instanceof String[] values
            ? java.util.Set.of(values)
            : java.util.Set.<String>of();
        var sql = """
            SELECT current_database() AS table_cat,
                   n.nspname AS table_schem,
                   c.relname AS table_name,
                   CASE c.relkind
                     WHEN 'r' THEN 'TABLE'
                     WHEN 'p' THEN 'TABLE'
                     WHEN 'v' THEN 'VIEW'
                     WHEN 'm' THEN 'MATERIALIZED VIEW'
                     WHEN 'f' THEN 'FOREIGN TABLE'
                     ELSE 'OTHER'
                   END AS table_type,
                   obj_description(c.oid, 'pg_class') AS remarks
            FROM pg_catalog.pg_class c
            JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
            WHERE c.relkind IN ('r','p','v','m','f')
              AND n.nspname NOT IN ('pg_catalog', 'information_schema')
              AND n.nspname NOT LIKE 'pg_toast%%'
              %s
              %s
            ORDER BY table_type, table_schem, table_name
            """.formatted(
                likeCondition("n.nspname", schemaPattern),
                likeCondition("c.relname", tablePattern)
            );
        var rows = query(sql);
        if (!types.isEmpty()) {
            rows.removeIf(row -> !types.contains(String.valueOf(row.get("TABLE_TYPE"))));
        }
        return result(tableColumns(), rows);
    }

    private ResultSet getColumns(Object[] args) throws SQLException {
        var schemaPattern = pattern(args, 1);
        var tablePattern = pattern(args, 2);
        var columnPattern = pattern(args, 3);
        var sql = """
            SELECT current_database() AS table_cat,
                   n.nspname AS table_schem,
                   c.relname AS table_name,
                   a.attname AS column_name,
                   a.atttypid::int AS pg_type_oid,
                   a.atttypmod AS pg_type_mod,
                   format_type(a.atttypid, a.atttypmod) AS type_name,
                   CASE WHEN a.attnotnull THEN 0 ELSE 1 END AS nullable,
                   pg_get_expr(d.adbin, d.adrelid) AS column_def,
                   a.attnum AS ordinal_position,
                   col_description(a.attrelid, a.attnum) AS remarks
            FROM pg_catalog.pg_attribute a
            JOIN pg_catalog.pg_class c ON c.oid = a.attrelid
            JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
            LEFT JOIN pg_catalog.pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
            WHERE c.relkind IN ('r','p','v','m','f')
              AND a.attnum > 0
              AND NOT a.attisdropped
              AND n.nspname NOT IN ('pg_catalog', 'information_schema')
              AND n.nspname NOT LIKE 'pg_toast%%'
              %s
              %s
              %s
            ORDER BY table_schem, table_name, ordinal_position
            """.formatted(
                likeCondition("n.nspname", schemaPattern),
                likeCondition("c.relname", tablePattern),
                likeCondition("a.attname", columnPattern)
            );
        var raw = query(sql);
        var rows = new ArrayList<Map<String, Object>>();
        for (var row : raw) {
            var oid = number(row.get("PG_TYPE_OID")).intValue();
            var typmod = number(row.get("PG_TYPE_MOD")).intValue();
            var nullable = number(row.get("NULLABLE")).intValue();
            var columnSize = columnSize(oid, typmod);
            var decimalDigits = decimalDigits(oid, typmod);
            var charOctetLength = charOctetLength(oid, columnSize);
            var out = new LinkedHashMap<String, Object>();
            out.put("TABLE_CAT", row.get("TABLE_CAT"));
            out.put("TABLE_SCHEM", row.get("TABLE_SCHEM"));
            out.put("TABLE_NAME", row.get("TABLE_NAME"));
            out.put("COLUMN_NAME", row.get("COLUMN_NAME"));
            out.put("DATA_TYPE", JdbcCompat.oidToJdbcType(oid));
            out.put("TYPE_NAME", row.get("TYPE_NAME"));
            out.put("COLUMN_SIZE", columnSize);
            out.put("BUFFER_LENGTH", null);
            out.put("DECIMAL_DIGITS", decimalDigits);
            out.put("NUM_PREC_RADIX", 10);
            out.put("NULLABLE", nullable);
            out.put("REMARKS", row.get("REMARKS"));
            out.put("COLUMN_DEF", row.get("COLUMN_DEF"));
            out.put("SQL_DATA_TYPE", null);
            out.put("SQL_DATETIME_SUB", null);
            out.put("CHAR_OCTET_LENGTH", charOctetLength);
            out.put("ORDINAL_POSITION", row.get("ORDINAL_POSITION"));
            out.put("IS_NULLABLE", nullable == DatabaseMetaData.columnNullable ? "YES" : "NO");
            out.put("SCOPE_CATALOG", null);
            out.put("SCOPE_SCHEMA", null);
            out.put("SCOPE_TABLE", null);
            out.put("SOURCE_DATA_TYPE", null);
            out.put("IS_AUTOINCREMENT", "NO");
            out.put("IS_GENERATEDCOLUMN", "NO");
            rows.add(out);
        }
        return result(columnColumns(), rows);
    }

    private Integer columnSize(int oid, int typmod) {
        var jdbcType = JdbcCompat.oidToJdbcType(oid);
        return switch (jdbcType) {
            case Types.NUMERIC, Types.DECIMAL -> typmod >= 4 ? ((typmod - 4) >> 16) & 0xffff : null;
            case Types.CHAR, Types.VARCHAR -> typmod >= 4 ? typmod - 4 : null;
            case Types.INTEGER -> 10;
            case Types.SMALLINT -> 5;
            case Types.BIGINT -> 19;
            case Types.REAL -> 8;
            case Types.DOUBLE -> 17;
            default -> null;
        };
    }

    private Integer decimalDigits(int oid, int typmod) {
        var jdbcType = JdbcCompat.oidToJdbcType(oid);
        return switch (jdbcType) {
            case Types.NUMERIC, Types.DECIMAL -> typmod >= 4 ? (typmod - 4) & 0xffff : null;
            case Types.REAL -> 8;
            case Types.DOUBLE -> 17;
            default -> null;
        };
    }

    private Integer charOctetLength(int oid, Integer columnSize) {
        var jdbcType = JdbcCompat.oidToJdbcType(oid);
        return switch (jdbcType) {
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY ->
                columnSize;
            default -> null;
        };
    }

    private ResultSet getPrimaryKeys(Object[] args) throws SQLException {
        var schema = value(args, 1);
        var table = value(args, 2);
        var sql = """
            SELECT current_database() AS table_cat,
                   n.nspname AS table_schem,
                   c.relname AS table_name,
                   a.attname AS column_name,
                   u.ord::int AS key_seq,
                   i.relname AS pk_name
            FROM pg_catalog.pg_index ix
            JOIN pg_catalog.pg_class c ON c.oid = ix.indrelid
            JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
            JOIN pg_catalog.pg_class i ON i.oid = ix.indexrelid
            JOIN unnest(ix.indkey) WITH ORDINALITY AS u(attnum, ord) ON true
            JOIN pg_catalog.pg_attribute a ON a.attrelid = c.oid AND a.attnum = u.attnum
            WHERE ix.indisprimary
              %s
              %s
            ORDER BY table_schem, table_name, key_seq
            """.formatted(
                equalsCondition("n.nspname", schema),
                equalsCondition("c.relname", table)
            );
        return result(primaryKeyColumns(), query(sql));
    }

    private ResultSet getIndexInfo(Object[] args) throws SQLException {
        var schema = value(args, 1);
        var table = value(args, 2);
        var unique = args != null && args.length > 3 && Boolean.TRUE.equals(args[3]);
        var sql = """
            SELECT current_database() AS table_cat,
                   n.nspname AS table_schem,
                   c.relname AS table_name,
                   NOT ix.indisunique AS non_unique,
                   n.nspname AS index_qualifier,
                   i.relname AS index_name,
                   a.attname AS column_name,
                   u.ord::int AS ordinal_position,
                   CASE WHEN o.option & 1 = 1 THEN 'D' ELSE 'A' END AS asc_or_desc
            FROM pg_catalog.pg_index ix
            JOIN pg_catalog.pg_class c ON c.oid = ix.indrelid
            JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
            JOIN pg_catalog.pg_class i ON i.oid = ix.indexrelid
            JOIN unnest(ix.indkey, ix.indoption) WITH ORDINALITY AS u(attnum, option, ord) ON true
            LEFT JOIN pg_catalog.pg_attribute a ON a.attrelid = c.oid AND a.attnum = u.attnum
            LEFT JOIN LATERAL (SELECT u.option) o ON true
            WHERE true
              %s
              %s
              %s
            ORDER BY non_unique, index_name, ordinal_position
            """.formatted(
                equalsCondition("n.nspname", schema),
                equalsCondition("c.relname", table),
                unique ? "AND ix.indisunique" : ""
            );
        var raw = query(sql);
        var rows = new ArrayList<Map<String, Object>>();
        for (var row : raw) {
            var out = new LinkedHashMap<String, Object>();
            out.put("TABLE_CAT", row.get("TABLE_CAT"));
            out.put("TABLE_SCHEM", row.get("TABLE_SCHEM"));
            out.put("TABLE_NAME", row.get("TABLE_NAME"));
            out.put("NON_UNIQUE", row.get("NON_UNIQUE"));
            out.put("INDEX_QUALIFIER", row.get("INDEX_QUALIFIER"));
            out.put("INDEX_NAME", row.get("INDEX_NAME"));
            out.put("TYPE", DatabaseMetaData.tableIndexOther);
            out.put("ORDINAL_POSITION", row.get("ORDINAL_POSITION"));
            out.put("COLUMN_NAME", row.get("COLUMN_NAME"));
            out.put("ASC_OR_DESC", row.get("ASC_OR_DESC"));
            out.put("CARDINALITY", 0L);
            out.put("PAGES", 0L);
            out.put("FILTER_CONDITION", null);
            rows.add(out);
        }
        return result(indexColumns(), rows);
    }

    private ResultSet getImportedKeys(Object[] args) throws SQLException {
        return getForeignKeys(null, null, null, value(args, 1), value(args, 2));
    }

    private ResultSet getExportedKeys(Object[] args) throws SQLException {
        return getForeignKeys(value(args, 1), value(args, 2), null, null, null);
    }

    private ResultSet getCrossReference(Object[] args) throws SQLException {
        return getForeignKeys(
            value(args, 1),
            value(args, 2),
            value(args, 3),
            value(args, 4),
            value(args, 5)
        );
    }

    private ResultSet getForeignKeys(
        String parentSchema,
        String parentTable,
        String foreignCatalog,
        String foreignSchema,
        String foreignTable
    ) throws SQLException {
        var sql = """
            SELECT current_database() AS pktable_cat,
                   pn.nspname AS pktable_schem,
                   pc.relname AS pktable_name,
                   pa.attname AS pkcolumn_name,
                   current_database() AS fktable_cat,
                   fn.nspname AS fktable_schem,
                   fc.relname AS fktable_name,
                   fa.attname AS fkcolumn_name,
                   k.ord::int AS key_seq,
                   con.confupdtype AS update_rule_code,
                   con.confdeltype AS delete_rule_code,
                   con.conname AS fk_name,
                   pki.relname AS pk_name,
                   con.condeferrable AS condeferrable,
                   con.condeferred AS condeferred
            FROM pg_catalog.pg_constraint con
            JOIN pg_catalog.pg_class fc ON fc.oid = con.conrelid
            JOIN pg_catalog.pg_namespace fn ON fn.oid = fc.relnamespace
            JOIN pg_catalog.pg_class pc ON pc.oid = con.confrelid
            JOIN pg_catalog.pg_namespace pn ON pn.oid = pc.relnamespace
            JOIN unnest(con.conkey, con.confkey) WITH ORDINALITY AS k(fk_attnum, pk_attnum, ord) ON true
            JOIN pg_catalog.pg_attribute fa ON fa.attrelid = fc.oid AND fa.attnum = k.fk_attnum
            JOIN pg_catalog.pg_attribute pa ON pa.attrelid = pc.oid AND pa.attnum = k.pk_attnum
            LEFT JOIN pg_catalog.pg_index pkix ON pkix.indrelid = pc.oid AND pkix.indisprimary
            LEFT JOIN pg_catalog.pg_class pki ON pki.oid = pkix.indexrelid
            WHERE con.contype = 'f'
              %s
              %s
              %s
              %s
            ORDER BY pktable_schem, pktable_name, fktable_schem, fktable_name, key_seq
            """.formatted(
                equalsCondition("pn.nspname", parentSchema),
                equalsCondition("pc.relname", parentTable),
                equalsCondition("fn.nspname", foreignSchema),
                equalsCondition("fc.relname", foreignTable)
            );
        var raw = query(sql);
        var rows = new ArrayList<Map<String, Object>>();
        for (var row : raw) {
            var out = new LinkedHashMap<String, Object>();
            out.put("PKTABLE_CAT", row.get("PKTABLE_CAT"));
            out.put("PKTABLE_SCHEM", row.get("PKTABLE_SCHEM"));
            out.put("PKTABLE_NAME", row.get("PKTABLE_NAME"));
            out.put("PKCOLUMN_NAME", row.get("PKCOLUMN_NAME"));
            out.put("FKTABLE_CAT", row.get("FKTABLE_CAT"));
            out.put("FKTABLE_SCHEM", row.get("FKTABLE_SCHEM"));
            out.put("FKTABLE_NAME", row.get("FKTABLE_NAME"));
            out.put("FKCOLUMN_NAME", row.get("FKCOLUMN_NAME"));
            out.put("KEY_SEQ", row.get("KEY_SEQ"));
            out.put("UPDATE_RULE", fkRule(String.valueOf(row.get("UPDATE_RULE_CODE"))));
            out.put("DELETE_RULE", fkRule(String.valueOf(row.get("DELETE_RULE_CODE"))));
            out.put("FK_NAME", row.get("FK_NAME"));
            out.put("PK_NAME", row.get("PK_NAME"));
            out.put("DEFERRABILITY", deferrability(row));
            rows.add(out);
        }
        return result(importedKeyColumns(), rows);
    }

    private ResultSet getSchemas() throws SQLException {
        var rows = query("""
            SELECT nspname AS table_schem, current_database() AS table_catalog
            FROM pg_catalog.pg_namespace
            WHERE nspname NOT LIKE 'pg_toast%'
            ORDER BY nspname
            """);
        return result(
            List.of(new Column("TABLE_SCHEM", 19), new Column("TABLE_CATALOG", 19)),
            rows
        );
    }

    private ResultSet getTypeInfo() {
        var rows = new ArrayList<Map<String, Object>>();
        addType(rows, "bool", Types.BOOLEAN, 1, false);
        addType(rows, "bytea", Types.BINARY, Integer.MAX_VALUE, true);
        addType(rows, "int2", Types.SMALLINT, 5, false);
        addType(rows, "int4", Types.INTEGER, 10, false);
        addType(rows, "int8", Types.BIGINT, 19, false);
        addType(rows, "text", Types.VARCHAR, Integer.MAX_VALUE, true);
        addType(rows, "varchar", Types.VARCHAR, Integer.MAX_VALUE, true);
        addType(rows, "numeric", Types.NUMERIC, 1000, false);
        addType(rows, "float4", Types.REAL, 8, false);
        addType(rows, "float8", Types.DOUBLE, 17, false);
        addType(rows, "date", Types.DATE, 13, true);
        addType(rows, "time", Types.TIME, 15, true);
        addType(rows, "timestamp", Types.TIMESTAMP, 29, true);
        addType(rows, "timestamptz", Types.TIMESTAMP_WITH_TIMEZONE, 35, true);
        addType(rows, "uuid", Types.OTHER, 36, true);
        addType(rows, "json", Types.OTHER, Integer.MAX_VALUE, true);
        addType(rows, "jsonb", Types.OTHER, Integer.MAX_VALUE, true);
        return result(typeInfoColumns(), rows);
    }

    private void addType(List<Map<String, Object>> rows, String name, int dataType, int precision, boolean quoted) {
        var row = new LinkedHashMap<String, Object>();
        row.put("TYPE_NAME", name);
        row.put("DATA_TYPE", dataType);
        row.put("PRECISION", precision);
        row.put("LITERAL_PREFIX", quoted ? "'" : null);
        row.put("LITERAL_SUFFIX", quoted ? "'" : null);
        row.put("CREATE_PARAMS", null);
        row.put("NULLABLE", DatabaseMetaData.typeNullable);
        row.put("CASE_SENSITIVE", quoted);
        row.put("SEARCHABLE", DatabaseMetaData.typeSearchable);
        row.put("UNSIGNED_ATTRIBUTE", false);
        row.put("FIXED_PREC_SCALE", false);
        row.put("AUTO_INCREMENT", false);
        row.put("LOCAL_TYPE_NAME", name);
        row.put("MINIMUM_SCALE", 0);
        row.put("MAXIMUM_SCALE", dataType == Types.NUMERIC ? 1000 : 0);
        row.put("SQL_DATA_TYPE", null);
        row.put("SQL_DATETIME_SUB", null);
        row.put("NUM_PREC_RADIX", 10);
        rows.add(row);
    }

    private ResultSet tableTypes() {
        var rows = new ArrayList<Map<String, Object>>();
        for (var type : List.of("TABLE", "VIEW", "MATERIALIZED VIEW", "FOREIGN TABLE")) {
            var row = new LinkedHashMap<String, Object>();
            row.put("TABLE_TYPE", type);
            rows.add(row);
        }
        return result(List.of(new Column("TABLE_TYPE", 19)), rows);
    }

    private ResultSet singleColumn(String name, Object value) {
        var row = new LinkedHashMap<String, Object>();
        row.put(name, value);
        return result(List.of(new Column(name, 19)), List.of(row));
    }

    private ResultSet result(List<Column> columns, List<Map<String, Object>> rows) {
        return PgResultSet.create(null, columns, rows);
    }

    private List<Map<String, Object>> query(String sql) throws SQLException {
        try (var statement = connection.proxy().createStatement()) {
            try (var result = statement.executeQuery(sql)) {
                var metadata = result.getMetaData();
                var rows = new ArrayList<Map<String, Object>>();
                while (result.next()) {
                    var row = new LinkedHashMap<String, Object>();
                    for (var i = 1; i <= metadata.getColumnCount(); i++) {
                        row.put(metadata.getColumnLabel(i).toUpperCase(java.util.Locale.ROOT), result.getObject(i));
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    private String pattern(Object[] args, int index) {
        return value(args, index);
    }

    private String value(Object[] args, int index) {
        if (args == null || args.length <= index || args[index] == null) {
            return null;
        }
        return String.valueOf(args[index]);
    }

    private Number number(Object value) {
        return value instanceof Number number ? number : 0;
    }

    private int fkRule(String code) {
        return switch (code) {
            case "c" -> DatabaseMetaData.importedKeyCascade;
            case "n" -> DatabaseMetaData.importedKeySetNull;
            case "d" -> DatabaseMetaData.importedKeySetDefault;
            case "r" -> DatabaseMetaData.importedKeyRestrict;
            default -> DatabaseMetaData.importedKeyNoAction;
        };
    }

    private int deferrability(Map<String, Object> row) {
        if (!Boolean.TRUE.equals(row.get("CONDEFERRABLE"))) {
            return DatabaseMetaData.importedKeyNotDeferrable;
        }
        if (Boolean.TRUE.equals(row.get("CONDEFERRED"))) {
            return DatabaseMetaData.importedKeyInitiallyDeferred;
        }
        return DatabaseMetaData.importedKeyInitiallyImmediate;
    }

    private String likeCondition(String expression, String pattern) {
        return pattern == null ? "" : "AND " + expression + " LIKE " + literal(pattern) + " ESCAPE '\\'";
    }

    private String equalsCondition(String expression, String value) {
        return value == null ? "" : "AND " + expression + " = " + literal(value);
    }

    private String literal(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'";
    }

    private List<Column> tableColumns() {
        return List.of(
            new Column("TABLE_CAT", 19),
            new Column("TABLE_SCHEM", 19),
            new Column("TABLE_NAME", 19),
            new Column("TABLE_TYPE", 19),
            new Column("REMARKS", 25),
            new Column("TYPE_CAT", 19),
            new Column("TYPE_SCHEM", 19),
            new Column("TYPE_NAME", 19),
            new Column("SELF_REFERENCING_COL_NAME", 19),
            new Column("REF_GENERATION", 19)
        );
    }

    private List<Column> columnColumns() {
        return List.of(
            new Column("TABLE_CAT", 19),
            new Column("TABLE_SCHEM", 19),
            new Column("TABLE_NAME", 19),
            new Column("COLUMN_NAME", 19),
            new Column("DATA_TYPE", 23),
            new Column("TYPE_NAME", 19),
            new Column("COLUMN_SIZE", 23),
            new Column("BUFFER_LENGTH", 23),
            new Column("DECIMAL_DIGITS", 23),
            new Column("NUM_PREC_RADIX", 23),
            new Column("NULLABLE", 23),
            new Column("REMARKS", 25),
            new Column("COLUMN_DEF", 25),
            new Column("SQL_DATA_TYPE", 23),
            new Column("SQL_DATETIME_SUB", 23),
            new Column("CHAR_OCTET_LENGTH", 23),
            new Column("ORDINAL_POSITION", 23),
            new Column("IS_NULLABLE", 19),
            new Column("SCOPE_CATALOG", 19),
            new Column("SCOPE_SCHEMA", 19),
            new Column("SCOPE_TABLE", 19),
            new Column("SOURCE_DATA_TYPE", 21),
            new Column("IS_AUTOINCREMENT", 19),
            new Column("IS_GENERATEDCOLUMN", 19)
        );
    }

    private List<Column> primaryKeyColumns() {
        return List.of(
            new Column("TABLE_CAT", 19),
            new Column("TABLE_SCHEM", 19),
            new Column("TABLE_NAME", 19),
            new Column("COLUMN_NAME", 19),
            new Column("KEY_SEQ", 21),
            new Column("PK_NAME", 19)
        );
    }

    private List<Column> indexColumns() {
        return List.of(
            new Column("TABLE_CAT", 19),
            new Column("TABLE_SCHEM", 19),
            new Column("TABLE_NAME", 19),
            new Column("NON_UNIQUE", 16),
            new Column("INDEX_QUALIFIER", 19),
            new Column("INDEX_NAME", 19),
            new Column("TYPE", 21),
            new Column("ORDINAL_POSITION", 21),
            new Column("COLUMN_NAME", 19),
            new Column("ASC_OR_DESC", 19),
            new Column("CARDINALITY", 20),
            new Column("PAGES", 20),
            new Column("FILTER_CONDITION", 25)
        );
    }

    private List<Column> importedKeyColumns() {
        return List.of(
            new Column("PKTABLE_CAT", 19),
            new Column("PKTABLE_SCHEM", 19),
            new Column("PKTABLE_NAME", 19),
            new Column("PKCOLUMN_NAME", 19),
            new Column("FKTABLE_CAT", 19),
            new Column("FKTABLE_SCHEM", 19),
            new Column("FKTABLE_NAME", 19),
            new Column("FKCOLUMN_NAME", 19),
            new Column("KEY_SEQ", 21),
            new Column("UPDATE_RULE", 21),
            new Column("DELETE_RULE", 21),
            new Column("FK_NAME", 19),
            new Column("PK_NAME", 19),
            new Column("DEFERRABILITY", 21)
        );
    }

    private List<Column> typeInfoColumns() {
        return List.of(
            new Column("TYPE_NAME", 19),
            new Column("DATA_TYPE", 23),
            new Column("PRECISION", 23),
            new Column("LITERAL_PREFIX", 19),
            new Column("LITERAL_SUFFIX", 19),
            new Column("CREATE_PARAMS", 19),
            new Column("NULLABLE", 21),
            new Column("CASE_SENSITIVE", 16),
            new Column("SEARCHABLE", 21),
            new Column("UNSIGNED_ATTRIBUTE", 16),
            new Column("FIXED_PREC_SCALE", 16),
            new Column("AUTO_INCREMENT", 16),
            new Column("LOCAL_TYPE_NAME", 19),
            new Column("MINIMUM_SCALE", 21),
            new Column("MAXIMUM_SCALE", 21),
            new Column("SQL_DATA_TYPE", 23),
            new Column("SQL_DATETIME_SUB", 23),
            new Column("NUM_PREC_RADIX", 23)
        );
    }
}
