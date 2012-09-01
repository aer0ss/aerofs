/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net.link;

import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.event.lib.AbstractEBSelfHandling;
import com.aerofs.daemon.lib.Prio;
import com.google.inject.Inject;

public class LinkStateService extends AbstractLinkStateService
{
    private final CoreQueue _cq;

    @Inject
    public LinkStateService(CoreQueue cq)
    {
        _cq = cq;
    }

    @Override
    public void execute(final Runnable runnable)
    {
        _cq.enqueueBlocking(new AbstractEBSelfHandling() {
            @Override
            public void handle_()
            {
                runnable.run();
            }
        }, Prio.HI);
    }
}
