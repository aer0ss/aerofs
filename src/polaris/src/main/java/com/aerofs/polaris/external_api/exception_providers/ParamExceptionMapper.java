package com.aerofs.polaris.external_api.exception_providers;

import com.aerofs.rest.api.Error;
import org.glassfish.jersey.server.ParamException;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

// N.B. Duplicate of ParamExceptionMapper in daemon but daemon uses jersey 1.x and baseline/polaris
// uses 2.x. Could potentially delete the one in daemon at a later time once we switch to Polaris
// sync world completely.
@Provider
public class ParamExceptionMapper implements ExceptionMapper<ParamException>
{
    private static String paramterType(ParamException e)
    {
        if (e instanceof ParamException.HeaderParamException) return "header";
        if (e instanceof ParamException.QueryParamException) return "query parameter";
        if (e instanceof ParamException.PathParamException) return "path parameter";
        return "parameter";
    }

    @Override
    public Response toResponse(ParamException e)
    {
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(new com.aerofs.rest.api.Error(Error.Type.BAD_ARGS,
                        String.format("Invalid %s: %s", paramterType(e), e.getParameterName())))
                .build();
    }
}
