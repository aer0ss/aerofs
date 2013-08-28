/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport;

import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;

/**
 * A dummy {@link IIMCExecutor}. Amazingly, this works because
 * apparently none of the transport events make use of any of the
 * {@code IIMCExecutor} features anymore. Probably because of
 * all the stream changes, and I assume that streams were the biggest
 * user of the feedback mechanism of {@code IIMCExecutor}.
 */
public final class FakeIMCExecutor implements IIMCExecutor
{
    @Override
    public void execute_(IEvent ev, Prio prio)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean enqueue_(IEvent ev, Prio prio)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void enqueueBlocking_(IEvent ev, Prio prio)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void done_(IEvent ev)
    {
        // noop
    }
}
