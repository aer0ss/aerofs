package com.aerofs.polaris.dao;

import com.aerofs.polaris.api.FileMetadata;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;

@RegisterMapper(Metadata.FileMetadataMapper.class)
public interface Metadata {

    @SqlUpdate("insert into metadata(oid, version, hash, size, mtime) values(:oid, :version, :hash, :size, :mtime)")
    int add(@Bind("oid") String oid, @Bind("version") long version, @Bind("hash") String hash, @Bind("size") long size, @Bind("mtime") long mtime);

    @SqlQuery("select oid, version, hash, size, mtime, from metadata where oid = :oid and version = :version")
    FileMetadata get(@Bind("oid") String oid, @Bind("version") long version);

    @SuppressWarnings("unused")
    void close();

    public final class FileMetadataMapper implements ResultSetMapper<FileMetadata> {

        private static final int COL_OID     = 1;
        private static final int COL_VERSION = 2;
        private static final int COL_HASH    = 3;
        private static final int COL_SIZE    = 4;
        private static final int COL_MTIME   = 5;

        @Override
        @Nullable
        public FileMetadata map(int index, ResultSet r, StatementContext ctx) throws SQLException {
            if (!r.next()) {
                return null;
            } else {
                return new FileMetadata(r.getString(COL_OID), r.getLong(COL_VERSION), r.getString(COL_HASH), r.getLong(COL_SIZE), r.getLong(COL_MTIME));
            }
        }
    }
}
