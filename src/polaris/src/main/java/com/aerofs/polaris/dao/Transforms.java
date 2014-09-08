package com.aerofs.polaris.dao;

import com.aerofs.polaris.api.InvalidTypeException;
import com.aerofs.polaris.api.Transform;
import com.aerofs.polaris.api.TransformType;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
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

    @SuppressWarnings("unused")
    void close();

    final class TransformMapper implements ResultSetMapper<Transform> {

        @Override
        @Nullable
        public Transform map(int index, ResultSet r, StatementContext ctx) throws SQLException {
            int storedTypeId = r.getInt(6);
            try {
                return new Transform(r.getLong(1), r.getString(2), r.getString(3), TransformType.fromTypeId(r.getInt(5)), r.getLong(4), r.getString(6), r.getString(7));
            } catch (InvalidTypeException e) {
                throw new SQLException("fail convert " + storedTypeId + " into TransformType");
            }
        }
    }
}
