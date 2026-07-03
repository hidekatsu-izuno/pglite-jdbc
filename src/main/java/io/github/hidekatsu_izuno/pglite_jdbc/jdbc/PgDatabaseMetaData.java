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
            case "allProceduresAreCallable", "allTablesAreSelectable" -> true;
            case "nullsAreSortedHigh" -> true;
            case "nullsAreSortedLow", "nullsAreSortedAtStart", "nullsAreSortedAtEnd" -> false;
            case "supportsTransactions" -> true;
            case "supportsResultSetType" -> (Integer) args[0] != ResultSet.TYPE_SCROLL_SENSITIVE;
            case "supportsResultSetConcurrency" ->
                (Integer) args[0] != ResultSet.TYPE_SCROLL_SENSITIVE;
            case "ownUpdatesAreVisible", "ownDeletesAreVisible", "ownInsertsAreVisible" -> true;
            case "usesLocalFiles", "usesLocalFilePerTable" -> false;
            case "supportsMixedCaseIdentifiers" -> false;
            case "storesUpperCaseIdentifiers" -> false;
            case "storesMixedCaseIdentifiers" -> false;
            case "supportsMixedCaseQuotedIdentifiers" -> true;
            case "storesUpperCaseQuotedIdentifiers" -> false;
            case "storesLowerCaseQuotedIdentifiers" -> false;
            case "storesMixedCaseQuotedIdentifiers" -> false;
            case "supportsAlterTableWithAddColumn", "supportsAlterTableWithDropColumn",
                "supportsColumnAliasing", "nullPlusNonNullIsNull" -> true;
            case "supportsTableCorrelationNames", "supportsExpressionsInOrderBy",
                "supportsOrderByUnrelated", "supportsGroupBy", "supportsGroupByUnrelated",
                "supportsGroupByBeyondSelect", "supportsLikeEscapeClause",
                "supportsMultipleResultSets", "supportsMultipleTransactions",
                "supportsNonNullableColumns", "supportsMinimumSQLGrammar",
                "supportsANSI92EntryLevelSQL", "supportsIntegrityEnhancementFacility",
                "supportsOuterJoins", "supportsFullOuterJoins", "supportsLimitedOuterJoins",
                "supportsSelectForUpdate", "supportsStoredProcedures",
                "supportsSubqueriesInComparisons", "supportsSubqueriesInExists",
                "supportsSubqueriesInIns", "supportsSubqueriesInQuantifieds",
                "supportsCorrelatedSubqueries", "supportsUnion", "supportsUnionAll",
                "supportsOpenStatementsAcrossCommit", "supportsOpenStatementsAcrossRollback",
                "supportsDataDefinitionAndDataManipulationTransactions",
                "supportsBatchUpdates", "supportsStoredFunctionsUsingCallSyntax",
                "generatedKeyAlwaysReturned", "supportsSavepoints", "supportsGetGeneratedKeys",
                "supportsResultSetHoldability", "locatorsUpdateCopy" -> true;
            case "supportsDifferentTableCorrelationNames", "supportsCoreSQLGrammar",
                "supportsExtendedSQLGrammar", "supportsANSI92IntermediateSQL",
                "supportsANSI92FullSQL", "supportsPositionedDelete", "supportsPositionedUpdate",
                "supportsOpenCursorsAcrossCommit", "supportsOpenCursorsAcrossRollback",
                "supportsDataManipulationTransactionsOnly", "dataDefinitionCausesTransactionCommit",
                "dataDefinitionIgnoredInTransactions", "supportsConvert", "supportsNamedParameters",
                "supportsMultipleOpenResults", "supportsStatementPooling",
                "autoCommitFailureClosesAllResultSets" -> false;
            case "supportsTransactionIsolationLevel" -> supportsTransactionIsolationLevel((Integer) args[0]);
            case "getResultSetHoldability" -> ResultSet.HOLD_CURSORS_OVER_COMMIT;
            case "getSQLStateType" -> DatabaseMetaData.sqlStateSQL;
            case "isCatalogAtStart" -> true;
            case "supportsSchemasInTableDefinitions", "supportsSchemasInDataManipulation",
                "supportsSchemasInProcedureCalls", "supportsSchemasInIndexDefinitions",
                "supportsSchemasInPrivilegeDefinitions" -> true;
            case "supportsCatalogsInTableDefinitions", "supportsCatalogsInDataManipulation",
                "supportsCatalogsInProcedureCalls", "supportsCatalogsInIndexDefinitions",
                "supportsCatalogsInPrivilegeDefinitions" -> false;
            case "getIdentifierQuoteString" -> "\"";
            case "getExtraNameCharacters" -> "";
            case "getNumericFunctions" ->
                "abs,acos,asin,atan,atan2,ceiling,cos,cot,degrees,exp,floor,log,log10,mod,pi,power,radians,round,sign,sin,sqrt,tan,truncate";
            case "getStringFunctions" ->
                "ascii,char,concat,lcase,left,length,ltrim,repeat,rtrim,space,substring,ucase,replace";
            case "getSystemFunctions" -> "database,ifnull,user";
            case "getTimeDateFunctions" ->
                "curdate,curtime,dayname,dayofmonth,dayofweek,dayofyear,hour,minute,month,monthname,now,quarter,second,week,year,timestampadd";
            case "storesLowerCaseIdentifiers" -> true;
            case "getCatalogSeparator" -> ".";
            case "getCatalogTerm" -> "database";
            case "getSchemaTerm" -> "schema";
            case "getProcedureTerm" -> "function";
            case "getSearchStringEscape" -> "\\";
            case "getMaxColumnNameLength", "getMaxCursorNameLength", "getMaxSchemaNameLength",
                "getMaxProcedureNameLength", "getMaxCatalogNameLength", "getMaxTableNameLength",
                "getMaxUserNameLength" -> 63;
            case "getMaxColumnsInIndex" -> 32;
            case "getMaxColumnsInTable" -> 1600;
            case "getMaxConnections" -> 8192;
            case "getMaxRowSize" -> 1073741824;
            case "doesMaxRowSizeIncludeBlobs" -> false;
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
            case "getSQLKeywords" -> "reindex";
            case "getVersionColumns" -> result(bestRowIdentifierColumns(), List.of());
            case "getBestRowIdentifier" -> getBestRowIdentifier(args);
            case "getUDTs" -> getUDTs(args);
            case "getFunctionColumns" -> getRoutineColumns(args, true);
            case "getProcedureColumns" -> getRoutineColumns(args, false);
            case "getFunctions" -> getFunctions(args);
            case "getProcedures" -> getProcedures(args);
            case "getTablePrivileges" -> getTablePrivileges(args);
            case "getColumnPrivileges" -> getColumnPrivileges(args);
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
        if (!catalogMatches(value(args, 0))) {
            return result(tableColumns(), List.of());
        }
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
                     WHEN 'p' THEN 'PARTITIONED TABLE'
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

    private boolean supportsTransactionIsolationLevel(int level) {
        return switch (level) {
            case Connection.TRANSACTION_NONE,
                Connection.TRANSACTION_READ_UNCOMMITTED,
                Connection.TRANSACTION_READ_COMMITTED,
                Connection.TRANSACTION_REPEATABLE_READ,
                Connection.TRANSACTION_SERIALIZABLE -> true;
            default -> false;
        };
    }

    private ResultSet getColumns(Object[] args) throws SQLException {
        if (!catalogMatches(value(args, 0))) {
            return result(columnColumns(), List.of());
        }
        var schemaPattern = pattern(args, 1);
        var tablePattern = pattern(args, 2);
        var columnPattern = pattern(args, 3);
        var sql = """
            SELECT current_database() AS table_cat,
                   n.nspname AS table_schem,
                   c.relname AS table_name,
                   a.attname AS column_name,
                   CASE WHEN t.typtype = 'd' THEN t.typbasetype ELSE a.atttypid END::int AS pg_type_oid,
                   CASE
                     WHEN t.typtype = 'd' THEN t.typbasetype
                     WHEN t.typelem <> 0 AND t.typcategory = 'A' THEN t.typelem
                     ELSE a.atttypid
                   END::int AS pg_size_type_oid,
                   CASE WHEN t.typtype = 'd' AND t.typtypmod >= 0 THEN t.typtypmod ELSE a.atttypmod END AS pg_type_mod,
                   t.typname AS type_name,
                   pg_get_serial_sequence(format('%%I.%%I', n.nspname, c.relname), a.attname) AS serial_sequence,
                   CASE WHEN a.attnotnull OR t.typnotnull THEN 0 ELSE 1 END AS nullable,
                   pg_get_expr(d.adbin, d.adrelid) AS column_def,
                   a.attnum AS ordinal_position,
                   a.attidentity AS identity_kind,
                   a.attgenerated AS generated_kind,
                   col_description(a.attrelid, a.attnum) AS remarks
            FROM pg_catalog.pg_attribute a
            JOIN pg_catalog.pg_class c ON c.oid = a.attrelid
            JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
            JOIN pg_catalog.pg_type t ON t.oid = a.atttypid
            LEFT JOIN pg_catalog.pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
            WHERE c.relkind IN ('r','p','v','m','f')
              AND a.attnum > 0
              AND NOT a.attisdropped
              AND n.nspname NOT IN ('pg_catalog', 'information_schema')
              AND n.nspname NOT LIKE 'pg_toast%%'
              %s
              %s
            ORDER BY table_schem, table_name, ordinal_position
            """.formatted(
                likeCondition("n.nspname", schemaPattern),
                likeCondition("c.relname", tablePattern)
            );
        var raw = query(sql);
        var rows = new ArrayList<Map<String, Object>>();
        var ordinal = 0;
        var currentTable = "";
        for (var row : raw) {
            var tableKey = row.get("TABLE_SCHEM") + "." + row.get("TABLE_NAME");
            if (!tableKey.equals(currentTable)) {
                currentTable = tableKey;
                ordinal = 0;
            }
            ordinal++;
            var oid = number(row.get("PG_TYPE_OID")).intValue();
            var sizeOid = number(row.get("PG_SIZE_TYPE_OID")).intValue();
            var typmod = number(row.get("PG_TYPE_MOD")).intValue();
            var nullable = number(row.get("NULLABLE")).intValue();
            var serial = row.get("SERIAL_SEQUENCE") != null;
            var columnSize = columnSize(sizeOid, typmod);
            var decimalDigits = decimalDigits(sizeOid, typmod);
            var charOctetLength = charOctetLength(sizeOid, columnSize);
            var typeName = serialTypeName(oid, String.valueOf(row.get("TYPE_NAME")), serial);
            var identity = !String.valueOf(row.get("IDENTITY_KIND")).isEmpty();
            var generated = !String.valueOf(row.get("GENERATED_KIND")).isEmpty();
            var out = new LinkedHashMap<String, Object>();
            out.put("TABLE_CAT", row.get("TABLE_CAT"));
            out.put("TABLE_SCHEM", row.get("TABLE_SCHEM"));
            out.put("TABLE_NAME", row.get("TABLE_NAME"));
            out.put("COLUMN_NAME", row.get("COLUMN_NAME"));
            out.put("DATA_TYPE", JdbcCompat.oidToJdbcType(oid));
            out.put("TYPE_NAME", typeName);
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
            out.put("ORDINAL_POSITION", ordinal);
            out.put("IS_NULLABLE", nullable == DatabaseMetaData.columnNullable ? "YES" : "NO");
            out.put("SCOPE_CATALOG", null);
            out.put("SCOPE_SCHEMA", null);
            out.put("SCOPE_TABLE", null);
            out.put("SOURCE_DATA_TYPE", null);
            out.put("IS_AUTOINCREMENT", serial || identity ? "YES" : "NO");
            out.put("IS_GENERATEDCOLUMN", generated ? "YES" : "NO");
            rows.add(out);
        }
        if (columnPattern != null) {
            rows.removeIf(row -> !like(String.valueOf(row.get("COLUMN_NAME")), columnPattern));
        }
        return result(columnColumns(), rows);
    }

    private String serialTypeName(int oid, String typeName, boolean serial) {
        if (!serial) {
            return typeName;
        }
        return switch (oid) {
            case 20 -> "bigserial";
            case 21 -> "smallserial";
            case 23 -> "serial";
            default -> typeName;
        };
    }

    private Integer columnSize(int oid, int typmod) {
        if (oid == 1560 || oid == 1562) {
            return typmod >= 0 ? typmod : null;
        }
        var jdbcType = JdbcCompat.oidToJdbcType(oid);
        return switch (jdbcType) {
            case Types.NUMERIC, Types.DECIMAL -> typmod >= 4 ? ((typmod - 4) >> 16) & 0xffff : 0;
            case Types.CHAR, Types.VARCHAR -> typmod >= 4 ? typmod - 4 : Integer.MAX_VALUE;
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY -> Integer.MAX_VALUE;
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
        if (!catalogMatches(value(args, 0))) {
            return result(primaryKeyColumns(), List.of());
        }
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
              AND u.ord <= ix.indnkeyatts
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
        if (!catalogMatches(value(args, 0))) {
            return result(indexColumns(), List.of());
        }
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
                   pg_get_indexdef(ix.indexrelid, u.ord::int, true) AS column_name,
                   u.ord::int AS ordinal_position,
                   CASE WHEN o.option & 1 = 1 THEN 'D' ELSE 'A' END AS asc_or_desc,
                   pg_get_expr(ix.indpred, ix.indrelid) AS filter_condition,
                   obj_description(i.oid, 'pg_class') AS remarks
            FROM pg_catalog.pg_index ix
            JOIN pg_catalog.pg_class c ON c.oid = ix.indrelid
            JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
            JOIN pg_catalog.pg_class i ON i.oid = ix.indexrelid
            JOIN unnest(ix.indkey, ix.indoption) WITH ORDINALITY AS u(attnum, option, ord) ON true
            LEFT JOIN LATERAL (SELECT u.option) o ON true
            WHERE u.ord <= ix.indnkeyatts
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
            out.put("FILTER_CONDITION", row.get("FILTER_CONDITION"));
            out.put("REMARKS", row.get("REMARKS"));
            rows.add(out);
        }
        return result(indexColumns(), rows);
    }

    private ResultSet getBestRowIdentifier(Object[] args) throws SQLException {
        if (!catalogMatches(value(args, 0))) {
            return result(bestRowIdentifierColumns(), List.of());
        }
        var schema = value(args, 1);
        var table = value(args, 2);
        var sql = """
            SELECT a.attname AS column_name,
                   CASE WHEN t.typtype = 'd' THEN t.typbasetype ELSE a.atttypid END::int AS pg_type_oid,
                   CASE WHEN t.typtype = 'd' AND t.typtypmod >= 0 THEN t.typtypmod ELSE a.atttypmod END AS pg_type_mod,
                   t.typname AS type_name
            FROM pg_catalog.pg_index ix
            JOIN pg_catalog.pg_class c ON c.oid = ix.indrelid
            JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
            JOIN unnest(ix.indkey) WITH ORDINALITY AS u(attnum, ord) ON true
            JOIN pg_catalog.pg_attribute a ON a.attrelid = c.oid AND a.attnum = u.attnum
            JOIN pg_catalog.pg_type t ON t.oid = a.atttypid
            WHERE ix.indisprimary
              AND u.ord <= ix.indnkeyatts
              %s
              %s
            ORDER BY u.ord
            """.formatted(
                equalsCondition("n.nspname", schema),
                equalsCondition("c.relname", table)
            );
        var raw = query(sql);
        var rows = new ArrayList<Map<String, Object>>();
        for (var row : raw) {
            var oid = number(row.get("PG_TYPE_OID")).intValue();
            var typmod = number(row.get("PG_TYPE_MOD")).intValue();
            var columnSize = columnSize(oid, typmod);
            var out = new LinkedHashMap<String, Object>();
            out.put("SCOPE", DatabaseMetaData.bestRowSession);
            out.put("COLUMN_NAME", row.get("COLUMN_NAME"));
            out.put("DATA_TYPE", JdbcCompat.oidToJdbcType(oid));
            out.put("TYPE_NAME", row.get("TYPE_NAME"));
            out.put("COLUMN_SIZE", columnSize);
            out.put("BUFFER_LENGTH", null);
            out.put("DECIMAL_DIGITS", decimalDigits(oid, typmod));
            out.put("PSEUDO_COLUMN", DatabaseMetaData.bestRowNotPseudo);
            rows.add(out);
        }
        return result(bestRowIdentifierColumns(), rows);
    }

    private ResultSet getUDTs(Object[] args) throws SQLException {
        if (!catalogMatches(value(args, 0))) {
            return result(udtColumns(), List.of());
        }
        var schemaPattern = pattern(args, 1);
        var typePattern = pattern(args, 2);
        if (typePattern != null && typePattern.contains(".")) {
            var parts = typePattern.split("\\.");
            if (parts.length >= 2) {
                schemaPattern = parts[parts.length - 2];
                typePattern = parts[parts.length - 1];
            }
        }
        var types = args != null && args.length > 3 && args[3] instanceof int[] values
            ? java.util.Arrays.stream(values).boxed().collect(java.util.stream.Collectors.toSet())
            : java.util.Set.<Integer>of();
        var sql = """
            SELECT current_database() AS type_cat,
                   n.nspname AS type_schem,
                   t.typname AS type_name,
                   CASE t.typtype
                     WHEN 'd' THEN %d
                     WHEN 'c' THEN %d
                     WHEN 'e' THEN %d
                     ELSE %d
                   END AS data_type,
                   obj_description(t.oid, 'pg_type') AS remarks,
                   CASE WHEN t.typtype = 'd' THEN t.typbasetype ELSE NULL END::int AS base_type_oid
            FROM pg_catalog.pg_type t
            JOIN pg_catalog.pg_namespace n ON n.oid = t.typnamespace
            WHERE t.typtype IN ('d', 'c', 'e')
              AND n.nspname NOT IN ('pg_catalog', 'information_schema')
              AND n.nspname NOT LIKE 'pg_toast%%'
              AND t.typname NOT LIKE '\\_%%'
              %s
              %s
            ORDER BY data_type, type_schem, type_name
            """.formatted(
                Types.DISTINCT,
                Types.STRUCT,
                Types.DISTINCT,
                Types.JAVA_OBJECT,
                likeCondition("n.nspname", schemaPattern),
                likeCondition("t.typname", typePattern)
            );
        var raw = query(sql);
        var rows = new ArrayList<Map<String, Object>>();
        for (var row : raw) {
            var dataType = number(row.get("DATA_TYPE")).intValue();
            if (!types.isEmpty() && !types.contains(dataType)) {
                continue;
            }
            var baseType = row.get("BASE_TYPE_OID") == null
                ? null
                : JdbcCompat.oidToJdbcType(number(row.get("BASE_TYPE_OID")).intValue());
            var out = new LinkedHashMap<String, Object>();
            out.put("TYPE_CAT", row.get("TYPE_CAT"));
            out.put("TYPE_SCHEM", row.get("TYPE_SCHEM"));
            out.put("TYPE_NAME", row.get("TYPE_NAME"));
            out.put("CLASS_NAME", null);
            out.put("DATA_TYPE", dataType);
            out.put("REMARKS", row.get("REMARKS"));
            out.put("BASE_TYPE", baseType);
            rows.add(out);
        }
        return result(udtColumns(), rows);
    }

    private ResultSet getRoutineColumns(Object[] args, boolean functions) throws SQLException {
        if (!catalogMatches(value(args, 0))) {
            return result(functions ? functionColumnColumns() : procedureColumnColumns(), List.of());
        }
        var schemaPattern = pattern(args, 1);
        var routinePattern = pattern(args, 2);
        var columnPattern = pattern(args, 3);
        var sql = """
            SELECT current_database() AS routine_cat,
                   n.nspname AS routine_schem,
                   p.proname AS routine_name,
                   p.oid::text AS specific_name,
                   p.prorettype::int AS return_type_oid,
                   rt.typname AS return_type_name,
                   rt.typtype AS return_type_kind,
                   COALESCE(array_to_string(p.proargnames, ','), '') AS arg_names,
                   COALESCE(array_to_string(p.proargmodes, ','), '') AS arg_modes,
                   CASE
                     WHEN p.proallargtypes IS NULL THEN p.proargtypes::text
                     ELSE array_to_string(p.proallargtypes, ' ')
                   END AS arg_type_oids
            FROM pg_catalog.pg_proc p
            JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace
            JOIN pg_catalog.pg_type rt ON rt.oid = p.prorettype
            WHERE n.nspname NOT IN ('pg_catalog', 'information_schema')
              %s
              %s
            ORDER BY routine_schem, routine_name, specific_name
            """.formatted(
                likeCondition("n.nspname", schemaPattern),
                likeCondition("p.proname", routinePattern)
            );
        var raw = query(sql);
        var rows = new ArrayList<Map<String, Object>>();
        for (var routine : raw) {
            var returnOid = number(routine.get("RETURN_TYPE_OID")).intValue();
            var argNames = split(String.valueOf(routine.get("ARG_NAMES")), ",");
            var argModes = split(String.valueOf(routine.get("ARG_MODES")), ",");
            var argOids = splitInts(String.valueOf(routine.get("ARG_TYPE_OIDS")));
            var compositeReturn = "c".equals(String.valueOf(routine.get("RETURN_TYPE_KIND")));
            if ((functions || argModes.stream().noneMatch(mode -> "o".equals(mode) || "b".equals(mode))) && !compositeReturn) {
                addRoutineColumn(rows, routine, "returnValue", functions, true, returnOid, 0);
            }
            for (var i = 0; i < argOids.size(); i++) {
                var mode = i < argModes.size() ? argModes.get(i) : "i";
                var name = i < argNames.size() && !argNames.get(i).isEmpty() ? argNames.get(i) : "$" + (i + 1);
                var columnType = routineColumnType(mode, functions);
                if (columnType == null) {
                    continue;
                }
                addRoutineColumn(rows, routine, name, functions, columnType, argOids.get(i), i + 1);
            }
            if (compositeReturn) {
                addCompositeReturnColumns(rows, routine, returnOid, functions);
            }
        }
        if (columnPattern != null) {
            rows.removeIf(row -> !like(String.valueOf(row.get("COLUMN_NAME")), columnPattern));
        }
        return result(functions ? functionColumnColumns() : procedureColumnColumns(), rows);
    }

    private ResultSet getProcedures(Object[] args) throws SQLException {
        if (!catalogMatches(value(args, 0))) {
            return result(procedureColumns(), List.of());
        }
        var schemaPattern = pattern(args, 1);
        var procedurePattern = pattern(args, 2);
        var sql = """
            SELECT current_database() AS procedure_cat,
                   n.nspname AS procedure_schem,
                   p.proname AS procedure_name,
                   obj_description(p.oid, 'pg_proc') AS remarks,
                   p.oid::text AS specific_name
            FROM pg_catalog.pg_proc p
            JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace
            WHERE n.nspname NOT IN ('pg_catalog', 'information_schema')
              %s
              %s
            ORDER BY procedure_schem, procedure_name, specific_name
            """.formatted(
                likeCondition("n.nspname", schemaPattern),
                likeCondition("p.proname", procedurePattern)
            );
        var raw = query(sql);
        var rows = new ArrayList<Map<String, Object>>();
        for (var row : raw) {
            var out = new LinkedHashMap<String, Object>();
            out.put("PROCEDURE_CAT", row.get("PROCEDURE_CAT"));
            out.put("PROCEDURE_SCHEM", row.get("PROCEDURE_SCHEM"));
            out.put("PROCEDURE_NAME", row.get("PROCEDURE_NAME"));
            out.put("RESERVED1", null);
            out.put("RESERVED2", null);
            out.put("RESERVED3", null);
            out.put("REMARKS", row.get("REMARKS"));
            out.put("PROCEDURE_TYPE", DatabaseMetaData.procedureReturnsResult);
            out.put("SPECIFIC_NAME", row.get("SPECIFIC_NAME"));
            rows.add(out);
        }
        return result(procedureColumns(), rows);
    }

    private ResultSet getTablePrivileges(Object[] args) throws SQLException {
        if (!catalogMatches(value(args, 0))) {
            return result(tablePrivilegeColumns(), List.of());
        }
        var schemaPattern = pattern(args, 1);
        var tablePattern = pattern(args, 2);
        var sql = """
            SELECT current_database() AS table_cat,
                   n.nspname AS table_schem,
                   c.relname AS table_name,
                   r.rolname AS grantor,
                   r.rolname AS grantee
            FROM pg_catalog.pg_class c
            JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
            JOIN pg_catalog.pg_roles r ON r.oid = c.relowner
            WHERE c.relkind IN ('r','p','v','m','f')
              AND n.nspname NOT IN ('pg_catalog', 'information_schema')
              %s
              %s
            ORDER BY table_schem, table_name
            """.formatted(
                likeCondition("n.nspname", schemaPattern),
                likeCondition("c.relname", tablePattern)
            );
        var rows = new ArrayList<Map<String, Object>>();
        for (var row : query(sql)) {
            for (var privilege : List.of("SELECT", "INSERT", "UPDATE", "DELETE", "REFERENCES", "TRIGGER")) {
                var out = new LinkedHashMap<String, Object>();
                out.put("TABLE_CAT", row.get("TABLE_CAT"));
                out.put("TABLE_SCHEM", row.get("TABLE_SCHEM"));
                out.put("TABLE_NAME", row.get("TABLE_NAME"));
                out.put("GRANTOR", row.get("GRANTOR"));
                out.put("GRANTEE", row.get("GRANTEE"));
                out.put("PRIVILEGE", privilege);
                out.put("IS_GRANTABLE", "YES");
                rows.add(out);
            }
        }
        return result(tablePrivilegeColumns(), rows);
    }

    private ResultSet getColumnPrivileges(Object[] args) throws SQLException {
        if (!catalogMatches(value(args, 0))) {
            return result(columnPrivilegeColumns(), List.of());
        }
        var schemaPattern = pattern(args, 1);
        var table = value(args, 2);
        var columnPattern = pattern(args, 3);
        var sql = """
            SELECT current_database() AS table_cat,
                   n.nspname AS table_schem,
                   c.relname AS table_name,
                   a.attname AS column_name,
                   r.rolname AS grantor,
                   r.rolname AS grantee
            FROM pg_catalog.pg_class c
            JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
            JOIN pg_catalog.pg_attribute a ON a.attrelid = c.oid
            JOIN pg_catalog.pg_roles r ON r.oid = c.relowner
            WHERE c.relkind IN ('r','p','v','m','f')
              AND a.attnum > 0
              AND NOT a.attisdropped
              %s
              %s
              %s
            ORDER BY table_schem, table_name, column_name
            """.formatted(
                likeCondition("n.nspname", schemaPattern),
                equalsCondition("c.relname", table),
                likeCondition("a.attname", columnPattern)
            );
        var rows = new ArrayList<Map<String, Object>>();
        for (var row : query(sql)) {
            for (var privilege : List.of("SELECT", "INSERT", "UPDATE", "REFERENCES")) {
                var out = new LinkedHashMap<String, Object>();
                out.put("TABLE_CAT", row.get("TABLE_CAT"));
                out.put("TABLE_SCHEM", row.get("TABLE_SCHEM"));
                out.put("TABLE_NAME", row.get("TABLE_NAME"));
                out.put("COLUMN_NAME", row.get("COLUMN_NAME"));
                out.put("GRANTOR", row.get("GRANTOR"));
                out.put("GRANTEE", row.get("GRANTEE"));
                out.put("PRIVILEGE", privilege);
                out.put("IS_GRANTABLE", "YES");
                rows.add(out);
            }
        }
        return result(columnPrivilegeColumns(), rows);
    }

    private void addCompositeReturnColumns(
        List<Map<String, Object>> rows,
        Map<String, Object> routine,
        int returnOid,
        boolean functions
    ) throws SQLException {
        var sql = """
            SELECT a.attname AS column_name,
                   CASE WHEN t.typtype = 'd' THEN t.typbasetype ELSE a.atttypid END::int AS pg_type_oid,
                   CASE WHEN t.typtype = 'd' AND t.typtypmod >= 0 THEN t.typtypmod ELSE a.atttypmod END AS pg_type_mod
            FROM pg_catalog.pg_type rt
            JOIN pg_catalog.pg_class c ON c.oid = rt.typrelid
            JOIN pg_catalog.pg_attribute a ON a.attrelid = c.oid
            JOIN pg_catalog.pg_type t ON t.oid = a.atttypid
            WHERE rt.oid = %d
              AND a.attnum > 0
              AND NOT a.attisdropped
            ORDER BY a.attnum
            """.formatted(returnOid);
        var raw = query(sql);
        var ordinal = rows.size();
        for (var row : raw) {
            var oid = number(row.get("PG_TYPE_OID")).intValue();
            ordinal++;
            addRoutineColumn(
                rows,
                routine,
                String.valueOf(row.get("COLUMN_NAME")),
                functions,
                functions ? DatabaseMetaData.functionColumnResult : DatabaseMetaData.procedureColumnResult,
                oid,
                ordinal
            );
        }
    }

    private ResultSet getFunctions(Object[] args) throws SQLException {
        if (!catalogMatches(value(args, 0))) {
            return result(functionColumns(), List.of());
        }
        var schemaPattern = pattern(args, 1);
        var functionPattern = pattern(args, 2);
        var sql = """
            SELECT current_database() AS function_cat,
                   n.nspname AS function_schem,
                   p.proname AS function_name,
                   obj_description(p.oid, 'pg_proc') AS remarks,
                   p.oid::text AS specific_name
            FROM pg_catalog.pg_proc p
            JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace
            WHERE n.nspname NOT IN ('pg_catalog', 'information_schema')
              %s
              %s
            ORDER BY function_schem, function_name, specific_name
            """.formatted(
                likeCondition("n.nspname", schemaPattern),
                likeCondition("p.proname", functionPattern)
            );
        var rows = new ArrayList<Map<String, Object>>();
        for (var row : query(sql)) {
            var out = new LinkedHashMap<String, Object>();
            out.put("FUNCTION_CAT", row.get("FUNCTION_CAT"));
            out.put("FUNCTION_SCHEM", row.get("FUNCTION_SCHEM"));
            out.put("FUNCTION_NAME", row.get("FUNCTION_NAME"));
            out.put("REMARKS", row.get("REMARKS"));
            out.put("FUNCTION_TYPE", DatabaseMetaData.functionReturnsTable);
            out.put("SPECIFIC_NAME", row.get("SPECIFIC_NAME"));
            rows.add(out);
        }
        return result(functionColumns(), rows);
    }

    private void addRoutineColumn(
        List<Map<String, Object>> rows,
        Map<String, Object> routine,
        String columnName,
        boolean functions,
        boolean returnValue,
        int oid,
        int ordinal
    ) {
        addRoutineColumn(
            rows,
            routine,
            columnName,
            functions,
            returnValue
                ? (functions ? DatabaseMetaData.functionReturn : DatabaseMetaData.procedureColumnReturn)
                : (functions ? DatabaseMetaData.functionColumnIn : DatabaseMetaData.procedureColumnIn),
            oid,
            ordinal
        );
    }

    private void addRoutineColumn(
        List<Map<String, Object>> rows,
        Map<String, Object> routine,
        String columnName,
        boolean functions,
        int columnType,
        int oid,
        int ordinal
    ) {
        var out = new LinkedHashMap<String, Object>();
        out.put(functions ? "FUNCTION_CAT" : "PROCEDURE_CAT", routine.get("ROUTINE_CAT"));
        out.put(functions ? "FUNCTION_SCHEM" : "PROCEDURE_SCHEM", routine.get("ROUTINE_SCHEM"));
        out.put(functions ? "FUNCTION_NAME" : "PROCEDURE_NAME", routine.get("ROUTINE_NAME"));
        out.put("COLUMN_NAME", columnName);
        out.put("COLUMN_TYPE", columnType);
        out.put("DATA_TYPE", JdbcCompat.oidToJdbcType(oid));
        out.put("TYPE_NAME", JdbcCompat.oidToPgType(oid));
        out.put("PRECISION", columnSize(oid, -1));
        out.put("LENGTH", null);
        out.put("SCALE", decimalDigits(oid, -1));
        out.put("RADIX", 10);
        out.put("NULLABLE", DatabaseMetaData.functionNullableUnknown);
        out.put("REMARKS", null);
        out.put("CHAR_OCTET_LENGTH", charOctetLength(oid, columnSize(oid, -1)));
        out.put("ORDINAL_POSITION", ordinal);
        out.put("IS_NULLABLE", "");
        out.put("SPECIFIC_NAME", routine.get("SPECIFIC_NAME"));
        rows.add(out);
    }

    private Integer routineColumnType(String mode, boolean functions) {
        return switch (mode) {
            case "i" -> functions ? DatabaseMetaData.functionColumnIn : DatabaseMetaData.procedureColumnIn;
            case "o" -> functions ? DatabaseMetaData.functionColumnOut : DatabaseMetaData.procedureColumnOut;
            case "b" -> functions ? DatabaseMetaData.functionColumnInOut : DatabaseMetaData.procedureColumnInOut;
            case "t" -> functions ? DatabaseMetaData.functionReturn : DatabaseMetaData.procedureColumnReturn;
            default -> null;
        };
    }

    private ResultSet getImportedKeys(Object[] args) throws SQLException {
        if (!catalogMatches(value(args, 0))) {
            return result(importedKeyColumns(), List.of());
        }
        return getForeignKeys(null, null, null, value(args, 1), value(args, 2));
    }

    private ResultSet getExportedKeys(Object[] args) throws SQLException {
        if (!catalogMatches(value(args, 0))) {
            return result(importedKeyColumns(), List.of());
        }
        return getForeignKeys(value(args, 1), value(args, 2), null, null, null);
    }

    private ResultSet getCrossReference(Object[] args) throws SQLException {
        if (!catalogMatches(value(args, 0)) || !catalogMatches(value(args, 3))) {
            return result(importedKeyColumns(), List.of());
        }
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
                   pkcon.conname AS pk_name,
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
            LEFT JOIN pg_catalog.pg_constraint pkcon
              ON pkcon.conrelid = pc.oid
             AND pkcon.contype IN ('p', 'u')
             AND pkcon.conkey = con.confkey
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
        addType(rows, "bit", Types.BIT, 83886080, false);
        addType(rows, "bool", Types.BIT, 0, false);
        addType(rows, "box", Types.OTHER, 0, true);
        addType(rows, "bytea", Types.BINARY, 0, true);
        addType(rows, "char", Types.CHAR, 0, true);
        addType(rows, "cidr", Types.OTHER, 0, true);
        addType(rows, "circle", Types.OTHER, 0, true);
        addType(rows, "date", Types.DATE, 0, true);
        addType(rows, "float4", Types.REAL, 0, false);
        addType(rows, "float8", Types.DOUBLE, 0, false);
        addType(rows, "inet", Types.OTHER, 0, true);
        addType(rows, "int2", Types.SMALLINT, 0, false);
        addType(rows, "smallserial", Types.SMALLINT, 0, false, true);
        addType(rows, "int4", Types.INTEGER, 0, false);
        addType(rows, "serial", Types.INTEGER, 0, false, true);
        addType(rows, "int8", Types.BIGINT, 0, false);
        addType(rows, "bigserial", Types.BIGINT, 0, false, true);
        addType(rows, "interval", Types.OTHER, 6, true);
        addType(rows, "line", Types.OTHER, 0, true);
        addType(rows, "lseg", Types.OTHER, 0, true);
        addType(rows, "macaddr", Types.OTHER, 0, true);
        addType(rows, "money", Types.DOUBLE, 0, false);
        addType(rows, "numeric", Types.NUMERIC, 1000, false);
        addType(rows, "path", Types.OTHER, 0, true);
        addType(rows, "pg_lsn", Types.OTHER, 0, true);
        addType(rows, "point", Types.OTHER, 0, true);
        addType(rows, "polygon", Types.OTHER, 0, true);
        addType(rows, "refcursor", Types.REF_CURSOR, 0, true);
        addType(rows, "text", Types.VARCHAR, 0, true);
        addType(rows, "time", Types.TIME, 6, true);
        addType(rows, "timestamp", Types.TIMESTAMP, 6, true);
        addType(rows, "timestamptz", Types.TIMESTAMP, 6, true);
        addType(rows, "timetz", Types.TIME, 6, true);
        addType(rows, "tsquery", Types.OTHER, 0, true);
        addType(rows, "tsvector", Types.OTHER, 0, true);
        addType(rows, "txid_snapshot", Types.OTHER, 0, true);
        addType(rows, "uuid", Types.OTHER, 0, true);
        addType(rows, "varbit", Types.OTHER, 83886080, false);
        addType(rows, "varchar", Types.VARCHAR, 10485760, true);
        addType(rows, "xml", Types.SQLXML, 0, true);
        addType(rows, "json", Types.OTHER, 0, true);
        addType(rows, "jsonb", Types.OTHER, 0, true);
        return result(typeInfoColumns(), rows);
    }

    private void addType(List<Map<String, Object>> rows, String name, int dataType, int precision, boolean quoted) {
        addType(rows, name, dataType, precision, quoted, false);
    }

    private void addType(
        List<Map<String, Object>> rows,
        String name,
        int dataType,
        int precision,
        boolean quoted,
        boolean autoIncrement
    ) {
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
        row.put("UNSIGNED_ATTRIBUTE", !isSignedType(dataType));
        row.put("FIXED_PREC_SCALE", false);
        row.put("AUTO_INCREMENT", autoIncrement);
        row.put("LOCAL_TYPE_NAME", name);
        row.put("MINIMUM_SCALE", 0);
        row.put("MAXIMUM_SCALE", dataType == Types.NUMERIC ? 1000 : 0);
        row.put("SQL_DATA_TYPE", null);
        row.put("SQL_DATETIME_SUB", null);
        row.put("NUM_PREC_RADIX", 10);
        rows.add(row);
    }

    private boolean isSignedType(int dataType) {
        return switch (dataType) {
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT,
                Types.REAL, Types.FLOAT, Types.DOUBLE, Types.NUMERIC, Types.DECIMAL -> true;
            default -> false;
        };
    }

    private ResultSet tableTypes() {
        var rows = new ArrayList<Map<String, Object>>();
        for (var type : List.of(
            "FOREIGN TABLE",
            "INDEX",
            "PARTITIONED INDEX",
            "MATERIALIZED VIEW",
            "PARTITIONED TABLE",
            "SEQUENCE",
            "SYSTEM INDEX",
            "SYSTEM TABLE",
            "SYSTEM TOAST INDEX",
            "SYSTEM TOAST TABLE",
            "SYSTEM VIEW",
            "TABLE",
            "TEMPORARY INDEX",
            "TEMPORARY SEQUENCE",
            "TEMPORARY TABLE",
            "TEMPORARY VIEW",
            "TYPE",
            "VIEW"
        )) {
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

    private boolean catalogMatches(String catalog) {
        return catalog == null || catalog.equals(connection.database());
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
        if (pattern == null) {
            return "";
        }
        if (hasDanglingSearchEscape(pattern)) {
            return "AND false";
        }
        return "AND " + expression + " LIKE " + literal(pattern) + " ESCAPE '\\'";
    }

    private String equalsCondition(String expression, String value) {
        return value == null ? "" : "AND " + expression + " = " + literal(value);
    }

    private String literal(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private boolean hasDanglingSearchEscape(String value) {
        var escapes = 0;
        for (var i = value.length() - 1; i >= 0 && value.charAt(i) == '\\'; i--) {
            escapes++;
        }
        return escapes % 2 == 1;
    }

    private boolean like(String value, String pattern) {
        if (pattern == null) {
            return true;
        }
        if (hasDanglingSearchEscape(pattern)) {
            return false;
        }
        var regex = new StringBuilder();
        var escaping = false;
        for (var i = 0; i < pattern.length(); i++) {
            var c = pattern.charAt(i);
            if (escaping) {
                regex.append(java.util.regex.Pattern.quote(String.valueOf(c)));
                escaping = false;
            } else if (c == '\\') {
                escaping = true;
            } else if (c == '%') {
                regex.append(".*");
            } else if (c == '_') {
                regex.append('.');
            } else {
                regex.append(java.util.regex.Pattern.quote(String.valueOf(c)));
            }
        }
        return value.matches(regex.toString());
    }

    private List<String> split(String value, String delimiter) {
        if (value == null || value.isEmpty()) {
            return List.of();
        }
        return java.util.Arrays.asList(value.split(java.util.regex.Pattern.quote(delimiter), -1));
    }

    private List<Integer> splitInts(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        var out = new ArrayList<Integer>();
        for (var part : value.trim().split("\\s+")) {
            out.add(Integer.parseInt(part));
        }
        return out;
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
            new Column("FILTER_CONDITION", 25),
            new Column("REMARKS", 25)
        );
    }

    private List<Column> bestRowIdentifierColumns() {
        return List.of(
            new Column("SCOPE", 21),
            new Column("COLUMN_NAME", 19),
            new Column("DATA_TYPE", 23),
            new Column("TYPE_NAME", 19),
            new Column("COLUMN_SIZE", 23),
            new Column("BUFFER_LENGTH", 23),
            new Column("DECIMAL_DIGITS", 21),
            new Column("PSEUDO_COLUMN", 21)
        );
    }

    private List<Column> udtColumns() {
        return List.of(
            new Column("TYPE_CAT", 19),
            new Column("TYPE_SCHEM", 19),
            new Column("TYPE_NAME", 19),
            new Column("CLASS_NAME", 19),
            new Column("DATA_TYPE", 23),
            new Column("REMARKS", 25),
            new Column("BASE_TYPE", 21)
        );
    }

    private List<Column> functionColumnColumns() {
        return List.of(
            new Column("FUNCTION_CAT", 19),
            new Column("FUNCTION_SCHEM", 19),
            new Column("FUNCTION_NAME", 19),
            new Column("COLUMN_NAME", 19),
            new Column("COLUMN_TYPE", 21),
            new Column("DATA_TYPE", 23),
            new Column("TYPE_NAME", 19),
            new Column("PRECISION", 23),
            new Column("LENGTH", 23),
            new Column("SCALE", 21),
            new Column("RADIX", 21),
            new Column("NULLABLE", 21),
            new Column("REMARKS", 25),
            new Column("CHAR_OCTET_LENGTH", 23),
            new Column("ORDINAL_POSITION", 23),
            new Column("IS_NULLABLE", 19),
            new Column("SPECIFIC_NAME", 19)
        );
    }

    private List<Column> functionColumns() {
        return List.of(
            new Column("FUNCTION_CAT", 19),
            new Column("FUNCTION_SCHEM", 19),
            new Column("FUNCTION_NAME", 19),
            new Column("REMARKS", 25),
            new Column("FUNCTION_TYPE", 21),
            new Column("SPECIFIC_NAME", 19)
        );
    }

    private List<Column> procedureColumns() {
        return List.of(
            new Column("PROCEDURE_CAT", 19),
            new Column("PROCEDURE_SCHEM", 19),
            new Column("PROCEDURE_NAME", 19),
            new Column("RESERVED1", 19),
            new Column("RESERVED2", 19),
            new Column("RESERVED3", 19),
            new Column("REMARKS", 25),
            new Column("PROCEDURE_TYPE", 21),
            new Column("SPECIFIC_NAME", 19)
        );
    }

    private List<Column> tablePrivilegeColumns() {
        return List.of(
            new Column("TABLE_CAT", 19),
            new Column("TABLE_SCHEM", 19),
            new Column("TABLE_NAME", 19),
            new Column("GRANTOR", 19),
            new Column("GRANTEE", 19),
            new Column("PRIVILEGE", 19),
            new Column("IS_GRANTABLE", 19)
        );
    }

    private List<Column> columnPrivilegeColumns() {
        return List.of(
            new Column("TABLE_CAT", 19),
            new Column("TABLE_SCHEM", 19),
            new Column("TABLE_NAME", 19),
            new Column("COLUMN_NAME", 19),
            new Column("GRANTOR", 19),
            new Column("GRANTEE", 19),
            new Column("PRIVILEGE", 19),
            new Column("IS_GRANTABLE", 19)
        );
    }

    private List<Column> procedureColumnColumns() {
        return List.of(
            new Column("PROCEDURE_CAT", 19),
            new Column("PROCEDURE_SCHEM", 19),
            new Column("PROCEDURE_NAME", 19),
            new Column("COLUMN_NAME", 19),
            new Column("COLUMN_TYPE", 21),
            new Column("DATA_TYPE", 23),
            new Column("TYPE_NAME", 19),
            new Column("PRECISION", 23),
            new Column("LENGTH", 23),
            new Column("SCALE", 21),
            new Column("RADIX", 21),
            new Column("NULLABLE", 21),
            new Column("REMARKS", 25)
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
