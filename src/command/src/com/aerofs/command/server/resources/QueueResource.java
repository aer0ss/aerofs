/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.command.server.resources;

import com.aerofs.base.BaseParam.Topics;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.proto.Cmd.Command;
import com.aerofs.proto.Cmd.CommandType;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue.Epoch;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.aerofs.verkehr.client.rest.VerkehrClient;
import org.slf4j.Logger;

import javax.ws.rs.POST;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import static com.aerofs.sp.server.CommandUtil.createCommandMessage;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

public final class QueueResource
{
    private static final Logger l = Loggers.getLogger(QueueResource.class);

    private final CommandType _type;
    private final DID _did;

    private final JedisThreadLocalTransaction _trans;
    private final VerkehrClient _verkehr;
    private final JedisEpochCommandQueue _queue;

    public QueueResource(CommandType type, DID did, JedisThreadLocalTransaction trans, VerkehrClient verkehr)
    {
        _type = type;
        _did = did;
        _trans = trans;
        _verkehr = verkehr;
        _queue = new JedisEpochCommandQueue(_trans);
    }

    @POST
    @Produces(APPLICATION_JSON)
    public Response postCommandType() throws Exception
    {
        l.info("ENQUEUE did=" + _did.toStringFormal() + " type=" + _type);

        _trans.begin();
        Epoch epoch = _queue.enqueue(_did, createCommandMessage(_type));
        _trans.commit();

        Command command = Command.newBuilder()
                .setEpoch(epoch.get())
                .setType(_type)
                .build();
        _verkehr.publish(Topics.getCMDTopic(_did.toStringFormal(), true), command.toByteArray()).get();

        return Response.noContent().build();
    }
}
