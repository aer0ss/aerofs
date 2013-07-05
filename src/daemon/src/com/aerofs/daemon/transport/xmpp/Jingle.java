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
    private final TransportStats transportStats = new TransportStats();

    public Jingle(DID localdid, byte[] scrypted, String absRTRoot, String id, int rank, IBlockingPrioritizedEventSink<IEvent> sink, MaxcastFilterReceiver maxcastFilterReceiver, RockLog rocklog)
    {
        super(localdid, scrypted, id, rank, sink, maxcastFilterReceiver, rocklog);
        com.aerofs.daemon.transport.xmpp.jingle.Jingle jingle = new com.aerofs.daemon.transport.xmpp.jingle.Jingle(localdid, getXmppPassword(), absRTRoot, id, rank, this, transportStats);
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
        return transportStats.getBytesReceived();
    }

    @Override
    public long bytesOut()
    {
        return transportStats.getBytesSent();
    }
}
