package com.aerofs.polaris.dao.types;

import com.aerofs.polaris.api.types.ObjectType;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.Argument;
import org.skife.jdbi.v2.tweak.ArgumentFactory;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class ObjectTypeArgument implements Argument {

    private final ObjectType objectType;

    public ObjectTypeArgument(ObjectType objectType) {
        this.objectType = objectType;
    }

    @Override
    public void apply(int position, PreparedStatement statement, StatementContext ctx) throws SQLException {
        statement.setInt(position, objectType.typeId);
    }

    public static final class ObjectTypeArgumentFactory implements ArgumentFactory<ObjectType> {

        @Override
        public boolean accepts(Class<?> expectedType, Object value, StatementContext ctx) {
            return value instanceof ObjectType;
        }

        @Override
        public Argument build(Class<?> expectedType, ObjectType value, StatementContext ctx) {
            return new ObjectTypeArgument(value);
        }
    }
}
