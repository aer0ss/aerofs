/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.verkehr;

import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.verkehr.VerkehrNotificationSubscriber.IVerkehrListener;
import com.aerofs.daemon.event.lib.AbstractEBSelfHandling;
import com.aerofs.daemon.lib.Prio;

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

    @Override public void onSubscribed() {}
    @Override public void onConnected() {}
    @Override public void onDisconnected() {}
}