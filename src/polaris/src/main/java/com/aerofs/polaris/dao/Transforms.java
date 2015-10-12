package com.aerofs.polaris.dao;

import com.aerofs.ids.DID;
import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.api.types.ObjectType;
import com.aerofs.polaris.api.types.Transform;
import com.aerofs.polaris.api.types.TransformType;
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
    @SqlUpdate("insert into transforms(atomic_operation_id, atomic_operation_index, atomic_operation_total, originator, store_oid, oid, transform_type, new_version, child_oid, child_name, migrant_oid, timestamp) values(:atomic_operation_id, :atomic_operation_index, :atomic_operation_total, :originator, :store_oid, :oid, :transform_type, :new_version, :child_oid, :child_name, :migrant_oid, :timestamp)")
    long add(@Bind("originator") DID originator, @Bind("store_oid") UniqueID store, @Bind("oid") UniqueID oid, @Bind("transform_type") TransformType transformType, @Bind("new_version") long newVersion, @Bind("child_oid") @Nullable UniqueID child, @Bind("child_name") @Nullable byte[] name, @Bind("migrant_oid") @Nullable UniqueID migrant, @Bind("timestamp") long timestamp, @BindAtomic @Nullable Atomic atomic);

    @SqlQuery("select logical_timestamp, originator, store_oid, transforms.oid, transform_type, new_version, child_oid, object_type, child_name, atomic_operation_id, atomic_operation_index, atomic_operation_total, hash, size, mtime, migrant_oid, timestamp from transforms left join object_types on (transforms.child_oid = object_types.oid) left join file_properties on (transforms.oid = file_properties.oid and transforms.new_version = file_properties.version) where logical_timestamp > :logical_timestamp and store_oid = :store_oid order by logical_timestamp asc limit :limit")
    ResultIterator<Transform> getTransformsSince(@Bind("logical_timestamp") long since, @Bind("store_oid") UniqueID store, @Bind("limit") long count);

    // these two methods are needed because "child_oid is null" is the only way to enforce that a column is null in mysql
    @SqlQuery("select max(logical_timestamp) from transforms where store_oid = :store_oid and oid = :oid and child_oid = :child_oid")
    long getLatestMatchingTransformTimestamp(@Bind("store_oid") UniqueID store, @Bind("oid") UniqueID oid, @Bind("child_oid") UniqueID child);

    @SqlQuery("select max(logical_timestamp) from transforms where store_oid = :store_oid and oid = :oid and child_oid is null")
    long getLatestMatchingTransformTimestamp(@Bind("store_oid") UniqueID store, @Bind("oid") UniqueID oid);

    @SqlQuery("select max(logical_timestamp) from transforms")
    long getLatestLogicalTimestamp();

    @SuppressWarnings("unused")
    void close();

    final class TypedTransformMapper implements ResultSetMapper<Transform> {

        private static final int COL_LOGICAL_TIMESTAMP      = 1;
        private static final int COL_ORIGINATOR             = 2;
        private static final int COL_STORE_OID              = 3;
        private static final int COL_OID                    = 4;
        private static final int COL_TRANSFORM_TYPE         = 5;
        private static final int COL_NEW_VERSION            = 6;
        private static final int COL_CHILD_OID              = 7;
        private static final int COL_CHILD_OBJECT_TYPE      = 8;
        private static final int COL_CHILD_NAME             = 9;
        private static final int COL_ATOMIC_OPERATION_ID    = 10;
        private static final int COL_ATOMIC_OPERATION_INDEX = 11;
        private static final int COL_ATOMIC_OPERATION_TOTAL = 12;
        private static final int COL_CONTENT_HASH           = 13;
        private static final int COL_CONTENT_SIZE           = 14;
        private static final int COL_CONTENT_MTIME          = 15;
        private static final int COL_MIGRANT_OID            = 16;
        private static final int COL_TIMESTAMP              = 17;

        @Override
        public Transform map(int index, ResultSet r, StatementContext ctx) throws SQLException {
            try {
                long logicalTimestamp = r.getLong(COL_LOGICAL_TIMESTAMP);
                TransformType transformType = TransformType.fromTypeId(r.getInt(COL_TRANSFORM_TYPE));

                Transform transform = new Transform(
                        logicalTimestamp,
                        new DID(r.getBytes(COL_ORIGINATOR)),
                        new UniqueID(r.getBytes(COL_STORE_OID)),
                        new UniqueID(r.getBytes(COL_OID)),
                        transformType,
                        r.getLong(COL_NEW_VERSION),
                        r.getLong(COL_TIMESTAMP)
                );

                String atomicOperationId = r.getString(COL_ATOMIC_OPERATION_ID);
                if (atomicOperationId != null) {
                    transform.setAtomicOperationParameters(atomicOperationId, r.getInt(COL_ATOMIC_OPERATION_INDEX), r.getInt(COL_ATOMIC_OPERATION_TOTAL));
                }

                byte[] childBytes = r.getBytes(COL_CHILD_OID);
                if (childBytes!= null) {
                    UniqueID child = new UniqueID(childBytes);
                    if (transformType == TransformType.REMOVE_CHILD) {
                        transform.setChildParameters(child, null, null);
                    } else {
                        int objectTypeId = r.getInt(COL_CHILD_OBJECT_TYPE);
                        Preconditions.checkState(objectTypeId != 0, "invalid object type for child %s", child);
                        transform.setChildParameters(child, ObjectType.fromTypeId(objectTypeId), r.getBytes(COL_CHILD_NAME));
                    }
                }

                byte[] hash = r.getBytes(COL_CONTENT_HASH);
                if (hash != null) {
                    transform.setContentParameters(hash, r.getLong(COL_CONTENT_SIZE), r.getLong(COL_CONTENT_MTIME));
                }

                byte[] migrant = r.getBytes(COL_MIGRANT_OID);
                if (migrant != null) {
                    transform.setMigrantOid(new UniqueID(migrant));
                }

                return transform;
            } catch (IllegalArgumentException e) {
                throw new SQLException("invalid stored type", e);
            }
        }
    }
}
