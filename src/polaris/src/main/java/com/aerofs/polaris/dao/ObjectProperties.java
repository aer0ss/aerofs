package com.aerofs.polaris.dao;

import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.api.types.Content;
import com.aerofs.polaris.resources.SharedFolderStatsResource;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.Mapper;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;

@RegisterMapper(ObjectProperties.ContentMapper.class)
public interface ObjectProperties {

    @SqlUpdate("insert into file_properties(oid, version, hash, size, mtime) values(:oid, :version, :hash, :size, :mtime)")
    int add(@Bind("oid") UniqueID oid, @Bind("version") long version, @Bind("hash") byte[] hash, @Bind("size") long size, @Bind("mtime") long mtime);

    @Nullable
    @SqlQuery("select oid, version, hash, size, mtime, from file_properties where oid = :oid and version = :version")
    Content get(@Bind("oid") UniqueID oid, @Bind("version") long version);

    @Nullable
    @SqlQuery("select oid, version, hash, size, mtime from file_properties where oid = :oid order by version desc limit 1")
    Content getLatest(@Bind("oid") UniqueID oid);

    @SqlQuery("select sum(size) from file_properties f inner join children c on c.child_oid=f.oid where ((oid,version) " +
            "in (select oid, max(version) from file_properties group by oid) and deleted=0)")
    long getTotalSizeOfFilesInOrg();

    @SqlQuery("select max(sub.count) maxFileSize, avg(sub.count) avgFileSize from (select count(*) count from " +
            "objects o inner join children c on o.oid = c.child_oid inner join object_types t on o.oid = t.oid where " +
            "t.object_type=2 and c.deleted=0 and substring(hex(o.store_oid), 13, 1) = '0' group by o.store_oid) sub")
    @Mapper(ObjectProperties.SharedFolderStatsMapper.class)
    SharedFolderStatsResource getSharedFoldersStats();

    @SuppressWarnings("unused")
    void close();

    final class ContentMapper implements ResultSetMapper<Content> {

        private static final int COL_OID     = 1;
        private static final int COL_VERSION = 2;
        private static final int COL_HASH    = 3;
        private static final int COL_SIZE    = 4;
        private static final int COL_MTIME   = 5;

        @Override
        public Content map(int index, ResultSet r, StatementContext ctx) throws SQLException {
            return new Content(new UniqueID(r.getBytes(COL_OID)), r.getLong(COL_VERSION), r.getBytes(COL_HASH), r.getLong(COL_SIZE), r.getLong(COL_MTIME));
        }
    }

    final class SharedFolderStatsMapper implements ResultSetMapper<SharedFolderStatsResource> {

        private static final int COL_MAX_FILE_COUNT  = 1;
        private static final int COL_AVG_FILE_COUNT  = 2;

        @Override
        public SharedFolderStatsResource map(int index, ResultSet r, StatementContext ctx) throws SQLException {
            return new SharedFolderStatsResource(r.getLong(COL_MAX_FILE_COUNT), r.getLong(COL_AVG_FILE_COUNT));
        }
    }
}
