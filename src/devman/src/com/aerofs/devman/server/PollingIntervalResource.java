/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.devman.server;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/" + ResourceConstants.POLLING_INTERVAL_PATH)
public final class PollingIntervalResource
{
    private final long _pollingInterval;

    public PollingIntervalResource(long pollingInterval)
    {
        _pollingInterval = pollingInterval;
    }

    @GET
    @Produces(APPLICATION_JSON)
    public long getPollingInterval()
    {
        return _pollingInterval;
    }
}