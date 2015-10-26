package com.aerofs.polaris.dao.types;

import com.aerofs.polaris.dao.LockStatus;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.Argument;
import org.skife.jdbi.v2.tweak.ArgumentFactory;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class LockStatusArgument implements Argument {

    private final LockStatus lockStatus;

    public LockStatusArgument(LockStatus lockStatus) {
        this.lockStatus = lockStatus;
    }

    @Override
    public void apply(int position, PreparedStatement statement, StatementContext ctx) throws SQLException {
        statement.setInt(position, lockStatus.typeId);
    }

    public static final class LockStatusArgumentFactory implements ArgumentFactory<LockStatus> {

        @Override
        public boolean accepts(Class<?> expectedType, Object value, StatementContext ctx) {
            return value instanceof LockStatus;
        }

        @Override
        public Argument build(Class<?> expectedType, LockStatus value, StatementContext ctx) {
            return new LockStatusArgument(value);
        }
    }
}
