/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.submit;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.polaris.GsonUtil;
import com.aerofs.daemon.core.polaris.PolarisAsyncClient;
import com.aerofs.daemon.core.polaris.api.*;
import com.aerofs.daemon.core.polaris.api.Batch.BatchOp;
import com.aerofs.daemon.core.polaris.api.BatchResult.BatchOpResult;
import com.aerofs.daemon.core.polaris.api.BatchResult.PolarisError;
import com.aerofs.daemon.core.polaris.api.RemoteChange.Type;
import com.aerofs.daemon.core.polaris.async.AsyncTaskCallback;
import com.aerofs.daemon.core.polaris.db.*;
import com.aerofs.daemon.core.polaris.db.MetaChangesDatabase.MetaChange;
import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase.RemoteLink;
import com.aerofs.daemon.core.status.PauseSync;
import com.aerofs.daemon.core.store.DaemonPolarisStore;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.ids.OID;
import com.aerofs.ids.UniqueID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.sched.ExponentialRetry.ExRetryLater;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.*;

import static com.aerofs.daemon.core.polaris.api.LocalChange.Type.INSERT_CHILD;
import static com.aerofs.daemon.core.polaris.api.LocalChange.Type.MOVE_CHILD;
import static com.google.common.base.Preconditions.checkState;

/**
 * Submit metadata changes to Polaris
 *
 * This class handles protocol encoding/decoding and DB cleanup after successful submission.
 *
 * For now each change is submitted in its own HTTP request but in the future the /batch route
 * should be used to reduce the number of round-trips.
 */
public class MetaChangeSubmitter implements Submitter
{
    private final static Logger l = Loggers.getLogger(MetaChangeSubmitter.class);

    private final PolarisAsyncClient _client;
    private final MapAlias2Target _a2t;
    private final MetaChangesDatabase _mcdb;
    private final MetaBufferDatabase _mbdb;
    private final RemoteLinkDatabase _rpdb;
    private final IMapSIndex2SID _sidx2sid;
    private final CentralVersionDatabase _cvdb;
    private final ChangeEpochDatabase _cedb;
    private final PauseSync _pauseSync;
    private final DirectoryService _ds;
    private final TransManager _tm;
    private final MapSIndex2Store _sidx2s;
    private final LocalACL _lacl;
    private final CfgLocalUser _localUser;

    @Inject
    public MetaChangeSubmitter(
            PolarisAsyncClient client,
            MetaChangesDatabase mcdb,
            MetaBufferDatabase mbdb,
            RemoteLinkDatabase rpdb,
            CentralVersionDatabase cvdb,
            ChangeEpochDatabase cedb,
            IMapSIndex2SID sidx2sid,
            MapAlias2Target a2t,
            PauseSync pauseSync,
            DirectoryService ds,
            TransManager tm,
            LocalACL lacl,
            CfgLocalUser localUser,
            MapSIndex2Store sidx2s)
    {
        _client = client;
        _mcdb = mcdb;
        _mbdb = mbdb;
        _rpdb = rpdb;
        _cvdb = cvdb;
        _cedb = cedb;
        _sidx2sid = sidx2sid;
        _a2t = a2t;
        _pauseSync = pauseSync;
        _ds = ds;
        _tm = tm;
        _sidx2s = sidx2s;
        _lacl = lacl;
        _localUser = localUser;
    }

    @Override
    public String name()
    {
        return "meta-submit";
    }

    /**
     * Updating the newParent field in the meta changes table would require a scan or an index.
     *
     * A scan could impose a huge penalty on every alias operation.
     * An index would impose a significant ongoing storage cost as well as noticeable write overhead
     * for every local change.
     *
     * Instead we incur a cheap write at alias time and a cheap read upon submission.
     *
     * TODO: further reduce storage overhead by expiring alias entries
     */
    public MetaChange resolveAliasing_(MetaChange c) throws SQLException
    {
        c.newParent = _a2t.dereferenceAliasedOID_(new SOID(c.sidx, c.newParent)).oid();
        return c;
    }

