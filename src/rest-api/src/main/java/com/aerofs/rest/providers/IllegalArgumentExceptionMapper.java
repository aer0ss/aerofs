/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.rest.providers;

import com.aerofs.rest.api.Error;
import com.aerofs.rest.api.Error.Type;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * Allow use of {@link com.google.common.base.Preconditions#checkArgument} for input validation.
 */
@Provider
public class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException>
{
    @Override
    public Response toResponse(IllegalArgumentException e)
    {
        return Response.status(Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new Error(Type.BAD_ARGS, "Invalid parameter: " + e.getMessage()))
                .build();
    }
}
