package com.aerofs.daemon.core.activity;

import com.aerofs.daemon.core.IVersionUpdater;
import com.aerofs.daemon.core.polaris.fetch.ChangeFetcher;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.IDirectoryServiceListener;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.IActivityLogDatabase;
import com.aerofs.daemon.lib.db.IActivityLogDatabase.ActivityRow;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransLocal;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static com.aerofs.proto.Ritual.GetActivitiesReply.ActivityType.CREATION_VALUE;
import static com.aerofs.proto.Ritual.GetActivitiesReply.ActivityType.DELETION_VALUE;
import static com.aerofs.proto.Ritual.GetActivitiesReply.ActivityType.MODIFICATION_VALUE;
import static com.aerofs.proto.Ritual.GetActivitiesReply.ActivityType.MOVEMENT_VALUE;
import static com.aerofs.proto.Ritual.GetActivitiesReply.ActivityType.OUTBOUND_VALUE;
import static com.google.common.base.Preconditions.checkState;

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
public class ActivityLog implements IDirectoryServiceListener,
        IVersionUpdater.IListener, ChangeFetcher.Listener
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
                        @Override
                        public void committing_(Trans t) throws SQLException
                        {
                            ActivityLog.this.committing_(map, t);
                        }
                    });

                    return map;
                }
            };

    private final CfgLocalDID _localDID;
    private final IActivityLogDatabase _aldb;

    @Inject
    public ActivityLog(DirectoryService ds, IActivityLogDatabase aldb, CfgLocalDID localDID,
                       IVersionUpdater vu, ChangeFetcher cf)
    {
        ds.addListener_(this);
        _aldb = aldb;
        _localDID = localDID;

        vu.addListener_(this);
        cf.addListener_(this);
    }

    public static boolean isOutbound(ActivityRow ar)
    {
        return (ar._type & OUTBOUND_VALUE) != 0;
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
        ActivityEntry en = getEntry_(soid, t);
        en._type |= type;

        // don't change the initial path once set. this is needed to work with multiple movements
        // within a single transaction.
        if (en._path == null) en._path = path;

        return en;
    }

    /**
     * Multiple core threads can be running concurrently by releasing the core lock tmeporarily.
     *
     * We want to automatically associate a mobile DID on behalf of which a request is made so
     * that all transaction can be tagged with that DID to preserve the audit trail without
     * requiring all code starting a transaction to be aware of the mobile DID from which the
     * request may have originated.
     *
     * Hence the use of a thread local. See {@link com.aerofs.daemon.rest.handler.AbstractRestHdIMC}
     * for the actual usage pattern.
     */
    private final static ThreadLocal<DID> _tlOrigin = new ThreadLocal<>();

    /**
     * When doing a modification on behalf of an REST API client we want to tag activity log entries
     * with the mobile DID / app ID associated with the OAuth token authorizing the operation
     *
     * NB: this assumes that the request executes in a single core thread.
     */
    public static void onBehalfOf(DID did)
    {
        checkState((_tlOrigin.get() == null) != (did == null));
        _tlOrigin.set(did);
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
        ActivityEntry en = setEntryFields_(soid, DELETION_VALUE, path, t);
        en._type &= ~MOVEMENT_VALUE;
        en._pathTo = null;
    }

    @Override
    public void objectObliterated_(OA oa, Trans t) throws SQLException
    {
        SOID soid = oa.soid();
        Map<SOID, ActivityEntry> map = _tlMap.get(t);
        ActivityEntry en = map.get(soid);
        if (en != null && en._type == CREATION_VALUE) {
            // temporary object created by aliasing, only live within transaction context
            map.remove(soid);
        }
    }

    @Override
    public void objectContentCreated_(SOKID sokid, Path path, Trans t) throws SQLException
    {
        // TODO(huguesb): handle conflict branches properly in activity log
        // NOTE: ww says it's not worth the engineering effort at this time (11/12)
        setEntryFields_(sokid.soid(), MODIFICATION_VALUE, path, t);
    }

    @Override
    public void objectContentModified_(SOKID sokid, Path path, Trans t) throws SQLException
    {
        // TODO(huguesb): handle conflict branches properly in activity log
        // NOTE: ww says it's not worth the engineering effort at this time (11/12)
        setEntryFields_(sokid.soid(), MODIFICATION_VALUE, path, t);
    }

    @Override
    public void objectContentDeleted_(SOKID sokid, Trans t) throws SQLException
    {
    }

    @Override
    public void updated_(SOCID socid, Trans t) {
        updated_(socid.soid(), _localDID.get(), t);
    }

    @Override
    public void updated_(SOID soid, DID did, Trans t) {
        ActivityEntry en = getEntry_(soid, t);
        en._dids.add(did);
    }

    private void committing_(Map<SOID, ActivityEntry> map, Trans t) throws SQLException
    {
        DID origin = _tlOrigin.get();

        for (Entry<SOID, ActivityEntry> en : map.entrySet()) {
            ActivityEntry ae = en.getValue();

            // add to the activity log only if the entry has both activities and contributing dids.
            // see class-level comment for detail.
            if (ae._type == 0 || ae._dids.isEmpty()) continue;

            // the assertion may fail if placed _before_ the above test
            assert ae._path != null;

            // if the trans was made on behalf of a mobile device, at the DID to the set
            if (origin != null) {
                // TODO: check that the set of DIDs only contain the local device?
                ae._dids.add(origin);
            }

            // remove the movement activity if the path is not changed. this may happen if, say,
            // the object is moved to a new location and back to the old one during the transaction.
            if (ae._path.equals(ae._pathTo)) {
                ae._type &= ~MOVEMENT_VALUE;
                if (ae._type == 0) continue;
                ae._pathTo = null;
            }

            _aldb.insertActivity_(en.getKey(), ae._type, ae._path, ae._pathTo, ae._dids, t);
        }
    }

    /**
     * See the method of the same name in IActivityLogDatabase for detail.
     */
    public IDBIterator<ActivityRow> getActivites_(long idxLast) throws SQLException
    {
        return _aldb.getActivities_(idxLast);
    }

    /**
     * See the method of the same name in IActivityLogDatabase for detail.
     */
    public IDBIterator<ActivityRow> getActivitesAfterIndex_(long idxStart) throws SQLException
    {
        return _aldb.getActivitiesAfterIndex_(idxStart);
    }
}
