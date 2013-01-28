package com.aerofs.daemon.core;

import com.google.inject.Inject;

import com.aerofs.lib.sched.Scheduler;

public class CoreScheduler extends Scheduler
{
    @Inject
    public CoreScheduler(CoreQueue q)
    {
        super(q, "core-sched");
    }
}
