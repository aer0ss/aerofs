package com.aerofs.polaris.dao.types;

import com.aerofs.ids.OID;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.Argument;
import org.skife.jdbi.v2.tweak.ArgumentFactory;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class OIDTypeArgument implements Argument {

    private final OID oid;

    public OIDTypeArgument(OID oid) {
        this.oid = oid;
    }

    @Override
    public void apply(int position, PreparedStatement statement, StatementContext ctx) throws SQLException {
        statement.setBytes(position, oid.getBytes());
    }

    public static final class OIDTypeArgumentFactory implements ArgumentFactory<OID> {

        @Override
        public boolean accepts(Class<?> expectedType, Object value, StatementContext ctx) {
            return value instanceof OID;
        }

        @Override
        public Argument build(Class<?> expectedType, OID value, StatementContext ctx) {
            return new OIDTypeArgument(value);
        }
    }
}
