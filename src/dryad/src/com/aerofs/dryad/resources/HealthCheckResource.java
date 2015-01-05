/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.dryad.resources;

import com.aerofs.dryad.LogStore;
import com.aerofs.restless.Service;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

@Path(Service.VERSION + "/status")
public class HealthCheckResource
{
    private static final Logger l = LoggerFactory.getLogger(HealthCheckResource.class);

    private final LogStore _logStore;

    @Context private UriInfo _uriInfo;

    @Inject
    public HealthCheckResource(LogStore logStore)
    {
        _logStore = logStore;
    }

    /**
     * @return 503 if the health check fails and the service is unavailable.
     *         200 if the all health checks succeed.
     */
    @GET
    public Response getStatus()
    {
        // by now, we can be sure the service is alive and routes are working.
        l.info("GET {}", _uriInfo.getPath());

        try {
            // ensure file persistence is working
            _logStore.throwIfNotHealthy();
        } catch (Exception e) {
            return Response.status(Status.SERVICE_UNAVAILABLE).build();
        }

        return Response.ok().build();
    }
}
