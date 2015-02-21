package com.aerofs.polaris.dao;

import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.api.types.LogicalObject;
import com.aerofs.polaris.api.types.ObjectType;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;

@RegisterMapper(LogicalObjects.LogicalObjectMapper.class)
public interface LogicalObjects {

    @SqlUpdate("insert into objects(root_oid, oid, version) values(:root_oid, :oid, :version)")
    int add(@Bind("root_oid") UniqueID root, @Bind("oid") UniqueID oid, @Bind("version") long version);

    @SqlUpdate("update objects set root_oid = :root_oid, version = :version where oid = :oid")
    int update(@Bind("root_oid") UniqueID root, @Bind("oid") UniqueID oid, @Bind("version") long version);

    @Nullable
    @SqlQuery("select root_oid, objects.oid, version, object_type from objects inner join object_types on (objects.oid = object_types.oid) where objects.oid = :oid")
    LogicalObject get(@Bind("oid") UniqueID oid);

    @Nullable
    @SqlQuery("select root_oid, objects.oid, version, object_type from objects inner join object_types on (objects.oid = object_types.oid) where objects.oid = :oid and version = :version")
    LogicalObject get(@Bind("oid") UniqueID oid, @Bind("version") long version);

    @SuppressWarnings("unused")
    void close();

    final class LogicalObjectMapper implements ResultSetMapper<LogicalObject> {

        private static final int COL_ROOT_OID    = 1;
        private static final int COL_OID         = 2;
        private static final int COL_VERSION     = 3;
        private static final int COL_OBJECT_TYPE = 4;

        @Override
        public LogicalObject map(int index, ResultSet r, StatementContext ctx) throws SQLException {
            try {
                return new LogicalObject(new UniqueID(r.getBytes(COL_ROOT_OID)), new UniqueID(r.getBytes(COL_OID)), r.getLong(COL_VERSION), ObjectType.fromTypeId(r.getInt(COL_OBJECT_TYPE)));
            } catch (IllegalArgumentException e) {
                throw new SQLException("invalid stored type", e);
            }
        }
    }
}
