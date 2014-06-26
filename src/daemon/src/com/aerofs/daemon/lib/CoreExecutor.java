/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.lib;

import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.Prio;
import com.google.common.base.Preconditions;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.concurrent.Executor;

/**
 * Instances of this class executes {@link Runnable} instances as
 * events on the core thread. When {@link CoreExecutor#execute(Runnable)} is
 * invoked, the current thread wraps the given {@link Runnable} in
 * a self-handling event and enqueues it onto the core queue.
 * The enqueue is:
 * <ul>
 *     <li>Blocking if the core lock is not held. In this case the current thread
 *         waits until the event can be successfully placed on the core queue.</li>
 *     <li>Not blocking if the core lock is not held.</li>
 * </ul>
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
        AbstractEBSelfHandling event = new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                runnable.run();
            }
        };

        // FIXME (AG): I feel dirty doing this
        //
        // this check assumes deep knowledge of how locking
        // in the core works specifically, that the core queue
        // lock is the core lock
        if (holdsCoreLock()) {
            boolean added = _coreQueue.enqueue_(event, Prio.LO);
            Preconditions.checkState(added);
        } else {
            _coreQueue.enqueueBlocking(event, Prio.LO);
        }
    }

    private boolean holdsCoreLock()
    {
        return _coreQueue.getLock().isHeldByCurrentThread();
    }
}
