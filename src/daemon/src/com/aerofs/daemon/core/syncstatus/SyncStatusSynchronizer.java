package com.aerofs.daemon.core.syncstatus;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.NativeVersionControl.IVersionControlListener;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.DirectoryService.AbstractDirectoryServiceListener;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.persistency.PersistentQueueDriver;
import com.aerofs.daemon.core.persistency.IPersistentQueue;
import com.aerofs.daemon.core.store.DeviceBitMap;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.MapSIndex2DeviceBitMap;
import com.aerofs.daemon.core.syncstatus.SyncStatusConnection.ExSignIn;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.ExpRetryScheduler;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.ISyncStatusDatabase;
import com.aerofs.daemon.lib.db.ISyncStatusDatabase.ModifiedObject;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransLocal;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.BitVector;
import com.aerofs.lib.Path;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Tick;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.proto.SpNotifications.PBSyncStatNotification;
import com.aerofs.proto.SyncStatus.GetSyncStatusReply;
import com.aerofs.proto.SyncStatus.GetSyncStatusReply.DeviceSyncStatus;
import com.aerofs.proto.SyncStatus.GetSyncStatusReply.DevicesSyncStatus;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;

import java.io.IOException;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.Callable;

/**
 * This class keeps local sync status information in sync with the central server.
 *
 * There are four main aspects to the sync status synchronizer logic:
 *
 *  1) push: Whenever a new changes happen locally (NativeVersionControl listener) the synchronizer
 *  will add modified SOIDs to the push queue and schedule a scan of that table. The scan will
 *  compute new version hashes for objects in the push queue and send them to the server (which will
 *  result in a new epoch being received from verkher soon after). After successful push of a
 *  version hash, the local push epoch is updated. This epoch is basically the index of the last row
 *  of the push queue table successfully pushed.
 *
 *  2) pull: Whenever a new pull epoch is received through {@link SyncStatusNotificationSubscriber}
 *  the synchronizer compares it to the last known pull epoch and initiates pull from the server if
 *  needed. The pull epoch is assigned by the syncstatus server at device granularity and increases
 *  with every change made that could result in an out-of-sync state between two devices.
 *  For performance reasons, the notification allows a "fast-forward" : the last updated sync status
 *  can be included in the notification, allowing the client to update its DB without an extra round
 *  trip if the epoch difference is exactly one.
 *
 *  3) startup: The startup phase is responsible for flushing any local information not yet
 *  transmitted to the sync status server and pulling the server for new information (to account
 *  for dropped verkher notifications).
 *
 *  4) epoch rollback: On successful connection, the syncstat server tell the client the epoch of
 *  the last push it received. This is used to recover from server-side data loss. When this epoch
 *  is smaller than the client's own push epoch, a rollback is required to re-send the version hash
 *  lost by the server. Because the push queue is regularly truncated to avoid unbounded growth, in
 *  some rare cases it is possible that the epoch cannot be rolled back far enough and a full
 *  bootstrap is necessary to ensure all lost version hashes are re-sent
 */
