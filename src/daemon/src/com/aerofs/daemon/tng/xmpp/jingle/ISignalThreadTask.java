/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp.jingle;

/**
 * Interface for tasks to be executed by the {@link SignalThread}
 */
interface ISignalThreadTask extends Runnable
{
    public void error(Exception e);
}
