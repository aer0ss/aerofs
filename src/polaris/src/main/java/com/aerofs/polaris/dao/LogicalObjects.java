package com.aerofs.polaris.dao;

import com.aerofs.polaris.api.LogicalObject;
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

    @SqlUpdate("delete from objects where oid = :oid")
    int remove(@Bind("oid") String oid);

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

        private static final int COL_ROOT_OID = 1;
        private static final int COL_OID      = 2;
        private static final int COL_VERSION  = 3;

        @Override
        @Nullable
        public LogicalObject map(int index, ResultSet r, StatementContext ctx) throws SQLException {
            if (!r.next()) {
                return null;
            } else {
                return new LogicalObject(r.getString(COL_ROOT_OID), r.getString(COL_OID), r.getLong(COL_VERSION));
            }
        }
    }
}
