/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.lib.async;

import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.event.lib.AbstractEBSelfHandling;
import com.aerofs.daemon.lib.Prio;

public class CoreExecutor implements ISingleThreadedPrioritizedExecutor
{
    private CoreQueue _q;
    private CoreScheduler _sched;

    public CoreExecutor(CoreQueue q, CoreScheduler sched)
    {
        this._q = q;
        this._sched = sched;
    }

    @Override
    public void execute(final Runnable runnable, Prio pri)
    {
        _q.enqueueBlocking(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                runnable.run();
            }
        }, pri);
    }

    @Override
    public void execute(Runnable runnable)
    {
        execute(runnable, Prio.LO);
    }

    @Override
    public void executeAfterDelay(final Runnable runnable, long delayInMilliseconds)
    {
        _sched.schedule(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                execute(runnable);
            }
        }, delayInMilliseconds);
    }
}
