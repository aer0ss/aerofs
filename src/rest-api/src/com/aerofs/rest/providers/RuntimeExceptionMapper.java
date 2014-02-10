/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.rest.providers;

import com.aerofs.base.Loggers;
import com.aerofs.rest.api.Error;
import com.aerofs.rest.api.Error.Type;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * Return 500 instead of closing connection on runtime exception
 * This prevents nginx throwing a 502 when the gateway closes the downstream connection.
 */
@Provider
public class RuntimeExceptionMapper implements ExceptionMapper<RuntimeException>
{
    @Override
    public Response toResponse(RuntimeException e)
    {
        if (e instanceof WebApplicationException) {
            return ((WebApplicationException)e).getResponse();
        }
        Loggers.getLogger(RuntimeExceptionMapper.class).warn("ex ", e);
        return Response.status(Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new Error(Type.INTERNAL_ERROR, "Internal error while servicing request"))
                .build();
    }
}
