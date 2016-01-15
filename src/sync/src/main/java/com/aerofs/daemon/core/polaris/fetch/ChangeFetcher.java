/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.fetch;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.polaris.GsonUtil;
import com.aerofs.daemon.core.polaris.PolarisClient;
import com.aerofs.daemon.core.polaris.api.RemoteChange;
import com.aerofs.daemon.core.polaris.api.Transforms;
import com.aerofs.daemon.core.polaris.async.AsyncTaskCallback;
import com.aerofs.daemon.core.polaris.db.ChangeEpochDatabase;
import com.aerofs.daemon.core.status.PauseSync;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.sched.ExponentialRetry.ExRetryLater;
import com.google.common.base.Objects;
import com.google.inject.Inject;
import org.jboss.netty.handler.codec.http.*;
import org.slf4j.Logger;

import java.util.ArrayList;
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
    private final MapSIndex2Store _sidx2store;
    private final ChangeEpochDatabase _cedb;
    private final TransManager _tm;

    private final PauseSync _pauseSync;

    private final List<Listener> _listeners = new ArrayList<>();

    public interface Listener {
        void updated_(SOID soid, DID did, Trans t);
    }

    @Inject
    public ChangeFetcher(PolarisClient client, PauseSync pauseSync, ChangeEpochDatabase cedb,
            ApplyChange at, IMapSIndex2SID sidx2sid, IMapSID2SIndex sid2sidx, TransManager tm,
            MapSIndex2Store sidx2store)
    {
        _client = client;
        _pauseSync = pauseSync;
        _at = at;
        _cedb = cedb;
        _tm = tm;
        _sidx2sid = sidx2sid;
        _sid2sidx = sid2sidx;
        _sidx2store = sidx2store;
    }

    public void addListener_(Listener l) {
        _listeners.add(l);
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
        DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                encoder.toString());
        req.headers().set(HttpHeaders.Names.CONTENT_LENGTH, "0");

        _client.send(req, cb, r -> handle_(sid, lastLocalEpoch, r));
    }

    private boolean handle_(SID sid, long lastLocalEpoch, HttpResponse r) throws Exception
    {
        // TODO: streaming response processing
        String content = r.getContent().toString(BaseUtil.CHARSET_UTF);
        if (!r.getStatus().equals(HttpResponseStatus.OK)) {
            l.info("polaris error {}\n{}", r.getStatus(), content);
            if (r.getStatus().equals(HttpResponseStatus.FORBIDDEN)) {
                throw new ExNoPerm();
            }
            if (r.getStatus().getCode() >= 500) {
                throw new ExRetryLater(r.getStatus().getReasonPhrase());
            }
            throw new ExProtocolError(r.getStatus().getReasonPhrase());
        }

        SIndex sidx = _sid2sidx.getNullable_(sid);
        if (sidx == null) {
            l.info("ignoring response for absent store {}", sid.toStringFormal());
            return false;
        }
        long epochBoundary = _cedb.getHighestChangeEpoch_(sidx);

        Transforms c = GsonUtil.GSON.fromJson(content, Transforms.class);

        if (c.transforms == null || c.transforms.isEmpty()) {
            l.debug("no transforms");
            // the max transform count may be strictly superior to the last transform for the store
            // if transform epochs are shared by multiple stores. In this case we need to apply
            // buffered changes for the max reported epoch when all transforms have been received
            // otherwise we risk holding on buffered changes until a new changes is made in this
            // store.
            applyBufferedChanges_(sidx, c.maxTransformCount);
            if (epochBoundary > lastLocalEpoch) {
                try (Trans t = _tm.begin_()) {
                    _cedb.setHighestChangeEpoch_(sidx, lastLocalEpoch, t);
                    t.commit_();
                }
                _sidx2store.get_(sidx).startSubmissions();
            }
            return false;
        }

        long lastLogicalTimestamp = Objects.firstNonNull(_cedb.getChangeEpoch_(sidx), 0L);
        for (RemoteChange rc : c.transforms) {
            if (rc.logicalTimestamp <= lastLogicalTimestamp) {
                throw new ExProtocolError();
            }
            // Polaris use the SID as the root object of a store
            // we need to convert that back to OID.ROOT for local processing
            if (_sidx2sid.get_(sidx).equals(rc.oid)) {
                rc.oid = OID.ROOT;
            }
            try (Trans t = _tm.begin_()) {
                DID did = new DID(rc.originator);
                _listeners.forEach(l -> {
                    if (rc.child == null) {
                        l.updated_(new SOID(sidx, new OID(rc.oid)), did, t);
                    } else {
                        l.updated_(new SOID(sidx, new OID(rc.child)), did, t);
                    }
                });
                _at.apply_(sidx, rc, c.maxTransformCount, t);
                _cedb.setChangeEpoch_(sidx, rc.logicalTimestamp, t);
                t.commit_();
            }
            lastLogicalTimestamp = rc.logicalTimestamp;
        }
        applyBufferedChanges_(sidx, lastLogicalTimestamp);
        if (epochBoundary > lastLocalEpoch && lastLogicalTimestamp >= epochBoundary) {
            _sidx2store.get_(sidx).startSubmissions();
        }
        return true;
    }

    private void applyBufferedChanges_(SIndex sidx, long timestamp) throws Exception
    {
        // NB: can throw for exp retry
        l.debug("apply buffered {} {}", sidx, timestamp);
        try (Trans t = _tm.begin_()) {
            _at.applyBufferedChanges_(sidx, timestamp, t);
            t.commit_();
        }
    }
}
