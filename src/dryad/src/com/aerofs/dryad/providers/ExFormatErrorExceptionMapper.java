/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.dryad.providers;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.rest.providers.RuntimeExceptionMapper;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;

public class ExFormatErrorExceptionMapper implements  ExceptionMapper<ExFormatError>
{
    @Override
    public Response toResponse(ExFormatError e)
    {
        Loggers.getLogger(RuntimeExceptionMapper.class).warn("ex ", e);
        return Response.status(Status.BAD_REQUEST).build();
    }
}
