package com.aerofs.daemon.core;

import com.aerofs.daemon.core.tc.TC;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
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

    public void schedule(IEvent ev)
    {
        // this can be called from the main thread during the initialization phase
        // which for all intent and purposes is equivalent to a core thread, except
        // it doesn't have a TCB/Prio/...
        Prio prio = TC.currentThreadPrio();
        if (!_q.enqueue(ev, prio == null ? Prio.LO : prio)) {
            schedule(ev, 0);
        }
    }
}
