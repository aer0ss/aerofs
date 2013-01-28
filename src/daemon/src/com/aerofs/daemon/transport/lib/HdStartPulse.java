/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.EOStartPulse;
import com.aerofs.daemon.event.net.EOTpStartPulse;
import com.aerofs.lib.event.Prio;

/**
 * Handler that handles incoming {@link EOStartPulse} events from the core
 */
public class HdStartPulse implements IEventHandler<EOStartPulse>
{
    HdStartPulse(ITransportImpl tp)
    {
        this.tp = tp;
    }

    @Override
    public void handle_(EOStartPulse ev, Prio prio)
    {
        tp.sched().schedule(new EOTpStartPulse(ev.did()), 0);
    }

    private final ITransportImpl tp;
}
