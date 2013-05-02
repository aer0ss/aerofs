/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.launch;

import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.sched.ExponentialRetry;
import com.aerofs.lib.sched.IScheduler;

import java.util.concurrent.Callable;

// This class is almost exactly like RunAtLeastOnce, except that RALO
// gets to use injection, whereas this is in the GUI and has to have
// things instantiated manually
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
        new ExponentialRetry(_sched).retry(getClass().getSimpleName(), new Callable<Void>()
        {
            @Override
            public Void call() throws Exception
            {
                run_();
                return null;
            }
        });
    }

    protected abstract void run_() throws Exception;
}
