package com.aerofs.polaris.dao;

import com.aerofs.ids.UniqueID;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

public interface LogicalTimestamps {

    // see post by Michael Austin in http://dev.mysql.com/doc/refman/5.0/en/insert-on-duplicate.html
    @SqlUpdate("insert into store_max_logical_timestamp(store_oid, logical_timestamp) values(:store_oid, :logical_timestamp) on duplicate key update store_max_logical_timestamp.logical_timestamp = if(values(logical_timestamp) > store_max_logical_timestamp.logical_timestamp, values(logical_timestamp), store_max_logical_timestamp.logical_timestamp)")
    void updateLatest(@Bind("store_oid") UniqueID store, @Bind("logical_timestamp") long logicalTimestamp);

    @SqlQuery("select coalesce(sum(logical_timestamp), -1) from store_max_logical_timestamp where store_oid = :store_oid")
    long getLatest(@Bind("store_oid") UniqueID store);

    @SuppressWarnings("unused")
    void close();
}
