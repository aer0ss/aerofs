/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.bifrost.oaaas.resource;

import com.aerofs.bifrost.oaaas.repository.ResourceServerRepository;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

/**
 * Resource for handling all calls related to client management.
 */
@Path("/healthcheck")
public class HealthCheckResource
{
    @Inject
    private SessionFactory sessionFactory;

    @Inject
    private ResourceServerRepository _resourceServerRepository;

    @GET
    public Response healthCheck()
    {
        _resourceServerRepository.findByKey("");
        return Response.noContent().build();
    }
}
