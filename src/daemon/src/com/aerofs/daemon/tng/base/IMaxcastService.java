/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.daemon.core.net.link.INetworkLinkStateListener;
import com.aerofs.daemon.lib.IDebug;
import com.aerofs.daemon.lib.IStartable;
import com.aerofs.daemon.tng.IMaxcast;
import com.aerofs.daemon.tng.IMaxcastListener;

import java.util.concurrent.Executor;

public interface IMaxcastService extends IMaxcast, INetworkLinkStateListener, IStartable, IDebug
{
    void addListener_(IMaxcastListener listener, Executor notificationExecutor);
}
