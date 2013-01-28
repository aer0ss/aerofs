/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng;

import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;

public class DropDelayedInlineExecutor implements ISingleThreadedPrioritizedExecutor
{
    private final ImmediateInlineExecutor _executor = new ImmediateInlineExecutor();

    @Override
    public final void execute(Runnable runnable, Prio pri)
    {
        _executor.execute(runnable, pri);
    }

    @Override
    public final void execute(Runnable runnable)
    {
        _executor.execute(runnable);
    }

    /**
     * Violates LSP for testing purposes - drops the delayed task
     *
     * @param runnable ignored - this task is dropped immediately
     * @param delayInMilliseconds ignored
     */
    @Override
    public void executeAfterDelay(Runnable runnable, long delayInMilliseconds) // not final for spy
    {}
}