    private final int MAX_BATCH_SIZE = 20;

    private class RemoteLinkProxy
    {
        private final SIndex _sidx;
        private final Map<OID, RemoteLink> _overlay = new HashMap<>();

        RemoteLinkProxy(SIndex sidx)
        {
            _sidx = sidx;
        }

        RemoteLink getParent_(OID oid) throws SQLException
        {
            RemoteLink lnk = _overlay.get(oid);
            return lnk != null ? lnk : _rpdb.getParent_(_sidx, oid);
        }

        public void setParent(OID oid, OID newParent, String newName)
        {
            _overlay.put(oid, new RemoteLink(newParent, newName, -1));
        }
    }

    /**
     * Asynchronously submit the next queued local metadata change(s) to Polaris.
     */
    @Override
    public void submit_(SIndex sidx, AsyncTaskCallback cb) throws Exception
    {
        if (_pauseSync.isPaused()) {
            l.warn("paused {}", sidx);
            cb.onSuccess_(false);
            return;
        }

        if (!_lacl.check_(_localUser.get(), sidx, Permissions.EDITOR)) {
            l.warn("not editor {}", sidx);
            cb.onFailure_(new ExNoPerm());
            return;
        }

        long highest = _cedb.getHighestChangeEpoch_(sidx);
        Long current = _cedb.getChangeEpoch_(sidx);
        // current will only be set if we've migrated to polaris
        // if not migrated to polaris we should ignore this check
        if (current != null && highest > current) {
            l.info("delay submit: current {} < highest {}", current, highest);
            cb.onSuccess_(false);
            return;
        }

        // get next meta change from queue
        RemoteLinkProxy proxy = new RemoteLinkProxy(sidx);
        List<MetaChange> changes = Lists.newArrayList();
        List<BatchOp> ops = Lists.newArrayList();
        Set<Long> noops = new HashSet<>();
        try (IDBIterator<MetaChange> it = _mcdb.getChangesSince_(sidx, Long.MIN_VALUE)) {
            while (it.next_() && ops.size() < MAX_BATCH_SIZE) {
                MetaChange c = resolveAliasing_(it.get_());
                // do not send local changes for objects with buffered changes
                // to ensure consistent resolution of meta/meta conflicts
                if (_mbdb.isBuffered_(new SOID(sidx, c.oid))) {
                    if (ops.isEmpty()) {
                        cb.onFailure_(new ExRetryLater("buffered"));
                        return;
                    }
                    break;
                }
                BatchOp op = op_(c, proxy);
                if (op != null) {
                    changes.add(c);
                    ops.add(op);
                } else {
                    noops.add(c.idx);
                }
            }
        }

        if (!noops.isEmpty()) {
            try (Trans t = _tm.begin_()) {
                for (long idx : noops) {
                    l.info("discard no-op {} {}", sidx, idx);
                    _mcdb.deleteChange_(sidx, idx, t);
                }
                t.commit_();
            }
        }

        if (ops.isEmpty()) {
            cb.onSuccess_(false);
        } else {
            batchSubmit(changes, new Batch(ops), cb);
        }
    }

