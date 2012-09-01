/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.daemon.core.net.link.ILinkStateListener;
import com.aerofs.daemon.lib.IDebug;
import com.aerofs.daemon.lib.IStartable;
import com.aerofs.daemon.tng.IPresenceListener;

import java.util.concurrent.Executor;

public interface IPresenceService extends ILinkStateListener, IStartable, IDebug
{
    void addListener_(IPresenceListener listener, Executor notificationExecutor);

    void addListener_(IPeerPresenceListener listener, Executor notificationExecutor);
}