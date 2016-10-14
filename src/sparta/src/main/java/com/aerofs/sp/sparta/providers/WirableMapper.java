/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.sparta.providers;

import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExExternalServiceUnavailable;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.rest.api.Error;
import com.aerofs.rest.api.Error.Type;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;

public class WirableMapper implements ExceptionMapper<AbstractExWirable>
{
    @Override
    public Response toResponse(AbstractExWirable e)
    {
        if (e instanceof ExBadArgs) {
            return Response.status(Status.BAD_REQUEST)
                    .entity(new Error(Type.BAD_ARGS, e.getMessage()))
                    .build();
        } else if (e instanceof ExNotFound) {
            return Response.status(Status.NOT_FOUND)
                    .entity(new Error(Type.NOT_FOUND, e.getMessage()))
                    .build();
        } else if (e instanceof ExAlreadyExist) {
            return Response.status(Status.CONFLICT)
                    .entity(new Error(Type.CONFLICT, e.getMessage()))
                    .build();
        } else if (e instanceof ExNoPerm) {
            return Response.status(Status.FORBIDDEN)
                    .entity(new Error(Type.FORBIDDEN, e.getMessage()))
                    .build();
        } else if (e instanceof ExExternalServiceUnavailable) {
            return Response.status(Status.SERVICE_UNAVAILABLE)
                    .entity(new Error(Type.INTERNAL_ERROR, e.getMessage()))
                    .build();
        }
        return Response.status(Status.INTERNAL_SERVER_ERROR)
                .entity(new Error(Type.INTERNAL_ERROR, "Internal error: " + e.getClass()))
                .build();
    }
}
