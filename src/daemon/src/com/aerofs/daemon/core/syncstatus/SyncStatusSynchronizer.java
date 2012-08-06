package com.aerofs.daemon.core.syncstatus;

import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.log4j.Logger;

import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.store.SIDMap;
import com.aerofs.daemon.core.store.Stores.IDIDBiMap;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.event.lib.AbstractEBSelfHandling;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.db.IActivityLogDatabase;
import com.aerofs.daemon.lib.db.IActivityLogDatabase.ModifiedObject;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.BitVector;
import com.aerofs.lib.Param.SyncStat;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.Tick;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.syncstat.SyncStatBlockingClient;
import com.aerofs.proto.Syncstat.GetSyncStatusReply;
import com.aerofs.proto.Syncstat.GetSyncStatusReply.OidSyncStatus;
import com.aerofs.proto.Syncstat.GetSyncStatusReply.DeviceSyncStatus;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;

/**
 * This class keeps local sync status information in sync with the central server.
 *
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
    // TODO (MJ) rename to whichever interface you actually need
    private final SIDMap _sidmap;

    private SyncStatBlockingClient _c;

    @Inject
    public SyncStatusSynchronizer(CoreQueue q, TC tc, TransManager tm,
            LocalSyncStatus lsync, SyncStatBlockingClient.Factory ssf,
            IActivityLogDatabase aldb, NativeVersionControl nvc, SIDMap sidmap) {
        _c = null;
        _q = q;
        _tc = tc;
        _tm = tm;
        _ssf = ssf;
        _lsync = lsync;
        _aldb = aldb;
        _nvc = nvc;
        _sidmap = sidmap;

        // 1) process items in the bootstrap table, if any
        // 2) process items in the activity log left by a previous run
        // 3) make a first pull
        // TODO(huguesb): uncomment after proper testing
        //scheduleStartup();
    }

    /**
     * Wrapper to access sync status client
     * @throws Exception
     */
    private SyncStatBlockingClient c() throws Exception {
        if (_c == null) {
            try {
                // TODO: check connection/disconnection/reconnection semantics w/ mattp
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
     * Compare an epoch received in a verkehr notification to the latest
     * local epoch and schedule a sync status pull if needed
     */
    public void checkEpoch_(long serverEpoch) {
        long localEpoch = 0;
        if (serverEpoch > localEpoch) {
            // TODO(huguesb): uncomment after proper testing
            //schedulePull_();
        }
    }

    private void scheduleStartup() {
        _q.enqueueBlocking(new AbstractEBSelfHandling() {
            @Override
            public void handle_() {
                try {
                    bootstrap_();
                    scanActivityLog_();
                    pullSyncStatus_();
                } catch (Exception e) {
                    l.warn("Sync status startup failed", e);
                    // TODO: close connection?
                    // TODO: exp retry?
                }
            }
        }, Prio.LO);
    }

    /**
     * Read a batch of bootstrap SOIDS from the DB
     * @throws SQLException
     */
    private List<SOID> getBootstrapBatch_() throws SQLException {
        final int BOOTSTRAP_BATCH_MAX_SIZE = 100;
        List<SOID> batch = new ArrayList<SOID>();
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
    private void bootstrap_() throws Exception {
        // batch DB reads
        List<SOID> batch = getBootstrapBatch_();
        while (!batch.isEmpty()) {
            // RPC calls
            for (SOID soid : batch) {
                l.warn("bootstrap push : " + soid.toString());
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

    private void schedulePull_() {
        AbstractEBSelfHandling eb = new AbstractEBSelfHandling() {
            @Override
            public void handle_() {
                try {
                    pullSyncStatus_();
                } catch (Exception e) {
                    l.warn("Pull from sync status server failed", e);
                    // TODO: close connection?
                    // TODO: exp retry?
                }
            }
        };

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
            idx = _lsync.addDevice_(sidx, did, t);
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
    private void pullSyncStatus_() throws Exception {
        boolean more = true;

        while (more) {
            long localEpoch = _lsync.getPullEpoch_();

            // release the core lock during the RPC call
            GetSyncStatusReply reply = null;
            Token tk = _tc.acquireThrows_(Cat.UNLIMITED, "syncstatpull");
            TCB tcb = null;
            try {
                tcb = tk.pseudoPause_("syncstatpull");
                l.warn("SyncStatusClient.getSyncStatus : not implemented");
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
                return;
            }

            Map<SOID, BitVector> update = new HashMap<SOID, BitVector>();
            // Must perform this computation outside of the update transaction
            // to avoid problems when registering a new DID
            for (OidSyncStatus ostat : reply.getOidSyncStatusesList()) {
                // TODO: group entries by sid in the reply to reduce number of lookups?
                SIndex sidx = _sidmap.get_(new SID(ostat.getSid()));
                if (sidx == null) continue;

                SOID soid = new SOID(sidx, new OID(ostat.getOid()));
                IDIDBiMap dids = _lsync.getDeviceMapping_(sidx);

                // compress the reply into a sync status bit vector
                BitVector bv = new BitVector(dids.size(), false);
                for (DeviceSyncStatus dstat : ostat.getDevicesList()) {
                    DID did = new DID(dstat.getDid());
                    Integer idx = dids.get(did);
                    if (idx == null) {
                        // new DID, register it with this store
                        idx = addDevice_(sidx, did);
                    }
                    bv.set(idx, dstat.getIsSynced());
                }
                update.put(soid,  bv);
            }

            Trans t = _tm.begin_();
            try {
                // set the new sync status vector for the updated objects
                for (Entry<SOID, BitVector> e : update.entrySet()) {
                    _lsync.setSyncStatus_(e.getKey(), e.getValue(), t);
                }
                // update epoch to avoid re-downloading the information
                _lsync.setPullEpoch_(reply.getSsEpoch(), t);
                t.commit_();
            } finally {
                t.end_();
            }

            more = reply.getMore();
        }
    }

    /**
     * Read a batch of bootstrap SOIDS from the DB
     * @throws SQLException
     */
    private List<ModifiedObject> getModifiedObjectBatch_(long pushEpoch) throws SQLException {
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

    // TODO(huguesb): set to false after proper testing
    private boolean _scanInProgress = true;
    /**
     * Scans the activity log table and push new version hashes to the server
     */
    public void scanActivityLog_() {
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
                    l.warn("activity push : " + mo._soid.toString());
                    pushVersionHash_(mo._soid);
                    assert lastIndex < mo._idx;
                    lastIndex = mo._idx;
                }

                // update push epoch
                setPushEpoch_(lastIndex);

                // batch DB reads
                batch = getModifiedObjectBatch_(lastIndex);
            }
        } catch (Exception e) {
            l.warn("Push to sync status server failed", e);
            // TODO: better error handling
        } finally {
            _scanInProgress = false;
        }
    }

    /**
     * Wrap SyncStatusDatabase.setLastActivityIndex in a transaction
     */
    private void setPushEpoch_(long idx) throws SQLException {
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
    private void pushVersionHash_(SOID soid) throws Exception {
        // TODO: batch pushes to reduce communication overhead?
        byte[] vh = getVersionHash_(soid);
        Token tk = _tc.acquireThrows_(Cat.UNLIMITED, "syncstatpush");
        TCB tcb = null;
        try {
            tcb = tk.pseudoPause_("syncstatpush");
            c().setVersionHash(_sidmap.get_(soid.sidx()).toPB(),
                               soid.oid().toPB(),
                               ByteString.copyFrom(vh));
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
    private static class TickPair {
        public Tick _mt, _ct;

        public long metaTick() {
            return _mt != null ? _mt.getLong() : 0;
        }
        public long contentTick() {
            return _ct != null ? _ct.getLong() : 0;
        }

        public TickPair(Tick meta) {
            _mt = meta;
        }
    }

    /**
     * Compute version hash for an object
     * @param soid
     * @return version hash
     */
    private byte[] getVersionHash_(SOID soid) throws SQLException {
        // aggregate all versions for both meta and content components

        // Map needs to be sorted for deterministic version hash computation
        SortedMap<DID, TickPair> aggregated = new TreeMap<DID, TickPair>();

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