    /**
     * IMPORTANT: this method should only access IMMUTABLE data from the DB
     *
     * Any access to mutable data must be proxied through an in-memory overlay that accounts
     * for the changes caused by previous operations in the same batch.
     */
    private BatchOp op_(MetaChange c, RemoteLinkProxy proxy) throws SQLException
    {
        SIndex sidx = c.sidx;
        OID parent;
        LocalChange change = new LocalChange();
        change.child = c.oid.toStringFormal();

        RemoteLink lnk = proxy.getParent_(c.oid);

        l.info("op: {} {}{} {} {} {} {}", c.idx, sidx, c.oid, lnk, c.newParent, c.newName, c.migrant);

        if (lnk == null || lnk.parent == null) {
            checkState(c.newParent != null && !c.newParent.isTrash());
            parent = c.newParent;
            change.type = LocalChange.Type.INSERT_CHILD;
            change.childName = c.newName;
            if (c.migrant != null) {
                change.migrant = c.migrant.toStringFormal();
            }
            OA oa = _ds.getOA_(new SOID(sidx, c.oid));
            change.childObjectType = type(oa.type());
            proxy.setParent(c.oid, c.newParent, c.newName);
        } else if (c.newParent.isTrash()) {
            checkState(!lnk.parent.isTrash());
            parent = lnk.parent;
            change.type = LocalChange.Type.REMOVE_CHILD;
            proxy.setParent(c.oid, null, null);
        } else if (lnk.parent.equals(c.newParent) && lnk.name.equals(c.newName)) {
            // no-op move, discard
            return null;
        } else {
            parent = lnk.parent;
            change.type = MOVE_CHILD;
            change.newChildName = c.newName;
            change.newParent = rootOID2SID(sidx, c.newParent).toStringFormal();
            proxy.setParent(c.oid, c.newParent, c.newName);
        }
        return new BatchOp(rootOID2SID(c.sidx, parent).toStringFormal(), change);
    }

    private UniqueID rootOID2SID(SIndex sidx, OID oid)
    {
        return oid.isRoot() ? _sidx2sid.get_(sidx) : oid;
    }

    private static ObjectType type(OA.Type type)
    {
        switch (type) {
        case FILE: return ObjectType.FILE;
        case DIR: return ObjectType.FOLDER;
        case ANCHOR: return ObjectType.STORE;
        default:
            throw new AssertionError();
        }
    }

    private void batchSubmit(List<MetaChange> c, Batch batch, AsyncTaskCallback cb)
    {
        _client.post("/batch/transforms", batch, cb, r -> handleBatch_(c, batch, r));
    }

    private boolean handleBatch_(List<MetaChange> c, Batch batch, HttpResponse resp)
             throws Exception
    {
        int statusCode = resp.getStatus().getCode();
        String body = resp.getContent().toString(BaseUtil.CHARSET_UTF);
        switch (statusCode) {
        case 200: {
            BatchResult r = null;
            try {
                r = GsonUtil.GSON.fromJson(body, BatchResult.class);
            } catch (JsonSyntaxException e) {
                l.warn("invalid json", e.getMessage());
            }
            if (r == null || r.results == null) {
                throw new ExProtocolError("invalid reply: "+ r + " " + body);
            }
            if (r.results.size() > batch.operations.size()) {
                throw new ExProtocolError("invalid result size");
            }

            int failed = 0;
            SIndex sidx = null;

            // TODO: optimistic transaction merging
            for (int i = 0; i < r.results.size(); ++i) {
                BatchOpResult or = r.results.get(i);
                LocalChange lc = batch.operations.get(i).operation;
                if (or.successful) {
                    try (Trans t = _tm.begin_()) {
                        ackSubmission_(c.get(i), lc.type, or.updated, t);
                        t.commit_();
                    } catch (Exception e) {
                        l.warn("failed to ack {} {}", lc, c.get(i));
                        throw e;
                    }
                    if (or.updated.size() > 0) {
                        if (sidx == null) {
                            sidx = c.get(i).sidx;
                        } else {
                            // we segregate update submission by store
                            checkState(sidx == c.get(i).sidx);
                        }
                    }
                } else if (or.errorCode == PolarisError.NAME_CONFLICT) {
                    onConflict_(c.get(i), lc, or.errorMessage);
                } else if (or.errorCode == PolarisError.NO_SUCH_OBJECT) {
                    onMissing_(c.get(i), lc, or.errorMessage);
                } else {
                    // TODO(phoenix): figure out which errors need special handling
                    ++failed;
                    l.warn("batch op failed {} {} {}", or.errorCode, or.errorMessage,
                            GsonUtil.GSON.toJson(batch.operations.get(i)));
                }
            }
            if (failed > 0) throw new ExRetryLater("batch not complete");
            return true;
        }
        case 403:
            throw new ExNoPerm();
        default:
            l.warn("unexpected error {}\n", statusCode, body);
            throw new ExRetryLater("unexpected status code: " + statusCode);
        }
    }

