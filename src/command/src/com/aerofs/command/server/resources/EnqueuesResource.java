/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.command.server.resources;

import com.aerofs.base.id.DID;
import com.aerofs.command.server.ResourceConstants;
import com.aerofs.proto.Cmd.CommandType;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.aerofs.verkehr.client.lib.admin.VerkehrAdmin;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;

import static com.google.common.base.Strings.isNullOrEmpty;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

@Path("/" + ResourceConstants.ENQUEUES_PATH)
public final class EnqueuesResource
{
    private final DID _did;

    private final JedisThreadLocalTransaction _trans;
    private final VerkehrAdmin _verkehr;

    public EnqueuesResource(DID did, JedisThreadLocalTransaction trans, VerkehrAdmin verkehr)
    {
        _did = did;
        _trans = trans;
        _verkehr = verkehr;
    }

    @Path("/{" + ResourceConstants.ENQUEUES_SUBRESOURCE + "}")
    public EnqueueResource getDevice(
            @PathParam(ResourceConstants.ENQUEUES_SUBRESOURCE) String commandType)
            throws Exception
    {
        if (isNullOrEmpty(commandType)) {
            throw new WebApplicationException(BAD_REQUEST);
        }

        CommandType type;
        try {
            type = CommandType.valueOf(commandType);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(BAD_REQUEST);
        }

        return new EnqueueResource(type, _did, _trans, _verkehr);
    }
}