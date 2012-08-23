package com.aerofs.daemon.core.syncstatus;

import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.concurrent.Callable;

import com.aerofs.daemon.core.ActivityLog;
import com.aerofs.daemon.core.ActivityLog.IActivityLogListener;
import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.store.DeviceBitMap;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.MapSIndex2DeviceBitMap;
import com.aerofs.daemon.event.lib.AbstractEBSelfHandling;
import com.aerofs.daemon.lib.ExponentialRetry;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.BitVector;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.proto.Sp.PBSyncStatNotification;
import com.aerofs.proto.Syncstat.GetSyncStatusReply;
import com.aerofs.proto.Syncstat.GetSyncStatusReply.DeviceSyncStatus;
import com.aerofs.proto.Syncstat.GetSyncStatusReply.SyncStatus;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.log4j.Logger;

import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.db.IActivityLogDatabase;
import com.aerofs.daemon.lib.db.IActivityLogDatabase.ModifiedObject;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.Param.SyncStat;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.Tick;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.syncstat.SyncStatBlockingClient;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;

/**
 * This class keeps local sync status information in sync with the central server.
 *
 * There are three main aspects to the sync status synchronizer logic :
 *
 *  1) push: Whenever a new changes happen locally (catched by the ActivityLog) the synchronizer
 *  will scan the activity log table for modified SOIDs, compute their new version hash and send it
 *  to the server (which should result in a new epoch being received from verkher soon after). After
 *  successful push of a version hash, the local push epoch is updated. This epoch is basically the
 *  index of the last row of the activity log table successfully pushed.
 *
 *  2) pull : Whenever a new pull epoch is received through {@link SyncStatusNotificationSubscriber}
 *  the synchronizer compares it to the last known pull epoch and initiates pull from the server if
 *  needed. The pull epoch is assigned by the syncstatus server at device granularity and increases
 *  with every change made that could result in an out-of-sync state between two devices.
 *  For performance reasons, the notification allows a "fast-forward" : the last updated sync status
 *  can be included in the notification, allowing the client to update its DB without an extra round
 *  trip if the epoch difference is exactly one.
 *
 *  3) startup : The startup phase is responsible for flushing any local information not yet
 *  transmitted to the sync status server and pulling the server for new information (to account
 *  for dropped verkher notifications).
 */
public class SyncStatusSynchronizer
{
    private static final Logger l = Util.l(SyncStatusSynchronizer.class);

    private final TC _tc;
    private final CoreQueue _q;
    private final TransManager _tm;
    private final LocalSyncStatus _lsync;
    private final SyncStatBlockingClient.Factory _ssf;
    private final IActivityLogDatabase _aldb;
    private final NativeVersionControl _nvc;
    private final IMapSIndex2SID _sidx2sid;
    private final IMapSID2SIndex _sid2sidx;
    private final MapSIndex2DeviceBitMap _sidx2dbm;
    private final DirectoryService _ds;
    private final ExponentialRetry _er;

    private final boolean _enable;

    private SyncStatBlockingClient _c;

    @Inject
    public SyncStatusSynchronizer(CoreQueue q, CoreScheduler sched, TC tc, TransManager tm,
            LocalSyncStatus lsync, DirectoryService ds, SyncStatBlockingClient.Factory ssf,
            IMapSIndex2SID sidx2sid, IMapSID2SIndex sid2sidx, MapSIndex2DeviceBitMap sidx2dbm,
            ActivityLog al, IActivityLogDatabase aldb, NativeVersionControl nvc,
            CfgLocalUser localUser)
    {
        _c = null;
        _q = q;
        _tc = tc;
        _tm = tm;
        _ds = ds;
        _ssf = ssf;
        _lsync = lsync;
        _aldb = aldb;
        _nvc = nvc;
        _sidx2sid = sidx2sid;
        _sid2sidx = sid2sidx;
        _sidx2dbm = sidx2dbm;
        _er = new ExponentialRetry(sched);
        _enable = localUser.get().endsWith("@aerofs.com");

        al.addListener_(new IActivityLogListener()
        {
            @Override
            public void activitiesAdded_()
            {
                scanActivityLog_();
            }
        });

        // 1) process items in the bootstrap table, if any
        // 2) process items in the activity log left by a previous run
        // 3) make a first pull
        scheduleStartup();
    }

    /**
     * Wrapper to access sync status client
     */
    private synchronized SyncStatBlockingClient c() throws Exception
    {
        if (_c == null) {
            try {
                _c = _ssf.create(SyncStat.URL, Cfg.user());
                _c.signInRemote();
            } catch (Exception e) {
                l.warn("Connection to sync status server failed", e);
                throw e;
            }
        }
        return _c;
    }

