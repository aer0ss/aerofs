/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.command.server.resources;

import com.aerofs.base.id.DID;
import com.aerofs.command.server.ResourceConstants;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.aerofs.verkehr.client.lib.admin.VerkehrAdmin;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;

import static com.google.common.base.Strings.isNullOrEmpty;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

@Path("/" + ResourceConstants.DEVICES_PATH)
public final class DevicesResource
{
    private final JedisThreadLocalTransaction _trans;
    private final VerkehrAdmin _verkehr;

    public DevicesResource(JedisThreadLocalTransaction trans, VerkehrAdmin verkehr)
    {
        _trans = trans;
        _verkehr = verkehr;
    }

    @Path("/{" + ResourceConstants.DEVICES_SUBRESOURCE + "}")
    public DeviceResource getDevice(
            @PathParam(ResourceConstants.DEVICES_SUBRESOURCE) String deviceName)
            throws Exception
    {
        if (isNullOrEmpty(deviceName)) {
            throw new WebApplicationException(BAD_REQUEST);
        }

        return new DeviceResource(new DID(deviceName), _trans, _verkehr);
    }
}