package com.aerofs.polaris.dao;

import com.aerofs.polaris.api.ObjectType;
import com.aerofs.polaris.api.Transform;
import com.aerofs.polaris.api.TransformType;
import com.google.common.base.Preconditions;
import org.skife.jdbi.v2.ResultIterator;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.GetGeneratedKeys;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;

@RegisterMapper(Transforms.TypedTransformMapper.class)
public interface Transforms {

    @GetGeneratedKeys
    @SqlUpdate("insert into transforms(atomic_operation_id, atomic_operation_index, atomic_operation_total, originator, root_oid, oid, transform_type, new_version, child_oid, child_name, timestamp) values(:atomic_operation_id, :atomic_operation_index, :atomic_operation_total, :originator, :root_oid, :oid, :transform_type, :new_version, :child_oid, :child_name, :timestamp)")
    long add(@Bind("originator") String originator, @Bind("root_oid") String root, @Bind("oid") String oid, @Bind("transform_type") TransformType transformType, @Bind("new_version") long newVersion, @Bind("child_oid") @Nullable String child, @Bind("child_name") @Nullable String name, @Bind("timestamp") long timestamp, @BindAtomic @Nullable Atomic atomic);

    @SqlQuery("select logical_timestamp from transforms where oid = :oid and new_version = :version")
    long getLogicalTimestampFor(@Bind("oid") String oid, @Bind("version") long version);

    @SqlQuery("select logical_timestamp, originator, root_oid, transforms.oid, transform_type, new_version, child_oid, object_type, child_name, atomic_operation_id, atomic_operation_index, atomic_operation_total, timestamp from transforms left join object_types on (transforms.child_oid = object_types.oid) where logical_timestamp > :logical_timestamp and root_oid = :root_oid order by logical_timestamp asc")
    ResultIterator<Transform> getTransformsSince(@Bind("logical_timestamp") long since, @Bind("root_oid") String root);

    @SqlQuery("select max(logical_timestamp) from transforms where root_oid = :root_oid")
    int getTransformCount(@Bind("root_oid") String root);

    @SuppressWarnings("unused")
    void close();

    public final class TypedTransformMapper implements ResultSetMapper<Transform> {

        private static final int COL_LOGICAL_TIMESTAMP      = 1;
        private static final int COL_ORIGINATOR             = 2;
        private static final int COL_ROOT_OID               = 3;
        private static final int COL_OID                    = 4;
        private static final int COL_TRANSFORM_TYPE         = 5;
        private static final int COL_NEW_VERSION            = 6;
        private static final int COL_CHILD_OID              = 7;
        private static final int COL_CHILD_OBJECT_TYPE      = 8;
        private static final int COL_CHILD_NAME             = 9;
        private static final int COL_ATOMIC_OPERATION_ID    = 10;
        private static final int COL_ATOMIC_OPERATION_INDEX = 11;
        private static final int COL_ATOMIC_OPERATION_TOTAL = 12;
        private static final int COL_TIMESTAMP              = 13;

        @Override
        public Transform map(int index, ResultSet r, StatementContext ctx) throws SQLException {
            try {
                Transform transform = new Transform(
                        r.getLong(COL_LOGICAL_TIMESTAMP),
                        r.getString(COL_ORIGINATOR),
                        r.getString(COL_ROOT_OID),
                        r.getString(COL_OID),
                        TransformType.fromTypeId(r.getInt(COL_TRANSFORM_TYPE)),
                        r.getLong(COL_NEW_VERSION),
                        r.getLong(COL_TIMESTAMP)
                );

                String atomicOperationId = r.getString(COL_ATOMIC_OPERATION_ID);
                if (atomicOperationId != null) {
                    transform.setAtomicOperationParameters(atomicOperationId, r.getInt(COL_ATOMIC_OPERATION_INDEX), r.getInt(COL_ATOMIC_OPERATION_TOTAL));
                }

                String child = r.getString(COL_CHILD_OID);
                if (child != null) {
                    int objectTypeId = r.getInt(COL_CHILD_OBJECT_TYPE);
                    Preconditions.checkState(objectTypeId != 0, "invalid object type for child %s", child);
                    transform.setChildParameters(child, ObjectType.fromTypeId(objectTypeId), r.getString(COL_CHILD_NAME));
                }

                return transform;
            } catch (IllegalArgumentException e) {
                throw new SQLException("invalid stored type", e);
            }
        }
    }
}
