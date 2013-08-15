/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.EOStartPulse;
import com.aerofs.daemon.event.net.EOTpStartPulse;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.sched.IScheduler;

/**
 * Handler that handles incoming {@link EOStartPulse} events from the core
 */
public class HdStartPulse implements IEventHandler<EOStartPulse>
{
    private final IScheduler scheduler;

    HdStartPulse(IScheduler scheduler)
    {
        this.scheduler = scheduler;
    }

    @Override
    public void handle_(EOStartPulse ev, Prio prio)
    {
        scheduler.schedule(new EOTpStartPulse(ev.did()), 0);
    }
}
