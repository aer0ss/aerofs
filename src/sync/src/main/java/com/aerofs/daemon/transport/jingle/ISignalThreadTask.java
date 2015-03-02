/**
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc. 2011.
 */

package com.aerofs.daemon.transport.jingle;

/**
 * Implemented by tasks that will be executed in the libjingle
 * {@link SignalThread}.
 */
interface ISignalThreadTask extends Runnable
{
    /**
     * Called when the {@link Runnable#run()} method throws an exception.
     *
     * @param e the exception thrown by the task
     */
    void error(Exception e);
}
