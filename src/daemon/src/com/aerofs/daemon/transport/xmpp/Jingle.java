/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.xmpp;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.daemon.transport.lib.TransportStats;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.rocklog.RockLog;

import static com.aerofs.daemon.transport.lib.TPUtil.registerMulticastHandler;

public class Jingle extends XMPP
{
    private final TransportStats _ts = new TransportStats();

    public Jingle(DID localdid, String id, int rank, IBlockingPrioritizedEventSink<IEvent> sink, MaxcastFilterReceiver mcfr, RockLog rocklog)
    {
        super(localdid, id, rank, sink, mcfr, rocklog);
        com.aerofs.daemon.transport.xmpp.jingle.Jingle jingle = new com.aerofs.daemon.transport.xmpp.jingle.Jingle(id, rank, this, _ts);
        setConnectionService_(jingle);
        registerMulticastHandler(this);
    }

    @Override
    public boolean supportsMulticast()
    {
        return true;
    }

    @Override
    public long bytesIn()
    {
        return _ts.getBytesReceived();
    }

    @Override
    public long bytesOut()
    {
        return _ts.getBytesSent();
    }
}