    // necessary to handle sharing/migration corner cases
    // in particular when a subtree is locally moved out of a shared folder before the
    // SHARE transform is received.
    private void onMissing_(MetaChange c, LocalChange change, String body) throws Exception {
        OID oid = new OID(body);
        l.debug("not found {} {} {}", oid, c, change);
        if (((change.type == INSERT_CHILD) || (change.type == MOVE_CHILD)) && c.newParent.equals(oid)) {
            MetaChange p = _mcdb.getFirstChange_(c.sidx, oid);
            if (p == null) {
                l.info("no changes for {}", oid);
                throw new ExRetryLater("not found " + oid);
            }

            // find the first meta change idx for this store
            long base;
            try (IDBIterator<MetaChange> it = _mcdb.getChangesSince_(c.sidx, Long.MIN_VALUE)) {
                checkState(it.next_());
                base = it.get_().idx;
            }

            l.info("move to front {} {} {}", oid, p, base);

            // move parent insert to front
            try (Trans t = _tm.begin_()) {
                checkState(1 == _mcdb.moveChange_(c.sidx, p.idx, base - 1, t));
                t.commit_();
            }
            return;
        }
        throw new ExRetryLater("not found " + oid);
    }

    private boolean onConflict_(MetaChange c, LocalChange change, String body) throws Exception
    {
        OA oa =_ds.getOANullable_(new SOID(c.sidx, c.oid));
        if (oa == null) throw new ExRetryLater("object disappeared");

        OID local = _ds.getChild_(c.sidx, c.newParent, c.newName);
        if (!c.oid.equals(local)) {
            // The change rejected by polaris does not reflect the latest local state therefore we
            // cannot rely on ApplyChangeImpl to resolve the conflict.
            //
            // Ideally we'd omit submission of transient objects but this would introduce
            // serious complexity to correctly handle non-transient objects being moved under
            // transient folders and to avoid issues when folders are moved out of the trash.
            //
            // TODO: revisit once legacy code is burned?
            try (Trans t = _tm.begin_()) {
                // Update transform to use a random name to avoid the conflict, with the
                // expectation that a subsequent rename will overwrite that.
                // Ideally we'd insert the object at its current (parent, name) location
                // directly but that is more complicated as it requires compacting the local
                // transforms log to avoid further conflicts and even worse inconsistencies
                // TODO: safely compact local transforms instead
                String temporaryName = OID.generate().toStringFormal();
                _mcdb.updateChange_(c.sidx, c.idx, temporaryName, t);
                l.info("update conflicting local change {} {} {} {}", c.idx, c.sidx, c.oid,
                        temporaryName);
                t.commit_();
            }
            return true;
        }
        // TODO: temporarily pause submission?
        // TODO: schedule immediate meta fetch?
        // TODO: restart submission when changes are received
        l.info("conflict: exp retry\n{}", body);
        throw new ExRetryLater("conflict");
    }

    /**
     * Detect whether a given remote change matches a submitted but not yet ack'ed local change
     *
     * If a matching local change is found it is ack'ed and the caller can safely discard the
     * remote change.
     *
     * @return whether the remote change matched a submitted change
     */
    public boolean ackMatchingSubmittedMetaChange_(SIndex sidx, RemoteChange rc, RemoteLink lnk,
            Trans t)
            throws SQLException, ExFormatError
    {
        if (rc.transformType == Type.SHARE) return false;

        MetaChange lc = submittedMetaChange_(sidx);
        if (lc == null) return false;
        LocalChange.Type lct = matches_(lc, rc, lnk);
        if (lct == null) return false;

        l.info("ack submission: {}", lc);
        UpdatedObject ack = new UpdatedObject();
        ack.object = new LogicalObject();
        ack.object.oid = rc.oid;
        ack.object.version = rc.newVersion;
        ack.transformTimestamp = rc.logicalTimestamp;
        ackSubmission_(lc, lct, ImmutableList.of(ack), t);
        return true;
    }

