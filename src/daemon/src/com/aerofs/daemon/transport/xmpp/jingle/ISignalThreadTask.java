/**
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc. 2011.
 */

package com.aerofs.daemon.transport.xmpp.jingle;

/**
 * Interface for tasks to be executed by the {@link SignalThread}
 */
public interface ISignalThreadTask extends Runnable
{
    public void error(Exception e);
}
