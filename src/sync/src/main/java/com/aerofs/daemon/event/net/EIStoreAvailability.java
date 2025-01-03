package com.aerofs.daemon.event.net;

import com.aerofs.ids.DID;
import com.aerofs.ids.SID;
import com.aerofs.lib.event.IEvent;

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
    public final DID _did;
    public final SID _sid;
    public final boolean _join;

    /**
     * It is an error to submit an empty did-to-sid map if you are online.
     */
    public EIStoreAvailability(DID did, SID sid, boolean join)
    {
        checkArgument(did != null, "Online store availability event must include one device");
        _did = did;
        _sid = sid;
        _join = join;
    }
}
