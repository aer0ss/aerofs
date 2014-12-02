/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.command.server.resources;

import com.aerofs.proto.Cmd.CommandType;
import com.google.common.collect.ImmutableList;

import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.util.Arrays;
import java.util.List;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/command_types")
@Singleton
public final class CommandTypesResource {

    @SuppressWarnings("Convert2MethodRef")
    private static final List<String> COMMAND_TYPES = ImmutableList.copyOf(Arrays.stream(CommandType.values()).map(type -> type.toString()).iterator());

    @GET
    @Produces(APPLICATION_JSON)
    public List<String> getCommandTypes() {
        return COMMAND_TYPES;
    }
}