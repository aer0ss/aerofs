/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net.link;

import com.aerofs.daemon.lib.IStartable;

import java.util.concurrent.Executor;

public interface ILinkStateService extends IStartable
{
    void addListener_(ILinkStateListener listener, Executor callbackExecutor);

    void removeListener_(ILinkStateListener listener);
}