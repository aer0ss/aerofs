package com.aerofs.daemon.event.net;

import com.aerofs.daemon.transport.ITransport;
import com.aerofs.ids.DID;
import com.aerofs.lib.event.IEvent;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Store availability is <strong>edge</strong> triggered.
 *
 * Must use enqueueBlocking() rather than enqueue() to insert
 * the event into the core queue since this event <strong>must</strong>
 * be delivered.
 */
public class EIDevicePresence implements IEvent
{
    public final DID _did;
    public final ITransport _tp;
    public final boolean _online;

    /**
     * It is an error to submit an empty did-to-sid map if you are online.
     */
    public EIDevicePresence(ITransport tp, boolean online, DID did)
    {
        checkArgument((!online) || did != null, "Online store availability event must include one device");
        _did = did;
        _tp = tp;
        _online = online;
    }
}
