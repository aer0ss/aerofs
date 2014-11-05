package com.aerofs.daemon.core;

import com.aerofs.daemon.core.tc.TC;
import com.aerofs.lib.event.IEvent;
import com.google.inject.Inject;

import com.aerofs.lib.sched.Scheduler;

public class CoreScheduler extends Scheduler
{
    private final CoreQueue _q;

    @Inject
    public CoreScheduler(CoreQueue q)
    {
        super(q, "core-sched");
        _q = q;
    }

    public void schedule_(IEvent ev)
    {
        if (!_q.enqueue_(ev, TC.currentThreadPrio())) {
            schedule(ev, 0);
        }
    }
}
