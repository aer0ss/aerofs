package com.aerofs.polaris.dao;

import com.aerofs.polaris.api.InvalidTypeException;
import com.aerofs.polaris.api.LogicalObject;
import com.aerofs.polaris.api.ObjectType;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;

@RegisterMapper(LogicalObjects.LogicalObjectMapper.class)
public interface LogicalObjects {

    @SqlUpdate("insert into objects(root_oid, oid, version) values(:root_oid, :oid, :version)")
    int add(@Bind("root_oid") String root, @Bind("oid") String oid, @Bind("version") long version);

    @SqlUpdate("update objects set root_oid = :root_oid, version = :version where oid = :oid")
    int update(@Bind("root_oid") String root, @Bind("oid") String oid, @Bind("version") long version);

    @Nullable
    @SqlQuery("select root_oid, oid, version from objects where oid = :oid")
    LogicalObject get(@Bind("oid") String oid);

    @Nullable
    @SqlQuery("select root_oid, oid, version from objects where oid = :oid and version = :version")
    LogicalObject get(@Bind("oid") String oid, @Bind("version") long version);

    @SqlQuery("select root_oid, oid, version from objects where root_oid = :root_oid")
    Iterator<LogicalObject> getChildren(@Bind("root_oid") String root);

    @SuppressWarnings("unused")
    void close();

    public static final class LogicalObjectMapper implements ResultSetMapper<LogicalObject> {

        @Override
        @Nullable
        public LogicalObject map(int index, ResultSet r, StatementContext ctx) throws SQLException {
            return new LogicalObject(r.getString(1), r.getString(2), r.getLong(3));
        }
    }
}