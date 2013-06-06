/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.xmpp;

import com.aerofs.base.id.DID;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.daemon.transport.lib.ITransportStats.BasicStatsCounter;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;

import static com.aerofs.daemon.transport.lib.TPUtil.registerMulticastHandler;

public class Jingle extends XMPP
{
    public Jingle(DID localdid, String id, int rank, IBlockingPrioritizedEventSink<IEvent> sink,
            MaxcastFilterReceiver mcfr)
    {
        super(localdid, id, rank, sink, mcfr);
        com.aerofs.daemon.transport.xmpp.jingle.Jingle jingle =
                new com.aerofs.daemon.transport.xmpp.jingle.Jingle(id, rank, this, new BasicStatsCounter());
        setPipe_(jingle);
        registerMulticastHandler(this);
    }

    @Override
    public boolean supportsMulticast()
    {
        return true;
    }
}
