/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.command.server.resources;

import com.aerofs.base.id.DID;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.aerofs.verkehr.client.rest.VerkehrClient;

import javax.ws.rs.Path;

@Path("/devices")
public final class DeviceResource
{
    private final DID _did;

    private final JedisThreadLocalTransaction _trans;
    private final VerkehrClient _verkehr;

    public DeviceResource(DID did, JedisThreadLocalTransaction trans, VerkehrClient verkehr)
    {
        _did = did;
        _trans = trans;
        _verkehr = verkehr;
    }

    @Path("/{queues}")
    public QueuesResource getDevice()
    {
        return new QueuesResource(_did, _trans, _verkehr);
    }
}