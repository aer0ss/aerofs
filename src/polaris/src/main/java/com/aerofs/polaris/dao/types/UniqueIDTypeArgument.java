package com.aerofs.polaris.dao.types;

import com.aerofs.ids.UniqueID;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.Argument;
import org.skife.jdbi.v2.tweak.ArgumentFactory;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class UniqueIDTypeArgument implements Argument {

    private final UniqueID oid;

    public UniqueIDTypeArgument(UniqueID oid) {
        this.oid = oid;
    }

    @Override
    public void apply(int position, PreparedStatement statement, StatementContext ctx) throws SQLException {
        statement.setBytes(position, oid.getBytes());
    }

    public static final class UniqueIDTypeArgumentFactory implements ArgumentFactory<UniqueID> {

        @Override
        public boolean accepts(Class<?> expectedType, Object value, StatementContext ctx) {
            return value instanceof UniqueID;
        }

        @Override
        public Argument build(Class<?> expectedType, UniqueID value, StatementContext ctx) {
            return new UniqueIDTypeArgument(value);
        }
    }
}
