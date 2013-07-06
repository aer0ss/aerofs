/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.pump;

import com.aerofs.daemon.core.net.link.AbstractLinkStateService;

import java.util.concurrent.Executor;

import static java.util.concurrent.Executors.newSingleThreadExecutor;

final class SingleThreadedLinkStateService extends AbstractLinkStateService
{
    private final Executor executor = newSingleThreadExecutor();

    @Override
    public void execute(Runnable runnable)
    {
        executor.execute(runnable);
    }
}
