/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.serverstatus;

import com.google.common.collect.Lists;

import java.util.List;

/**
 * Basic implementation of the connection status interface
 */
public class AbstractConnectionStatusNotifier implements IConnectionStatusNotifier
{
    private final List<IListener> _listeners = Lists.newArrayList();

    /**
     * Add a listener that will be notified whenever the connection is made or lost
     */
    @Override
    public void addListener_(IListener listener)
    {
        _listeners.add(listener);
    }

    /*
     * Subclasses should call this method whenever the connection is made
     */
    protected void notifyConnected_()
    {
        for (IListener listener : _listeners) listener.onConnected();
    }

    /*
     * Subclasses should call this method whenever the connection is lost
     */
    protected void notifyDisconnected_()
    {
        for (IListener listener : _listeners) listener.onDisconnected();
    }
}
