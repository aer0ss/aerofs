/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.rest.providers;

import com.aerofs.rest.api.Error;
import com.aerofs.rest.api.Error.Type;
import com.sun.jersey.api.ParamException;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * Return 400 instead of 404 when request parameters are invalid
 */
@Provider
public class ParamExceptionMapper implements ExceptionMapper<ParamException>
{
    @Override
    public Response toResponse(ParamException e)
    {
        return Response.status(Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(new Error(Type.BAD_ARGS,
                        String.format("Invalid parameter: %s", e.getParameterName())))
                .build();
    }
}
