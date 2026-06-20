package io.github.hidekatsu_izuno.pglite_jdbc.ds;

import io.github.hidekatsu_izuno.pglite_jdbc.ds.common.BaseDataSource;
import javax.sql.DataSource;

public class PGSimpleDataSource extends BaseDataSource implements DataSource {
    @Override
    public String getDescription() {
        return "Non-Pooling PGlite DataSource";
    }
}
