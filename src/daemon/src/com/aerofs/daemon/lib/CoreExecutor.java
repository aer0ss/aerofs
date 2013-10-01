/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.lib;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.Prio;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.concurrent.Executor;

import static com.google.common.base.Preconditions.checkState;

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
    private static final Logger l = Loggers.getLogger(CoreExecutor.class);

    private final TC _tc;
    private final CoreQueue _coreQueue;

    @Inject
    public CoreExecutor(TC tc, CoreQueue coreQueue)
    {
        _tc = tc;
        _coreQueue = coreQueue;
    }

    @Override
    public void execute(final @Nonnull Runnable runnable)
    {
        // FIXME (DF): this assertion as-is is too strong to keep - ritual calls
        //             trigger it, and other things we haven't looked at may also
        //checkState(!_tc.isCoreThread(), "enqueuing from core thread:%s", Thread.currentThread());

        _coreQueue.enqueueBlocking(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                runnable.run();
            }
        }, Prio.LO);
    }
}
