package com.aerofs.restless.providers;

import com.aerofs.restless.Configuration;
import com.google.inject.Inject;
import com.sun.jersey.api.NotFoundException;

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
    private final Configuration _config;

    @Inject
    public NotFoundMapper(Configuration config)
    {
        _config = config;
    }

    @Override
    public Response toResponse(NotFoundException exception)
    {
        return _config.resourceNotFound(exception.getNotFoundUri().getPath());
    }
}
