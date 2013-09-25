/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.lib;

import com.aerofs.daemon.core.CoreIMCExecutor;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.Prio;

import javax.inject.Inject;
import java.util.concurrent.Executor;

/**
 * Instances of this class executes runnables as IMC events on the core thread. When execute() is
 * invoked, the current thread enqueues a self handling event to run the runnable on the core
 * queue.
 *
 * If the core queue is full, it will block the current thread until the event is successfully
 * enqueued on the core queue.
 */
public class CoreExecutor implements Executor
{
    private final IIMCExecutor _executor;

    @Inject
    public CoreExecutor(CoreIMCExecutor executor)
    {
        _executor = executor.imce();
    }

    @Override
    public void execute(final Runnable runnable)
    {
        // block current thread until we succeed in enqueuing an event onto the core queue.
        _executor.enqueueBlocking_(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                runnable.run();
            }
        }, Prio.LO);
    }
}
