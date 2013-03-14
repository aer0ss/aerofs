/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.devman.server.resources;

import com.aerofs.devman.server.ResourceConstants;
import com.aerofs.base.id.DID;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;

import static com.google.common.base.Strings.isNullOrEmpty;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

@Path("/" + ResourceConstants.DEVICES_PATH)
public final class DevicesResource
{
    private final JedisThreadLocalTransaction _trans;

    public DevicesResource(JedisThreadLocalTransaction trans)
    {
        _trans = trans;
    }

    @Path("/{" + ResourceConstants.DEVICES_SUBRESOURCE + "}")
    public DeviceResource getDevice(
            @PathParam(ResourceConstants.DEVICES_SUBRESOURCE) String deviceName)
            throws Exception
    {
        if (isNullOrEmpty(deviceName)) {
            throw new WebApplicationException(BAD_REQUEST);
        }

        return new DeviceResource(new DID(deviceName), _trans);
    }
}