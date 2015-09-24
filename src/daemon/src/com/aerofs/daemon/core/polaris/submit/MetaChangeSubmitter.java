/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.submit;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.ids.OID;
import com.aerofs.ids.UniqueID;
import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.polaris.GsonUtil;
import com.aerofs.daemon.core.polaris.api.Batch;
import com.aerofs.daemon.core.polaris.api.Batch.BatchOp;
import com.aerofs.daemon.core.polaris.api.BatchResult;
import com.aerofs.daemon.core.polaris.api.BatchResult.BatchOpResult;
import com.aerofs.daemon.core.polaris.api.BatchResult.PolarisError;
import com.aerofs.daemon.core.polaris.api.LocalChange;
import com.aerofs.daemon.core.polaris.api.LogicalObject;
import com.aerofs.daemon.core.polaris.api.ObjectType;
import com.aerofs.daemon.core.polaris.api.RemoteChange;
import com.aerofs.daemon.core.polaris.api.RemoteChange.Type;
import com.aerofs.daemon.core.polaris.api.UpdatedObject;
import com.aerofs.daemon.core.polaris.async.AsyncTaskCallback;
import com.aerofs.daemon.core.polaris.db.CentralVersionDatabase;
import com.aerofs.daemon.core.polaris.db.MetaBufferDatabase;
import com.aerofs.daemon.core.polaris.db.MetaChangesDatabase;
import com.aerofs.daemon.core.polaris.PolarisClient;
import com.aerofs.daemon.core.polaris.db.MetaChangesDatabase.MetaChange;
import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase.RemoteLink;
import com.aerofs.daemon.core.status.PauseSync;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.core.store.PolarisStore;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private final PolarisClient _client;
    private final MapAlias2Target _a2t;
    private final MetaChangesDatabase _mcdb;
    private final MetaBufferDatabase _mbdb;
    private final RemoteLinkDatabase _rpdb;
    private final IMapSIndex2SID _sidx2sid;
    private final CentralVersionDatabase _cvdb;
    private final PauseSync _pauseSync;
    private final DirectoryService _ds;
    private final TransManager _tm;
    private final MapSIndex2Store _sidx2s;

    @Inject
    public MetaChangeSubmitter(PolarisClient client, MetaChangesDatabase mcdb,
            MetaBufferDatabase mbdb, RemoteLinkDatabase rpdb, CentralVersionDatabase cvdb,
            IMapSIndex2SID sidx2sid, MapAlias2Target a2t, PauseSync pauseSync,
            DirectoryService ds, TransManager tm, MapSIndex2Store sidx2s)
    {
        _client = client;
        _mcdb = mcdb;
        _mbdb = mbdb;
        _rpdb = rpdb;
        _cvdb = cvdb;
        _sidx2sid = sidx2sid;
        _a2t = a2t;
        _pauseSync = pauseSync;
        _ds = ds;
        _tm = tm;
        _sidx2s = sidx2s;
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
        private final Map<OID, OID> _overlay = new HashMap<>();

        RemoteLinkProxy(SIndex sidx)
        {
            _sidx = sidx;
        }

        OID getParent_(OID oid) throws SQLException
        {
            if (_overlay.containsKey(oid)) return  _overlay.get(oid);
            RemoteLink lnk = _rpdb.getParent_(_sidx, oid);
            return lnk != null ? lnk.parent : null;
        }

        public void setParent(OID oid, OID newParent)
        {
            _overlay.put(oid, newParent);
        }
    }

    /**
     * Asynchronously submit the next queued local metadata change(s) to Polaris.
     */
    @Override
    public void submit_(SIndex sidx, AsyncTaskCallback cb) throws SQLException
    {
        if (_pauseSync.isPaused()) {
            l.warn("paused {}", sidx);
            cb.onFailure_(new ExRetryLater("paused"));
            return;
        }

        // get next meta change from queue
        RemoteLinkProxy proxy = new RemoteLinkProxy(sidx);
        List<MetaChange> changes = Lists.newArrayList();
        List<BatchOp> ops = Lists.newArrayList();
        try (IDBIterator<MetaChange> it = _mcdb.getChangesSince_(sidx, 0)) {
            while (it.next_() && ops.size() < MAX_BATCH_SIZE) {
                MetaChange c = resolveAliasing_(it.get_());
                changes.add(c);
                ops.add(op_(c, proxy));
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

        OID oldParent = proxy.getParent_(c.oid);
        // FIXME: null parent caused by sharing?

        l.info("op: {} {} {} {} {}", sidx, c.oid, oldParent, c.newParent, c.newName);

        // FIXME: conversion pitfall: deletion of auto-joined store
        if (oldParent == null) {
            checkState(c.newParent != null && !c.newParent.isTrash());
            parent = c.newParent;
            change.type = LocalChange.Type.INSERT_CHILD;
            change.childName = c.newName;
            OA oa = _ds.getOA_(new SOID(sidx, c.oid));
            change.childObjectType = type(oa.type());
            proxy.setParent(c.oid, c.newParent);
        } else if (c.newParent.isTrash()) {
            checkState(!oldParent.isTrash());
            parent = oldParent;
            change.type = LocalChange.Type.REMOVE_CHILD;
            proxy.setParent(c.oid, null);
        } else {
            parent = oldParent;
            change.type = LocalChange.Type.MOVE_CHILD;
            change.newChildName = c.newName;
            change.newParent = rootOID2SID(sidx, c.newParent).toStringFormal();
            proxy.setParent(c.oid, c.newParent);
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
                    onConflict_(c.get(i), lc, "");
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
        default:
            l.warn("unexpected error {}\n", statusCode, body);
            throw new ExRetryLater("unexpected status code: " + statusCode);
        }
    }

    private boolean onConflict_(MetaChange c, LocalChange change, String body) throws Exception
    {
        OA oa = _ds.getOANullable_(new SOID(c.sidx, c.oid));

        if (oa == null || !oa.name().equals(c.newName) || !oa.parent().equals(c.newParent)) {
            /**
             * Complication: name conflicts in the middle of a chain of renames/moves
             *
             * we need to clean up the change queue here because it won't be done by
             * the fetcher until another peer renames the same child, which may never
             * happen.
             */
            l.info("discard conflicting local change {} {} {}", c.idx, c.sidx, c.oid);
            try (Trans t = _tm.begin_()) {
                _mcdb.deleteChange_(c.sidx, c.idx, t);
                t.commit_();
            }
            return true;
        }

        // TODO: on PARENT_CONFLICT
        //  -> wait until fetch complete
        //  -> if still present, use a new OID

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
        try (IDBIterator<MetaChange> it = _mcdb.getChangesSince_(sidx, 0)) {
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

            return oldParent == null ? LocalChange.Type.INSERT_CHILD : LocalChange.Type.MOVE_CHILD;
        case REMOVE_CHILD:
        case DELETE_CHILD:
            return rc.oid.equals(oldParent)
                    && lc.newParent.isTrash()
                    ? LocalChange.Type.REMOVE_CHILD : null;
        case RENAME_CHILD:
            return rc.oid.equals(oldParent)
                    && rc.oid.equals(lc.newParent)
                    && rc.childName.equals(lc.newName)
                    ? LocalChange.Type.MOVE_CHILD : null;
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
                _cvdb.setVersion_(c.sidx, oid, ack.object.version, t);
            }
        }

        // any buffered remote change for the affected child can be safely discarded
        _mbdb.remove_(c.sidx, c.oid, t);

        switch (transformType) {
        case INSERT_CHILD:
            checkState(acks.size() == 1);
            _rpdb.insertParent_(c.sidx, c.oid, c.newParent, c.newName,
                    acks.get(0).transformTimestamp, t);

            OA oa = _ds.getOANullable_(new SOID(c.sidx, c.oid));
            if (oa != null && !oa.isExpelled() && oa.isFile()) {
                // fast retry content submission
                ((PolarisStore)_sidx2s.get_(c.sidx)).contentSubmitter().startOnCommit_(t);
            }
            break;
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
