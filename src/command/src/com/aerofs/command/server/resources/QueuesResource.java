/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.command.server.resources;

import com.aerofs.base.id.DID;
import com.aerofs.proto.Cmd.CommandType;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.aerofs.sp.server.CommandUtil;
import com.aerofs.verkehr.client.rest.VerkehrClient;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;

import static com.google.common.base.Strings.isNullOrEmpty;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

public final class QueuesResource
{
    private final DID _did;
    private final JedisThreadLocalTransaction _trans;
    private final VerkehrClient _verkehr;

    public QueuesResource(DID did, JedisThreadLocalTransaction trans, VerkehrClient verkehr)
    {
        _did = did;
        _trans = trans;
        _verkehr = verkehr;
    }

    @Path("/{queue}")
    public QueueResource getQueue(@PathParam("queue") String commandType) throws Exception
    {
        if (isNullOrEmpty(commandType)) {
            throw new WebApplicationException(BAD_REQUEST);
        }

        String commandMessage;
        try {
            commandMessage = CommandUtil.createCommandMessage(CommandType.valueOf(commandType));
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(BAD_REQUEST);
        }

        return new QueueResource(commandMessage, _did, _trans, _verkehr);
    }
}
