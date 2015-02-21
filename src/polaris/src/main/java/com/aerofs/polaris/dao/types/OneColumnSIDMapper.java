package com.aerofs.polaris.dao.types;

import com.aerofs.ids.SID;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class OneColumnSIDMapper implements ResultSetMapper<SID> {

    private static final int COL_SID = 1;

    @Override
    public @Nullable SID map(int index, ResultSet r, StatementContext ctx) throws SQLException {
        try {
            byte[] bytes = r.getBytes(COL_SID);
            if (bytes != null) {
                return new SID(bytes);
            } else {
                return null;
            }
        } catch (IllegalArgumentException e) {
            throw new SQLException("invalid stored type", e);
        }
    }
}