    /**
     * Force reconnection to sync stat server on next RPC call
     */
    private synchronized void closeClient()
    {
        _c = null;
    }

    /**
     * Handle a sync status notification received from verkher
     */
    void notificationReceived_(PBSyncStatNotification notification) throws SQLException
    {
        // TODO (huguesb): remove check when ready for all users
        if (!_enable) return;

        long localEpoch = _lsync.getPullEpoch_();
        long serverEpoch = notification.getSsEpoch();

        if (serverEpoch > localEpoch) {
            if (notification.hasStatus()) {
                boolean fastForward = serverEpoch == localEpoch + 1;
                // always apply status change but only bump epoch if the difference between local
                // epoch and server epoch is 1 (i.e. don't bump epoch if we missed a notification
                updateDB(fastForward ? serverEpoch : localEpoch,
                         Lists.newArrayList(notification.getStatus()));
                // skip pull on successful fast forward
                if (fastForward) {
                    l.info("successful fast forward from " + localEpoch + " to " + serverEpoch);
                    return;
                }
            }
            schedulePull_();
        }
    }

    private void scheduleStartup()
    {
        final Callable<Void> startUp = new Callable<Void>() {
            @Override
            public Void call() throws Exception
            {
                try {
                    bootstrap_();
                    scanActivityLog_();
                    pullSyncStatus_();
                } catch (Exception e) {
                    closeClient();
                    // rethrow to let ExponentialRetry kick in
                    throw e;
                }
                return null;
            }
        };

        _q.enqueueBlocking(new AbstractEBSelfHandling() {
            @Override
            public void handle_() {
                _er.retry("SyncStatStart", startUp);
            }
        }, Prio.LO);
    }

    /**
     * @return a batch of bootstrap SOIDS from the DB
     */
    private List<SOID> getBootstrapBatch_() throws SQLException
    {
        final int BOOTSTRAP_BATCH_MAX_SIZE = 100;
        List<SOID> batch = Lists.newArrayList();
        IDBIterator<SOID> soids = _lsync.getBootstrapSOIDs_();
        try {
            while (soids.next_() && batch.size() < BOOTSTRAP_BATCH_MAX_SIZE) {
                batch.add(soids.get_());
            }
        } finally {
            soids.close_();
        }
        return batch;
    }

    /**
     * Bootstrap sync status :
     * When a client with existing files updates AeroFS to a version that enables
     * sync status we cannot afford to wait on activity log to report them to us.
     * A post update task will populate a bootstrap table with existing OIDs and
     * they will be sent to the server in the background.
     */
    private void bootstrap_() throws Exception
    {
        // TODO (huguesb): remove check when ready for all users
        if (!_enable) return;

        // batch DB reads
        List<SOID> batch = getBootstrapBatch_();
        while (!batch.isEmpty()) {
            // RPC calls
            for (SOID soid : batch) {
                l.info("bootstrap push : " + soid.toString());
                pushVersionHash_(soid);
            }

            // batch DB writes
            Trans t = _tm.begin_();
            try {
                for (SOID soid : batch)
                    _lsync.removeBootsrapSOID_(soid, t);
                t.commit_();
            } finally {
                t.end_();
            }

            // batch DB reads
            batch = getBootstrapBatch_();
        }
    }

    /**
     * Schedule a pull from the sync status server with exponential retry
     */
    private void schedulePull_()
    {
        schedule_(new AbstractEBSelfHandling() {
            @Override
            public void handle_() {
                // exp retry (w/ forced server reconnect) in case of failure
                _er.retry("SyncStatPull", new Callable<Void>()
                {
                    @Override
                    public Void call() throws Exception
                    {
                        try {
                            pullSyncStatus_();
                        } catch (Exception e) {
                            closeClient();
                            // let ExponentialRetry kick in
                            throw e;
                        }
                        return null;
                    }
                });
            }
        });
    }

    /**
     * Schedule a self handling event : try non-blocking enqueue first and on filaure release core
     * lock and do a blocking enqueue
     */
    private void schedule_(AbstractEBSelfHandling eb)
    {
        // try non-blocking scheduling w/ core lock held
        if (_q.enqueue_(eb, Prio.LO)) return;

        // fall back on blocking w/o core lock held
        try {
            Token tk = _tc.acquireThrows_(Cat.UNLIMITED, "syncstatq");
            TCB tcb = null;
            try {
                tcb = tk.pseudoPause_("syncstatq");
                _q.enqueueBlocking(eb, Prio.LO);
            } finally {
                if (tcb != null) tcb.pseudoResumed_();
                tk.reclaim_();
            }
        } catch (Exception e) {
            l.error("Failed to schedule sync status pull", e);
        }
    }

