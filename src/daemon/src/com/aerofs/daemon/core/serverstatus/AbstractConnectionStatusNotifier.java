/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.serverstatus;

import com.google.common.collect.Maps;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executor;

/**
 * Basic implementation of the connection status interface
 */
public abstract class AbstractConnectionStatusNotifier implements IConnectionStatusNotifier
{
    private final Map<IListener, Executor> _listeners = Maps.newHashMap();

    /**
     * Add a listener that will be notified whenever the connection is made or lost
     */
    @Override
    public void addListener_(IListener listener, Executor callbackExecutor)
    {
        _listeners.put(listener, callbackExecutor);
    }

    /*
     * Subclasses should call this method whenever the connection is made
     */
    protected void notifyConnected_()
    {
        for (Entry<IListener, Executor> entry : _listeners.entrySet()) {
            final IListener listener = entry.getKey();
            final Executor executor = entry.getValue();

            executor.execute(new Runnable()
            {
                @Override
                public void run()
                {
                    listener.onConnected();
                }
            });
        }
    }

    /*
     * Subclasses should call this method whenever the connection is lost
     */
    protected void notifyDisconnected_()
    {
        for (Entry<IListener, Executor> entry : _listeners.entrySet()) {
            final IListener listener = entry.getKey();
            final Executor executor = entry.getValue();

            executor.execute(new Runnable()
            {
                @Override
                public void run()
                {
                    listener.onDisconnected();
                }
            });
        }
    }
}
