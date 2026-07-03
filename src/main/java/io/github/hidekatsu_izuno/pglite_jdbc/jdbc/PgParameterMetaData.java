package io.github.hidekatsu_izuno.pglite_jdbc.jdbc;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.interface_;
import java.sql.ParameterMetaData;
import java.util.List;

final class PgParameterMetaData {
    private PgParameterMetaData() {}

    static ParameterMetaData create(PgConnection connection, List<interface_.QueryParamField> params) {
        var types = new int[params != null ? params.size() : 0];
        for (var i = 0; i < types.length; i++) {
            types[i] = params.get(i).dataTypeID();
        }
        return create(connection, types);
    }

    static ParameterMetaData create(PgConnection connection, int[] types) {
        return new org.postgresql.jdbc.PgParameterMetaData(
            connection.baseConnection(),
            types.clone()
        );
    }
}
