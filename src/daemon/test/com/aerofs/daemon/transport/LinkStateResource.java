/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport;

import com.aerofs.daemon.core.net.link.LinkStateService;
import org.junit.rules.ExternalResource;

public final class LinkStateResource extends ExternalResource
{
    private final LinkStateService linkStateService = new LinkStateService();

    @Override
    protected void before()
            throws Throwable
    {
        super.before();
        linkStateService.markLinksUp_();
    }

    @Override
    protected void after()
    {
        linkStateService.markLinksDown_();
        super.after();
    }

    public LinkStateService getLinkStateService()
    {
        return linkStateService;
    }
}
