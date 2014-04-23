package com.aerofs.daemon.event.net;

import java.util.Collection;

import com.aerofs.lib.event.IEvent;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.base.id.SID;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

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
     * It is an error to submit an empty did-to-sid map if you are online.
     */
    public EIPresence(ITransport tp, boolean online, DID did, Collection<SID> sids)
    {
        Preconditions.checkArgument( (!online) || did != null,
                "Online presence event must include one device");
        _did2sids = ImmutableMap.of(did, sids);
        _tp = tp;
        _online = online;
    }
}
