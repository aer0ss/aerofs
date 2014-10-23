/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.fetch;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.polaris.GsonUtil;
import com.aerofs.daemon.core.polaris.api.RemoteChange;
import com.aerofs.daemon.core.polaris.async.AsyncTaskCallback;
import com.aerofs.daemon.core.polaris.db.ChangeEpochDatabase;
import com.aerofs.daemon.core.polaris.PolarisClient;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.id.SIndex;
import com.aerofs.oauth.OAuthVerificationHandler.UnexpectedResponse;
import com.google.inject.Inject;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringEncoder;
import org.slf4j.Logger;

import java.util.List;

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

    @Inject
    public ChangeFetcher(PolarisClient client, ChangeEpochDatabase cedb,
            ApplyChange at, IMapSIndex2SID sidx2sid, IMapSID2SIndex sid2sidx, TransManager tm)
    {
        _client = client;
        _at = at;
        _cedb = cedb;
        _tm = tm;
        _sidx2sid = sidx2sid;
        _sid2sidx = sid2sidx;
    }

    public void fetch_(SIndex sidx, AsyncTaskCallback cb) throws Exception
    {
        SID sid = _sidx2sid.get_(sidx);
        Long epoch = _cedb.getChangeEpoch_(sidx);
        if (epoch == null) {
            l.warn("No fetch epoch for {} {}", sidx, sid);
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
        req.headers().set(Names.CONTENT_LENGTH, "0");

        _client.send(req, cb, r -> handle_(sid, r));
    }

    private static class Changes
    {
        long maxTransformCount;
        List<RemoteChange> transforms;
    }

    private boolean handle_(SID sid, FullHttpResponse r) throws Exception
    {
        // TODO: streaming response processing
        String content = r.content().toString(BaseUtil.CHARSET_UTF);
        if (!r.status().equals(HttpResponseStatus.OK)) {
            l.info("polaris error {}\n{}", r.status(), content);
            throw new UnexpectedResponse(r.status().code());
        }

        SIndex sidx = _sid2sidx.getNullable_(sid);
        Changes c = GsonUtil.GSON.fromJson(content, Changes.class);

        if (sidx == null || c.transforms == null || c.transforms.isEmpty()) {
            return false;
        }

        Trans t = _tm.begin_();
        try {
            _cedb.setRemoteChangeEpoch_(sidx, c.maxTransformCount, t);
            t.commit_();
        } finally {
            t.end_();
        }

        long lastLogicalTimestamp = 0;
        for (RemoteChange rc : c.transforms) {
            // Polaris use the SID as the root object of a store
            // we need to convert that back to OID.ROOT for local processing
            if (_sidx2sid.get_(sidx).equals(rc.oid)) {
                rc.oid = OID.ROOT;
            }

            t = _tm.begin_();
            try {
                _at.apply_(sidx, rc, c.maxTransformCount, t);
                _cedb.setChangeEpoch_(sidx, rc.logicalTimestamp, t);
                t.commit_();
            } finally {
                t.end_();
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
        Trans t = _tm.begin_();
        try {
            _at.applyBufferedChanges_(sidx, timestamp, t);
            t.commit_();
        } finally {
            t.end_();
        }
    }
}
