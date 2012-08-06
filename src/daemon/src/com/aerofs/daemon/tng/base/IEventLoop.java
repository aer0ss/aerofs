/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.daemon.lib.IDebug;
import com.aerofs.daemon.lib.IStartable;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;

public interface IEventLoop extends ISingleThreadedPrioritizedExecutor, IStartable, IDebug
{
    void assertEventThread();

    void assertNonEventThread();
}
