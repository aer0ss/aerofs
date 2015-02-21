package com.aerofs.polaris.dao.types;

import com.aerofs.ids.OID;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class OneColumnOIDMapper implements ResultSetMapper<OID> {

    private static final int COL_OID = 1;

    @Override
    public @Nullable OID map(int index, ResultSet r, StatementContext ctx) throws SQLException {
        try {
            byte[] bytes = r.getBytes(COL_OID);
            if (bytes != null) {
                return new OID(bytes);
            } else {
                return null;
            }
        } catch (IllegalArgumentException e) {
            throw new SQLException("invalid stored type", e);
        }
    }
}
