/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.transport.xmpp.routing;

import com.aerofs.daemon.event.lib.imc.IResultWaiter;

/**
 * A noop {@link IResultWaiter} that does nothing on when its methods are called
 */
public class NoopWaiter implements IResultWaiter
{
    @Override
    public void okay()
    {
        // empty
    }

    @Override
    public void error(Exception e)
    {
        // empty
    }

    /**
     * Convenience static instance of {@link NoopWaiter}
     */
    public static final NoopWaiter NOOP_WAITER = new NoopWaiter();
}
