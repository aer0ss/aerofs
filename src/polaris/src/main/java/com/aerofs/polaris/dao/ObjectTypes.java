package com.aerofs.polaris.dao;

import com.aerofs.polaris.api.InvalidTypeException;
import com.aerofs.polaris.api.ObjectType;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

@RegisterMapper(ObjectTypes.ObjectTypeMapper.class)
public interface ObjectTypes {

    @SqlUpdate("insert into object_types(oid, object_type) values(:oid, :object_type)")
    int add(@Bind("oid") String oid, @Bind("object_type") ObjectType objectType);

    @SqlQuery("select object_type from object_types where oid = :oid")
    ObjectType get(@Bind("oid") String oid);

    @SuppressWarnings("unused")
    void close();

    public static final class ObjectTypeMapper implements ResultSetMapper<ObjectType>{

        @Override
        public ObjectType map(int index, ResultSet r, StatementContext ctx) throws SQLException {
            int typeId = r.getInt(1);

            try {
                return ObjectType.fromTypeId(typeId);
            } catch (InvalidTypeException e) {
                throw new SQLException("cannot convert " + typeId + " into ObjectType");
            }
        }
    }
}
