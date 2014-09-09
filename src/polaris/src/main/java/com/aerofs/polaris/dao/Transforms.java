package com.aerofs.polaris.dao;

import com.aerofs.polaris.api.InvalidTypeException;
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

@RegisterMapper(Transforms.TransformMapper.class)
public interface Transforms {

    @SqlUpdate("insert into transforms(root_oid, oid, transform_type, new_version, child_oid, child_name) values(:root_oid, :oid, :transform_type, :new_version, :child_oid, :child_name)")
    int add(@Bind("root_oid") String root, @Bind("oid") String oid, @Bind("transform_type") TransformType transformType, @Bind("new_version") long newVersion, @Bind("child_oid") @Nullable String child, @Bind("child_name") @Nullable String name);

    @SqlQuery("select change_id, root_oid, oid, transform_type, new_version, child_oid, child_name from transforms where change_id > :change_id and root_oid = :root_oid")
    ResultIterator<Transform> getTransformsSince(@Bind("change_id") long since, @Bind("root_oid") String root);

    @SuppressWarnings("unused")
    void close();

    public final class TransformMapper implements ResultSetMapper<Transform> {

        private static final int COL_CHANGE_ID      = 1;
        private static final int COL_ROOT_OID       = 2;
        private static final int COL_OID            = 3;
        private static final int COL_TRANSFORM_TYPE = 4;
        private static final int COL_NEW_VERSION    = 5;
        private static final int COL_CHILD_OID      = 6;
        private static final int COL_CHILD_NAME     = 7;

        @Override
        @Nullable
        public Transform map(int index, ResultSet r, StatementContext ctx) throws SQLException {
            if (!r.next()) {
                return null;
            }

            int storedTypeId = r.getInt(COL_TRANSFORM_TYPE);
            try {
                return new Transform(r.getLong(COL_CHANGE_ID), r.getString(COL_ROOT_OID), r.getString(COL_OID), TransformType.fromTypeId(storedTypeId), r.getLong(COL_NEW_VERSION), r.getString(COL_CHILD_OID), r.getString(COL_CHILD_NAME));
            } catch (InvalidTypeException e) {
                throw new SQLException("fail convert " + storedTypeId + " into TransformType");
            }
        }
    }
}
