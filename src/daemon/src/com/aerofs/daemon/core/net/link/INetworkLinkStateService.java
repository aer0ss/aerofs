/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net.link;

import com.aerofs.daemon.lib.IStartable;

import java.util.concurrent.Executor;

public interface INetworkLinkStateService extends IStartable
{
    void addListener_(INetworkLinkStateListener listener, Executor callbackExecutor);

    void removeListener_(INetworkLinkStateListener listener);
}