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

    @SqlQuery("select logical_timestamp, root_oid, transforms.oid, transform_type, new_version, child_oid, object_type, child_name from transforms left join object_types on (transforms.child_oid = object_types.oid) where logical_timestamp > :logical_timestamp and root_oid = :root_oid order by logical_timestamp asc")
    ResultIterator<Transform> getTransformsSince(@Bind("logical_timestamp") long since, @Bind("root_oid") String root);

    @SuppressWarnings("unused")
    void close();

    public final class TypedTransformMapper implements ResultSetMapper<Transform> {

        private static final int COL_LOGICAL_TIMESTAMP = 1;
        private static final int COL_ROOT_OID          = 2;
        private static final int COL_OID               = 3;
        private static final int COL_TRANSFORM_TYPE    = 4;
        private static final int COL_NEW_VERSION       = 5;
        private static final int COL_CHILD_OID         = 6;
        private static final int COL_CHILD_OBJECT_TYPE = 7;
        private static final int COL_CHILD_NAME        = 8;

        @Override
        public Transform map(int index, ResultSet r, StatementContext ctx) throws SQLException {
            try {
                // alternatively, do http://stackoverflow.com/questions/2920364/checking-for-a-null-int-value-from-a-java-resultset
                ObjectType childObjectType = (r.getInt(COL_CHILD_OBJECT_TYPE) == 0 ? null : ObjectType.fromTypeId(r.getInt(COL_CHILD_OBJECT_TYPE)));
                TransformType transformType = TransformType.fromTypeId(r.getInt(COL_TRANSFORM_TYPE));

                return new Transform(
                        r.getLong(COL_LOGICAL_TIMESTAMP),
                        r.getString(COL_ROOT_OID),
                        r.getString(COL_OID),
                        transformType,
                        r.getLong(COL_NEW_VERSION),
                        r.getString(COL_CHILD_OID),
                        childObjectType,
                        r.getString(COL_CHILD_NAME));
            } catch (IllegalArgumentException e) {
                throw new SQLException("invalid stored type", e);
            }
        }
    }
}
