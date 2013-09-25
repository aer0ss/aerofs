/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.serverstatus;

import java.util.concurrent.Executor;

/**
 * Interface to be implemented by all persistent connections to a server whose connection status
 * may affect the functionality of the client
 */
public interface IConnectionStatusNotifier
{
    /**
     * A basic listener through which other classes interested in the connection status can be
     * notified when it changes
     *
     * The listeners should not do any computationally-intensive work in the callbacks.
     */
    public static interface IListener
    {
        void onConnected();
        void onDisconnected();
    }

    /**
     * Add a listener that will be notified whenever the connection is made or lost, the callback
     * will be called using the executor.
     */
    void addListener_(IListener listener, Executor callbackExecutor);
}
