/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.command.server.resources;

import com.aerofs.base.id.DID;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.aerofs.verkehr.client.rest.VerkehrClient;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;

import static com.google.common.base.Strings.isNullOrEmpty;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

@Path("/devices")
public final class DevicesResource
{
    private final JedisThreadLocalTransaction _trans;
    private final VerkehrClient _verkehr;

    public DevicesResource(JedisThreadLocalTransaction trans, VerkehrClient verkehr)
    {
        _trans = trans;
        _verkehr = verkehr;
    }

    @Path("/{device}")
    public DeviceResource getDevice(@PathParam("device") String deviceName) throws Exception
    {
        if (isNullOrEmpty(deviceName)) {
            throw new WebApplicationException(BAD_REQUEST);
        }

        return new DeviceResource(new DID(deviceName), _trans, _verkehr);
    }
}
