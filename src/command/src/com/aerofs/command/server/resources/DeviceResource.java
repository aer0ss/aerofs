/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.command.server.resources;

import com.aerofs.command.server.ResourceConstants;
import com.aerofs.base.id.DID;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.aerofs.verkehr.client.lib.admin.VerkehrAdmin;

import javax.ws.rs.Path;

@Path("/" + ResourceConstants.DEVICES_PATH)
public final class DeviceResource
{
    private final DID _did;

    private final JedisThreadLocalTransaction _trans;
    private final VerkehrAdmin _verkehr;

    public DeviceResource(DID did, JedisThreadLocalTransaction trans, VerkehrAdmin verkehr)
    {
        _did = did;
        _trans = trans;
        _verkehr = verkehr;
    }

    @Path("/{" + ResourceConstants.ENQUEUES_PATH + "}")
    public EnqueuesResource getDevice()
    {
        return new EnqueuesResource(_did, _trans, _verkehr);
    }
}