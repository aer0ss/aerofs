/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.devman.server;

import com.aerofs.base.id.DID;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

@Path("/" + ResourceConstants.LAST_SEEN_PATH)
public final class DevicesLastSeenResource
{
    private final JedisThreadLocalTransaction _trans;

    public DevicesLastSeenResource(JedisThreadLocalTransaction trans)
    {
        _trans = trans;
    }

    @Path("/{" + ResourceConstants.LAST_SEEN_DEVICE + "}")
    public DeviceLastSeenResource getTopic(
            @PathParam(ResourceConstants.LAST_SEEN_DEVICE) String did)
            throws Exception
    {
        return new DeviceLastSeenResource(new DID(did), _trans);
    }
}