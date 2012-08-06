/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tap;

import com.aerofs.daemon.core.net.link.AbstractNetworkLinkStateService;
import com.aerofs.daemon.tng.base.EventQueueBasedEventLoop;
import com.aerofs.lib.Util;
import com.google.inject.Inject;

import static com.aerofs.daemon.lib.DaemonParam.LINK_STATE_MONITOR_INTERVAL;

public class EventLoopBasedNetworkLinkStateService extends AbstractNetworkLinkStateService
{
    private final EventQueueBasedEventLoop _executor;

    @Inject
    public EventLoopBasedNetworkLinkStateService(EventQueueBasedEventLoop executor)
    {
        _executor = executor;
    }

    @Override
    public void start_()
    {
        _executor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    checkLinkState_();
                } catch (Exception e) {
                    Util.l(this).warn("retry later: " + e);
                }

                _executor.executeAfterDelay(this, LINK_STATE_MONITOR_INTERVAL);
            }
        });
    }
}
