package com.aerofs.polaris.dao;

import com.aerofs.ids.UniqueID;
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

@RegisterMapper(ObjectTypes.ObjectTypeMapper.class)
public interface ObjectTypes {

    @SqlUpdate("insert into object_types(oid, object_type) values(:oid, :object_type)")
    int add(@Bind("oid") UniqueID oid, @Bind("object_type") ObjectType objectType);

    @SqlUpdate("delete from object_types where oid = :oid")
    int delete(@Bind("oid") UniqueID oid);

    @Nullable
    @SqlQuery("select object_type from object_types where oid = :oid")
    ObjectType get(@Bind("oid") UniqueID oid);

    @RegisterMapper(OneColumnUniqueIDMapper.class)
    @SqlQuery("select oid from object_types where object_type = :object_type")
    ResultIterator<UniqueID> getByType(@Bind("object_type") ObjectType objectType);

    @SuppressWarnings("unused")
    void close();

    final class ObjectTypeMapper implements ResultSetMapper<ObjectType> {

        private static final int COL_OBJECT_TYPE = 1;

        @Override
        public ObjectType map(int index, ResultSet r, StatementContext ctx) throws SQLException {
            try {
                return ObjectType.fromTypeId(r.getInt(COL_OBJECT_TYPE));
            } catch (IllegalArgumentException e) {
                throw new SQLException("invalid stored type", e);
            }
        }
    }
}
