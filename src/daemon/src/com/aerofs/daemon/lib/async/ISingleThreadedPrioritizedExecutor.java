/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.lib.async;

import com.aerofs.lib.event.Prio;

import java.util.concurrent.Executor;

/**
 * Executor that guarantees all {@link Runnable}s will be executed on the same thread, which may or
 * may not be the same as the calling thread
 */
public interface ISingleThreadedPrioritizedExecutor extends Executor
{
    /**
     * Executes a runnable at the given priority
     *
     * @param runnable Runnable to execute
     * @param pri The priority at which to execute
     */
    void execute(Runnable runnable, Prio pri);

    /**
     * {@inheritDoc}
     * <p/>
     * Executes the afore mentioned Runnable at the {@link Prio#LO} priority
     *
     * @param runnable The Runnable to execute
     */
    @Override
    void execute(Runnable runnable);

    /**
     * Execute the given {@code Runnable} after a delay. Execution does not have to happen
     * <em>exactly</em> when the delay period expires - it may happen a little before or after.
     *
     * @param runnable task to execute
     * @param delayInMilliseconds amount the task should be delayed by
     */
    void executeAfterDelay(final Runnable runnable, long delayInMilliseconds);
}
