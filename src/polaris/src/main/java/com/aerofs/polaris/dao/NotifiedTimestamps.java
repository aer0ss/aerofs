package com.aerofs.polaris.dao;

import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.api.types.Timestamps;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

@RegisterMapper(NotifiedTimestamps.TimestampsMapper.class)
public interface NotifiedTimestamps {

    @SqlUpdate("insert into store_notified_logical_timestamp(store_oid, logical_timestamp) values(:store_oid, :logical_timestamp) on duplicate key update store_notified_logical_timestamp.logical_timestamp = if(values(logical_timestamp) > store_notified_logical_timestamp.logical_timestamp, values(logical_timestamp), store_notified_logical_timestamp.logical_timestamp)")
    void updateLatest(@Bind("store_oid") UniqueID store, @Bind("logical_timestamp") long logicalTimestamp);

    @SqlQuery("select coalesce(sum(logical_timestamp), -1) from store_notified_logical_timestamp where store_oid = :store_oid")
    long getLatest(@Bind("store_oid") UniqueID store);

    @SqlQuery("select actual.store_oid, actual.logical_timestamp, coalesce(notify.logical_timestamp, -1) from store_max_logical_timestamp as actual left join store_notified_logical_timestamp as notify on actual.store_oid = notify.store_oid where actual.store_oid = :store_oid")
    Timestamps getActualAndNotifiedTimestamps(@Bind("store_oid") UniqueID store);

    @SuppressWarnings("unused")
    void close();

    final class TimestampsMapper implements ResultSetMapper<Timestamps> {

        private static final int COL_STORE_OID          = 1;
        private static final int COL_ACTUAL_TIMESTAMP   = 2;
        private static final int COL_NOTIFIED_TIMESTAMP = 3;

        @Override
        public Timestamps map(int index, ResultSet r, StatementContext ctx) throws SQLException {
            try {
                return new Timestamps(new UniqueID(r.getBytes(COL_STORE_OID)), r.getLong(COL_ACTUAL_TIMESTAMP), r.getLong(COL_NOTIFIED_TIMESTAMP));
            } catch (Exception e) {
                throw new SQLException("invalid stored type", e);
            }
        }
    }
}
