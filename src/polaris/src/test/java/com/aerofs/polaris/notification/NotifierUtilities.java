package com.aerofs.polaris.notification;

import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.api.PolarisUtilities;
import com.aerofs.polaris.dao.LogicalTimestamps;
import com.aerofs.polaris.dao.NotifiedTimestamps;
import org.skife.jdbi.v2.DBI;

abstract class NotifierUtilities {

    static String getVerkehrUpdateTopic(UniqueID store) {
        return PolarisUtilities.getVerkehrUpdateTopic(store.toStringFormal());
    }

    static void setLatestLogicalTimestamp(DBI dbi, UniqueID store, long timestamp) {
        dbi.inTransaction((conn, status) -> {
            LogicalTimestamps timestamps = conn.attach(LogicalTimestamps.class);
            timestamps.updateLatest(store, timestamp);
            return null;
        });
    }

    static long getLatestLogicalTimestamp(DBI dbi, UniqueID store) {
        return dbi.inTransaction((conn, status) -> {
            LogicalTimestamps timestamps = conn.attach(LogicalTimestamps.class);
            return timestamps.getLatest(store);
        });
    }

    static long getLatestNotifiedLogicalTimestamp(DBI dbi, UniqueID store) {
        return dbi.inTransaction((conn, status) -> {
            NotifiedTimestamps timestamps = conn.attach(NotifiedTimestamps.class);
            return timestamps.getLatest(store);
        });
    }

    public NotifierUtilities() {
        // private to prevent instantiation by subclasses
    }
}
