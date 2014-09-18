/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.async;

@FunctionalInterface
public interface AsyncTask
{
    /**
     * Called with core lock held
     *
     * May return before the actual work is complete, e.g. if network
     * communication is involved, but should be resilient to subsequent
     * changes to core db.
     *
     * @param cb callback invoked on success or failure of the async task
     */
    public void run_(AsyncTaskCallback cb);
}
