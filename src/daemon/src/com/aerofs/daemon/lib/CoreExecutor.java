/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.lib;

import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.Prio;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.concurrent.Executor;

/**
 * Instances of this class executes {@link Runnable} instances as
 * events on the core thread. When {@link CoreExecutor#execute(Runnable)} is
 * invoked, the current thread wraps the given {@link Runnable} in
 * a self-handling event and enqueues it onto the core queue.
 *
 * MUST NOT be called from a core thread
 *
 * The enqueue is blocking. Because the same lock is currently used to control
 * access to the core queue and the core DB, the calling thread may be blocked
 * for a while.
 */
public class CoreExecutor implements Executor
{
    private final CoreQueue _coreQueue;

    @Inject
    public CoreExecutor(CoreQueue coreQueue)
    {
        _coreQueue = coreQueue;
    }

    @Override
    public void execute(final @Nonnull Runnable runnable)
    {
        _coreQueue.enqueueBlocking(new AbstractEBSelfHandling() {
            @Override
            public void handle_()
            {
                runnable.run();
            }
        }, Prio.LO);
    }
}
