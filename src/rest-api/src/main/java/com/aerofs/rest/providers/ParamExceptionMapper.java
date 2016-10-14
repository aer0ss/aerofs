/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.rest.providers;

import com.aerofs.rest.api.Error;
import com.aerofs.rest.api.Error.Type;
import com.sun.jersey.api.ParamException;
import com.sun.jersey.api.ParamException.HeaderParamException;
import com.sun.jersey.api.ParamException.PathParamException;
import com.sun.jersey.api.ParamException.QueryParamException;

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
    private static String paramterType(ParamException e)
    {
        if (e instanceof HeaderParamException) return "header";
        if (e instanceof QueryParamException) return "query parameter";
        if (e instanceof PathParamException) return "path parameter";
        return "parameter";
    }

    @Override
    public Response toResponse(ParamException e)
    {
        return Response.status(Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(new Error(Type.BAD_ARGS,
                        String.format("Invalid %s: %s", paramterType(e), e.getParameterName())))
                .build();
    }
}
