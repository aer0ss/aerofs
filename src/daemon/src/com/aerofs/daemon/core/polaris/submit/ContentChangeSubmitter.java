/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.submit;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.polaris.PolarisClient;
import com.aerofs.daemon.core.polaris.api.Ack;
import com.aerofs.daemon.core.polaris.GsonUtil;
import com.aerofs.daemon.core.polaris.api.LocalChange;
import com.aerofs.daemon.core.polaris.api.LocalChange.Type;
import com.aerofs.daemon.core.polaris.api.UpdatedObject;
import com.aerofs.daemon.core.polaris.async.AsyncTaskCallback;
import com.aerofs.daemon.core.polaris.db.CentralVersionDatabase;
import com.aerofs.daemon.core.polaris.db.ContentChangesDatabase;
import com.aerofs.daemon.core.polaris.db.ContentChangesDatabase.ContentChange;
import com.aerofs.daemon.core.polaris.db.RemoteContentDatabase;
import com.aerofs.daemon.core.status.PauseSync;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.sched.ExponentialRetry.ExRetryLater;
import com.google.common.base.Objects;
import com.google.inject.Inject;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;

import java.io.IOException;
import java.sql.SQLException;

import static com.google.common.base.Preconditions.checkState;

/**
 * Submit content changes to Polaris
 *
 * This class handles protocol encoding/decoding and DB cleanup after successful submission.
 *
 * For now each change is submitted in its own HTTP request but in the future the /batch route
 * should be used to reduce the number of round-trips.
 */
public class ContentChangeSubmitter implements Submitter
{
    private final static Logger l = Loggers.getLogger(ContentChangeSubmitter.class);

    private final PolarisClient _client;
    private final ContentChangesDatabase _ccdb;
    private final CentralVersionDatabase _cvdb;
    private final RemoteContentDatabase _rcdb;
    private final PauseSync _pauseSync;
    private final DirectoryService _ds;
    private final IPhysicalStorage _ps;
    private final TransManager _tm;

    @Inject
    public ContentChangeSubmitter(PolarisClient client, ContentChangesDatabase ccdb,
            RemoteContentDatabase rcdb, CentralVersionDatabase cvdb, PauseSync pauseSync,
            DirectoryService ds, IPhysicalStorage ps, TransManager tm)
    {
        _client = client;
        _rcdb = rcdb;
        _ccdb = ccdb;
        _cvdb = cvdb;
        _pauseSync = pauseSync;
        _ds = ds;
        _ps = ps;
        _tm = tm;
    }

    @Override
    public String name()
    {
        return "content-submit";
    }

    /**
     * Asynchronously submit the next queued local metadata change to Polaris.
     *
     * TODO: batch multiple changes in a single request
     */
    @Override
    public void submit_(SIndex sidx, AsyncTaskCallback cb)
            throws SQLException
    {
        if (_pauseSync.isPaused()) {
            l.warn("paused {}", sidx);
            cb.onFailure_(new ExRetryLater("paused"));
            return;
        }

        // TODO: avoid wasteful repeated iteration of ignored entries
        try (IDBIterator<ContentChange> it = _ccdb.getChanges_(sidx)) {
            while (it.next_()) {
                if (submit_(it.get_(), cb)) {
                    return;
                }
            }
            cb.onSuccess_(false);
        }
    }

    private boolean submit_(ContentChange c, AsyncTaskCallback cb)
            throws SQLException
    {
        OA oa = _ds.getOA_(new SOID(c.sidx, c.oid));
        // don't submit changes when a conflict exists
        if (oa.cas().size() != 1) return false;
        CA ca = oa.caMaster();

        ContentHash h = _ds.getCAHash_(new SOKID(oa.soid(), KIndex.MASTER));
        checkState(h != null);

        try {
            IPhysicalFile pf = _ps.newFile_(_ds.resolve_(oa), KIndex.MASTER);
            if (pf.wasModifiedSince(ca.mtime(), ca.length())) return false;
        } catch (IOException e) {
            return false;
        }

        LocalChange change = new LocalChange();
        change.type = Type.UPDATE_CONTENT;
        // TODO(phoenix): use CA base version?
        change.localVersion = Objects.firstNonNull(_cvdb.getVersion_(c.sidx, c.oid), 0L);
        change.mtime = ca.mtime();
        change.size = ca.length();
        change.hash = h.toHex();

        submit(c, change, cb);
        return true;
    }

    private void submit(ContentChange c, LocalChange change, AsyncTaskCallback cb)
    {
        FullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
                "/objects/" + c.oid.toStringFormal(),
                Unpooled.wrappedBuffer(BaseUtil.string2utf(GsonUtil.GSON.toJson(change))));
        req.headers().add(Names.CONTENT_TYPE, "application/json");
        req.headers().add(Names.TRANSFER_ENCODING, "chunked");

        _client.send(req, cb, r -> handle_(c, change, r));
    }

    private boolean handle_(ContentChange c, LocalChange change, FullHttpResponse r)
            throws Exception
    {
        int statusCode = r.status().code();
        String body = r.content().toString(BaseUtil.CHARSET_UTF);
        switch (statusCode) {
        case 200:
            return onSuccess_(c, change, body);
        case 409:
            return onConflict_(c, change, body);
        default:
            l.warn("unexpected error {}\n{}", statusCode, body);
            throw new ExRetryLater("unexpected status code: " + statusCode);
        }
    }

    private boolean onSuccess_(ContentChange c, LocalChange change, String body)
            throws Exception
    {
        Ack ack = GsonUtil.GSON.fromJson(body, Ack.class);
        try (Trans t = _tm.begin_()) {
            if (ack.updated.size() != 1) throw new ExProtocolError();
            ackSubmission_(c, ack.updated.get(0), t);
            t.commit_();
        }
        return true;
    }

    private boolean onConflict_(ContentChange c, LocalChange change, String body)
    {
        l.info("conflict {} {}: {}", c.sidx, c.oid, body);
        // TODO: ?
        return true;
    }

    private void ackSubmission_(ContentChange c, UpdatedObject updated, Trans t)
            throws SQLException
    {
        checkState(c.oid.equals(updated.object.oid));

        l.info("content advertised {}{} {}", c.sidx, c.oid, updated.object.version);
        Long v = _cvdb.getVersion_(c.sidx, c.oid);
        if (v == null || v < updated.object.version) {
            _cvdb.setVersion_(c.sidx, c.oid, updated.object.version, t);
            _rcdb.deleteUpToVersion_(c.sidx, c.oid, updated.object.version, t);
        }

        if (!_ccdb.deleteChange_(c.sidx, c.idx, t)) {
            l.info("submitted change now obsolete {}{}: {}", c.sidx, c.oid, c.idx);
        }

        // TODO: ?
    }
}
