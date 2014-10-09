package com.aerofs.polaris.dao;

import com.aerofs.polaris.api.types.LogicalObject;
import com.aerofs.polaris.api.types.ObjectType;
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

@RegisterMapper(LogicalObjects.LogicalObjectMapper.class)
public interface LogicalObjects {

    @SqlUpdate("insert into objects(root_oid, oid, version) values(:root_oid, :oid, :version)")
    int add(@Bind("root_oid") String root, @Bind("oid") String oid, @Bind("version") long version);

    @SqlUpdate("update objects set root_oid = :root_oid, version = :version where oid = :oid")
    int update(@Bind("root_oid") String root, @Bind("oid") String oid, @Bind("version") long version);

    @SqlUpdate("delete from objects where oid = :oid")
    int remove(@Bind("oid") String oid);

    @Nullable
    @SqlQuery("select root_oid, objects.oid, version, object_type from objects inner join object_types on (objects.oid = object_types.oid) where objects.oid = :oid")
    LogicalObject get(@Bind("oid") String oid);

    @Nullable
    @SqlQuery("select root_oid, objects.oid, version, object_type from objects inner join object_types on (objects.oid = object_types.oid) where objects.oid = :oid and version = :version")
    LogicalObject get(@Bind("oid") String oid, @Bind("version") long version);

    @SqlQuery("select root_oid, objects.oid, version, object_type from objects inner join object_types on (objects.oid = object_types.oid) where root_oid = :root_oid")
    ResultIterator<LogicalObject> getChildren(@Bind("root_oid") String root);

    @SuppressWarnings("unused")
    void close();

    public static final class LogicalObjectMapper implements ResultSetMapper<LogicalObject> {

        private static final int COL_ROOT_OID    = 1;
        private static final int COL_OID         = 2;
        private static final int COL_VERSION     = 3;
        private static final int COL_OBJECT_TYPE = 4;

        @Override
        public LogicalObject map(int index, ResultSet r, StatementContext ctx) throws SQLException {
            try {
                return new LogicalObject(r.getString(COL_ROOT_OID), r.getString(COL_OID), r.getLong(COL_VERSION), ObjectType.fromTypeId(r.getInt(COL_OBJECT_TYPE)));
            } catch (IllegalArgumentException e) {
                throw new SQLException("invalid stored type", e);
            }
        }
    }
}
