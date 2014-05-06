/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.sparta.resources;

import com.aerofs.rest.api.Error;
import com.aerofs.rest.api.Error.Type;
import com.aerofs.rest.auth.IAuthToken;
import com.aerofs.restless.Auth;
import com.aerofs.restless.Service;
import com.aerofs.restless.Since;
import com.aerofs.sp.sparta.Transactional;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.sql.SQLException;

@Path(Service.VERSION + "/devices")
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class DevicesResource
{
    @Since("1.1")
    @GET
    public Response list(@Auth IAuthToken token) throws SQLException
    {
        return Response.ok()
                .entity(new Error(Type.INTERNAL_ERROR, "Not implemented"))
                .build();
    }
}
