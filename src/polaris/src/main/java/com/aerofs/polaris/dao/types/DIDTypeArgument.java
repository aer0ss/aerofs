package com.aerofs.polaris.dao.types;

import com.aerofs.ids.DID;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.Argument;
import org.skife.jdbi.v2.tweak.ArgumentFactory;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class DIDTypeArgument implements Argument {

    private final DID did;

    public DIDTypeArgument(DID did) {
        this.did = did;
    }

    @Override
    public void apply(int position, PreparedStatement statement, StatementContext ctx) throws SQLException {
        statement.setBytes(position, did.getBytes());
    }

    public static final class DIDTypeArgumentFactory implements ArgumentFactory<DID> {

        @Override
        public boolean accepts(Class<?> expectedType, Object value, StatementContext ctx) {
            return value instanceof DID;
        }

        @Override
        public Argument build(Class<?> expectedType, DID value, StatementContext ctx) {
            return new DIDTypeArgument(value);
        }
    }
}
