/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.rest.providers;

import com.aerofs.rest.api.Error;
import com.aerofs.rest.api.Error.Type;
import com.google.gson.JsonParseException;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class JsonExceptionMapper implements ExceptionMapper<JsonParseException>
{
    @Override
    public Response toResponse(JsonParseException e)
    {
        return Response.status(Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(new Error(Type.BAD_ARGS,
                        String.format("Invalid JSON input")))
                .build();
    }
}
