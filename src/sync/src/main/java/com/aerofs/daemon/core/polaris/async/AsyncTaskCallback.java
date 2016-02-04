/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.async;

/**
 * Callback object invoked on completion of an {@link AsyncTask}
 *
 * This is mostly used to control scheduling of recurring tasks by
 * {@link AsyncWorkGroupScheduler}
 */
public interface AsyncTaskCallback
{
    /**
     * Called when the async task completed successfully
     *
     * NB: called with the core lock held
     *
     * @param hasMore whether more work can be done immediately
     */
    void onSuccess_(boolean hasMore);

    /**
     * Called when the async task failed
     *
     * NB: called with the core lock held
     *
     * @param t exception thrown
     */
    void onFailure_(Throwable t);
}