    /**
     * Wrap LocalSyncStatus.addDevice_ in a transaction
     */
    private int addDevice_(SIndex sidx, DID did) throws SQLException
    {
        int idx;
        Trans t = _tm.begin_();
        try {
            idx = _sidx2dbm.addDevice_(sidx, did, t);
            t.commit_();
        } finally {
            t.end_();
        }
        return idx;
    }

    /**
     * Make the actual GetSyncStatus call to the sync status server and update
     * local DB accordingly
     */
    private void pullSyncStatus_() throws Exception
    {
        // TODO (huguesb): remove check when ready for all users
        if (!_enable) return;

        boolean more = true;

        while (more) {
            long localEpoch = _lsync.getPullEpoch_();
            l.info("pull " + localEpoch);

            // release the core lock during the RPC call
            GetSyncStatusReply reply = null;
            Token tk = _tc.acquireThrows_(Cat.UNLIMITED, "syncstatpull");
            TCB tcb = null;
            try {
                tcb = tk.pseudoPause_("syncstatpull");
                reply = c().getSyncStatus(localEpoch);
            } finally {
                if (tcb != null) tcb.pseudoResumed_();
                tk.reclaim_();
            }

            if (reply == null) return;

            long localEpochAfterCall = _lsync.getPullEpoch_();
            long newEpoch = reply.getSsEpoch();
            // Check whether this pull bring us any new information
            if (newEpoch <= localEpochAfterCall) {
                if (reply.getMore()) {
                    // give a try to the next batch
                    continue;
                }
                // nothing new...
                l.info("Nothing new in server reply");
                return;
            }

            updateDB(reply.getSsEpoch(), reply.getSyncStatusesList());
            more = reply.getMore();
        }
    }

    /**
     * Update DB on reception of new sync status information
     * @param newPullEpoch new value of pull epoch after update
     * @param syncStatusList list of SyncStatus protobuf to received from server
     */
    private void updateDB(long newPullEpoch, Iterable<SyncStatus> syncStatusList)
            throws SQLException {
        Map<SOID, BitVector> update = Maps.newHashMap();
        // Must perform this computation outside of the update transaction
        // to avoid problems when registering a new DID
        for (SyncStatus ostat : syncStatusList) {
            // TODO: group entries by sid in the reply to reduce number of lookups?
            SID sid = new SID(ostat.getSid());
            SIndex sidx = _sid2sidx.getNullable_(sid);
            if (sidx == null) {
                l.warn("unknown SID : " + sid.toStringFormal());
                continue;
            }

            SOID soid = new SOID(sidx, new OID(ostat.getOid()));
            DeviceBitMap dids = _sidx2dbm.getDeviceMapping_(sidx);

            l.info("new status for : " + soid.toString());

            // compress the reply into a sync status bit vector
            BitVector bv = new BitVector(dids.size(), false);
            for (DeviceSyncStatus dstat : ostat.getDevicesList()) {
                DID did = new DID(dstat.getDid());
                Integer idx = dids.get(did);
                if (idx == null) {
                    // new DID, register it with this store
                    idx = addDevice_(sidx, did);
                    l.info("[" + sidx +  "] new DID : " + did.toStringFormal() + " -> " + idx);
                }
                bv.set(idx, dstat.getIsSynced());
            }
            update.put(soid,  bv);
        }

        Trans t = _tm.begin_();
        try {
            // set the new sync status vector for the updated objects
            for (Entry<SOID, BitVector> e : update.entrySet()) {
                _ds.setSyncStatus_(e.getKey(), e.getValue(), t);
            }
            // update epoch to avoid re-downloading the information
            _lsync.setPullEpoch_(newPullEpoch, t);
            t.commit_();
        } finally {
            t.end_();
        }
    }

    /**
     * Read a batch of bootstrap SOIDS from the DB
     * @throws SQLException
     */
    private List<ModifiedObject> getModifiedObjectBatch_(long pushEpoch) throws SQLException
    {
        final int MODIFIED_OBJECT_BATCH_MAX_SIZE = 100;
        List<ModifiedObject> batch = new ArrayList<ModifiedObject>();
        IDBIterator<ModifiedObject> it = _aldb.getModifiedObjects_(pushEpoch);
        try {
            while (it.next_() && batch.size() < MODIFIED_OBJECT_BATCH_MAX_SIZE) {
                batch.add(it.get_());
            }
        } finally {
            it.close_();
        }
        return batch;
    }

