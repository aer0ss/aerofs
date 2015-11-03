package com.aerofs.polaris.dao;

import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.api.types.JobStatus;
import com.aerofs.polaris.logical.Migrator.IDPair;
import com.aerofs.polaris.logical.Migrator.MigrationJob;
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

public interface Migrations {
    @SqlUpdate("insert into migration_jobs(migrant, destination, job_id, originator, job_status) values(:src, :dst, :job_id, :originator, :status)")
    int addMigration(@Bind("src") UniqueID src, @Bind("dst") UniqueID dst, @Bind("job_id") UniqueID jobID, @Bind("originator") DID originator, @Bind("status") JobStatus status);

    @SqlUpdate("update migration_jobs set job_status = :status where job_id = :job_id")
    int updateStatus(@Bind("job_id") UniqueID job, @Bind("status") JobStatus status);

    @RegisterMapper(Migrations.MigrationJobMapper.class)
    @SqlQuery("select migrant, destination, job_id, originator, job_status from migration_jobs where job_id = :job_id")
    @Nullable MigrationJob getJob(@Bind("job_id") UniqueID job);

    @RegisterMapper(Migrations.MigrationJobMapper.class)
    // make sure this matches the value for RUNNING in JobStatus.java
    @SqlQuery("select migrant, destination, job_id, originator, job_status from migration_jobs where job_status = 2")
    ResultIterator<MigrationJob> activeMigrations();

    @SqlUpdate("insert into migrating_object_oids(old_oid, new_oid, job_id) values (:old_oid, :new_oid, :job_id)")
    int addOidMapping(@Bind("old_oid") UniqueID oldOID, @Bind("new_oid") UniqueID newOID, @Bind("job_id") UniqueID jobID);

    @RegisterMapper(IDPairMapper.class)
    @SqlQuery("select old_oid, new_oid from migrating_object_oids where job_id = :job_id")
    ResultIterator<IDPair> getAllOIDMappingsForJob(@Bind("job_id") UniqueID jobID);

    @SqlUpdate("delete from migrating_object_oids where job_id = :job_id")
    int clearAllMappingsForJob(@Bind("job_id") UniqueID jobID);

    @SuppressWarnings("unused")
    void close();

    final class MigrationJobMapper implements ResultSetMapper<MigrationJob> {

        private static final int COL_FROM           = 1;
        private static final int COL_TO             = 2;
        private static final int COL_JOB_ID         = 3;
        private static final int COL_ORIGINATOR     = 4;
        private static final int COL_STATUS         = 5;

        @Override
        public MigrationJob map(int index, ResultSet r, StatementContext ctx) throws SQLException {
            try {
                return new MigrationJob(new UniqueID(r.getBytes(COL_FROM)), new UniqueID(r.getBytes(COL_TO)), new UniqueID(r.getBytes(COL_JOB_ID)), new DID(r.getBytes(COL_ORIGINATOR)), JobStatus.fromTypeId(r.getInt(COL_STATUS)));
            } catch (IllegalArgumentException e) {
                throw new SQLException("invalid stored type", e);
            }
        }
    }

    final class IDPairMapper implements ResultSetMapper<IDPair> {
        private static final int COL_OLD_ID = 1;
        private static final int COL_NEW_ID = 2;

        @Override
        public IDPair map(int index, ResultSet r, StatementContext ctx) throws SQLException {
            return new IDPair(new UniqueID(r.getBytes(COL_OLD_ID)), new UniqueID(r.getBytes(COL_NEW_ID)));
        }

    }
}
