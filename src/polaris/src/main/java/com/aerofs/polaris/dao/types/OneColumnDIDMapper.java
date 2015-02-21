package com.aerofs.polaris.dao.types;

import com.aerofs.ids.DID;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class OneColumnDIDMapper implements ResultSetMapper<DID> {

    private static final int COL_DID = 1;

    @Override
    public @Nullable DID map(int index, ResultSet r, StatementContext ctx) throws SQLException {
        try {
            byte[] bytes = r.getBytes(COL_DID);
            if (bytes != null) {
                return new DID(bytes);
            } else {
                return null;
            }
        } catch (IllegalArgumentException e) {
            throw new SQLException("invalid stored type", e);
        }
    }
}
