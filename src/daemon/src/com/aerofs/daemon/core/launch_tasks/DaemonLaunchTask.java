/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.launch_tasks;

import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.sched.ExponentialRetry;

import java.util.concurrent.Callable;

/**
 * Regular DPUTs cannot solve every problems, in particular some update tasks requires an active
 * internet connection, can be completed in any order and do not have a hard deadline on completion.
 *
 * Such tasks should be idempotent and handle failure gracefully through an exponential retry
 */
abstract class DaemonLaunchTask extends AbstractEBSelfHandling
{
    protected final CoreScheduler _sched;

    DaemonLaunchTask(CoreScheduler sched)
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
        new ExponentialRetry(_sched).retry(getClass().getSimpleName(), () -> {
            run_();
            return null;
        });
    }

    protected abstract void run_() throws Exception;
}
