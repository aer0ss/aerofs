/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.command.server.resources;

import com.aerofs.proto.Cmd.CommandType;
import com.google.common.collect.Lists;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.util.List;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/command_types")
public final class CommandTypesResource
{
    @GET
    @Produces(APPLICATION_JSON)
    public List<String> getCommandTypes()
    {
        List<String> result = Lists.newLinkedList();

        for (CommandType type : CommandType.values()) {
            result.add(type.toString());
        }

        return result;
    }
}