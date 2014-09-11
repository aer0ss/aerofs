package com.aerofs.polaris.dao;

import com.aerofs.polaris.api.ObjectType;
import com.aerofs.polaris.api.Transform;
import com.aerofs.polaris.api.TransformType;
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

@RegisterMapper(Transforms.TypedTransformMapper.class)
public interface Transforms {

    @SqlUpdate("insert into transforms(root_oid, oid, transform_type, new_version, child_oid, child_name) values(:root_oid, :oid, :transform_type, :new_version, :child_oid, :child_name)")
    int add(@Bind("root_oid") String root, @Bind("oid") String oid, @Bind("transform_type") TransformType transformType, @Bind("new_version") long newVersion, @Bind("child_oid") @Nullable String child, @Bind("child_name") @Nullable String name);

    @SqlQuery("select logical_timestamp, root_oid, transforms.oid, object_type, transform_type, new_version, child_oid, child_name from transforms inner join object_types on (transforms.oid = object_types.oid) where logical_timestamp > :logical_timestamp and root_oid = :root_oid")
    ResultIterator<Transform> getTransformsSince(@Bind("logical_timestamp") long since, @Bind("root_oid") String root);

    @SuppressWarnings("unused")
    void close();

    public final class TypedTransformMapper implements ResultSetMapper<Transform> {

        private static final int COL_LOGICAL_TIMESTAMP = 1;
        private static final int COL_ROOT_OID          = 2;
        private static final int COL_OID               = 3;
        private static final int COL_OBJECT_ID         = 4;
        private static final int COL_TRANSFORM_TYPE    = 5;
        private static final int COL_NEW_VERSION       = 6;
        private static final int COL_CHILD_OID         = 7;
        private static final int COL_CHILD_NAME        = 8;

        @Override
        public Transform map(int index, ResultSet r, StatementContext ctx) throws SQLException {
            int storedObjectId = r.getInt(COL_OBJECT_ID);
            int storedTypeId = r.getInt(COL_TRANSFORM_TYPE);
            try {
                return new Transform(
                        r.getLong(COL_LOGICAL_TIMESTAMP),
                        r.getString(COL_ROOT_OID),
                        r.getString(COL_OID),
                        ObjectType.fromTypeId(storedObjectId),
                        TransformType.fromTypeId(storedTypeId),
                        r.getLong(COL_NEW_VERSION),
                        r.getString(COL_CHILD_OID),
                        r.getString(COL_CHILD_NAME));
            } catch (IllegalArgumentException e) {
                throw new SQLException("invalid stored type", e);
            }
        }
    }
}
