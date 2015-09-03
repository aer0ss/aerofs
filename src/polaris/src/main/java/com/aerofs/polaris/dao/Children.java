package com.aerofs.polaris.dao;

import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.api.types.Child;
import com.aerofs.polaris.api.types.DeletableChild;
import com.aerofs.polaris.api.types.ObjectType;
import com.aerofs.polaris.dao.types.OneColumnUniqueIDMapper;
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

    @SqlUpdate("insert into children(parent_oid, child_oid, child_name) values(:parent_oid, :child_oid, :child_name)")
    int add(@Bind("parent_oid") UniqueID parent, @Bind("child_oid") UniqueID child, @Bind("child_name") byte[] childName);

    @SqlUpdate("insert into children(parent_oid, child_oid, child_name, deleted) values(:parent_oid, :child_oid, :child_name, :deleted)")
    int add(@Bind("parent_oid") UniqueID parent, @Bind("child_oid") UniqueID child, @Bind("child_name") byte[] childName, @Bind("deleted") boolean deleted);

    @SqlUpdate("update children set child_name = :child_name where parent_oid = :parent_oid and child_oid = :child_oid")
    void update(@Bind("parent_oid") UniqueID parent, @Bind("child_oid") UniqueID child, @Bind("child_name") byte[] newChildName);

    @SqlUpdate("delete from children where parent_oid = :parent_oid and child_oid = :child_oid")
    int remove(@Bind("parent_oid") UniqueID parent, @Bind("child_oid") UniqueID child);

    @SqlQuery("select count(child_oid) from children where child_oid = :child_oid and deleted = 0")
    int getActiveReferenceCount(@Bind("child_oid") UniqueID child);

    @SqlQuery("select count(child_oid) from children where parent_oid = :parent_oid and child_oid = :child_oid and deleted = 0")
    boolean isActiveChild(@Bind("parent_oid") UniqueID parent, @Bind("child_oid") UniqueID child);

    // this method should not be used to find parents of mount points, use MountPoints.java instead
    @Nullable
    @RegisterMapper(OneColumnUniqueIDMapper.class)
    @SqlQuery("select parent_oid from children where child_oid = :child_oid")
    UniqueID getParent(@Bind("child_oid") UniqueID child);

    @Nullable
    @RegisterMapper(OneColumnUniqueIDMapper.class)
    @SqlQuery("select child_oid from children where parent_oid = :parent_oid and child_name = :child_name and deleted = 0")
    UniqueID getActiveChildNamed(@Bind("parent_oid") UniqueID parent, @Bind("child_name") byte[] childName);

    @Nullable
    @SqlQuery("select child_name from children where parent_oid = :parent_oid and child_oid = :child_oid and deleted = 0")
    byte[] getActiveChildName(@Bind("parent_oid") UniqueID parent, @Bind("child_oid") UniqueID child);

    @Nullable
    @SqlQuery("select child_name from children where parent_oid = :parent_oid and child_oid = :child_oid")
    byte[] getChildName(@Bind("parent_oid") UniqueID parent, @Bind("child_oid") UniqueID child);

    @SqlQuery("select child_oid, child_name, object_type, deleted from children inner join object_types on (children.child_oid = object_types.oid) where children.parent_oid = :parent_oid")
    ResultIterator<DeletableChild> getChildren(@Bind("parent_oid") UniqueID parent);

    @SqlUpdate("update children set deleted = :deleted where child_oid = :child_oid")
    int setDeleted(@Bind("child_oid") UniqueID child, @Bind("deleted") boolean deleted);

    // this method should not be used when checking if a mount point has been deleted, as they're completely removed from the children table upon deletion
    @SqlQuery("select deleted from children where parent_oid = :parent_oid and child_oid = :child_oid")
    boolean isDeleted(@Bind("parent_oid") UniqueID parent, @Bind("child_oid") UniqueID child);

    @SuppressWarnings("unused")
    void close();

    final class ChildMapper implements ResultSetMapper<DeletableChild> {

        private static final int COL_CHILD_OID   = 1;
        private static final int COL_CHILD_NAME  = 2;
        private static final int COL_OBJECT_TYPE = 3;
        private static final int COL_DELETED     = 4;

        @Override
        public DeletableChild map(int index, ResultSet r, StatementContext ctx) throws SQLException {
            try {
                return new DeletableChild(new UniqueID(r.getBytes(COL_CHILD_OID)), ObjectType.fromTypeId(r.getInt(COL_OBJECT_TYPE)), r.getBytes(COL_CHILD_NAME), r.getBoolean(COL_DELETED));
            } catch (IllegalArgumentException e) {
                throw new SQLException("invalid stored type", e);
            }
        }
    }
}
