package com.aerofs.polaris.dao.types;

import com.aerofs.ids.SID;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.Argument;
import org.skife.jdbi.v2.tweak.ArgumentFactory;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class SIDTypeArgument implements Argument {

    private final SID sid;

    public SIDTypeArgument(SID sid) {
        this.sid = sid;
    }

    @Override
    public void apply(int position, PreparedStatement statement, StatementContext ctx) throws SQLException {
        statement.setBytes(position, sid.getBytes());
    }

    public static final class SIDTypeArgumentFactory implements ArgumentFactory<SID> {

        @Override
        public boolean accepts(Class<?> expectedType, Object value, StatementContext ctx) {
            return value instanceof SID;
        }

        @Override
        public Argument build(Class<?> expectedType, SID value, StatementContext ctx) {
            return new SIDTypeArgument(value);
        }
    }
}
