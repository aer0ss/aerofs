/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy;

@FunctionalInterface
public interface ScanCompletionCallback
{
    /**
     * Called once, when the scan session it was passed to completes
     *
     * NB: this is called from a core thread with the core lock held
     */
    public void done_();
}