    private @Nullable MetaChange submittedMetaChange_(SIndex sidx) throws SQLException
    {
        // TODO: only return change if it actually was submitted (use some sort of epoch)
        try (IDBIterator<MetaChange> it = _mcdb.getChangesSince_(sidx, Long.MIN_VALUE)) {
            return it.next_() ? resolveAliasing_(it.get_()) : null;
        }
    }

    private LocalChange.Type matches_(MetaChange lc, RemoteChange rc, RemoteLink lnk)
            throws SQLException, ExFormatError
    {
        if (!lc.oid.equals(new OID(rc.child))) return null;

        OID oldParent = lnk != null ? lnk.parent : null;
        switch (rc.transformType) {
        case INSERT_CHILD:
            if (!rc.childName.equals(lc.newName) || !rc.oid.equals(lc.newParent)) return null;

            return oldParent == null ? LocalChange.Type.INSERT_CHILD : MOVE_CHILD;
        case REMOVE_CHILD:
        case DELETE_CHILD:
            return rc.oid.equals(oldParent)
                    && lc.newParent.isTrash()
                    ? LocalChange.Type.REMOVE_CHILD : null;
        case RENAME_CHILD:
            return rc.oid.equals(oldParent)
                    && rc.oid.equals(lc.newParent)
                    && rc.childName.equals(lc.newName)
                    ? MOVE_CHILD : null;
        default:
            throw new AssertionError();
        }
    }

    private void ackSubmission_(MetaChange c, LocalChange.Type transformType,
            List<UpdatedObject> acks, Trans t)
            throws SQLException
    {
        // TODO(phoenix): safety check to ensure ACK is resilient to local changes after submit
        if (!_mcdb.deleteChange_(c.sidx, c.idx, t)) {
            l.warn("already ack'ed {}", c.idx);
            return;
        }

        UniqueID sid = _sidx2sid.get_(c.sidx);
        for (UpdatedObject ack : acks) {
            OID oid = sid.equals(ack.object.oid) ? OID.ROOT : new OID(ack.object.oid);
            Long version = Objects.firstNonNull(_cvdb.getVersion_(c.sidx, oid), 0L);

            // folder version is updated only when ALL changes up to that version are known
            // otherwise some changes would be incorrectly filtered out in ApplyChange
            if (version + 1 == ack.object.version) {
                l.debug("{}{} = {}", c.sidx, oid, ack.object.version);
                _cvdb.setVersion_(c.sidx, oid, ack.object.version, t);
            }
        }

        // any buffered remote change for the affected child can be safely discarded
        _mbdb.remove_(c.sidx, c.oid, t);

        switch (transformType) {
        case INSERT_CHILD: {
            checkState(acks.size() == 1);
            // on unlink/reinstall, races between submission, buffered inserts and historical SHARE
            // can lead to cases where the remote link is present when an INSERT is ack'd
            if (_rpdb.getParent_(c.sidx, c.oid) != null) {
                _rpdb.updateParent_(c.sidx, c.oid, c.newParent, c.newName,
                        acks.get(0).transformTimestamp, t);
            } else {
                _rpdb.insertParent_(c.sidx, c.oid, c.newParent, c.newName,
                        acks.get(0).transformTimestamp, t);
            }

            OA oa = _ds.getOANullable_(new SOID(c.sidx, c.oid));
            if (oa != null && !oa.isExpelled() && oa.isFile()) {
                // fast retry content submission
                ((DaemonPolarisStore) _sidx2s.get_(c.sidx)).contentSubmitter().startOnCommit_(t);
            }
            break;
        }
        case MOVE_CHILD:
            // IMPORTANT: this relies on the new parent being first in the list of updated objects
            // returned by Polaris
            checkState(acks.size() == 1 || acks.size() == 2);
            _rpdb.updateParent_(c.sidx, c.oid, c.newParent, c.newName,
                    acks.get(0).transformTimestamp, t);
            break;
        case REMOVE_CHILD:
            checkState(acks.size() == 1);
            _rpdb.removeParent_(c.sidx, c.oid, t);
            break;
        default:
            throw new AssertionError();
        }
    }
}
