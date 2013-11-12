package com.aerofs.daemon.rest.resources;


import com.aerofs.daemon.rest.RestService;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Non-authenticated version information for use by gateway
 */
@Path("/version")
@Produces(MediaType.APPLICATION_JSON)
public class VersionResource
{
    @GET
    public Response getHighestSupportedVersion()
    {
        return Response.ok()
                .entity(RestService.HIGHEST_SUPPORTED_VERSION)
                .build();
    }
}
