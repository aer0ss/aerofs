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

    final class FileMetadataMapper implements ResultSetMapper<FileMetadata> {

        @Override
        @Nullable
        public FileMetadata map(int index, ResultSet r, StatementContext ctx) throws SQLException {
            if (!r.next()) {
                return null;
            } else {
                return new FileMetadata(r.getString(1), r.getLong(2), r.getString(3), r.getLong(4), r.getLong(5));
            }
        }
    }
}