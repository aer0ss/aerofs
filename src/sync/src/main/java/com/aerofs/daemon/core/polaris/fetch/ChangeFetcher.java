/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.fetch;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.polaris.GsonUtil;
import com.aerofs.daemon.core.polaris.api.RemoteChange;
import com.aerofs.daemon.core.polaris.api.Transforms;
import com.aerofs.daemon.core.polaris.async.AsyncTaskCallback;
import com.aerofs.daemon.core.polaris.db.ChangeEpochDatabase;
import com.aerofs.daemon.core.polaris.PolarisClient;
import com.aerofs.daemon.core.status.PauseSync;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.sched.ExponentialRetry.ExRetryLater;
import com.google.inject.Inject;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;

/**
 * Fetch changes from Polaris.
 *
 * This class handles protocol encoding/decoding and manages change epochs.
 *
 * Scheduling is managed by {@link ChangeFetchScheduler}
 * HTTP communication is delegated to {@link com.aerofs.daemon.core.polaris.PolarisClient}
 * Application of remote changes is delegated to {@link ApplyChange}
 */
public class ChangeFetcher
{
    private final static Logger l = Loggers.getLogger(ChangeFetcher.class);

    private final PolarisClient _client;
    private final ApplyChange _at;
    private final IMapSIndex2SID _sidx2sid;
    private final IMapSID2SIndex _sid2sidx;
    private final ChangeEpochDatabase _cedb;
    private final TransManager _tm;

    private final PauseSync _pauseSync;

    @Inject
    public ChangeFetcher(PolarisClient client, PauseSync pauseSync, ChangeEpochDatabase cedb,
            ApplyChange at, IMapSIndex2SID sidx2sid, IMapSID2SIndex sid2sidx, TransManager tm)
    {
        _client = client;
        _pauseSync = pauseSync;
        _at = at;
        _cedb = cedb;
        _tm = tm;
        _sidx2sid = sidx2sid;
        _sid2sidx = sid2sidx;
    }

    public void fetch_(SIndex sidx, AsyncTaskCallback cb) throws Exception
    {
        if (_pauseSync.isPaused()) {
            l.warn("paused {}", sidx);
            cb.onFailure_(new ExRetryLater("paused"));
            return;
        }

        SID sid = _sidx2sid.get_(sidx);
        Long epoch = _cedb.getChangeEpoch_(sidx);
        if (epoch == null) {
            l.warn("No fetch epoch for {} {}", sidx, sid);
            cb.onFailure_(new ExAborted("no fetch epoch"));
            return;
        }

        applyBufferedChanges_(sidx, epoch);

        fetch(sid, epoch, cb);
    }

    private final static long CHANGES_PER_REQUEST = 100;

    private void fetch(SID sid, long lastLocalEpoch, AsyncTaskCallback cb)
    {
        QueryStringEncoder encoder = new QueryStringEncoder(
                "/transforms/" + sid.toStringFormal());
        encoder.addParam("since", Long.toString(lastLocalEpoch));
        encoder.addParam("count", Long.toString(CHANGES_PER_REQUEST));
        HttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                encoder.toString());
        req.headers().set(HttpHeaderNames.CONTENT_LENGTH, "0");

        _client.send(req, cb, r -> handle_(sid, r));
    }

    private boolean handle_(SID sid, FullHttpResponse r) throws Exception
    {
        // TODO: streaming response processing
        String content = r.content().toString(BaseUtil.CHARSET_UTF);
        if (!r.status().equals(HttpResponseStatus.OK)) {
            l.info("polaris error {}\n{}", r.status(), content);
            if (r.status().codeClass().equals(HttpStatusClass.SERVER_ERROR)) {
                throw new ExRetryLater(r.status().reasonPhrase());
            }
            throw new ExProtocolError(r.status().reasonPhrase());
        }

        SIndex sidx = _sid2sidx.getNullable_(sid);
        if (sidx == null) {
            l.info("ignoring response for absent store {}", sid.toStringFormal());
            return false;
        }

        Transforms c = GsonUtil.GSON.fromJson(content, Transforms.class);

        if (c.transforms == null || c.transforms.isEmpty()) {
            l.debug("no transforms");
            return false;
        }

        try (Trans t = _tm.begin_()) {
            _cedb.setRemoteChangeEpoch_(sidx, c.maxTransformCount, t);
            t.commit_();
        }

        long lastLogicalTimestamp = 0;
        for (RemoteChange rc : c.transforms) {
            // Polaris use the SID as the root object of a store
            // we need to convert that back to OID.ROOT for local processing
            if (_sidx2sid.get_(sidx).equals(rc.oid)) {
                rc.oid = OID.ROOT;
            }

            try (Trans t = _tm.begin_()) {
                _at.apply_(sidx, rc, c.maxTransformCount, t);
                _cedb.setChangeEpoch_(sidx, rc.logicalTimestamp, t);
                t.commit_();
            }
            if (rc.logicalTimestamp <= lastLogicalTimestamp) {
                throw new ExProtocolError();
            }
            lastLogicalTimestamp = rc.logicalTimestamp;
        }
        applyBufferedChanges_(sidx, lastLogicalTimestamp);
        return true;
    }

    private void applyBufferedChanges_(SIndex sidx, long timestamp) throws Exception
    {
        // NB: can throw for exp retry
        try (Trans t = _tm.begin_()) {
            _at.applyBufferedChanges_(sidx, timestamp, t);
            t.commit_();
        }
    }
}