public class SyncStatusSynchronizer extends AbstractDirectoryServiceListener
        implements IVersionControlListener
{
    private static final Logger l = Loggers.getLogger(SyncStatusSynchronizer.class);

    private final TransManager _tm;
    private final ISyncStatusDatabase _ssdb;
    private final SyncStatusConnection _ssc;
    private final NativeVersionControl _nvc;
    private final IMapSIndex2SID _sidx2sid;
    private final IMapSID2SIndex _sid2sidx;
    private final MapSIndex2DeviceBitMap _sidx2dbm;
    private final DirectoryService _ds;
    private final ExpRetryScheduler _ers;

    /**
     * A batch of SOIDs for which a SetVersionHash should be sent to the sync status server
     */
    private static class SVHBatch
    {
        final long _lastIndex;
        final long _nextIndex;
        final Map<SIndex, Set<OID>> _soids;
        SVHBatch(long last, long next, Map<SIndex, Set<OID>> soids)
        {
            _lastIndex = last;
            _nextIndex = next;
            _soids = soids;
        }
    }

    /**
     * SSPQ stands for SyncStatusPushQueue. It is a persistent queue of setVersionHash RPCs
     */
    private class SSPQ implements IPersistentQueue<SOID, SVHBatch>
    {
        private final long PUSH_DELAY = 500; // 500ms wait between two pushes

        @Override
        public void enqueue_(SOID soid, Trans t) throws SQLException
        {
            _ssdb.insertModifiedObject_(soid, t);
        }

        @Override
        public SVHBatch front_() throws SQLException
        {
            long lastIndex = _ssdb.getPushEpoch_();
            Map<SIndex, Set<OID>> soids = Maps.newHashMap();
            long nextIndex = getModifiedObjectBatch_(lastIndex, soids);
            return soids.isEmpty() ? null : new SVHBatch(lastIndex, nextIndex, soids);
        }

        @Override
        public boolean process_(SVHBatch batch, Token tk) throws Exception
        {
            try {
                // push version hashes to server
                pushVersionHashBatch_(batch._soids, batch._lastIndex, batch._nextIndex, tk);
            } catch (final ExSignIn e) {
                // sign-in handling needs to be done with core lock held
                onSignIn_(e._epoch);
                // immediately retry making the call now that we're signed-in
                return false;
            }

            // TODO(hugues): remove this throttling code at some point
            TCB tcb = tk.pseudoPause_("svh-throttle");
            try {
                Thread.sleep(PUSH_DELAY);
            } finally {
                tcb.pseudoResumed_();
            }

            return true;
        }

        @Override
        public void dequeue_(SVHBatch batch, Trans t) throws SQLException
        {
            // update push epoch
            _ssdb.setPushEpoch_(batch._nextIndex, t);
        }
    }

    private final PersistentQueueDriver<SOID, SVHBatch> _pqd;

    public static interface IListener
    {
        void devicesChanged(Set<SIndex> stores);
    }

    private final List<IListener> _listeners = Lists.newArrayList();

    @Inject
    public SyncStatusSynchronizer(TransManager tm, CoreScheduler sched,
            DirectoryService ds, SyncStatusConnection ssc, ISyncStatusDatabase ssdb,
            IMapSIndex2SID sidx2sid, IMapSID2SIndex sid2sidx, MapSIndex2DeviceBitMap sidx2dbm,
            NativeVersionControl nvc, PersistentQueueDriver.Factory factPQD)
    {
        _tm = tm;
        _ds = ds;
        _ssc = ssc;
        _ssdb = ssdb;
        _nvc = nvc;
        _sidx2sid = sidx2sid;
        _sid2sidx = sid2sidx;
        _sidx2dbm = sidx2dbm;
        _pqd = factPQD.create(new SSPQ());

        _ers = new ExpRetryScheduler(sched, "sspull", new Callable<Void>() {
            @Override
            public Void call() throws Exception
            {
                pullSyncStatus_();
                return null;
            }
        }, ExNoPerm.class, ExBadArgs.class, IOException.class);

        nvc.addListener_(this);

        _pqd.scheduleScan_();
    }

    public void addListener_(IListener listener)
    {
        _listeners.add(listener);
    }

    /**
     * Handle a sync status notification received from verkher
     */
    void notificationReceived_(PBSyncStatNotification notification) throws SQLException
    {
        long localEpoch = _ssdb.getPullEpoch_();
        long serverEpoch = notification.getSsEpoch();

        if (serverEpoch > localEpoch) {
            if (notification.hasStatus() && notification.getStatus().getDevicesCount() > 0) {
                // fast forward notification with empty device list is a violation of the protocol
                // (except for the device making the setVersionHash call), unfortunately the server
                // is currently sending such notifications...
                //assert notification.getStatus().getDevicesCount() > 0;

                boolean fastForward = serverEpoch == localEpoch + 1;
                // always apply status change but only bump epoch if the difference between local
                // epoch and server epoch is 1 (i.e. don't bump epoch if we missed a notification
                updateDB(fastForward ? serverEpoch : localEpoch,
                         Lists.newArrayList(notification.getStatus()));
                // skip pull on successful fast forward
                if (fastForward) {
                    l.debug("successful fast forward from " + localEpoch + " to " + serverEpoch);
                    return;
                }
            }
            l.debug("pull required to go from " + localEpoch + " to " + serverEpoch);
            schedulePull_();
        } else {
            // 1) corrupted DB?
            // 2) server-side data loss causing a regression of epoch numbers?
            // 3) pull completed before reception of notification?
            l.warn("notification anterior to pull epoch: " + serverEpoch + " vs " + localEpoch);
        }
    }

    /**
     * Schedule a pull from the sync status server with exponential retry
     */
    void schedulePull_()
    {
        _ers.schedule_();
    }

    /**
     * Make the actual GetSyncStatus call to the sync status server and update local DB accordingly.
     */
    private void pullSyncStatus_() throws Exception
    {
        boolean more = true;

        while (more) {
            long localEpoch = _ssdb.getPullEpoch_();
            l.debug("pull " + localEpoch);

            GetSyncStatusReply reply;
            try {
                // NOTE: release the core lock during the RPC call
                reply = _ssc.getSyncStatus_(localEpoch);
            } catch (ExSignIn e) {
                if (onSignIn_(e._epoch)) {
                    // need to schedule a new scan to resend vh after epoch rollback
                    _pqd.scheduleScan_();
                }
                // immediately retry making the call now that we're signed-in
                continue;
            }

            if (reply == null) return;

            long localEpochAfterCall = _ssdb.getPullEpoch_();
            long newEpoch = reply.getServerEpoch();
            // Check whether this pull bring us any new information
            if (newEpoch <= localEpochAfterCall) {
                if (reply.getMore()) {
                    // give a try to the next batch
                    continue;
                }
                // nothing new...
                l.debug("Nothing new in server reply: " + newEpoch + " vs " + localEpochAfterCall);
                return;
            }

            updateDB(reply.getServerEpoch(), reply.getSyncStatusesList());
            more = reply.getMore();
        }
    }

    /**
     * Update DB on reception of new sync status information
     * @param newPullEpoch new value of pull epoch after update
     * @param syncStatusList list of SyncStatus protobuf to received from server
     */
    private void updateDB(long newPullEpoch, Iterable<DevicesSyncStatus> syncStatusList)
            throws SQLException {
        Set<SIndex> stores = Sets.newHashSet();

        Trans t = _tm.begin_();
        try {
            for (DevicesSyncStatus ostat : syncStatusList) {
                // TODO(huguesb): group entries by sid in the reply to reduce number of lookups?
                SID sid = new SID(ostat.getSid());
                SIndex sidx = _sid2sidx.getNullable_(sid);
                if (sidx == null) {
                    l.warn("unknown SID: " + sid.toStringFormal());
                    continue;
                }

                SOID soid = new SOID(sidx, new OID(ostat.getOid()));

                // ignore missing SOIDs
                // NOTE: expelled SOIDs are still updated. This is required to keep sync status
                // consistent upon readmission in case the only version available from online peers
                // is the same as the last local version before expulsion
                // TODO(huguesb): keep store-level count of new out_sync objects?
                OA oa = _ds.getOANullable_(soid);
                if (oa == null) {
                    l.warn("unknown OID: " + soid.sidx() + "<" + soid.oid().toStringFormal() + ">");
                    continue;
                }

                DeviceBitMap dids = _sidx2dbm.getDeviceMapping_(sidx);

                // compress the reply into a sync status bit vector
                // NOTE: any device not mentioned in the message should remain unchanged so we need
                // to fetch the current (raw) sync status and modify it
                BitVector bv = _ds.getRawSyncStatus_(soid);

                l.debug("new status for : " + soid.toString() + " " + ostat.getDevicesCount()
                        + " [" + bv + "]");

                for (DeviceSyncStatus dstat : ostat.getDevicesList()) {
                    DID did = new DID(dstat.getDid());
                    Integer idx = dids.get(did);
                    if (idx == null) {
                        // new DID, register it with this store
                        stores.add(sidx);
                        idx = _sidx2dbm.addDevice_(sidx, did, t);
                        l.debug("[" + sidx +  "] new DID : " + did.toStringFormal() + " -> " + idx);
                    }
                    bv.set(idx, dstat.getIsSynced());
                }

                l.debug(" -> " + bv);

                // set the new sync status vector
                _ds.setSyncStatus_(soid, bv, t);
            }

            // update epoch to avoid re-downloading the information
            _ssdb.setPullEpoch_(newPullEpoch, t);
            t.commit_();
        } finally {
            t.end_();
        }

        if (!stores.isEmpty()) {
            // make sure all syncstatus-related caching is invalidated when new DIDs are registered
            for (IListener l : _listeners) l.devicesChanged(stores);
        }
    }

    /**
     * Read a batch of modified SOIDs from the activity log
     * @return the highest push epoch for the batch
     */
    private long getModifiedObjectBatch_(long pushEpoch, Map<SIndex, Set<OID>> soids)
            throws SQLException
    {
        final int MODIFIED_OBJECT_BATCH_MAX_SIZE = 100;
        long lastEpoch = pushEpoch;
        soids.clear();
        IDBIterator<ModifiedObject> it = _ssdb.getModifiedObjects_(pushEpoch);
        int oidsCounter = 0;
        try {
            while (it.next_() && oidsCounter++ < MODIFIED_OBJECT_BATCH_MAX_SIZE) {
                ModifiedObject mo = it.get_();
                Set<OID> oids = soids.get(mo._soid.sidx());
                if (oids == null) {
                    oids = Sets.newHashSet();
                    soids.put(mo._soid.sidx(), oids);
                }
                oids.add(mo._soid.oid());
                lastEpoch = Math.max(lastEpoch, mo._idx);
            }
        } finally {
            it.close_();
        }
        return lastEpoch;
    }

    /**
     * Make the actual SetVersionHash call to the sync status server
     */
    private void pushVersionHashBatch_(Map<SIndex, Set<OID>> soids, long currentEpoch,
            long nextEpoch, Token tk) throws Exception
    {
        int i = 0;
        for (Entry<SIndex, Set<OID>> e : soids.entrySet()) {
            SIndex sidx = e.getKey();

            // NOTE: store might have been deleted between the creation of an activity log
            // (or bootstrap) entry and a subsequent scan, if so ignore the object.
            // TODO: markj wants to make this stricter, requires discussion with weihanw
            SID sid = _sidx2sid.getNullable_(sidx);
            if (sid == null) continue;

            List<ByteString> oids = Lists.newArrayList();
            List<ByteString> vhs = Lists.newArrayList();
            for (OID oid : e.getValue()) {
                byte[] vh = getVersionHash_(new SOID(sidx, oid));
                l.debug(new SOID(sidx, oid).toString() + " : " + BaseUtil.hexEncode(vh));
                oids.add(oid.toPB());
                vhs.add(ByteString.copyFrom(vh));
            }

            l.debug("vh push: " + sidx + " " + oids.size());
            // only notify the server of the latest client epoch in the last chunk
            long clientEpoch = (++i == soids.size() ? nextEpoch : currentEpoch);
            _ssc.setVersionHash_(sid, oids, vhs, clientEpoch, tk);
        }
    }

    /**
     * Helper class to compute version hash
     * Grouping meta and content ticks for a given DID before digest computation
     * reduces the amount of data to digest by 33% and does not add significant
     * precomputation overhead since the entries need to be sorted anyway...
     */
    private static class TickPair
    {
        public Tick _mt, _ct;

        public long metaTick()
        {
            return _mt != null ? _mt.getLong() : 0;
        }

        public long contentTick()
        {
            return _ct != null ? _ct.getLong() : 0;
        }

        public TickPair(Tick meta)
        {
            _mt = meta;
        }
    }

    /**
     * @return version hash of the given object
     */
    private byte[] getVersionHash_(SOID soid) throws SQLException
    {
        // aggregate MASTER versions for both meta and content components
        // we intentionally do NOT take conflict branches into account as:
        //   * the sync status could appear as out-of-sync between two users with the exact same
        //   MASTER branch which would go against user expectations
        //   * there is no easy way to take conflict branches that does not leave room for
        //   inconsistent results

        // Map needs to be sorted for deterministic version hash computation
        SortedMap<DID, TickPair> aggregated = Maps.newTreeMap();

        Version vm = _nvc.getLocalVersion_(new SOCKID(soid, CID.META, KIndex.MASTER));
        for (Entry<DID, Tick> e : vm.getAll_().entrySet()) {
            aggregated.put(e.getKey(), new TickPair(e.getValue()));
        }

        Version vc = _nvc.getLocalVersion_(new SOCKID(soid, CID.CONTENT, KIndex.MASTER));
        for (Entry<DID, Tick> e : vc.getAll_().entrySet()) {
            TickPair tp = aggregated.get(e.getKey());
            if (tp == null) {
                tp = new TickPair(null);
                aggregated.put(e.getKey(), tp);
            }
            tp._ct = e.getValue();
        }

        // make a digest from that aggregate
        // (no security concern here, only compactness matters so MD5 is fine)
        MessageDigest md = SecUtil.newMessageDigestMD5();
        for (Entry<DID, TickPair> e : aggregated.entrySet()) {
            md.update(e.getKey().getBytes());
            md.update(Util.toByteArray(e.getValue().metaTick()));
            md.update(Util.toByteArray(e.getValue().contentTick()));
        }

        return md.digest();
    }

    /**
     * Rollback push epoch in case of data loss on the server
     *
     * @return whether the push epoch was rolled back
     */
    public boolean onSignIn_(long clientEpoch)
    {
        l.debug("connected: " + clientEpoch);
        try {
            boolean rollback = false;
            Trans t = _tm.begin_();
            try {
                /*
                 * Always reset the pull epoch on sign-in to prevent server-side data loss from
                 * causing client side data loss.
                 *
                 * Resetting the epoch ensures that the first getSyncStatus will fetch the last
                 * epoch from the server which is crucial to avoid accidentally discarding
                 * notifications after server-side data loss.
                 */
                _ssdb.setPullEpoch_(0L, t);

                /*
                 * Truncate push queue to avoid unbounded growth
                 */
                _ssdb.deleteModifiedObjects_(clientEpoch - 1, t);

                long pushEpoch = _ssdb.getPushEpoch_();
                if (clientEpoch < pushEpoch) {
                    l.warn("rollback from " + pushEpoch + " to " + clientEpoch);

                    // rollback push epoch to recover from server data loss
                    _ssdb.setPushEpoch_(clientEpoch, t);

                    /*
                     * if the push queue has already been truncated too much (i.e significant server
                     * data loss after last successful connection) we need to fill the bootstrap
                     * table to recover
                     */
                    if (needBootstrap(clientEpoch)) {
                        l.warn("rebootstrap");
                        _ssdb.bootstrap_(t);
                        _ssdb.setPushEpoch_(0, t);
                    }

                    _pqd.restartScan_();

                    rollback = true;
                }

                t.commit_();
            } finally {
                t.end_();
            }
            return rollback;
        } catch (SQLException e) {
            throw SystemUtil.fatalWithReturn(e);
        }
    }

    private boolean needBootstrap(long clientEpoch) throws SQLException
    {
        IDBIterator<ModifiedObject> mo = _ssdb.getModifiedObjects_(clientEpoch);
        try {
            return (!mo.next_() || mo.get_()._idx > clientEpoch);
        } finally {
            mo.close_();
        }
    }

    private final TransLocal<Set<SOID>> _tlModified = new TransLocal<Set<SOID>>() {
        @Override
        protected Set<SOID> initialValue(Trans t)
        {
            final Set<SOID> set = Sets.newHashSet();
            t.addListener_(new AbstractTransListener() {
                @Override
                public void committing_(Trans t) throws SQLException
                {
                    for (SOID soid : set) _pqd.enqueue_(soid, t);
                }

                @Override
                public void committed_()
                {
                    _pqd.scheduleScan_();
                }
            });
            return set;
        }
    };

    @Override
    public void localVersionAdded_(SOCKID sockid, Version v, Trans t) throws SQLException
    {
        // ignore conflict branches
        if (!sockid.kidx().equals(KIndex.MASTER)) return;

        // add object to push queue
        assert !v.isZero_() : sockid;
        assert !(sockid.oid().isRoot() || sockid.oid().isTrash()) : sockid;
        _tlModified.get(t).add(sockid.soid());
    }

    @Override
    public void objectCreated_(SOID obj, OID parent, Path pathTo, Trans t) throws SQLException
    {
        /**
         * Make sure we send version hash for new objects
         *
         * Although there is currently no case where an object is created and its version vector
         * remains empty, this assumption may not hold forever (in particular the ACL push required
         * by the team server will most likely change that to avoid false META conflicts). Avoid
         * coupling by making SyncStatusSynchronizer more robust.
         */
        if (!(obj.oid().isRoot() || obj.oid().isTrash())) _tlModified.get(t).add(obj);
    }
}
