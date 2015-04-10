package com.aerofs.polaris.dao;

import com.aerofs.ids.DID;
import com.aerofs.ids.SID;
import com.aerofs.polaris.api.types.JobStatus;
import com.aerofs.polaris.logical.StoreMigrator.MigrationJob;
import org.skife.jdbi.v2.ResultIterator;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;

@RegisterMapper(Migrations.MigrationJobMapper.class)
public interface Migrations {

    @SqlUpdate("insert into store_migrations(store_oid, originator, job_status) values(:store_oid, :originator, :status)")
    int add(@Bind("store_oid") SID store, @Bind("originator") DID originator, @Bind("status") JobStatus status);

    @SqlUpdate("update store_migrations set job_status = :status where store_oid = :store_oid")
    int updateStatus(@Bind("store_oid") SID store, @Bind("status") JobStatus status);

    @SqlQuery("select store_oid, originator, job_status from store_migrations where store_oid = :store_oid")
    @Nullable MigrationJob get(@Bind("store_oid") SID store);

    // make sure this matches the value for RUNNING in JobStatus.java
    @SqlQuery("select store_oid, originator, job_status from store_migrations where job_status = 2")
    ResultIterator<MigrationJob> activeMigrations();

    @SuppressWarnings("unused")
    void close();

    final class MigrationJobMapper implements ResultSetMapper<MigrationJob> {

        private static final int COL_STORE_OID   = 1;
        private static final int COL_ORIGINATOR  = 2;
        private static final int COL_STATUS  = 3;

        @Override
        public MigrationJob map(int index, ResultSet r, StatementContext ctx) throws SQLException {
            try {
                return new MigrationJob(new SID(r.getBytes(COL_STORE_OID)), new DID(r.getBytes(COL_ORIGINATOR)), JobStatus.fromTypeId(r.getInt(COL_STATUS)));
            } catch (IllegalArgumentException e) {
                throw new SQLException("invalid stored type", e);
            }
        }
    }
}
