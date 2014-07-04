package com.aerofs.daemon.event.net;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.lib.event.IEvent;
import com.google.common.collect.ImmutableMap;

import java.util.Collection;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Store availability is <strong>edge</strong> triggered.
 *
 * Must use enqueueBlocking() rather than enqueue() to insert
 * the event into the core queue since this event <strong>must</strong>
 * be delivered.
 */
public class EIStoreAvailability implements IEvent
{
    public final ImmutableMap<DID, Collection<SID>> _did2sids;
    public final ITransport _tp;
    public final boolean _online;

    /**
     * It is an error to submit an empty did-to-sid map if you are online.
     */
    public EIStoreAvailability(ITransport tp, boolean online, DID did, Collection<SID> sids)
    {
        checkArgument((!online) || did != null, "Online store availability event must include one device");
        _did2sids = ImmutableMap.of(did, sids);
        _tp = tp;
        _online = online;
    }
}
