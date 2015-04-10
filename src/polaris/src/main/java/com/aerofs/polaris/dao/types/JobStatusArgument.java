package com.aerofs.polaris.dao.types;

import com.aerofs.polaris.api.types.JobStatus;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.Argument;
import org.skife.jdbi.v2.tweak.ArgumentFactory;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class JobStatusArgument implements Argument{

    private final JobStatus jobStatus;

    public JobStatusArgument(JobStatus jobStatus) {
        this.jobStatus = jobStatus;
    }

    @Override
    public void apply(int position, PreparedStatement statement, StatementContext ctx) throws SQLException {
        statement.setInt(position, jobStatus.typeId);
    }

    public static final class JobStatusArgumentFactory implements ArgumentFactory<JobStatus> {

        @Override
        public boolean accepts(Class<?> expectedType, Object value, StatementContext ctx) {
            return value instanceof JobStatus;
        }

        @Override
        public Argument build(Class<?> expectedType, JobStatus value, StatementContext ctx) {
            return new JobStatusArgument(value);
        }
    }
}
