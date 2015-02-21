package com.aerofs.polaris.dao.types;

import com.aerofs.ids.UniqueID;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class OneColumnUniqueIDMapper implements ResultSetMapper<UniqueID> {

    private static final int COL_UNIQUE_ID = 1;

    @Override
    public @Nullable
    UniqueID map(int index, ResultSet r, StatementContext ctx) throws SQLException {
        try {
            byte[] bytes = r.getBytes(COL_UNIQUE_ID);
            if (bytes != null) {
                return new UniqueID(bytes);
            } else {
                return null;
            }
        } catch (IllegalArgumentException e) {
            throw new SQLException("invalid stored type", e);
        }
    }
}
