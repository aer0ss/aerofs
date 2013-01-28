/**
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */
package com.aerofs.daemon.event.net;

import com.aerofs.lib.event.IEvent;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.base.id.DID;

/**
 * Even that is sent to the core when the continuous pulses to a peer stop
 */
public class EIPulseStopped implements IEvent
{
    public EIPulseStopped(DID did, ITransport tp)
    {
        _did = did;
        _tp = tp;
    }

    public final DID _did;
    public final ITransport _tp;
}
