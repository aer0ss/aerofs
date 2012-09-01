/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tap;

import com.aerofs.daemon.core.net.link.AbstractLinkStateService;
import com.aerofs.daemon.tng.base.EventQueueBasedEventLoop;
import com.google.inject.Inject;

public class EventLoopBasedLinkStateService extends AbstractLinkStateService
{
    private final EventQueueBasedEventLoop _executor;

    @Inject
    public EventLoopBasedLinkStateService(EventQueueBasedEventLoop executor)
    {
        _executor = executor;
    }

    @Override
    public void execute(final Runnable run)
    {
        _executor.execute(run);
    }
}
