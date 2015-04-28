/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.ui.launch_tasks;

import com.aerofs.base.C;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.sched.ExponentialRetry;
import com.aerofs.lib.sched.IScheduler;

import java.util.concurrent.Callable;

// This class is almost exactly like DaemonLaunchTasks, except that the latter gets to use injection,
// whereas this is in the GUI and has to have things instantiated manually.
abstract class UILaunchTask extends AbstractEBSelfHandling
{
    protected final IScheduler _sched;
    UILaunchTask(IScheduler sched)
    {
        _sched = sched;
    }
    public void schedule()
    {
        _sched.schedule(this, 0);
    }

    @Override
    public void handle_()
    {
        // N.B. the only UILaunchTask we run now is ULTRecertify and this is what I think the optimal
        //   timing for recertify should be.
        new ExponentialRetry(_sched).retry(getClass().getSimpleName(), 10 * C.SEC, 10 * C.MIN,
                () -> { run_(); return null; });
    }

    protected abstract void run_() throws Exception;
}