    /**
     * Scans the activity log table and push new version hashes to the server
     *
     * Exponential retry in case of failure and automatic "batching" of concurrent scans (new scans
     * will abort if a scan is already in progress and old failed scans will abort the exponential
     * retry if a new scan comes in).
     */
    private long _scanSeq = 0;
    private void scanActivityLog_()
    {
        // TODO (huguesb): remove check when ready for all users
        if (!_enable) return;

        schedule_(new AbstractEBSelfHandling() {
            @Override
            public void handle_() {
                // to avoid unbounded queueing of failing scans when connection to server is lost, each call
                // added to the exp retry is assigned a sequence number
                final long _seq = ++_scanSeq;

                // exp retry (w/ forced server reconnect) in case of failure
                // TODO(huguesb): reduce latency of retry when connection to server comes back
                _er.retry("SyncStatActivityPush", new Callable<Void>()
                {
                    @Override
                    public Void call() throws Exception
                    {
                        if (_scanSeq != _seq) return null;
                        try {
                            scanActivityLogInternal_();
                        } catch (Exception e) {
                            closeClient();
                            // let ExponentialRetry kick in
                            throw e;
                        }
                        return null;
                    }
                });
            }
        });
    }

    private boolean _scanInProgress = false;
    private void scanActivityLogInternal_() throws Exception
    {
        // avoid concurrent scans : this method is called with the core lock held
        // but pushVersionHash release the core lock during the RPC call so we
        // need an extra check to avoid concurrent scans.
        if (_scanInProgress) return;
        _scanInProgress = true;

        try {
            // batch DB reads
            long lastIndex = _lsync.getPushEpoch_();
            List<ModifiedObject> batch = getModifiedObjectBatch_(lastIndex);
            while (!batch.isEmpty()) {
                // RPC calls
                for (ModifiedObject mo : batch) {
                    l.info("activity push : " + mo._soid.toString());
                    pushVersionHash_(mo._soid);
                    assert lastIndex < mo._idx;
                    lastIndex = mo._idx;
                }

                // update push epoch
                setPushEpoch_(lastIndex);

                // batch DB reads
                batch = getModifiedObjectBatch_(lastIndex);
            }
        } finally {
            _scanInProgress = false;
        }
    }

    /**
     * Wrap SyncStatusDatabase.setLastActivityIndex in a transaction
     */
    private void setPushEpoch_(long idx) throws SQLException
    {
        Trans t = _tm.begin_();
        try {
            _lsync.setPushEpoch_(idx, t);
            t.commit_();
        } finally {
            t.end_();
        }
    }

    /**
     * Make the actual SetVersionHash call to the sync status server and update
     * local DB accordingly
     */
    private void pushVersionHash_(SOID soid) throws Exception
    {
        // TODO: batch pushes to reduce communication overhead?

        // NOTE: store might have been deleted between the creation of an activity log / bootstrap
        // entry and a subsequent scan, if so ignore the object.
        // TODO: markj wants to make this stricter but it requires discussion with weihanw first
        SID sid = _sidx2sid.getNullable_(soid.sidx());
        if (sid == null) return;

        byte[] vh = getVersionHash_(soid);
        l.warn(soid.toString() + " : " + Util.hexEncode(vh));
        Token tk = _tc.acquireThrows_(Cat.UNLIMITED, "syncstatpush");
        TCB tcb = null;
        try {
            tcb = tk.pseudoPause_("syncstatpush");
            c().setVersionHash(soid.oid().toPB(), sid.toPB(), ByteString.copyFrom(vh));
        } finally {
            if (tcb != null) tcb.pseudoResumed_();
            tk.reclaim_();
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
        // aggregate all versions for both meta and content components

        // Map needs to be sorted for deterministic version hash computation
        SortedMap<DID, TickPair> aggregated = Maps.newTreeMap();

        Version vm = _nvc.getAllLocalVersions_(new SOCID(soid, CID.META));
        for (Entry<DID, Tick> e : vm.getAll_().entrySet()) {
            aggregated.put(e.getKey(), new TickPair(e.getValue()));
        }

        Version vc = _nvc.getAllLocalVersions_(new SOCID(soid, CID.CONTENT));
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
}
