/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.sparta.resources;

import com.aerofs.rest.api.Error;
import com.aerofs.rest.api.Error.Type;
import com.aerofs.rest.util.AuthToken;
import com.aerofs.restless.Auth;
import com.aerofs.restless.Service;
import com.aerofs.restless.Since;
import com.aerofs.sp.server.lib.device.Device;
import com.aerofs.sp.sparta.Transactional;
import com.google.inject.Inject;

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
    private final Device.Factory _factDevice;

    @Inject
    public DevicesResource(Device.Factory factDevice)
    {
        _factDevice = factDevice;
    }

    @Since("1.1")
    @GET
    public Response list(@Auth AuthToken token) throws SQLException
    {
        return Response.ok()
                .entity(new Error(Type.INTERNAL_ERROR, "Not implemented"))
                .build();
    }
}
