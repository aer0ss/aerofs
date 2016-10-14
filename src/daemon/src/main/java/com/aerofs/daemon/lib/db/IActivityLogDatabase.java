/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.lib.db;

import com.aerofs.ids.DID;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.SOID;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.Set;

/**
 * This database records past syncing activities. When possible, use the ActivityLog class which
 * provides a high-level wrapper for this interface.
 */
public interface IActivityLogDatabase
{
    /**
     * @param activites one or more ActivityTypes values combined by OR
     * @param path the path of the object
     * @param pathTo the new path of the object, valid iff activities include movement
     * @param dids the set of devices that contributes to the activity
     */
    void insertActivity_(SOID soid, int activites, Path path, @Nullable Path pathTo, Set<DID> dids,
            Trans t) throws SQLException;


    public static class ActivityRow
    {
        // the index of the row in the database. used for paging
        public final long _idx;

        // the identifier of the subject of the activity
        public final SOID _soid;

        // one or more ActivityTypes combined by OR
        public final int _type;

        // the path of the object
        public final Path _path;

        // the new path of the object, valid iff activities include movement
        public final @Nullable Path _pathTo;

        // the set of devices that contributes to the activity
        public final Set<DID> _dids;

        // the time when the activity was logged
        public final long _time;

        public ActivityRow(long idx, SOID soid, int type, Path path, Path pathTo, Set<DID> dids, long time)
        {
            _idx = idx;
            _soid = soid;
            _type = type;
            _path = path;
            _pathTo = pathTo;
            _dids = dids;
            _time = time;
        }
    }

    /**
     * Return all the activities with indices smaller than {@code idxLast}.
     * Activities are sorted in descending order.
     * @param idxLast set to Long.MAX_VALUE to return all the activities
     */
    IDBIterator<ActivityRow> getActivities_(long idxLast) throws SQLException;

    /**
     * Return all the activities with indices greater than {@code idxStart}.
     * Activities are sorted in ascending order.
     * @param idxStart positive integer after which to return activity log rows;
     * set to 0 to return all the activities
     */
    IDBIterator<ActivityRow> getActivitiesAfterIndex_(long idxStart)
                    throws SQLException;
}
