package io.github.hidekatsu_izuno.pglite_jdbc.ds;

import io.github.hidekatsu_izuno.pglite_jdbc.ds.common.BaseDataSource;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import javax.sql.DataSource;

public class PGSimpleDataSource extends BaseDataSource implements DataSource {
    private static final long serialVersionUID = 1L;

    @Override
    public String getDescription() {
        return "Non-Pooling PGlite DataSource";
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        writeBaseObject(out);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        readBaseObject(in);
    }
}
