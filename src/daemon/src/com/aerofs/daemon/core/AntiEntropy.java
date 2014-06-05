package com.aerofs.daemon.core;

import com.aerofs.base.C;
import com.aerofs.base.ElapsedTimer;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.net.To;
import com.aerofs.daemon.core.protocol.GetVersionsRequest;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.core.store.Store;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.lib.Util;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The AntiEntropy algorithm is responsible for scheduling the emission of GetVers requests
 * to remote peers which allows knowledge to propagate across the network.
 *
 * Currently, knowledge propagation can take one of two paths:
 *   - fast path, wherein a device registers a change and broadcasts a NewUpdates message.
 *     Upon reception of this message, other devices will schedule a GetVers request to the
 *     source of the NewUpdate message. Such "reactive" requests are rate-limited to at most
 *     1 per store per device per second.
 *   - slow path, wherein for each store, a device periodically pick a random available peer
 *     and sends it a GetVers request. The frequence of such "proactive" requests is roughly
 *     2 per store per minute.
 *
 * The use of periodic request shields us from the unreliable nature of the fast path (most
 * notably, on LAN the fast path uses IP multicast which is fairly likely to be lossy). It
 * does however lead to a fairly high bandwidth usage at idle time, which grows linearly with
 * the number of stores, regardless of activity.
 *
 * In the future, it would be desirable to leverage epidemic propagation and more detailed
 * tracking of connectivity loss to avoid periodic polling.
 */
public class AntiEntropy
{
    private static final Logger l = Loggers.getLogger(AntiEntropy.class);

    private final CoreScheduler _sched;
    private final GetVersionsRequest _pgvc;
    private final To.Factory _factTo;
    private final MapSIndex2Store _sidx2s;

    private final Map<SIndex, Request> _requests = Maps.newHashMap();

    @Inject
    public AntiEntropy(CoreScheduler sched, GetVersionsRequest pgvc, MapSIndex2Store sidx2s, To.Factory factTo)
    {
        _sched = sched;
        _pgvc = pgvc;
        _sidx2s = sidx2s;
        _factTo = factTo;
    }

    /**
     * Start periodic pulling for a given store
     */
    public void start_(SIndex sidx)
    {
        l.debug("start {}", sidx);

        Request req = _requests.get(sidx);
        if (req != null) {
            // if request scheduled too far in the future, abort it and re-schedule
            long now = System.currentTimeMillis();
            if (req._nextPeriodicReq - now < DaemonParam.ANTI_ENTROPY_INTERVAL / 10) return;
            req._abort = true;
        }

        l.debug("sched {}", sidx);
        req = new Request(sidx);
        _requests.put(sidx, req);
        _sched.schedule(req, 0);
    }

    /**
     * One-off version request for a given (store, device) pair in response to a push notification
     */
    public void request_(final SIndex sidx, final DID did)
    {
        l.debug("{} request pull for {}", did, sidx);
        Request req = _requests.get(sidx);
        if (req == null) {
            start_(sidx);
            req = checkNotNull(_requests.get(sidx));
        }

        // rate-limit requests made in response to push notification
        if (rateLimit_(sidx, did, req)) return;

        sendRequest_(req, did);
    }

    private boolean rateLimit_(final SIndex sidx, final DID did, Request req)
    {
        ElapsedTimer lastReq = req._lastReq.get(did);
        if (lastReq == null || lastReq.elapsed() >= 1 * C.SEC) return false;

        if (req._scheduled.contains(did)) return true;

        req._scheduled.add(did);
        _sched.schedule(new AbstractEBSelfHandling() {
            @Override
            public void handle_()
            {
                Request req = _requests.get(sidx);
                if (req != null) sendRequest_(req, did);
            }
        }, Math.max(1 * C.SEC - lastReq.elapsed(), 0));
        return true;
    }

    private void sendRequest_(Request req, DID did)
    {
        try {
            req._scheduled.remove(did);
            Store s = _sidx2s.getNullable_(req._sidx);
            if (s == null || !s.hasOnlinePotentialMemberDevices_()) return;
            req.sendGetVersRequests_(did);
        } catch (Exception e) {
            l.warn("{} {}", req._sidx, did, e);
        }
    }

    /**
     * Per-store request tracking
     */
    private class Request extends AbstractEBSelfHandling
    {
        final SIndex _sidx;
        final Set<DID> _scheduled = Sets.newHashSet();
        final Map<DID, ElapsedTimer> _lastReq = Maps.newHashMap();

        long _nextPeriodicReq;
        boolean _abort;

        Request(SIndex sidx)
        {
            _sidx = sidx;
        }

        @Override
        public void handle_()
        {
            if (_abort) return;
            if (sendGetVersRequest_()) {
                _sched.schedule(this, DaemonParam.ANTI_ENTROPY_INTERVAL);
                _nextPeriodicReq = System.currentTimeMillis() + DaemonParam.ANTI_ENTROPY_INTERVAL;
            } else {
                _requests.remove(_sidx);
            }
        }

        /**
         * @return whether to keep sending periodic GetVers requests
         */
        private boolean sendGetVersRequest_()
        {
            Store s = _sidx2s.getNullable_(_sidx);
            if (s == null) {
                l.debug("{} no longer exists. return", _sidx);
                return false;
            } else if (!s.hasOnlinePotentialMemberDevices_()) {
                l.debug("{}: no online devs. return", s);
                return false;
            } else {
                To to = _factTo.create_(_sidx, To.RANDCAST);
                try {
                    // TODO: take locality and last req time to pick among available devices
                    DID didTo = checkNotNull(to.pick_());
                    sendGetVersRequests_(didTo);
                } catch (RuntimeException e) {
                    // we tolerate no runtime exceptions
                    throw e;
                } catch (Exception e) {
                    l.warn("{}: {}", s, Util.e(e));
                }
            }
            return true;
        }

        private void sendGetVersRequests_(DID did) throws Exception
        {
            _pgvc.issueRequest_(did, _sidx);
            _lastReq.put(did, new ElapsedTimer());
        }
    }
}
