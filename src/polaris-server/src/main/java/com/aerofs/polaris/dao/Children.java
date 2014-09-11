package com.aerofs.polaris.dao;

import com.aerofs.polaris.api.Child;
import com.aerofs.polaris.api.ObjectType;
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

@RegisterMapper(Children.ChildMapper.class)
public interface Children {

    @SqlUpdate("insert into children(oid, child_oid, child_name) values(:oid, :child_oid, :child_name)")
    int add(@Bind("oid") String oid, @Bind("child_oid") String child, @Bind("child_name") String childName);

    @SqlUpdate("delete from children where oid = :oid and child_oid = :child_oid")
    int remove(@Bind("oid") String oid, @Bind("child_oid") String child);

    @SqlQuery("select count(child_oid) from children where child_oid = :child_oid")
    boolean isChild(@Bind("child_oid") String child);

    @SqlQuery("select count(child_oid) from children where oid = :oid and child_oid = :child_oid")
    boolean isChild(@Bind("oid") String oid, @Bind("child_oid") String child);

    @SqlQuery("select count(child_oid) from children where oid = :oid")
    int countChildren(@Bind("oid") String oid);

    @SqlQuery("select count(child_oid) from children where oid = :oid and child_name = :child_name")
    int countChildrenWithName(@Bind("oid") String oid, @Bind("child_name") String childName);

    @Nullable
    @SqlQuery("select child_name from children where oid = :oid and child_oid = :child_oid")
    String getChildName(@Bind("oid") String oid, @Bind("child_oid") String child);

    @SqlQuery("select child_oid, child_name, object_type from children inner join object_types on (children.child_oid = object_types.oid) where children.oid = :oid")
    ResultIterator<Child> listChildren(@Bind("oid") String oid);

    @SuppressWarnings("unused")
    void close();

    public static final class ChildMapper implements ResultSetMapper<Child> {

        private static final int COL_CHILD_OID   = 1;
        private static final int COL_CHILD_NAME  = 2;
        private static final int COL_OBJECT_TYPE = 3;

        @Override
        public Child map(int index, ResultSet r, StatementContext ctx) throws SQLException {
            int storedObjectType = r.getInt(COL_OBJECT_TYPE);
            try {
                return new Child(r.getString(COL_CHILD_OID), r.getString(COL_CHILD_NAME), ObjectType.fromTypeId(storedObjectType));
            } catch (IllegalArgumentException e) {
                throw new SQLException("invalid stored type", e);
            }
        }
    }
}
