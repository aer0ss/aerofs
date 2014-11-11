package com.aerofs.polaris.dao;

import com.aerofs.polaris.api.types.TransformType;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.Argument;
import org.skife.jdbi.v2.tweak.ArgumentFactory;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class TransformTypeArgument implements Argument {

    private final TransformType transformType;

    public TransformTypeArgument(TransformType transformType) {
        this.transformType = transformType;
    }

    @Override
    public void apply(int position, PreparedStatement statement, StatementContext ctx) throws SQLException {
        statement.setInt(position, transformType.typeId);
    }

    public static final class TransformTypeArgumentFactory implements ArgumentFactory<TransformType> {

        @Override
        public boolean accepts(Class<?> expectedType, Object value, StatementContext ctx) {
            return value instanceof TransformType;
        }

        @Override
        public Argument build(Class<?> expectedType, TransformType value, StatementContext ctx) {
            return new TransformTypeArgument(value);
        }
    }
}
