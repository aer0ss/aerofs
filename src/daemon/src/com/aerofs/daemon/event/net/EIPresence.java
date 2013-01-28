package com.aerofs.daemon.event.net;

import java.util.Collection;
import java.util.Collections;

import com.aerofs.lib.event.IEvent;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.base.id.SID;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import javax.annotation.Nonnull;

/**
 * The presence is *edge* triggered
 *
 * must use enqueueBlocking() rather than enqueue() to file the event. we need
 * reliable receiving of these events
 */
public class EIPresence implements IEvent
{
    public final ImmutableMap<DID, Collection<SID>> _did2sids;
    public final ITransport _tp;
    public final boolean _online;

    /**
     * @param did2sids : online == false and did2sids.isEmpty()
     * indicates that all the devices are offline.
     * did2sids mustn't be empty if online == true.
     */
    public EIPresence(ITransport tp, boolean online,
            @Nonnull ImmutableMap<DID, Collection<SID>> did2sids)
    {
        _did2sids = did2sids;
        _tp = tp;
        _online = online;
    }

    public EIPresence(ITransport tp, boolean online, DID did,
            Collection<SID> sids)
    {
        this(tp, online, ImmutableMap.of(did, sids));
    }

    public EIPresence(ITransport tp, boolean online, DID did, SID sid)
    {
        this(tp, online, did, ImmutableList.of(sid));
    }
}
