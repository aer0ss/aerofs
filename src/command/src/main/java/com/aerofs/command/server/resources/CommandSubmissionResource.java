/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.command.server.resources;

import com.aerofs.base.BaseParam;
import com.aerofs.base.id.DID;
import com.aerofs.ids.validation.Identifier;
import com.aerofs.proto.Cmd;
import com.aerofs.servlets.lib.db.jedis.JedisEpochCommandQueue;
import com.aerofs.servlets.lib.db.jedis.JedisThreadLocalTransaction;
import com.aerofs.sp.server.CommandUtil;
import com.aerofs.verkehr.client.rest.VerkehrClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

@Path("/devices")
public final class CommandSubmissionResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandSubmissionResource.class);

    @Path("/{device}/queues/{queue}")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public void submitCommand(
            @Context JedisThreadLocalTransaction transaction,
            @Context VerkehrClient verkehr,
            @PathParam("device") @Identifier String device,
            @PathParam("queue" ) @NotNull @Size(min = 1) String commandType) throws Exception {
        DID did = new DID(device);
        String stringifiedDevice = did.toStringFormal();
        String commandMessage = CommandUtil.createCommandMessage(Cmd.CommandType.valueOf(commandType));

        LOGGER.info("ENQUEUE did={} message={}", stringifiedDevice, commandMessage);

        JedisEpochCommandQueue queue = new JedisEpochCommandQueue(transaction);
        transaction.begin();
        try {
            JedisEpochCommandQueue.Epoch epoch = queue.enqueue(did, commandMessage);
            transaction.commit(); // send to redis before publishing to vk
            Cmd.Command command = CommandUtil.createCommandFromMessage(commandMessage, epoch.get());
            verkehr.publish(BaseParam.Topics.getCMDTopic(stringifiedDevice, true), command.toByteArray()).get();
        } catch (Exception e) {
            LOGGER.warn("FAIL ENQUEUE did={} message={}", stringifiedDevice, commandMessage, e);
            transaction.rollback();
        }
    }
}
