package com.aerofs.daemon.core;

import static com.aerofs.proto.Ritual.GetActivitiesReply.ActivityType.CREATION_VALUE;
import static com.aerofs.proto.Ritual.GetActivitiesReply.ActivityType.DELETION_VALUE;
import static com.aerofs.proto.Ritual.GetActivitiesReply.ActivityType.MODIFICATION_VALUE;
import static com.aerofs.proto.Ritual.GetActivitiesReply.ActivityType.MOVEMENT_VALUE;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Nullable;
import javax.inject.Inject;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.DirectoryService.IDirectoryServiceListener;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.IActivityLogDatabase;
import com.aerofs.daemon.lib.db.IActivityLogDatabase.ActivityRow;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransLocal;
import com.aerofs.lib.BitVector;
import com.aerofs.lib.Path;
import com.aerofs.lib.Version;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * This class records and retrieves local activity history. See GetActivities in Ritual API for
 * high-level usage.
 *
 * Implementation notes: each activity contains both types (creation, modification, etc) and
 * contributing devices. The former is obtained from DirectoryService operations, whereas the latter
 * is derived from NativeVersionControl operations. Since the two subsystems work independently,
 * we have to manually correlate their operations when generating activity logs. One way to achieve
 * this is to have the two subsystems to collaborate. But it's not worthwhile to make architectural
 * changes for such a peripheral feature.
 *
 * It is observed that the two subsystems use the same transaction to guarantee atomicity when
 * updating objects. Therefore, we use a TransLocal map to implement the correlation: the map's keys
 * are object IDs, and each value remembers activity types and contributing devices occurred for the
 * corresponding object during the transaction. At the end of the transaction, only objects with
 * _both_ types and devices are logged into the database.
 *
 * A down-side of this approach is potential heap overflow if a transaction touches lots of objects.
 * Fortunately, most transactions only deal with a small number of objects at a time. Transactions
 * by the scanner may be big, but its size is limited by ScanSession.CONTINUATION_UPDATES_THRESHOLD.
 */
public class ActivityLog implements IDirectoryServiceListener
{
    // the per-object entry for the trans-local map
    private static class ActivityEntry
    {
        // one or more ActivityTypes combined by OR
        int _type;

        // the path of the object
        Path _path;

        // the new path of the object, valid only when the object is moved
        @Nullable Path _pathTo;

        // the set of devices that contributes to the activity. it is derived from the object's
        // new local version. use tree sets as they are more space efficient than hash sets.
        final Set<DID> _dids = Sets.newTreeSet();
    }

    private final TransLocal<Map<SOID, ActivityEntry>> _tlMap =
            new TransLocal<Map<SOID, ActivityEntry>>() {
                @Override
                protected Map<SOID, ActivityEntry> initialValue(Trans t)
                {
                    final Map<SOID, ActivityEntry> map = Maps.newTreeMap();
                    t.addListener_(new AbstractTransListener() {
                        private boolean _activitiesAdded = false;
                        @Override
                        public void committing_(Trans t) throws SQLException
                        {
                            _activitiesAdded = ActivityLog.this.committing_(map, t);
                        }
                        @Override
                        public void committed_() {
                            // we can't start the scan before the transaction is committed
                            if (_activitiesAdded) {
                                for (IActivityLogListener listener : _listeners) {
                                    listener.activitiesAdded_();
                                }
                            }
                        }
                    });

                    return map;
                }
            };

    private IActivityLogDatabase _aldb;

    /**
     * Interface for ActivityLog listener
     */
    public interface IActivityLogListener
    {
        /**
         * Whenever a transaction adds new rows to the activity log table, this method will be
         * called from the commited_() callback of an ITransListener
         */
        void activitiesAdded_();
    }

    private final List<IActivityLogListener> _listeners = Lists.newArrayList();

    public void addListener_(IActivityLogListener listener)
    {
        _listeners.add(listener);
    }

    @Inject
    public ActivityLog(DirectoryService ds, IActivityLogDatabase aldb)
    {
        ds.addListener_(this);
        _aldb = aldb;
    }

    private ActivityEntry getEntry_(SOID soid, Trans t)
    {
        Map<SOID, ActivityEntry> map = _tlMap.get(t);
        ActivityEntry en = map.get(soid);
        if (en == null) {
            en = new ActivityEntry();
            map.put(soid, en);
        }
        return en;
    }

    private ActivityEntry setEntryFields_(SOID soid, int type, Path path, Trans t)
    {
        assert path != null;

        ActivityEntry en = getEntry_(soid, t);
        en._type |= type;

        // don't change the initial path once set. this is needed to work with multiple movements
        // within a single transaction.
        if (en._path == null) en._path = path;

        return en;
    }

    @Override
    public void objectCreated_(SOID soid, OID parent, Path path, Trans t) throws SQLException
    {
        setEntryFields_(soid, CREATION_VALUE, path, t);
    }

    @Override
    public void objectMoved_(SOID soid, OID parentFrom, OID parentTo,
            Path pathFrom, Path pathTo, Trans t) throws SQLException
    {
        ActivityEntry en = setEntryFields_(soid, MOVEMENT_VALUE, pathFrom, t);
        en._pathTo = pathTo;
    }

    @Override
    public void objectDeleted_(SOID soid, OID parent, Path path, Trans t) throws SQLException
    {
        setEntryFields_(soid, DELETION_VALUE, path, t);
    }

    @Override
    public void objectContentModified_(SOID soid, Path path, boolean firstBranchCreated, Trans t)
            throws SQLException
    {
        setEntryFields_(soid, MODIFICATION_VALUE, path, t);
    }

    @Override
    public void objectExpelled_(SOID soid, Trans t) throws SQLException
    {}

    @Override
    public void objectAdmitted_(SOID soid, Trans t) throws SQLException
    {}

    @Override
    public void objectSyncStatusChanged_(SOID obj, BitVector oldStatus, BitVector newStatus,
            Trans t) throws SQLException
    {}

    public void localVersionAdded_(SOID soid, Version vLocalAdded, Trans t)
    {
        ActivityEntry en = getEntry_(soid, t);
        en._dids.addAll(vLocalAdded.getAll_().keySet());
    }

    private boolean committing_(Map<SOID, ActivityEntry> map, Trans t) throws SQLException
    {
        int activitiesAdded = 0;
        for (Entry<SOID, ActivityEntry> en : map.entrySet()) {
            ActivityEntry ae = en.getValue();

            // add to the activity log only if the entry has both activities and contributing dids.
            // see class-level comment for detail.
            if (ae._type == 0 || ae._dids.isEmpty()) continue;

            // the assertion may fail if placed _before_ the above test
            assert ae._path != null;

            // remove the movement activity if the path is not changed. this may happen if, say,
            // the object is moved to a new location and back to the old one during the transaction.
            if (ae._path.equals(ae._pathTo)) {
                ae._type &= ~MOVEMENT_VALUE;
                if (ae._type == 0) continue;
                ae._pathTo = null;
            }

            _aldb.addActivity_(en.getKey(), ae._type, ae._path, ae._pathTo, ae._dids, t);
            ++activitiesAdded;
        }
        return activitiesAdded > 0;
    }

    /**
     * See the method of the same name in IActivityLogDatabase for detail.
     */
    public IDBIterator<ActivityRow> getActivites_(long idxLast) throws SQLException
    {
        return _aldb.getActivities_(idxLast);
    }
}
