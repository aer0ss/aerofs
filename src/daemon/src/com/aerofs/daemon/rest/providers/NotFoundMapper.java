package com.aerofs.daemon.rest.providers;

import com.aerofs.rest.api.Error;
import com.aerofs.proto.Common.PBException.Type;
import com.sun.jersey.api.NotFoundException;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * Return custom response when a requested URI is not explicitly handled
 * (Jersey would return a 404 with empty body which would be inconsistent
 * with the API docs saying we always return an Error object)
 */
@Provider
public class NotFoundMapper implements ExceptionMapper<NotFoundException>
{
    @Override
    public Response toResponse(NotFoundException exception){

        return Response.status(Response.Status.NOT_FOUND)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new Error(Type.NOT_FOUND.name(), "No such resource"))
                .build();
    }
}
