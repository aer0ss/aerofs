/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.submit;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.polaris.api.Ack;
import com.aerofs.daemon.core.polaris.GsonUtil;
import com.aerofs.daemon.core.polaris.api.LocalChange;
import com.aerofs.daemon.core.polaris.api.LogicalObject;
import com.aerofs.daemon.core.polaris.api.ObjectType;
import com.aerofs.daemon.core.polaris.api.RemoteChange;
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
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.sched.ExponentialRetry.ExRetryLater;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.List;

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

    @Inject
    public MetaChangeSubmitter(PolarisClient client, MetaChangesDatabase mcdb,
            MetaBufferDatabase mbdb, RemoteLinkDatabase rpdb, CentralVersionDatabase cvdb,
            IMapSIndex2SID sidx2sid, MapAlias2Target a2t, PauseSync pauseSync,
            DirectoryService ds, TransManager tm)
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

    /**
     * Asynchronously submit the next queued local metadata change to Polaris.
     *
     * TODO: batch multiple changes in a single request
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
        MetaChange c;
        try (IDBIterator<MetaChange> it = _mcdb.getChangesSince_(sidx, 0)) {
            if (!it.next_()) {
                cb.onSuccess_(false);
                return;
            }

            c = resolveAliasing_(it.get_());
        }

        c.newParent = _a2t.dereferenceAliasedOID_(new SOID(sidx, c.newParent)).oid();

        OID parent;
        LocalChange change = new LocalChange();
        change.child = c.oid.toStringFormal();

        RemoteLink lnk = _rpdb.getParent_(sidx, c.oid);
        OID oldParent = lnk != null ? lnk.parent : null;

        // FIXME: conversion pitfall: deletion of auto-joined store
        if (oldParent == null) {
            checkState(c.newParent != null && !c.newParent.isTrash());
            parent = c.newParent;
            change.type = LocalChange.Type.INSERT_CHILD;
            change.childName = c.newName;
            OA oa = _ds.getOA_(new SOID(sidx, c.oid));
            change.childObjectType = type(oa.type());
        } else if (c.newParent.isTrash()) {
            checkState(!oldParent.isTrash());
            parent = oldParent;
            change.type = LocalChange.Type.REMOVE_CHILD;
        } else {
            parent = oldParent;
            change.type = LocalChange.Type.MOVE_CHILD;
            change.newChildName = c.newName;
            change.newParent = rootOID2SID(sidx, c.newParent).toStringFormal();
        }
        submit(c, parent, change, cb);
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
        case ANCHOR: return ObjectType.MOUNT_POINT;
        default:
            throw new AssertionError();
        }
    }

    private void submit(MetaChange c, OID oid, LocalChange change,
            AsyncTaskCallback cb)
    {
        FullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
                "/objects/" + rootOID2SID(c.sidx, oid).toStringFormal(),
                Unpooled.wrappedBuffer(BaseUtil.string2utf(GsonUtil.GSON.toJson(change))));
        req.headers().add(Names.CONTENT_TYPE, "application/json");
        req.headers().add(Names.TRANSFER_ENCODING, "chunked");

        _client.send(req, cb, r -> handle_(c, change, r));
    }

    private boolean handle_(MetaChange c, LocalChange change, FullHttpResponse resp)
            throws Exception
    {
        int statusCode = resp.status().code();
        String body = resp.content().toString(BaseUtil.CHARSET_UTF);
        switch (statusCode) {
        case 200:
            return onSuccess_(c, change, body);
        case 409:
            return onConflict_(c, change, body);
        default:
            l.warn("unexpected error {}\n", statusCode, body);
            throw new ExRetryLater("unexpected status code: " + statusCode);
        }
    }

    private boolean onSuccess_(MetaChange c, LocalChange change, String body) throws Exception
    {
        Ack ack = GsonUtil.GSON.fromJson(body, Ack.class);
        try (Trans t = _tm.begin_()) {
            ackSubmission_(c, change.type, ack.updated, t);
            t.commit_();
        }
        return true;
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
        MetaChange lc = submittedMetaChange_(sidx);
        if (lc == null) return false;
        LocalChange.Type lct = matches_(lc, rc, lnk);
        if (lct == null) return false;

        l.info("ack submission");
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
            throws SQLException, ExFormatError
    {
        // TODO: safety check to ensure ACK is resilient to local changes after submit
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

            if (_ds.getOA_(new SOID(c.sidx, c.oid)).type() == OA.Type.FILE) {
                // TODO: on successful file creation, add CONTENT entry to queue
                // TODO: what if object was locally deleted after submission
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
