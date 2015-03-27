/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.event.net.rx.EORxEndStream;
import com.aerofs.daemon.transport.lib.StreamKey;
import com.google.common.collect.Maps;
import org.slf4j.Logger;

import java.util.Map;


public class IncomingStreams
{
    private static final Logger l = Loggers.getLogger(IncomingStreams.class);

    private final Map<StreamKey, Endpoint> _map = Maps.newTreeMap();

    public void begun_(StreamKey key, PeerContext pc) throws ExProtocolError
    {
        if (_map.containsKey(key)) {
            throw new ExProtocolError("stream " + key + " already begun");
        }

        _map.put(key, pc.ep());
        l.info("{} create stream {}:{}", key.did, pc.ep(), key);
    }

    // use this method either to naturally end the stream or forcibly abort the
    // stream from the receiving side. The sender will be notified when trying to
    // send further chunks
    public void end_(StreamKey key)
    {
        Endpoint ep = _map.remove(key);

        if (ep == null) {
            l.warn("{} end called for null stream {}", key.did, key.strmid);
            return;
        }

        l.info("{} end stream {}:{}", key.did, ep, key.strmid);

        // TODO: bypass transport queue and go directly to StreamManager?
        EORxEndStream ev = new EORxEndStream(ep.did(), key.strmid);
        if (ep.tp().q().enqueue(ev, TC.currentThreadPrio())) return;
        ep.tp().q().enqueueBlocking(ev, TC.currentThreadPrio());
    }
}
