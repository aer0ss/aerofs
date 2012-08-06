package com.aerofs.daemon.tng;

import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;

public final class ImmediateInlineExecutor implements ISingleThreadedPrioritizedExecutor
{
    @Override
    public void execute(Runnable runnable, Prio pri)
    {
        runnable.run();
    }

    @Override
    public void execute(Runnable runnable)
    {
        runnable.run();
    }

    /**
     * Violates LSP for testing purposes
     * @param runnable task to execute
     * @param delayInMilliseconds ignored - executes immediately
     */
    @Override
    public void executeAfterDelay(Runnable runnable, long delayInMilliseconds)
    {
        runnable.run();
    }
}
