package com.aerofs.polaris.resources;

import com.aerofs.auth.server.Roles;
import com.aerofs.polaris.external_api.rest.util.Since;
import com.aerofs.polaris.logical.ObjectStore;

import javax.annotation.security.RolesAllowed;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@RolesAllowed(Roles.SERVICE)
@Singleton
@Path("/stats")
@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_OCTET_STREAM})
public class StatsResource
{
    private final ObjectStore objectStore;

    public StatsResource(@Context ObjectStore objectStore)
    {
        this.objectStore = objectStore;
    }

    @Since("1.4")
    @GET
    public Response stats()
    {
        return Response.ok()
                .entity(objectStore.getOrgStats())
                .build();
    }
}