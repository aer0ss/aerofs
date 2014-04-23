/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.command.server.resources;

import com.aerofs.base.Loggers;
import com.aerofs.proto.Cmd.Command;
import com.aerofs.proto.Cmd.CommandType;
import com.aerofs.command.server.ResourceConstants;
import com.aerofs.base.id.DID;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue.Epoch;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.aerofs.verkehr.client.lib.admin.VerkehrAdmin;
import org.slf4j.Logger;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import static com.aerofs.sp.server.CommandUtil.createCommandMessage;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/" + ResourceConstants.ENQUEUES_SUBRESOURCE)
public final class EnqueueResource
{
    private static final Logger l = Loggers.getLogger(EnqueueResource.class);

    private final CommandType _type;
    private final DID _did;

    private final JedisThreadLocalTransaction _trans;
    private final VerkehrAdmin _verkehr;
    private final JedisEpochCommandQueue _queue;

    public EnqueueResource(CommandType type, DID did, JedisThreadLocalTransaction trans,
            VerkehrAdmin verkehr)
    {
        _type = type;
        _did = did;
        _trans = trans;
        _verkehr = verkehr;
        _queue = new JedisEpochCommandQueue(_trans);
    }

    @POST
    @Produces(APPLICATION_JSON)
    public void postCommandType() throws Exception
    {
        l.info("ENQUEUE did=" + _did.toStringFormal() + " type=" + _type);

        _trans.begin();
        Epoch epoch = _queue.enqueue(_did, createCommandMessage(_type));
        _trans.commit();

        Command command = Command.newBuilder()
                .setEpoch(epoch.get())
                .setType(_type)
                .build();
        _verkehr.deliverPayload_(_did.toStringFormal(), command.toByteArray()).get();
    }
}