/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.verkehr;

import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.verkehr.VerkehrNotificationSubscriber.IVerkehrListener;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.Prio;

/**
 *
 */
public abstract class AbstractVerkehrListener implements IVerkehrListener
{
    private final CoreQueue _q;

    public AbstractVerkehrListener(CoreQueue q)
    {
        _q = q;
    }

    protected final void runInCoreThread_(AbstractEBSelfHandling event)
    {
        _q.enqueueBlocking(event, Prio.LO);
    }

    @Override public void onSubscribed()
    {
        // noop
    }

    @Override public void onConnected()
    {
        // noop
    }

    @Override public void onDisconnected()
    {
        // noop
    }
}
